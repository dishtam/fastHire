package com.fasthire.scrape;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Scrapes every enabled ATS source. One source failing never stops the others.
 * Only rows that are actually inserted count as new, so dedup is the DB unique key.
 */
@Service
public class ScrapeService {
    private static final Logger log = LoggerFactory.getLogger(ScrapeService.class);

    private final JdbcTemplate jdbc;
    private final Map<String, JobSource> sourcesByType;

    public ScrapeService(JdbcTemplate jdbc, List<JobSource> scrapers) {
        this.jdbc = jdbc;
        this.sourcesByType = scrapers.stream().collect(Collectors.toMap(JobSource::atsType, Function.identity()));
    }

    /** @return ids of newly inserted jobs, for scoring. */
    public List<Long> scrapeAll() {
        List<SourceRef> refs = jdbc.query(
            "SELECT id, region, url, ats_type, ats_token, label FROM source WHERE enabled AND kind = 'ATS'",
            (rs, i) -> new SourceRef(rs.getLong("id"), rs.getString("region"), rs.getString("url"),
                rs.getString("ats_type"), rs.getString("ats_token"), rs.getString("label")));
        List<Long> newIds = new ArrayList<>();
        for (SourceRef ref : refs) {
            newIds.addAll(scrapeOne(ref));
        }
        return newIds;
    }

    List<Long> scrapeOne(SourceRef ref) {
        JobSource scraper = sourcesByType.get(ref.atsType());
        if (scraper == null) {
            jdbc.update("UPDATE source SET scrape_status = 'NO_SCRAPER' WHERE id = ?", ref.id());
            return List.of();
        }
        List<Long> inserted = new ArrayList<>();
        String error = null;
        int found = 0;
        try {
            List<RawJob> jobs = LocationFilter.apply(ref, scraper.fetch(ref));
            found = jobs.size();
            Set<String> known = new HashSet<>(jdbc.queryForList(
                "SELECT external_id FROM job WHERE source_id = ?", String.class, ref.id()));
            for (RawJob listed : jobs) {
                if (known.contains(listed.externalId())) {
                    continue;
                }
                RawJob j = listed;
                try {
                    j = scraper.enrich(ref, listed);
                } catch (IOException e) {
                    log.warn("Could not fetch description for {} job {}: {}", ref.atsType(), listed.externalId(), e.getMessage());
                }
                List<Long> ids = jdbc.queryForList("""
                    INSERT INTO job (source_id, external_id, employer_name, title, location, description, url, posted_at, display_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_id, external_id) DO NOTHING
                    RETURNING id
                    """, Long.class, ref.id(), j.externalId(), j.employerName(), j.title(), j.location(),
                    j.description(), j.url(), j.postedAt() == null ? null : java.sql.Timestamp.from(j.postedAt()),
                    j.displayId());
                inserted.addAll(ids);
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("Scrape failed for source {} ({}): {}", ref.id(), ref.url(), error);
        }
        jdbc.update("INSERT INTO scrape_run (source_id, jobs_found, jobs_new, error) VALUES (?, ?, ?, ?)",
            ref.id(), found, inserted.size(), error);
        jdbc.update("UPDATE source SET last_scraped_at = now(), scrape_status = ? WHERE id = ?",
            error == null ? "OK" : "ERROR", ref.id());
        return inserted;
    }
}
