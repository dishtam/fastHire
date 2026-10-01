package com.fasthire.alerts;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Reads new alert emails and stores their jobs, so they flow through the same scoring, drafting and dashboard
 * as scraped jobs. Each email is processed once (processed_email); each job is deduplicated by board job id,
 * and across sources by employer + title + location.
 */
@Service
public class AlertIngestService {
    private static final Logger log = LoggerFactory.getLogger(AlertIngestService.class);

    public record Result(int emails, int jobsFound, int newJobs, int emptyEmails) {}

    private final JdbcTemplate jdbc;
    private final MailboxClient mailbox;
    private final AlertParser parser;
    private final int lookbackDays;
    private final String fallbackRegion;

    public AlertIngestService(JdbcTemplate jdbc, MailboxClient mailbox, AlertParser parser,
                              @Value("${fasthire.mail.lookback-days:3}") int lookbackDays,
                              @Value("${fasthire.alerts.fallback-region:UAE}") String fallbackRegion) {
        this.jdbc = jdbc;
        this.mailbox = mailbox;
        this.parser = parser;
        this.lookbackDays = lookbackDays;
        this.fallbackRegion = fallbackRegion;
    }

    public Result ingest() throws IOException {
        if (!mailbox.configured()) {
            log.info("Alert mailbox not configured (MAIL_HOST / MAIL_USERNAME / MAIL_PASSWORD); skipping");
            return new Result(0, 0, 0, 0);
        }
        List<EmailContent> emails = mailbox.fetchSince(Instant.now().minus(Duration.ofDays(lookbackDays)));
        int processed = 0;
        int found = 0;
        int added = 0;
        int empty = 0;
        for (EmailContent email : emails) {
            Integer seen = jdbc.queryForObject("SELECT count(*) FROM processed_email WHERE message_id = ?",
                Integer.class, email.messageId());
            if (seen != null && seen > 0) {
                continue;
            }
            List<AlertJob> jobs = parser.parse(email);
            processed++;
            found += jobs.size();
            if (jobs.isEmpty()) {
                empty++;
                log.warn("No jobs found in email from '{}' with subject '{}'; if this is a job alert, its layout "
                    + "may need parser support", email.from(), email.subject());
            }
            for (AlertJob job : jobs) {
                added += store(job) ? 1 : 0;
            }
            jdbc.update("INSERT INTO processed_email (message_id, subject, jobs_found) VALUES (?, ?, ?) "
                + "ON CONFLICT DO NOTHING", email.messageId(), email.subject(), jobs.size());
        }
        log.info("Alert emails: {} new emails, {} jobs found, {} new jobs stored, {} emails with no jobs",
            processed, found, added, empty);
        return new Result(processed, found, added, empty);
    }

    /** @return true if the job was new. */
    private boolean store(AlertJob job) {
        String region = RegionGuesser.guess(job.location(), job.board().defaultRegion, fallbackRegion);
        if (job.employer() != null && !job.employer().isBlank()) {
            Integer same = jdbc.queryForObject("""
                SELECT count(*) FROM job WHERE lower(employer_name) = lower(?) AND lower(title) = lower(?)
                  AND coalesce(lower(location), '') = coalesce(lower(?), '')""", Integer.class,
                job.employer(), job.title(), job.location());
            if (same != null && same > 0) {
                return false;
            }
        }
        long sourceId = jdbc.queryForObject("""
            INSERT INTO source (region, url, label, kind, ats_type, scrape_status, last_scraped_at)
            VALUES (?, ?, ?, 'BOARD_EMAIL_ONLY', 'EMAIL', 'OK', now())
            ON CONFLICT (region, url) DO UPDATE SET scrape_status = 'OK', last_scraped_at = now()
            RETURNING id""", Long.class, region, "email:" + job.board().name().toLowerCase(),
            job.board().label + " alerts");
        int inserted = jdbc.update("""
            INSERT INTO job (source_id, external_id, employer_name, title, location, description, url, display_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (source_id, external_id) DO NOTHING""",
            sourceId, job.externalId(), blankToNull(job.employer()), job.title(), blankToNull(job.location()),
            blankToNull(job.snippet()), job.url(), job.externalId());
        return inserted > 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
