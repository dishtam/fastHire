package com.fasthire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasthire.alerts.AlertIngestService;
import com.fasthire.alerts.EmailContent;
import com.fasthire.alerts.MailboxClient;
import com.fasthire.llm.LlmClient;
import com.fasthire.pipeline.DailyPipeline;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AlertIngestIT extends AbstractDbTest {
    @Autowired AlertIngestService ingest;
    @Autowired DailyPipeline pipeline;
    @Autowired MockMvc mvc;
    @MockBean MailboxClient mailbox;
    @MockBean LlmClient llm;

    private static String fixture(String name) throws IOException {
        try (var in = AlertIngestIT.class.getResourceAsStream("/fixtures/alerts/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static EmailContent email(String id, String subject, String html) {
        return new EmailContent(id, "alerts@example.com", subject, html, "", Instant.now());
    }

    @BeforeEach
    void configured() throws Exception {
        when(mailbox.configured()).thenReturn(true);
    }

    @Test
    void storesJobsPerBoardAndRegionAndNeverProcessesAnEmailTwice() throws Exception {
        List<EmailContent> inbox = List.of(
            email("<1>", "LinkedIn alert", fixture("linkedin_synthetic.html")),
            email("<2>", "Naukri alert", fixture("naukri_synthetic.html")),
            email("<3>", "Newsletter", "<p>No jobs here</p>"));
        when(mailbox.fetchSince(any())).thenReturn(inbox);

        var first = ingest.ingest();
        assertThat(first.emails()).isEqualTo(3);
        assertThat(first.jobsFound()).isEqualTo(4);
        assertThat(first.newJobs()).isEqualTo(4);
        assertThat(first.emptyEmails()).isEqualTo(1);

        var sources = jdbc.queryForList("select region, url, kind, ats_type from source order by url");
        assertThat(sources).hasSize(2);
        assertThat(sources.get(0)).containsEntry("url", "email:linkedin").containsEntry("region", "UAE")
            .containsEntry("kind", "BOARD_EMAIL_ONLY").containsEntry("ats_type", "EMAIL");
        assertThat(sources.get(1)).containsEntry("url", "email:naukri").containsEntry("region", "IN");

        var job = jdbc.queryForMap("select * from job where external_id = '010124000123'");
        assertThat(job).containsEntry("title", "Java Developer").containsEntry("employer_name", "Infosys")
            .containsEntry("location", "Hyderabad").containsEntry("display_id", "010124000123");
        assertThat((String) job.get("description")).contains("2-5 Yrs", "Spring Boot");
        assertThat((String) job.get("url")).startsWith("https://www.naukri.com/job-listings-java-developer");

        var second = ingest.ingest(); // same mailbox contents
        assertThat(second.emails()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from job", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select jobs_found from processed_email where message_id = '<3>'", Integer.class))
            .isZero();
    }

    @Test
    void aJobAlreadyScrapedFromAnotherSourceIsNotDuplicated() throws Exception {
        long s = insertSource("UAE", "https://boards.greenhouse.io/acme", "ATS", "GREENHOUSE", "acme");
        jdbc.update("insert into job (source_id, external_id, employer_name, title, location, url) "
            + "values (?, 'g1', 'ACME TECHNOLOGIES', 'senior java developer', 'dubai, united arab emirates', 'u')", s);
        when(mailbox.fetchSince(any())).thenReturn(List.of(email("<1>", "LinkedIn alert", fixture("linkedin_synthetic.html"))));

        var r = ingest.ingest();
        assertThat(r.jobsFound()).isEqualTo(2);
        assertThat(r.newJobs()).isEqualTo(1); // only the Noon job; the Acme job exists from the careers site
    }

    @Test
    void skipsQuietlyWhenNoMailboxIsConfigured() throws Exception {
        when(mailbox.configured()).thenReturn(false);
        var r = ingest.ingest();
        assertThat(r.emails()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from source", Integer.class)).isZero();
    }

    @Test
    void theDailyPipelineIngestsAlertsAndScoresTheirJobs() throws Exception {
        when(mailbox.fetchSince(any())).thenReturn(List.of(email("<1>", "LinkedIn alert", fixture("linkedin_synthetic.html"))));
        when(llm.modelName()).thenReturn("stub");
        when(llm.completeJson(anyString(), anyString()))
            .thenReturn("{\"score\": 80, \"reason\": \"Java title matches.\"}");

        var result = pipeline.run();

        assertThat(result.matches()).isEqualTo(1); // "Senior Java Developer"; "Backend Engineer" fails the keyword filter
        assertThat(jdbc.queryForObject(
            "select llm_score from job_score s join job j on j.id = s.job_id where j.title = 'Senior Java Developer'",
            Integer.class)).isEqualTo(80);
    }

    @Test
    void previewParsesAnUploadedEmlWithoutStoringAnything() throws Exception {
        String eml = """
            From: alerts@bayt.example
            Subject: New jobs for you
            Message-ID: <preview-1@example.com>
            MIME-Version: 1.0
            Content-Type: text/html; charset=utf-8

            """ + fixture("bayt_synthetic.html");
        mvc.perform(multipart("/admin/alerts/preview")
                .file(new MockMultipartFile("file", "a.eml", "message/rfc822", eml.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subject").value("New jobs for you"))
            .andExpect(jsonPath("$.jobs").value(2))
            .andExpect(jsonPath("$.parsed[0].title").value("Java Developer"))
            .andExpect(jsonPath("$.parsed[0].employer").value("Emirates Group"));
        assertThat(jdbc.queryForObject("select count(*) from job", Integer.class)).isZero();
    }
}
