package com.fasthire.scrape;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScrapeAdminController {
    private final ScrapeService scrapeService;

    public ScrapeAdminController(ScrapeService scrapeService) {
        this.scrapeService = scrapeService;
    }

    /** Manual "run now"; the daily scheduler comes with scoring. */
    @PostMapping("/admin/scrape")
    public Map<String, Object> run() {
        List<Long> ids = scrapeService.scrapeAll();
        return Map.of("newJobs", ids.size());
    }
}
