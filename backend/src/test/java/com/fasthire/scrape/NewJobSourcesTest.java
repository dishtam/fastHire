package com.fasthire.scrape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewJobSourcesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    // ---------- Ashby
    @Test void ashbyListsListedJobsWithDescriptionsAndLocations() throws Exception {
        FakeFetcher f = new FakeFetcher().onGet("posting-api/job-board/acme", FakeFetcher.fixture("ashby.json"));
        SourceRef src = new SourceRef(1, "IN", "https://jobs.ashbyhq.com/acme", "ASHBY", "acme", "Acme (India office)");

        List<RawJob> jobs = new AshbyJobSource(f).fetch(src);

        assertThat(f.gets).containsExactly("https://api.ashbyhq.com/posting-api/job-board/acme?includeCompensation=true");
        assertThat(jobs).hasSize(2); // the unlisted posting is skipped
        RawJob a = jobs.get(0);
        assertThat(a.externalId()).isEqualTo("11111111-aaaa-bbbb-cccc-000000000001");
        assertThat(a.employerName()).isEqualTo("Acme"); // sheet label without the "(note)"
        assertThat(a.title()).isEqualTo("Backend Engineer");
        assertThat(a.location()).isEqualTo("Bengaluru, India; Remote - India");
        assertThat(a.description()).startsWith("Compensation: Rs 20L - 30L per year").contains("Build Java services.");
        assertThat(a.url()).endsWith("000000000001");
        assertThat(a.postedAt()).isNotNull();
        RawJob b = jobs.get(1);
        assertThat(b.location()).isEqualTo("Remote");
        assertThat(b.description()).isEqualTo("Design things"); // HTML description converted to text
        assertThat(b.url()).endsWith("/application"); // falls back to the apply URL
    }

    // ---------- SmartRecruiters
    @Test void smartRecruitersPagesByTotalFoundAndNamesCountries() throws Exception {
        FakeFetcher f = new FakeFetcher()
            .onGet("offset=0", FakeFetcher.fixture("sr_list_1.json"))
            .onGet("offset=2", FakeFetcher.fixture("sr_list_2.json"));
        SourceRef src = new SourceRef(2, "IN", "https://careers.smartrecruiters.com/Acme?search=India", "SMARTRECRUITERS", "Acme");

        List<RawJob> jobs = new SmartRecruitersJobSource(f).fetch(src);

        assertThat(f.gets).hasSize(2);
        assertThat(f.gets.get(0)).isEqualTo("https://api.smartrecruiters.com/v1/companies/Acme/postings?limit=100&q=India&offset=0");
        assertThat(f.gets.get(1)).endsWith("&offset=2");
        assertThat(jobs).extracting(RawJob::externalId).containsExactly("744000000000001", "744000000000002", "744000000000003");
        RawJob a = jobs.get(0);
        assertThat(a.employerName()).isEqualTo("Acme Corp");
        assertThat(a.location()).isEqualTo("Hyderabad, Telangana, India");
        assertThat(a.displayId()).isEqualTo("REF-101");
        assertThat(a.description()).contains("Experience level: Associate", "Employment type: Full-time", "Department: Engineering");
        assertThat(a.postedAt()).isNotNull();
        assertThat(jobs.get(1).location()).isEqualTo("Dubai, United Arab Emirates, Remote");
    }

    @Test void smartRecruitersEnrichAddsSectionsAndTheRealPostingUrl() throws Exception {
        FakeFetcher f = new FakeFetcher().onGet("/postings/744000000000001", FakeFetcher.fixture("sr_detail.json"));
        SourceRef src = new SourceRef(2, "IN", "https://careers.smartrecruiters.com/Acme", "SMARTRECRUITERS", "Acme");
        RawJob listed = new RawJob("744000000000001", "Acme", "Software Engineer - Java", "Hyderabad, India",
            "Experience level: Associate", "https://jobs.smartrecruiters.com/Acme/744000000000001", null, "REF-101");

        RawJob full = new SmartRecruitersJobSource(f).enrich(src, listed);

        assertThat(full.description()).contains("Experience level: Associate", "Job Description", "Write Java", "Use Spring Boot",
            "We build payments.", "2+ years");
        assertThat(full.url()).isEqualTo("https://jobs.smartrecruiters.com/Acme/744000000000001-software-engineer-java");
    }

    // ---------- Workday
    private static SourceRef wd(String url) {
        return new SourceRef(3, "IN", url, "WORKDAY", "acme/wd5/External", "Acme Inc");
    }

    @Test void workdayPostsASearchAndBuildsJobs() throws Exception {
        FakeFetcher f = new FakeFetcher();
        f.postHandler = body -> FakeFetcher.fixture("workday_list.json");

        List<RawJob> jobs = new WorkdayJobSource(f).fetch(wd("https://acme.wd5.myworkdayjobs.com/External?q=java+developer"));

        assertThat(f.posts).containsExactly("https://acme.wd5.myworkdayjobs.com/wday/cxs/acme/External/jobs");
        var body = mapper.readTree(f.postBodies.get(0));
        assertThat(body.path("limit").asInt()).isEqualTo(20);
        assertThat(body.path("offset").asInt()).isZero();
        assertThat(body.path("searchText").asText()).isEqualTo("java developer");
        assertThat(body.has("appliedFacets")).isTrue();

        assertThat(jobs).hasSize(3);
        RawJob a = jobs.get(0);
        assertThat(a.externalId()).isEqualTo("/job/Pune-India/Software-Engineer---Java_R-100");
        assertThat(a.displayId()).isEqualTo("R-100");
        assertThat(a.employerName()).isEqualTo("Acme Inc");
        assertThat(a.location()).isEqualTo("Pune, India");
        assertThat(a.url()).isEqualTo("https://acme.wd5.myworkdayjobs.com/en-US/External/job/Pune-India/Software-Engineer---Java_R-100");
        assertThat(a.postedAt()).isBetween(Instant.now().minus(Duration.ofDays(3)).minusSeconds(60), Instant.now().minus(Duration.ofDays(3)).plusSeconds(60));
    }

    @Test void workdayPagesInTwentiesUntilTheTotalIsReached() throws Exception {
        FakeFetcher f = new FakeFetcher();
        f.postHandler = body -> {
            try {
                int offset = mapper.readTree(body).path("offset").asInt();
                int n = Math.min(20, 25 - offset);
                StringBuilder sb = new StringBuilder("{\"total\": " + (offset == 0 ? 25 : 0) + ", \"jobPostings\": [");
                for (int i = 0; i < n; i++) {
                    sb.append(i > 0 ? "," : "").append("{\"title\":\"J").append(offset + i).append("\",\"externalPath\":\"/job/x/J")
                        .append(offset + i).append("\",\"locationsText\":\"Pune\",\"postedOn\":\"Posted Today\"}");
                }
                return sb.append("]}").toString();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        };
        assertThat(new WorkdayJobSource(f).fetch(wd("https://acme.wd5.myworkdayjobs.com/External"))).hasSize(25);
        assertThat(f.posts).hasSize(2); // 20 + 5, then stops on the short page
    }

    @Test void workdayNeverPagesForeverOnHugeTenants() throws Exception {
        FakeFetcher f = new FakeFetcher();
        StringBuilder page = new StringBuilder("{\"total\": 999999, \"jobPostings\": [");
        for (int i = 0; i < 20; i++) {
            page.append(i > 0 ? "," : "").append("{\"title\":\"J\",\"externalPath\":\"/job/").append(i).append("\"}");
        }
        String full = page.append("]}").toString();
        f.postHandler = body -> full;
        new WorkdayJobSource(f).fetch(wd("https://acme.wd5.myworkdayjobs.com/External"));
        assertThat(f.posts).hasSize(WorkdayJobSource.MAX_PAGES);
    }

    @Test void workdayEnrichAddsTheDescription() throws Exception {
        FakeFetcher f = new FakeFetcher().onGet("/wday/cxs/acme/External/job/Pune-India/", FakeFetcher.fixture("workday_detail.json"));
        RawJob listed = new RawJob("/job/Pune-India/Software-Engineer---Java_R-100", "Acme", "Software Engineer - Java",
            "Pune, India", "", "u", null, "R-100");
        RawJob full = new WorkdayJobSource(f).enrich(wd("https://acme.wd5.myworkdayjobs.com/External"), listed);
        assertThat(f.gets).containsExactly("https://acme.wd5.myworkdayjobs.com/wday/cxs/acme/External/job/Pune-India/Software-Engineer---Java_R-100");
        assertThat(full.description()).contains("Employment type: Full time", "Build Java services.", "Spring Boot");
        assertThat(full.url()).contains("/en-US/External/job/");
    }

    @Test void workdayPostedTextAndBadTokens() {
        assertThat(WorkdayJobSource.posted("Posted Yesterday")).isBefore(Instant.now().minus(Duration.ofHours(23)));
        assertThat(WorkdayJobSource.posted("Posted 30+ Days Ago")).isBefore(Instant.now().minus(Duration.ofDays(29)));
        assertThat(WorkdayJobSource.posted("whatever")).isNull();
        FakeFetcher f = new FakeFetcher();
        assertThatThrownBy(() -> new WorkdayJobSource(f).fetch(new SourceRef(1, "IN", "u", "WORKDAY", "bad-token")))
            .isInstanceOf(IOException.class).hasMessageContaining("tenant/wdN/site");
    }

    @Test void multiLocationWorkdayPostingsSurviveALocationFilter() throws Exception {
        FakeFetcher f = new FakeFetcher();
        f.postHandler = body -> FakeFetcher.fixture("workday_list.json");
        SourceRef src = wd("https://acme.wd5.myworkdayjobs.com/External?location=India|Pune");
        List<RawJob> filtered = LocationFilter.apply(src, new WorkdayJobSource(f).fetch(src));
        assertThat(filtered).extracting(RawJob::title).containsExactly("Software Engineer - Java", "Senior Engineer"); // London dropped
    }
}
