package com.fasthire.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * SmartRecruiters public posting API: GET api.smartrecruiters.com/v1/companies/{id}/postings (paged with
 * limit/offset, "totalFound" in the reply) and .../postings/{postingId} for the description. Add
 * {@code ?search=India} (or {@code ?q=}) to the sheet URL to narrow the list on the platform's side.
 */
@Component
public class SmartRecruitersJobSource implements JobSource {
    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 20;
    private static final String[] SECTIONS = {"companyDescription", "jobDescription", "qualifications", "additionalInformation"};

    private final Fetcher fetcher;
    private final ObjectMapper mapper = new ObjectMapper();

    public SmartRecruitersJobSource(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String atsType() {
        return "SMARTRECRUITERS";
    }

    @Override
    public List<RawJob> fetch(SourceRef src) throws IOException {
        String q = SourceParams.get(src.url(), "search", "q");
        String base = "https://api.smartrecruiters.com/v1/companies/" + src.atsToken() + "/postings?limit=" + PAGE_SIZE
            + (q == null ? "" : "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
        List<RawJob> out = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode root = mapper.readTree(fetcher.get(base + "&offset=" + offset));
            JsonNode content = root.path("content");
            if (!content.isArray() || content.isEmpty()) {
                break;
            }
            for (JsonNode p : content) {
                out.add(toJob(p, src));
            }
            offset += content.size();
            if (offset >= root.path("totalFound").asInt(0)) {
                break;
            }
        }
        return out;
    }

    /** The list has no description; fetch it for jobs we are about to store. */
    @Override
    public RawJob enrich(SourceRef src, RawJob job) throws IOException {
        JsonNode d = mapper.readTree(fetcher.get(
            "https://api.smartrecruiters.com/v1/companies/" + src.atsToken() + "/postings/" + job.externalId()));
        StringBuilder text = new StringBuilder(job.description());
        JsonNode sections = d.path("jobAd").path("sections");
        for (String s : SECTIONS) {
            String html = sections.path(s).path("text").asText("");
            if (!html.isBlank()) {
                String title = sections.path(s).path("title").asText("");
                text.append("\n\n").append(title.isBlank() ? "" : title + "\n").append(Html.toText(html));
            }
        }
        String url = d.path("postingUrl").asText(job.url());
        return new RawJob(job.externalId(), job.employerName(), job.title(), job.location(), text.toString().trim(),
            url, job.postedAt(), job.displayId());
    }

    private RawJob toJob(JsonNode p, SourceRef src) {
        StringBuilder facts = new StringBuilder();
        fact(facts, "Experience level", p.path("experienceLevel").path("label").asText(""));
        fact(facts, "Employment type", p.path("typeOfEmployment").path("label").asText(""));
        fact(facts, "Department", p.path("department").path("label").asText(""));
        String id = p.path("id").asText();
        String company = p.path("company").path("identifier").asText(src.atsToken());
        return new RawJob(id,
            p.path("company").path("name").asText(Employer.name(src, src.atsToken())),
            p.path("name").asText(),
            location(p.path("location")),
            facts.toString().trim(),
            "https://jobs.smartrecruiters.com/" + company + "/" + id,
            posted(p.path("releasedDate").asText("")),
            p.path("refNumber").asText(null));
    }

    /** "Hyderabad, Telangana, India": SmartRecruiters gives the country as a two-letter code. */
    static String location(JsonNode loc) {
        Set<String> parts = new LinkedHashSet<>();
        for (String field : new String[] {"city", "region"}) {
            String v = loc.path(field).asText("");
            if (!v.isBlank()) {
                parts.add(v);
            }
        }
        String code = loc.path("country").asText("");
        if (!code.isBlank()) {
            String name = code.length() == 2 ? Locale.of("", code).getDisplayCountry(Locale.ENGLISH) : code;
            parts.add(name.isBlank() ? code : name);
        }
        if (loc.path("remote").asBoolean(false)) {
            parts.add("Remote");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static void fact(StringBuilder sb, String label, String value) {
        if (!value.isBlank()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private static Instant posted(String iso) {
        try {
            return iso.isBlank() ? null : OffsetDateTime.parse(iso).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
