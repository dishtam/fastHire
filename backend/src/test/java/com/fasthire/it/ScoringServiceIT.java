package com.fasthire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasthire.llm.LlmClient;
import com.fasthire.scoring.ScoredJob;
import com.fasthire.scoring.ScoringService;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class ScoringServiceIT extends AbstractDbTest {
    @Autowired ScoringService scoring;
    @MockBean LlmClient llm;

    @Test
    void filtersScoresAndRetriesFailures() throws Exception {
        when(llm.modelName()).thenReturn("stub");
        long s = insertSource("UAE", "u", "ATS", "LEVER", "acme");
        long good = insertJob(s, "1", "Java Developer", "Acme", "Spring Boot and Kafka services");
        long bad = insertJob(s, "2", "Sales Intern", "Acme", "Java");
        long failing = insertJob(s, "3", "Backend Engineer", "Acme", "Java, Redis");

        when(llm.completeJson(anyString(), anyString()))
            .thenReturn("{\"score\": 88, \"reason\": \"Strong Java match.\"}")
            .thenThrow(new IOException("HTTP 429"));

        assertThat(scoring.unscoredJobIds()).containsExactly(good, bad, failing);
        List<ScoredJob> hits = scoring.scoreJobs(scoring.unscoredJobIds());

        assertThat(hits).extracting(ScoredJob::jobId).containsExactly(good);
        assertThat(hits.get(0).region()).isEqualTo("UAE");
        assertThat(jdbc.queryForObject("select reason from job_score where job_id=?", String.class, bad))
            .isEqualTo("Filtered out by keywords");
        assertThat(jdbc.queryForObject("select llm_score from job_score where job_id=?", Integer.class, good))
            .isEqualTo(88);
        // The failed call left no score row, so it is retried next run; the others are not.
        assertThat(scoring.unscoredJobIds()).containsExactly(failing);
    }
}
