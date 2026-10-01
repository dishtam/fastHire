package com.fasthire.resume;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasthire.llm.LlmClient;
import com.fasthire.resume.ResumeProfile.Bullet;
import com.fasthire.resume.ResumeProfile.Entry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Asks the LLM which bullets to use, in what order, and how to word them for a job, then validates everything
 * against the profile. The model can only choose and lightly rephrase; it can never add a fact.
 */
@Component
public class ResumeSelector {
    static final int MAX_BULLETS_PER_ENTRY = 4;
    static final int MAX_SKILLS = 20;
    static final int MAX_DESCRIPTION_CHARS = 6000;
    static final String SYSTEM = """
        You tailor a resume to a job by selecting and ordering the candidate's existing bullets. You never \
        add facts.
        Reply with JSON only: {"summary": "<2-3 sentences>", "skills": ["<skill>", ...], \
        "bullets": [{"id": "<bullet id>", "text": "<bullet text>"}, ...]}.
        Rules:
        - bullets: pick the bullets most relevant to the job and list them best first. Keep at least 1 and at \
        most 4 per experience entry; projects are optional. Use only the ids you are given.
        - text: either the original bullet exactly, or a light rephrase that uses the job's wording ONLY where \
        the original already says the same thing. A rephrase must not add any technology, skill, tool, number, \
        scope or responsibility that the original bullet does not contain.
        - skills: up to 20, chosen only from the provided skill list, spelled exactly as given, most relevant \
        to the job first. Do not include skills the candidate lacks, even if the job asks for them.
        - summary: 2-3 sentences using only facts in the candidate's summary and skills, angled to the job.""";

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResumeSelector(LlmClient llm) {
        this.llm = llm;
    }

    public Selection select(ResumeProfile p, String title, String employer, String description) throws IOException {
        String user = prompt(p, title, employer, description);
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return validate(p, mapper.readTree(llm.completeJson(SYSTEM, user)));
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    String prompt(ResumeProfile p, String title, String employer, String description) {
        StringBuilder sb = new StringBuilder("JOB\nTitle: " + title + "\nEmployer: " + nz(employer)
            + "\nDescription:\n" + truncate(nz(description)) + "\n\nCANDIDATE\nSummary: " + p.summary()
            + "\nSkills (use exactly these names): " + String.join(", ", p.allSkills()) + "\n\nEXPERIENCE ENTRIES\n");
        appendEntries(sb, p.experience());
        sb.append("\nPROJECTS\n");
        appendEntries(sb, p.projects());
        return sb.toString();
    }

    private static void appendEntries(StringBuilder sb, List<Entry> entries) {
        for (Entry e : entries) {
            sb.append("- ").append(e.heading()).append(e.org().isEmpty() ? "" : " at " + e.org()).append('\n');
            for (Bullet b : e.bullets()) {
                sb.append("    ").append(b.id()).append(": ").append(b.text()).append('\n');
            }
        }
    }

    /** Applies every safety rule. Bad parts fall back to the original wording instead of failing the resume. */
    Selection validate(ResumeProfile p, JsonNode reply) throws IOException {
        if (!reply.path("bullets").isArray()) {
            throw new IOException("Reply has no bullets list");
        }
        Map<String, Bullet> byId = new HashMap<>();
        p.allBullets().forEach(b -> byId.put(b.id(), b));

        LinkedHashMap<String, String> chosen = new LinkedHashMap<>();
        for (JsonNode n : reply.path("bullets")) {
            Bullet b = byId.get(n.path("id").asText());
            if (b == null || chosen.containsKey(b.id())) {
                continue;
            }
            String text = n.path("text").asText("").trim();
            boolean keep = !text.isEmpty() && (text.equals(b.text()) || RewriteGuard.faithfulBullet(b.text(), b.tags(), text));
            chosen.put(b.id(), keep ? text : b.text());
        }

        List<String> ids = new ArrayList<>();
        for (Entry e : p.experience()) {
            List<String> mine = chosen.keySet().stream().filter(id -> e.bullets().stream().anyMatch(b -> b.id().equals(id))).toList();
            if (mine.isEmpty() && !e.bullets().isEmpty()) {
                Bullet first = e.bullets().get(0);
                chosen.put(first.id(), first.text());
                mine = List.of(first.id());
            }
        }
        Map<String, Integer> perEntry = new HashMap<>();
        for (String id : chosen.keySet()) {
            Entry owner = ownerOf(p, id);
            int used = perEntry.merge(owner.heading() + "|" + owner.org(), 1, Integer::sum);
            if (owner.org().isEmpty() || used <= MAX_BULLETS_PER_ENTRY) {
                ids.add(id);
            }
        }

        Map<String, String> text = new LinkedHashMap<>();
        ids.forEach(id -> text.put(id, chosen.get(id)));
        return new Selection(summary(p, reply), skills(p, reply), ids, text);
    }

    private static Entry ownerOf(ResumeProfile p, String id) {
        for (Entry e : p.experience()) {
            if (e.bullets().stream().anyMatch(b -> b.id().equals(id))) {
                return e;
            }
        }
        for (Entry e : p.projects()) {
            if (e.bullets().stream().anyMatch(b -> b.id().equals(id))) {
                return e;
            }
        }
        throw new IllegalStateException("No owner for " + id);
    }

    private static String summary(ResumeProfile p, JsonNode reply) {
        String s = reply.path("summary").asText("").trim();
        StringBuilder all = new StringBuilder(p.summary()).append(' ').append(String.join(" ", p.allSkills()));
        p.allBullets().forEach(b -> all.append(' ').append(b.text()).append(' ').append(String.join(" ", b.tags())));
        p.education().forEach(e -> all.append(' ').append(e.degree()).append(' ').append(e.school()));
        p.achievements().forEach(a -> all.append(' ').append(a));
        return RewriteGuard.faithfulSummary(s, all.toString()) ? s : p.summary();
    }

    private static List<String> skills(ResumeProfile p, JsonNode reply) {
        Map<String, String> canonical = new LinkedHashMap<>();
        p.allSkills().forEach(s -> canonical.putIfAbsent(s.toLowerCase(Locale.ROOT), s));
        Set<String> picked = new LinkedHashSet<>();
        for (JsonNode n : reply.path("skills")) {
            String c = canonical.get(n.asText("").trim().toLowerCase(Locale.ROOT));
            if (c != null && picked.size() < MAX_SKILLS) {
                picked.add(c);
            }
        }
        if (picked.isEmpty()) {
            p.allSkills().stream().limit(12).forEach(picked::add);
        }
        return new ArrayList<>(picked);
    }

    private static String truncate(String s) {
        return s.length() > MAX_DESCRIPTION_CHARS ? s.substring(0, MAX_DESCRIPTION_CHARS) : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
