package com.fasthire.drafting;

import com.fasthire.profile.ProfileLoader;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Drafts messages for every job at or above the threshold that has none yet. */
@Service
public class DraftService {
    private static final Logger log = LoggerFactory.getLogger(DraftService.class);

    private final JdbcTemplate jdbc;
    private final ProfileLoader profiles;
    private final MessageDrafter drafter;
    private final int threshold;

    public DraftService(JdbcTemplate jdbc, ProfileLoader profiles, MessageDrafter drafter,
                        @Value("${fasthire.scoring.threshold:70}") int threshold) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.drafter = drafter;
        this.threshold = threshold;
    }

    /** @return number of jobs drafted. A failure on one job is logged and retried on the next run. */
    public int draftMissing() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT j.id, j.display_id, j.title, j.employer_name, j.location, j.description, j.url
            FROM job j JOIN job_score sc ON sc.job_id = j.id
            WHERE sc.llm_score >= ?
              AND NOT EXISTS (SELECT 1 FROM draft_message d WHERE d.job_id = j.id)
            ORDER BY sc.llm_score DESC""", threshold);
        int drafted = 0;
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            try {
                String displayId = (String) r.get("display_id");
                String ref = displayId != null && !displayId.isBlank() ? "Job ID " + displayId : (String) r.get("url");
                DraftPair pair = drafter.draft(profiles.get(), (String) r.get("title"),
                    (String) r.get("employer_name"), (String) r.get("location"), ref,
                    (String) r.get("description"));
                jdbc.update("INSERT INTO draft_message (job_id, type, body) VALUES (?, 'HM', ?), (?, 'EMPLOYEE', ?)",
                    id, pair.hiringManager(), id, pair.employee());
                drafted++;
            } catch (Exception e) {
                log.warn("Drafting failed for job {}: {}", id, e.getMessage());
            }
        }
        return drafted;
    }
}
