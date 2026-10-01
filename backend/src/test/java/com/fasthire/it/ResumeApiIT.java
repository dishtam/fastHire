package com.fasthire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasthire.llm.LlmClient;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ResumeApiIT extends AbstractDbTest {
    @Autowired MockMvc mvc;
    @MockBean LlmClient llm;

    private static final String REPLY = """
        {"summary": "Backend engineer with 4 years building payment services in Java and Spring Boot.",
         "skills": ["Kafka", "Java"],
         "bullets": [{"id": "ex-2", "text": "Built Kafka consumers processing 2M events per day"},
                     {"id": "ex-1", "text": "Cut API p95 latency from 800ms to 220ms by adding Redis caching on Kubernetes"}]}""";

    private long job() {
        long s = insertSource("UAE", "https://uae.example", "ATS", "LEVER", "uae");
        return insertJob(s, "1", "Senior Java Developer", "Noon", "Kafka, Spring Boot, microservices");
    }

    @Test
    void generatesATailoredPdfAndCachesTheSelection() throws Exception {
        long id = job();
        when(llm.completeJson(anyString(), anyString())).thenReturn(REPLY);

        byte[] pdf = mvc.perform(get("/api/jobs/" + id + "/resume.pdf"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Alex_Sample_Noon_Senior_Java_Developer.pdf")))
            .andReturn().getResponse().getContentAsByteArray();

        try (PDDocument doc = PDDocument.load(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Alex Sample", "Built Kafka consumers");
            // The model's unfaithful rewrite (added Kubernetes) was replaced by the original wording.
            assertThat(text).contains("Cut API p95 latency from 800ms to 220ms by adding Redis caching");
            assertThat(text).doesNotContain("Kubernetes");
            // Entries the model left out still appear once (Intern), unselected projects do not.
            assertThat(text).contains("Intern");
            assertThat(text).doesNotContain("Order Tracker");
        }

        mvc.perform(get("/api/jobs/" + id + "/resume.pdf")).andExpect(status().isOk());
        verify(llm, times(1)).completeJson(anyString(), anyString()); // second download came from the cache
        assertThat(jdbc.queryForObject("select count(*) from tailored_resume", Integer.class)).isEqualTo(1);
    }

    @Test
    void unknownJobIs404AndLlmFailureIs502WithAMessage() throws Exception {
        mvc.perform(get("/api/jobs/99999/resume.pdf")).andExpect(status().isNotFound());

        long id = job();
        when(llm.completeJson(anyString(), anyString())).thenThrow(new IOException("OpenAI HTTP 401"));
        mvc.perform(get("/api/jobs/" + id + "/resume.pdf"))
            .andExpect(status().isBadGateway())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error")
                .value(org.hamcrest.Matchers.containsString("OpenAI HTTP 401")));
        assertThat(jdbc.queryForObject("select count(*) from tailored_resume", Integer.class)).isZero();
    }
}
