package com.fasthire.scoring;

import com.fasthire.profile.Profile;
import com.fasthire.profile.ProfileLoader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Keyword filter, then LLM, for new jobs only. A failure on one job never stops the rest. */
@Service
public class ScoringService {
    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final JdbcTemplate jdbc;
    private final ProfileLoader profiles;
    private final KeywordFilter filter;
    private final FitScorer scorer;
    private final int threshold;

    public ScoringService(JdbcTemplate jdbc, ProfileLoader profiles, KeywordFilter filter, FitScorer scorer,
                          @Value("${fasthire.scoring.threshold:70}") int threshold) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.filter = filter;
        this.scorer = scorer;
        this.threshold = threshold;
    }

    /** Jobs with no score row yet: new ones, earlier ones, and ones whose LLM call failed last time. */
    public List<Long> unscoredJobIds() {
        return jdbc.queryForList(
            "SELECT id FROM job WHERE id NOT IN (SELECT job_id FROM job_score) ORDER BY id", Long.class);
    }

    /** @return jobs at or above the threshold, best first. */
    public List<ScoredJob> scoreJobs(List<Long> jobIds) {
        Profile profile = profiles.get();
        List<ScoredJob> hits = new ArrayList<>();
        int filtered = 0;
        int llmCalls = 0;
        for (long id : jobIds) {
            var rows = jdbc.queryForList("""
                SELECT j.title, j.employer_name, j.location, j.description, j.url, s.region
                FROM job j JOIN source s ON s.id = j.source_id WHERE j.id = ?""", id);
            if (rows.isEmpty()) {
                continue;
            }
            var r = rows.get(0);
            String title = (String) r.get("title");
            String employer = (String) r.get("employer_name");
            String location = (String) r.get("location");
            KeywordResult kw = filter.evaluate(profile, title, (String) r.get("description"));
            if (!kw.pass()) {
                filtered++;
                save(id, kw.score(), null, "Filtered out by keywords", null);
                continue;
            }
            try {
                llmCalls++;
                FitScore fit = scorer.score(profile, title, employer, location, (String) r.get("description"));
                save(id, kw.score(), fit.score(), fit.reason(), scorer.model());
                if (fit.score() >= threshold) {
                    hits.add(new ScoredJob(id, title, employer, location, (String) r.get("url"),
                        (String) r.get("region"), fit.score(), fit.reason()));
                }
            } catch (Exception e) {
                log.warn("Scoring failed for job {}: {}", id, e.getMessage());
            }
        }
        log.info("Scored {} new jobs: {} filtered by keywords, {} LLM calls, {} at or above {}",
            jobIds.size(), filtered, llmCalls, hits.size(), threshold);
        hits.sort(Comparator.comparingInt(ScoredJob::score).reversed());
        return hits;
    }

    private void save(long jobId, int keywordScore, Integer llmScore, String reason, String model) {
        jdbc.update("""
            INSERT INTO job_score (job_id, keyword_score, llm_score, reason, model)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (job_id) DO UPDATE SET keyword_score = EXCLUDED.keyword_score,
              llm_score = EXCLUDED.llm_score, reason = EXCLUDED.reason, model = EXCLUDED.model,
              scored_at = now()""", jobId, keywordScore, llmScore, reason, model);
    }
}
