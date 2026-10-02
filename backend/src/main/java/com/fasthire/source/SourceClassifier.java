package com.fasthire.source;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Sorts a career-site URL into a known ATS, a job board (alerts only), or unsupported. */
@Component
public class SourceClassifier {

    private static final List<String> BOARD_DOMAINS = List.of(
        "naukri.com", "naukrigulf.com", "linkedin.com", "indeed.com", "bayt.com",
        "instahyre.com", "foundit.in", "foundit.ae", "monster.com", "glassdoor.com", "shine.com",
        "wellfound.com", "cutshort.io", "hirist.tech", "timesjobs.com", "techgig.com", "weekday.works",
        "gulftalent.com", "laimoon.com", "talentmate.com", "mustakbil.com", "monstergulf.com", "relocate.me");

    private static final Pattern WORKDAY_HOST =
        Pattern.compile("^([a-z0-9-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com$");
    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2}([-_][A-Za-z]{2})?$");

    public Classification classify(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null || uri.getHost() == null) {
            return Classification.UNSUPPORTED;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        List<String> segments = List.of(path.split("/")).stream().filter(s -> !s.isBlank()).toList();
        String query = uri.getQuery() == null ? "" : uri.getQuery();

        for (String d : BOARD_DOMAINS) {
            if (host.equals(d) || host.endsWith("." + d)) {
                return Classification.BOARD;
            }
        }

        if (host.equals("boards.greenhouse.io") || host.equals("job-boards.greenhouse.io")) {
            if (!segments.isEmpty() && !segments.get(0).equals("embed")) {
                return Classification.ats("GREENHOUSE", segments.get(0));
            }
            Matcher m = Pattern.compile("(?:^|&)for=([^&]+)").matcher(query);
            if (m.find()) {
                return Classification.ats("GREENHOUSE", m.group(1));
            }
        }

        if ((host.equals("jobs.lever.co") || host.equals("jobs.eu.lever.co")) && !segments.isEmpty()) {
            return Classification.ats("LEVER", segments.get(0));
        }

        Matcher wd = WORKDAY_HOST.matcher(host);
        if (wd.matches()) {
            int i = !segments.isEmpty() && LOCALE.matcher(segments.get(0)).matches() ? 1 : 0;
            if (segments.size() > i) {
                return Classification.ats("WORKDAY", wd.group(1) + "/" + wd.group(2) + "/" + segments.get(i));
            }
        }

        if (host.equals("jobs.ashbyhq.com") && !segments.isEmpty()) {
            return Classification.ats("ASHBY", segments.get(0));
        }

        if (host.equals("careers.smartrecruiters.com") || host.equals("jobs.smartrecruiters.com")) {
            // /Company, /Company/123-title, or /oneclick-ui/company/Company/publication/...
            if (segments.size() >= 3 && segments.get(0).equals("oneclick-ui") && segments.get(1).equals("company")) {
                return Classification.ats("SMARTRECRUITERS", segments.get(2));
            }
            if (!segments.isEmpty() && !segments.get(0).equals("oneclick-ui")) {
                return Classification.ats("SMARTRECRUITERS", segments.get(0));
            }
        }

        boolean eightfold = host.endsWith(".eightfold.ai")
            || (path.startsWith("/careers") && (query.contains("pid=") || query.contains("sort_by=")));
        if (eightfold) {
            return Classification.ats("EIGHTFOLD", host);
        }

        return Classification.UNSUPPORTED;
    }

    private static URI parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (!s.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            s = "https://" + s;
        }
        // Sheet URLs may carry unencoded "|" or spaces (e.g. ?location=Dubai|UAE); URI rejects them.
        s = s.replace(" ", "%20").replace("|", "%7C");
        try {
            return URI.create(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
