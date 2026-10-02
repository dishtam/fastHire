package com.fasthire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

import com.fasthire.scrape.Fetcher;
import com.fasthire.scrape.ScrapeService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

class ScrapeServiceIT extends AbstractDbTest {
    @Autowired ScrapeService scrape;
    @MockBean Fetcher fetcher;

    private static String fixture(String name) throws IOException {
        try (var in = ScrapeServiceIT.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void insertsOnlyNewJobsAndRecordsRuns() throws Exception {
        insertSource("UAE", "https://boards.greenhouse.io/acme", "ATS", "GREENHOUSE", "acme");
        when(fetcher.get(contains("greenhouse"))).thenReturn(fixture("greenhouse.json"));

        assertThat(scrape.scrapeAll()).hasSize(2);
        assertThat(scrape.scrapeAll()).isEmpty();

        assertThat(jdbc.queryForObject("select count(*) from job", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select display_id from job where external_id='127817'", String.class))
            .isEqualTo("127817");
        assertThat(jdbc.queryForObject("select employer_name from job where external_id='127817'", String.class))
            .isEqualTo("Acme Inc");
        List<Integer> newCounts = jdbc.queryForList("select jobs_new from scrape_run order by id", Integer.class);
        assertThat(newCounts).containsExactly(2, 0);
        assertThat(jdbc.queryForObject("select scrape_status from source", String.class)).isEqualTo("OK");
    }

    @Test
    void oneFailingSourceDoesNotStopTheOthers() throws Exception {
        insertSource("UAE", "https://boards.greenhouse.io/broken", "ATS", "GREENHOUSE", "broken");
        insertSource("UAE", "https://jobs.lever.co/acme", "ATS", "LEVER", "acme");
        insertSource("IN", "https://x.example/careers", "ATS", "ICIMS", "t"); // a platform with no scraper
        when(fetcher.get(contains("broken"))).thenThrow(new IOException("HTTP 500"));
        when(fetcher.get(contains("lever"))).thenReturn(fixture("lever.json"));

        assertThat(scrape.scrapeAll()).hasSize(2);

        var statuses = jdbc.queryForList("select ats_type, scrape_status from source order by id");
        assertThat(statuses.get(0)).containsEntry("scrape_status", "ERROR");
        assertThat(statuses.get(1)).containsEntry("scrape_status", "OK");
        assertThat(statuses.get(2)).containsEntry("scrape_status", "NO_SCRAPER");
        assertThat(jdbc.queryForObject("select error from scrape_run where source_id=1", String.class))
            .contains("HTTP 500");
    }

    @Test
    void eightfoldJobIsStoredEvenWhenTheDescriptionCallFails() throws Exception {
        insertSource("IN", "https://jobs.amdocs.com/careers?query=Software+Engineer&location=india&pid=1",
            "ATS", "EIGHTFOLD", "jobs.amdocs.com");
        when(fetcher.get(contains("/api/pcsx/search"))).thenReturn(fixture("eightfold_search.json"));
        when(fetcher.get(contains("position_details"))).thenThrow(new IOException("HTTP 403"));

        assertThat(scrape.scrapeAll()).hasSize(1);
        var row = jdbc.queryForMap("select title, display_id, description from job");
        assertThat(row).containsEntry("title", "Fiber Engineer").containsEntry("display_id", "213469");
        assertThat((String) row.get("description")).contains("hybrid");
    }

    @Test
    void aLocationFilterInTheSourceUrlKeepsOnlyThatPlacesJobs() throws Exception {
        insertSource("UAE", "https://jobs.lever.co/acme?location=Abu%20Dhabi", "ATS", "LEVER", "acme");
        when(fetcher.get(contains("lever"))).thenReturn(fixture("lever.json"));

        assertThat(scrape.scrapeAll()).hasSize(1); // fixture has an Abu Dhabi job and one with no location
        assertThat(jdbc.queryForObject("select location from job", String.class)).isEqualTo("Abu Dhabi");
        assertThat(jdbc.queryForObject("select jobs_found from scrape_run", Integer.class)).isEqualTo(1);
    }

    @Test
    void ashbyWorkdayAndSmartRecruitersJobsAreStoredWithTheirDescriptions() throws Exception {
        insertSource("IN", "https://jobs.ashbyhq.com/acme", "ATS", "ASHBY", "acme");
        insertSource("IN", "https://acme.wd5.myworkdayjobs.com/External?q=java&location=Pune", "ATS", "WORKDAY", "acme/wd5/External");
        insertSource("IN", "https://careers.smartrecruiters.com/Acme", "ATS", "SMARTRECRUITERS", "Acme");
        when(fetcher.get(contains("posting-api"))).thenReturn(fixture("ashby.json"));
        when(fetcher.post(contains("/wday/cxs/acme/External/jobs"), org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(fixture("workday_list.json"));
        when(fetcher.get(contains("/wday/cxs/acme/External/job/"))).thenReturn(fixture("workday_detail.json"));
        when(fetcher.get(contains("/postings?limit"))).thenReturn(fixture("sr_list_2.json"));
        when(fetcher.get(contains("/postings/744000000000003"))).thenThrow(new IOException("HTTP 403"));

        assertThat(scrape.scrapeAll()).hasSize(2 + 2 + 1); // ashby 2, workday 2 (London filtered), smartrecruiters 1

        assertThat(jdbc.queryForObject("select count(*) from source where scrape_status = 'OK'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select description from job where title = 'Backend Engineer' and employer_name = 'acme'",
            String.class)).contains("Compensation: Rs 20L - 30L per year");
        assertThat(jdbc.queryForObject("select display_id from job where title = 'Software Engineer - Java'", String.class))
            .isEqualTo("R-100");
        assertThat(jdbc.queryForObject("select description from job where title = 'Software Engineer - Java'", String.class))
            .contains("Build Java services.");
        // The SmartRecruiters description call failed, but the job was still stored.
        assertThat(jdbc.queryForObject("select count(*) from job where title = 'QA Engineer'", Integer.class)).isEqualTo(1);
    }
}
