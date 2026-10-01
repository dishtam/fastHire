package com.fasthire.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasthire.llm.LlmClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeSelectorTest {
    private static class Stub implements LlmClient {
        final Deque<String> replies = new ArrayDeque<>();
        final List<String> prompts = new ArrayList<>();
        Stub(String... r) { replies.addAll(List.of(r)); }
        public String completeJson(String system, String user) { prompts.add(user); return replies.poll(); }
        public String modelName() { return "stub"; }
    }

    static ResumeProfile profile() throws IOException {
        return ResumeProfile.parse(Files.readString(Path.of("src/test/resources/fixtures/profile_test.yml")));
    }

    private Selection run(String reply) throws IOException {
        return new ResumeSelector(new Stub(reply)).select(profile(), "Java Developer", "Acme", "Kafka and Spring Boot");
    }

    @Test void parsesProfileStructure() throws Exception {
        ResumeProfile p = profile();
        assertThat(p.name()).isEqualTo("Alex Sample");
        assertThat(p.experience()).hasSize(2);
        assertThat(p.allBullets()).hasSize(6);
        assertThat(p.allSkills()).contains("Java", "Kafka", "AWS");
    }

    @Test void reordersBulletsAndKeepsValidRephrases() throws Exception {
        Selection s = run("""
            {"summary": "Backend engineer with 4 years of Java and Spring Boot experience.",
             "skills": ["kafka", "Java", "Spring Boot"],
             "bullets": [
               {"id": "ex-2", "text": "Built Kafka consumers processing 2M events per day"},
               {"id": "ex-1", "text": "Reduced API p95 latency from 800ms to 220ms using Redis caching"},
               {"id": "ac-1", "text": "Wrote unit tests with JUnit that raised coverage from 40% to 70%"}]}""");
        assertThat(s.bulletIds()).containsExactly("ex-2", "ex-1", "ac-1");
        assertThat(s.text().get("ex-1")).startsWith("Reduced API p95");
        assertThat(s.skills()).containsExactly("Kafka", "Java", "Spring Boot"); // canonical spelling
        assertThat(s.summary()).contains("4 years");
    }

    @Test void rephraseThatAddsASkillOrNumberFallsBackToTheOriginal() throws Exception {
        Selection s = run("""
            {"summary": "x", "skills": ["Java"],
             "bullets": [
               {"id": "ex-1", "text": "Cut API p95 latency from 800ms to 220ms by adding Redis caching on Kubernetes"},
               {"id": "ex-2", "text": "Built Kafka consumers processing 5M events per day"},
               {"id": "ac-1", "text": "Wrote unit tests with JUnit"}]}""");
        assertThat(s.text().get("ex-1")).isEqualTo("Cut API p95 latency from 800ms to 220ms by adding Redis caching");
        assertThat(s.text().get("ex-2")).isEqualTo("Built Kafka consumers processing 2M events per day");
        assertThat(s.text().get("ac-1")).isEqualTo("Wrote unit tests with JUnit");
    }

    @Test void unknownIdsDuplicatesAndUnknownSkillsAreDropped() throws Exception {
        Selection s = run("""
            {"summary": "", "skills": ["Java", "Kubernetes", "Terraform"],
             "bullets": [{"id": "ex-1", "text": ""}, {"id": "nope", "text": "x"}, {"id": "ex-1", "text": "dup"},
                         {"id": "ac-1", "text": ""}]}""");
        assertThat(s.bulletIds()).containsExactly("ex-1", "ac-1");
        assertThat(s.skills()).containsExactly("Java");
        assertThat(s.summary()).isEqualTo(profile().summary()); // empty summary falls back
    }

    @Test void everyExperienceEntryKeepsAtLeastOneBullet() throws Exception {
        Selection s = run("{\"summary\": \"\", \"skills\": [], \"bullets\": [{\"id\": \"ex-1\", \"text\": \"\"}]}");
        assertThat(s.bulletIds()).contains("ex-1", "ac-1");
        assertThat(s.skills()).isNotEmpty(); // empty skills fall back to the profile's first skills
    }

    @Test void unfaithfulSummaryFallsBackToTheProfileSummary() throws Exception {
        Selection s = run("{\"summary\": \"Backend engineer with 9 years of Kubernetes experience.\", \"skills\": [], "
            + "\"bullets\": [{\"id\": \"ex-1\", \"text\": \"\"}]}");
        assertThat(s.summary()).isEqualTo(profile().summary());
    }

    @Test void retriesOnceOnMalformedReplyThenFails() throws Exception {
        Stub llm = new Stub("not json", "{\"summary\": \"\", \"skills\": [], \"bullets\": []}");
        assertThat(new ResumeSelector(llm).select(profile(), "t", "e", "d").bulletIds()).containsExactly("ex-1", "ac-1");
        assertThat(llm.prompts).hasSize(2);

        Stub bad = new Stub("{\"nope\": 1}", "{\"nope\": 2}");
        assertThatThrownBy(() -> new ResumeSelector(bad).select(profile(), "t", "e", "d")).isInstanceOf(IOException.class);
    }

    @Test void promptListsBulletsWithIdsAndTheJob() throws Exception {
        Stub llm = new Stub("{\"summary\": \"\", \"skills\": [], \"bullets\": []}");
        new ResumeSelector(llm).select(profile(), "Java Developer", "Acme", "Kafka and Spring Boot");
        assertThat(llm.prompts.get(0)).contains("ex-1: Cut API p95", "Skills (use exactly these names)",
            "Title: Java Developer", "Kafka and Spring Boot", "ot-2:");
    }
}
