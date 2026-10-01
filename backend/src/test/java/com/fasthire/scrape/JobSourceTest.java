package com.fasthire.scrape;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobSourceTest {

    private static String fixture(String name) throws java.io.IOException {
        try (var in = JobSourceTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void greenhouseParsesJobsAndCallsBoardApi() throws Exception {
        String[] requested = new String[1];
        Fetcher f = url -> { requested[0] = url; return fixture("greenhouse.json"); };
        List<RawJob> jobs = new GreenhouseJobSource(f)
            .fetch(new SourceRef(1, "UAE", "u", "GREENHOUSE", "acme"));

        assertThat(requested[0]).isEqualTo("https://boards-api.greenhouse.io/v1/boards/acme/jobs?content=true");
        assertThat(jobs).hasSize(2);
        RawJob a = jobs.get(0);
        assertThat(a.externalId()).isEqualTo("127817");
        assertThat(a.employerName()).isEqualTo("Acme Inc");
        assertThat(a.location()).isEqualTo("Dubai, UAE");
        assertThat(a.description()).contains("Build services with Java and Spring Boot.", "Kafka", "Postgres");
        assertThat(a.postedAt()).isNotNull();
        assertThat(jobs.get(1).employerName()).isEqualTo("acme");
    }

    @Test
    void leverParsesJobsAndLists() throws Exception {
        Fetcher f = url -> fixture("lever.json");
        List<RawJob> jobs = new LeverJobSource(f)
            .fetch(new SourceRef(2, "UAE", "u", "LEVER", "acme"));

        assertThat(jobs).hasSize(2);
        RawJob a = jobs.get(0);
        assertThat(a.externalId()).isEqualTo("abc-123");
        assertThat(a.location()).isEqualTo("Abu Dhabi");
        assertThat(a.description()).contains("payment APIs", "Requirements", "Java", "SQL");
        assertThat(a.postedAt()).isNotNull();
        assertThat(jobs.get(1).description()).isEqualTo("Design things");
        assertThat(jobs.get(1).postedAt()).isNull();
    }

    @Test
    void eightfoldListsJobsReusingSearchAndPaginates() throws Exception {
        java.util.List<String> requested = new java.util.ArrayList<>();
        Fetcher f = url -> { requested.add(url); return fixture("eightfold_search.json"); };
        List<RawJob> jobs = new EightfoldJobSource(f).fetch(new SourceRef(3, "IN",
            "https://jobs.amdocs.com/careers?query=Software+Engineer&start=0&location=india&pid=1&sort_by=relevance",
            "EIGHTFOLD", "jobs.amdocs.com"));

        assertThat(requested).containsExactly(
            "https://jobs.amdocs.com/api/pcsx/search?domain=amdocs.com&query=Software+Engineer&location=india&sort_by=relevance&start=0");
        assertThat(jobs).hasSize(1);
        RawJob j = jobs.get(0);
        assertThat(j.externalId()).isEqualTo("563431014933815");
        assertThat(j.employerName()).isEqualTo("Amdocs");
        assertThat(j.title()).isEqualTo("Fiber Engineer");
        assertThat(j.location()).isEqualTo("Gurgaon,India; Pune, Maharashtra, India");
        assertThat(j.url()).isEqualTo("https://jobs.amdocs.com/careers/job/563431014933815");
        assertThat(j.description()).contains("hybrid");
        assertThat(j.postedAt()).isEqualTo(java.time.Instant.ofEpochSecond(1790010547L));
    }
}
