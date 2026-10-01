package com.fasthire.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasthire.profile.ProfileLoader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Builds a tailored resume PDF on demand. The model's bullet selection is cached per (job, profile version). */
@Service
public class ResumeService {
    public record Pdf(byte[] bytes, String filename) {}

    private final JdbcTemplate jdbc;
    private final ProfileLoader profiles;
    private final ResumeSelector selector;
    private final PdfRenderer renderer;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResumeService(JdbcTemplate jdbc, ProfileLoader profiles, ResumeSelector selector, PdfRenderer renderer) {
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.selector = selector;
        this.renderer = renderer;
    }

    public Pdf pdfForJob(long jobId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT title, employer_name, description FROM job WHERE id = ?", jobId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such job");
        }
        Map<String, Object> job = rows.get(0);
        ResumeProfile profile = profiles.resume();
        try {
            Selection selection = cached(jobId);
            if (selection == null) {
                selection = selector.select(profile, (String) job.get("title"), (String) job.get("employer_name"),
                    (String) job.get("description"));
                jdbc.update("""
                    INSERT INTO tailored_resume (job_id, profile_hash, selection) VALUES (?, ?, ?::jsonb)
                    ON CONFLICT (job_id, profile_hash) DO UPDATE SET selection = EXCLUDED.selection,
                      created_at = now()""", jobId, profiles.hash(), mapper.writeValueAsString(selection));
            }
            return new Pdf(renderer.render(profile, selection), filename(profile, (String) job.get("employer_name"),
                (String) job.get("title")));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not build resume: " + e.getMessage(), e);
        }
    }

    private Selection cached(long jobId) throws IOException {
        List<String> json = jdbc.queryForList(
            "SELECT selection::text FROM tailored_resume WHERE job_id = ? AND profile_hash = ?",
            String.class, jobId, profiles.hash());
        return json.isEmpty() ? null : mapper.readValue(json.get(0), Selection.class);
    }

    static String filename(ResumeProfile profile, String employer, String title) {
        String raw = String.join("_", profile.name(), employer == null ? "" : employer, title == null ? "" : title);
        String safe = raw.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return (safe.length() > 80 ? safe.substring(0, 80) : safe) + ".pdf";
    }
}
