package com.fasthire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasthire.drafting.DraftService;
import com.fasthire.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DraftingAndApiIT extends AbstractDbTest {
    @Autowired DraftService drafts;
    @Autowired MockMvc mvc;
    @MockBean LlmClient llm;

    private long indiaHigh;
    private long indiaLow;

    @BeforeEach
    void seed() {
        long in = insertSource("IN", "https://in.example", "ATS", "LEVER", "in");
        long uae = insertSource("UAE", "https://uae.example", "ATS", "LEVER", "uae");
        indiaHigh = insertJob(in, "1", "Java Developer", "Acme", "d");
        indiaLow = insertJob(in, "2", "Fiber Engineer", "Amdocs", "d");
        long uaeJob = insertJob(uae, "3", "Backend Engineer", "Noon", "d");
        long filtered = insertJob(in, "4", "Sales Intern", "Acme", "d");
        score(indiaHigh, 90);
        score(indiaLow, 10);
        score(uaeJob, 75);
        jdbc.update("INSERT INTO job_score (job_id, keyword_score, llm_score, reason) VALUES (?, 0, NULL, 'Filtered out by keywords')", filtered);
    }

    private void score(long jobId, int s) {
        jdbc.update("INSERT INTO job_score (job_id, keyword_score, llm_score, reason, model) VALUES (?, 50, ?, 'because', 'stub')", jobId, s);
    }

    @Test
    void draftsOnlyJobsAtOrAboveThresholdAndOnlyOnce() throws Exception {
        when(llm.completeJson(anyString(), anyString()))
            .thenReturn("{\"hiring_manager\": \"HM message\", \"employee\": \"Employee message\"}");

        assertThat(drafts.draftMissing()).isEqualTo(2); // 90 and 75; not 10, not filtered
        assertThat(drafts.draftMissing()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from draft_message", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from draft_message where job_id=?", Integer.class, indiaLow))
            .isZero();
    }

    @Test
    void jobsEndpointReturnsTopJobsPerRegionWithMessages() throws Exception {
        when(llm.completeJson(anyString(), anyString()))
            .thenReturn("{\"hiring_manager\": \"HM message\", \"employee\": \"Employee message\"}");
        drafts.draftMissing();

        mvc.perform(get("/api/jobs").param("region", "IN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)) // filtered job excluded
            .andExpect(jsonPath("$[0].title").value("Java Developer"))
            .andExpect(jsonPath("$[0].score").value(90))
            .andExpect(jsonPath("$[0].status").value("NEW"))
            .andExpect(jsonPath("$[0].hiringManagerMessage").value("HM message"))
            .andExpect(jsonPath("$[0].employeeMessage").value("Employee message"))
            .andExpect(jsonPath("$[1].title").value("Fiber Engineer"))
            .andExpect(jsonPath("$[1].hiringManagerMessage").doesNotExist());

        mvc.perform(get("/api/jobs").param("region", "uae").param("minScore", "80"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/jobs").param("region", "IN").param("limit", "1"))
            .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/jobs").param("region", "DE")).andExpect(status().isBadRequest());
    }

    @Test
    void statusCanBeUpdatedAndFiltered() throws Exception {
        mvc.perform(patch("/api/jobs/" + indiaHigh + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"applied\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPLIED"));

        mvc.perform(get("/api/jobs").param("region", "IN").param("status", "APPLIED"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("APPLIED"));
        mvc.perform(get("/api/jobs").param("region", "IN").param("status", "NEW"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Fiber Engineer"));

        mvc.perform(patch("/api/jobs/" + indiaHigh + "/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"hired\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/jobs/99999/status")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void sourcesEndpointListsHealth() throws Exception {
        mvc.perform(get("/api/sources"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].region").value("IN"))
            .andExpect(jsonPath("$[0].kind").value("ATS"));
    }
}
