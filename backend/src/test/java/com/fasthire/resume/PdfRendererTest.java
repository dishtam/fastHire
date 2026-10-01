package com.fasthire.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfRendererTest {
    private final ResumeProfile profile = ResumeProfile.parse(read("src/test/resources/fixtures/profile_test.yml"));

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Selection selection() {
        return new Selection("Backend engineer & Java \"specialist\" with 4 years <building> payment services.",
            List.of("Java", "Kafka", "Spring Boot", "AWS"),
            List.of("ex-2", "ex-1", "ac-1", "ot-1"),
            Map.of("ex-2", "Built Kafka consumers processing 2M events per day",
                "ex-1", "Cut API p95 latency from 800ms to 220ms by adding Redis caching",
                "ac-1", "Wrote unit tests with JUnit that raised coverage from 40% to 70%",
                "ot-1", "Built a REST API with Spring Boot and PostgreSQL serving 50 req/s"));
    }

    @Test
    void rendersAnAtsFriendlySingleColumnPdfWithTextInReadingOrder() throws Exception {
        byte[] pdf = new PdfRenderer().render(profile, selection());
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        try (PDDocument doc = PDDocument.load(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(doc);
            // Sections appear once each, in the expected order.
            List<String> order = List.of("Alex Sample", "Professional Summary", "Experience", "Software Engineer",
                "Example Corp | Pune, India | 2022-06 - Present", "Built Kafka consumers", "Cut API p95", "Intern",
                "Acme Ltd | Remote | 2021-06 - 2021-12", "Wrote unit tests", "Projects", "Order Tracker",
                "Built a REST API", "Technical Skills", "Languages: Java", "Education", "B.Tech, Computer Science",
                "Example University | 2018 - 2022 | CGPA: 8.5", "Achievements", "Top 5%");
            int last = -1;
            for (String s : order) {
                int at = text.indexOf(s);
                assertThat(at).as("'%s' present", s).isGreaterThan(-1);
                assertThat(at).as("'%s' after previous section", s).isGreaterThan(last);
                last = at;
            }
            // Special characters survive escaping; unselected bullets are absent.
            assertThat(text).contains("Backend engineer & Java \"specialist\" with 4 years <building>");
            assertThat(text).doesNotContain("Migrated a monolith");
            assertThat(text).doesNotContain("Deployed the service to AWS");
            // Contact line is plain text; placeholder link words are not printed.
            assertThat(text).contains("alex@example.com", "https://linkedin.com/in/alex-sample");
            assertThat(text).doesNotContain("GitHub");
            // Skills are grouped with the selected ones only.
            assertThat(text).contains("Languages: Java").contains("Backend: Kafka, Spring Boot");
            assertThat(text).doesNotContain("Python");
        }
    }

    @Test
    void dropsCharactersThePdfFontsCannotShow() throws Exception {
        Selection s = new Selection("Engineer – builds things 🚀 quickly", List.of("Java"),
            List.of("ex-1"), Map.of("ex-1", "Cut latency “by half”"));
        try (PDDocument doc = PDDocument.load(new PdfRenderer().render(profile, s))) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Engineer – builds things  quickly".replace("  ", " "), "by half");
        }
    }
}
