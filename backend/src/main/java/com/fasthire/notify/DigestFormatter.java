package com.fasthire.notify;

import com.fasthire.scoring.ScoredJob;
import java.util.List;

public final class DigestFormatter {
    static final int MAX_JOBS = 15;

    private DigestFormatter() {}

    /** Plain text (no Markdown, so nothing needs escaping). Telegram's limit is 4096 chars. */
    public static String format(List<ScoredJob> jobs) {
        if (jobs.isEmpty()) {
            return "fastHire: no new matching jobs today.\nReminder: refresh your Naukri/Instahyre profile.";
        }
        StringBuilder sb = new StringBuilder("fastHire: " + jobs.size() + " new matching job"
            + (jobs.size() == 1 ? "" : "s") + "\n");
        int shown = 0;
        for (ScoredJob j : jobs) {
            if (shown++ == MAX_JOBS) {
                sb.append("\n...and ").append(jobs.size() - MAX_JOBS).append(" more on the dashboard.");
                break;
            }
            sb.append('\n').append(j.score()).append("  ").append(j.title());
            if (j.employer() != null && !j.employer().isBlank()) {
                sb.append(" @ ").append(j.employer());
            }
            sb.append(" [").append(j.region()).append("]\n");
            sb.append(j.reason()).append('\n');
            if (j.url() != null) {
                sb.append(j.url()).append('\n');
            }
        }
        sb.append("\nReminder: refresh your Naukri/Instahyre profile.");
        String text = sb.toString();
        return text.length() > 4000 ? text.substring(0, 3990) + "\n..." : text;
    }
}
