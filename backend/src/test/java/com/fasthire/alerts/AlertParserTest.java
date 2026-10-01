package com.fasthire.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertParserTest {
    private final AlertParser parser = new AlertParser();

    private static String fixture(String name) throws IOException {
        try (var in = AlertParserTest.class.getResourceAsStream("/fixtures/alerts/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static EmailContent html(String html) {
        return new EmailContent("<id>", "alerts@example.com", "Jobs for you", html, "", Instant.now());
    }

    @Test void linkedinCardsAreMergedAndRead() throws Exception {
        List<AlertJob> jobs = parser.parse(html(fixture("linkedin_synthetic.html")));
        assertThat(jobs).hasSize(2); // the title link and the "View job" button are one job; search/unsubscribe links ignored

        AlertJob a = jobs.get(0);
        assertThat(a.board()).isEqualTo(Board.LINKEDIN);
        assertThat(a.externalId()).isEqualTo("3912345678");
        assertThat(a.title()).isEqualTo("Senior Java Developer");
        assertThat(a.employer()).isEqualTo("Acme Technologies");
        assertThat(a.location()).isEqualTo("Dubai, United Arab Emirates");
        assertThat(a.url()).isEqualTo("https://www.linkedin.com/comm/jobs/view/3912345678"); // tracking params dropped
        assertThat(a.snippet()).doesNotContain("Actively recruiting", "View job");

        AlertJob b = jobs.get(1);
        assertThat(b.externalId()).isEqualTo("3987654321"); // id taken from the end of a slug URL
        assertThat(b.employer()).isEqualTo("Noon");
        assertThat(b.location()).isEqualTo("Abu Dhabi, United Arab Emirates");
    }

    @Test void baytUnwrapsTrackingRedirectsAndReadsCompanyAndLocation() throws Exception {
        List<AlertJob> jobs = parser.parse(html(fixture("bayt_synthetic.html")));
        assertThat(jobs).hasSize(2);
        AlertJob a = jobs.get(0);
        assertThat(a.board()).isEqualTo(Board.BAYT);
        assertThat(a.externalId()).isEqualTo("4895123");
        assertThat(a.url()).isEqualTo("https://www.bayt.com/en/uae/jobs/java-developer-4895123/");
        assertThat(a.employer()).isEqualTo("Emirates Group");
        assertThat(a.location()).isEqualTo("Dubai, UAE");
        assertThat(a.snippet()).contains("Spring Boot");
        assertThat(jobs.get(1).title()).isEqualTo("Embedded Software Engineer");
        assertThat(jobs.get(1).employer()).isEqualTo("Axiom Devices");
    }

    @Test void naukriKeepsExperienceAndSkillsAsSnippet() throws Exception {
        List<AlertJob> jobs = parser.parse(html(fixture("naukri_synthetic.html")));
        assertThat(jobs).hasSize(2);
        AlertJob a = jobs.get(0);
        assertThat(a.board()).isEqualTo(Board.NAUKRI);
        assertThat(a.externalId()).isEqualTo("010124000123");
        assertThat(a.employer()).isEqualTo("Infosys");
        assertThat(a.location()).isEqualTo("Hyderabad");
        assertThat(a.snippet()).contains("2-5 Yrs", "Java, Spring Boot, Microservices");
        assertThat(jobs.get(1).employer()).isEqualTo("Zoho");
        assertThat(jobs.get(1).snippet()).isEmpty();
    }

    @Test void naukrigulfLinkIsRecognised() throws Exception {
        List<AlertJob> jobs = parser.parse(html(fixture("naukrigulf_synthetic.html")));
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).board()).isEqualTo(Board.NAUKRIGULF);
        assertThat(jobs.get(0).externalId()).isEqualTo("310124500123");
        assertThat(jobs.get(0).employer()).isEqualTo("Careem");
    }

    @Test void plainTextEmailsGiveTitleAndLink() {
        EmailContent e = new EmailContent("<t>", "x@y", "Alert", "", """
            Your job alert

            Java Developer
            https://www.bayt.com/en/uae/jobs/java-developer-4895123/?utm=x

            Backend Engineer
            https://www.linkedin.com/comm/jobs/view/3912345678?trk=1
            """, Instant.now());
        List<AlertJob> jobs = parser.parse(e);
        assertThat(jobs).extracting(AlertJob::title).containsExactly("Java Developer", "Backend Engineer");
        assertThat(jobs).extracting(AlertJob::externalId).containsExactly("4895123", "3912345678");
    }

    @Test void emailsWithoutJobLinksYieldNothing() {
        assertThat(parser.parse(html("<p>Welcome! <a href=\"https://www.linkedin.com/feed/\">Open</a></p>"))).isEmpty();
        assertThat(parser.parse(new EmailContent("<t>", "x@y", "s", "", "just text", Instant.now()))).isEmpty();
    }

    @Test void aLinkWithNoReadableTitleIsSkipped() {
        assertThat(parser.parse(html("<a href=\"https://www.linkedin.com/comm/jobs/view/3912345678\">View job</a>")))
            .isEmpty();
    }

    @Test void regionComesFromLocationThenBoardDefaultThenFallback() {
        assertThat(RegionGuesser.guess("Dubai, United Arab Emirates", null, "UAE")).isEqualTo("UAE");
        assertThat(RegionGuesser.guess("Bengaluru, Karnataka", "UAE", "UAE")).isEqualTo("IN");
        assertThat(RegionGuesser.guess("Remote", "IN", "UAE")).isEqualTo("IN");
        assertThat(RegionGuesser.guess(null, null, "UAE")).isEqualTo("UAE");
    }
}
