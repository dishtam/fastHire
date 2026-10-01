package com.fasthire.resume;

import com.fasthire.resume.ResumeProfile.Bullet;
import com.fasthire.resume.ResumeProfile.Entry;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the resume as strict XHTML. Deliberately plain for ATS parsers: one column, no tables, no images,
 * standard section names. No floats or absolute positioning either: PDF text extractors read the content
 * stream, and floated text is written out of document order.
 */
final class ResumeHtml {
    private static final CharsetEncoder WIN_ANSI = Charset.forName("windows-1252").newEncoder();
    private static final Map<String, String> GROUP_LABELS = Map.of(
        "languages", "Languages", "backend", "Backend", "testing", "Testing & QA", "frontend", "Frontend",
        "cloud_devops", "Cloud & DevOps", "ai_genai", "AI / GenAI", "practices", "Engineering Practices",
        "domains", "Domains");

    private ResumeHtml() {}

    static String build(ResumeProfile p, Selection sel) {
        StringBuilder h = new StringBuilder("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml"><head><title>""");
        h.append(esc(p.name())).append(" - Resume</title><style>").append(CSS).append("</style></head><body>");
        h.append("<h1>").append(esc(p.name())).append("</h1>");
        h.append("<p class=\"contact\">").append(esc(String.join(" | ", contact(p)))).append("</p>");

        if (!sel.summary().isBlank()) {
            h.append("<h2>Professional Summary</h2><p>").append(esc(sel.summary())).append("</p>");
        }

        h.append("<h2>Experience</h2>");
        for (Entry e : p.experience()) {
            List<Bullet> chosen = chosen(e, sel);
            if (chosen.isEmpty()) {
                continue;
            }
            h.append("<p class=\"head\">").append(esc(e.heading())).append("</p>");
            h.append("<p class=\"meta\">").append(esc(joined(e.org(), e.location(), e.dates()))).append("</p>");
            bullets(h, chosen, sel);
        }

        List<Entry> projects = p.projects().stream().filter(e -> !chosen(e, sel).isEmpty()).toList();
        if (!projects.isEmpty()) {
            h.append("<h2>Projects</h2>");
            for (Entry e : projects) {
                h.append("<p class=\"head\">").append(esc(e.heading())).append("</p>");
                bullets(h, chosen(e, sel), sel);
            }
        }

        if (!sel.skills().isEmpty()) {
            h.append("<h2>Technical Skills</h2>");
            for (var g : p.skills().entrySet()) {
                List<String> inGroup = sel.skills().stream().filter(g.getValue()::contains).toList();
                if (!inGroup.isEmpty()) {
                    h.append("<p class=\"skill\"><b>").append(esc(label(g.getKey()))).append(":</b> ")
                        .append(esc(String.join(", ", inGroup))).append("</p>");
                }
            }
        }

        if (!p.education().isEmpty()) {
            h.append("<h2>Education</h2>");
            for (var ed : p.education()) {
                h.append("<p class=\"head\">").append(esc(ed.degree())).append("</p>");
                h.append("<p class=\"meta\">").append(esc(joined(ed.school(), ed.year(),
                    ed.cgpa().isEmpty() ? "" : "CGPA: " + ed.cgpa()))).append("</p>");
            }
        }

        if (!p.achievements().isEmpty()) {
            h.append("<h2>Achievements</h2><ul>");
            p.achievements().forEach(a -> h.append("<li>").append(esc(a)).append("</li>"));
            h.append("</ul>");
        }
        return h.append("</body></html>").toString();
    }

    /** Joins the non-blank parts with " | ". */
    private static String joined(String... parts) {
        List<String> out = new ArrayList<>();
        for (String s : parts) {
            if (s != null && !s.isBlank()) {
                out.add(s);
            }
        }
        return String.join(" | ", out);
    }

    private static List<String> contact(ResumeProfile p) {
        List<String> parts = new ArrayList<>();
        for (String s : List.of(p.location(), p.phone(), p.email())) {
            if (!s.isBlank()) {
                parts.add(s);
            }
        }
        // Profile links are only useful when they are real URLs, not placeholder words.
        for (String s : List.of(p.linkedin(), p.github())) {
            if (s.startsWith("http")) {
                parts.add(s);
            }
        }
        return parts;
    }

    private static List<Bullet> chosen(Entry e, Selection sel) {
        return sel.bulletIds().stream()
            .flatMap(id -> e.bullets().stream().filter(b -> b.id().equals(id)))
            .toList();
    }

    private static void bullets(StringBuilder h, List<Bullet> chosen, Selection sel) {
        h.append("<ul>");
        chosen.forEach(b -> h.append("<li>").append(esc(sel.text().getOrDefault(b.id(), b.text()))).append("</li>"));
        h.append("</ul>");
    }

    private static String label(String key) {
        if (GROUP_LABELS.containsKey(key)) {
            return GROUP_LABELS.get(key);
        }
        String spaced = key.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /** XML-escapes and drops characters the standard PDF fonts cannot show. */
    static String esc(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\n' || c == '\t' || WIN_ANSI.canEncode(c) && c >= 32 || c == ' ') {
                switch (c) {
                    case '&' -> out.append("&amp;");
                    case '<' -> out.append("&lt;");
                    case '>' -> out.append("&gt;");
                    case '"' -> out.append("&quot;");
                    default -> out.append(c);
                }
            }
        }
        return out.toString();
    }

    private static final String CSS = """
        @page { size: A4; margin: 10mm 14mm; }
        body { font-family: Helvetica, sans-serif; font-size: 9.5pt; line-height: 1.25; color: #000; }
        h1 { font-size: 18pt; margin: 0 0 2pt 0; text-align: center; }
        p { margin: 0 0 3pt 0; }
        p.contact { text-align: center; margin-bottom: 4pt; }
        h2 { font-size: 10.5pt; margin: 7pt 0 3pt 0; padding-bottom: 1pt; border-bottom: 0.75pt solid #000; }
        p.head { margin: 4pt 0 0 0; font-weight: bold; }
        p.meta { margin: 0 0 1pt 0; font-style: italic; }
        ul { margin: 2pt 0 3pt 0; padding-left: 13pt; }
        li { margin: 0 0 1.5pt 0; }
        p.skill { margin-bottom: 2pt; }
        """;
}
