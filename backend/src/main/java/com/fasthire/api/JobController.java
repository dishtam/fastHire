package com.fasthire.api;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class JobController {
    private static final Set<String> REGIONS = Set.of("IN", "UAE");

    public record StatusUpdate(String status) {}

    private final JdbcTemplate jdbc;

    public JobController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Best-scoring jobs for a region. Jobs dropped by the keyword filter (no LLM score) are not listed. */
    @GetMapping("/api/jobs")
    public List<JobCard> jobs(@RequestParam String region,
                              @RequestParam(defaultValue = "20") int limit,
                              @RequestParam(required = false) JobStatus status,
                              @RequestParam(defaultValue = "0") int minScore) {
        String r = region.toUpperCase();
        if (!REGIONS.contains(r)) {
            throw new IllegalArgumentException("region must be IN or UAE");
        }
        List<Object> args = new ArrayList<>(List.of(r, minScore));
        String statusClause = "";
        if (status != null) {
            statusClause = " AND COALESCE(st.status, 'NEW') = ?";
            args.add(status.name());
        }
        args.add(Math.max(1, Math.min(limit, 100)));
        return jdbc.query("""
            SELECT j.id, j.title, j.employer_name, j.location, j.url, j.posted_at, s.region,
                   sc.llm_score, sc.reason, COALESCE(st.status, 'NEW') AS status,
                   (SELECT body FROM draft_message d WHERE d.job_id = j.id AND d.type = 'HM'
                      ORDER BY d.id DESC LIMIT 1) AS hm,
                   (SELECT body FROM draft_message d WHERE d.job_id = j.id AND d.type = 'EMPLOYEE'
                      ORDER BY d.id DESC LIMIT 1) AS emp
            FROM job j
            JOIN source s ON s.id = j.source_id
            JOIN job_score sc ON sc.job_id = j.id
            LEFT JOIN job_status st ON st.job_id = j.id
            WHERE sc.llm_score IS NOT NULL AND s.region = ? AND sc.llm_score >= ?""" + statusClause + """

            ORDER BY sc.llm_score DESC, j.first_seen_at DESC
            LIMIT ?""", (rs, i) -> {
                Timestamp posted = rs.getTimestamp("posted_at");
                return new JobCard(rs.getLong("id"), rs.getString("title"), rs.getString("employer_name"),
                    rs.getString("location"), rs.getString("url"), rs.getString("region"),
                    rs.getInt("llm_score"), rs.getString("reason"), JobStatus.valueOf(rs.getString("status")),
                    posted == null ? null : posted.toInstant(), rs.getString("hm"), rs.getString("emp"));
            }, args.toArray());
    }

    @PatchMapping("/api/jobs/{id}/status")
    public Map<String, String> updateStatus(@PathVariable long id, @RequestBody StatusUpdate body) {
        JobStatus status;
        try {
            status = JobStatus.valueOf(String.valueOf(body.status()).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("status must be one of NEW, APPLIED, REFERRED, INTERVIEW");
        }
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM job WHERE id = ?", Integer.class, id);
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such job");
        }
        jdbc.update("""
            INSERT INTO job_status (job_id, status) VALUES (?, ?)
            ON CONFLICT (job_id) DO UPDATE SET status = EXCLUDED.status, updated_at = now()""",
            id, status.name());
        return Map.of("status", status.name());
    }

    /** Which sources work, which need a scraper, and which failed: nothing is silently skipped. */
    @GetMapping("/api/sources")
    public List<SourceHealth> sources() {
        return jdbc.query("""
            SELECT id, region, url, label, kind, ats_type, scrape_status, last_scraped_at, enabled
            FROM source ORDER BY region, id""", (rs, i) -> {
                Timestamp t = rs.getTimestamp("last_scraped_at");
                return new SourceHealth(rs.getLong("id"), rs.getString("region"), rs.getString("url"),
                    rs.getString("label"), rs.getString("kind"), rs.getString("ats_type"),
                    rs.getString("scrape_status"), t == null ? null : t.toInstant(), rs.getBoolean("enabled"));
            });
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
