package com.fasthire.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Workday career sites (*.myworkdayjobs.com). The sheet URL gives tenant, data centre and site; the token is
 * "tenant/wdN/site". The same public JSON API serves every tenant: POST /wday/cxs/{tenant}/{site}/jobs lists
 * (20 per page, Workday rejects more) and GET /wday/cxs/{tenant}/{site}{externalPath} gives one job.
 * Add {@code ?q=java} to the sheet URL to search on Workday's side; big tenants have thousands of jobs, so
 * paging stops after MAX_PAGES.
 */
@Component
public class WorkdayJobSource implements JobSource {
    static final int PAGE_SIZE = 20;
    static final int MAX_PAGES = 30;
    private static final Pattern POSTED = Pattern.compile("(?i)posted\\s+(today|yesterday|(\\d+)\\+?\\s+days?\\s+ago)");

    private final Fetcher fetcher;
    private final ObjectMapper mapper = new ObjectMapper();

    public WorkdayJobSource(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String atsType() {
        return "WORKDAY";
    }

    @Override
    public List<RawJob> fetch(SourceRef src) throws IOException {
        Site site = Site.of(src);
        String search = SourceParams.get(src.url(), "q");
        List<RawJob> out = new ArrayList<>();
        int total = -1; // Workday reports the total on the first page only
        for (int page = 0; page < MAX_PAGES; page++) {
            int offset = page * PAGE_SIZE;
            ObjectNode body = mapper.createObjectNode();
            body.putObject("appliedFacets");
            body.put("limit", PAGE_SIZE);
            body.put("offset", offset);
            body.put("searchText", search == null ? "" : search);
            JsonNode root = mapper.readTree(fetcher.post(site.api() + "/jobs", body.toString()));
            if (page == 0) {
                total = root.path("total").asInt(-1);
            }
            JsonNode postings = root.path("jobPostings");
            if (!postings.isArray() || postings.isEmpty()) {
                break;
            }
            for (JsonNode p : postings) {
                out.add(toJob(p, src, site));
            }
            if (postings.size() < PAGE_SIZE || (total >= 0 && offset + postings.size() >= total)) {
                break;
            }
        }
        return out;
    }

    @Override
    public RawJob enrich(SourceRef src, RawJob job) throws IOException {
        Site site = Site.of(src);
        JsonNode info = mapper.readTree(fetcher.get(site.api() + job.externalId())).path("jobPostingInfo");
        String html = info.path("jobDescription").asText("");
        if (html.isBlank()) {
            return job;
        }
        StringBuilder text = new StringBuilder(job.description());
        String timeType = info.path("timeType").asText("");
        if (!timeType.isBlank()) {
            text.append("Employment type: ").append(timeType).append("\n\n");
        }
        text.append(Html.toText(html));
        String url = info.path("externalUrl").asText(job.url());
        return new RawJob(job.externalId(), job.employerName(), job.title(), job.location(), text.toString().trim(),
            url, job.postedAt(), job.displayId());
    }

    private RawJob toJob(JsonNode p, SourceRef src, Site site) {
        String path = p.path("externalPath").asText();
        JsonNode bullets = p.path("bulletFields");
        String reqId = bullets.isArray() && !bullets.isEmpty() ? bullets.get(0).asText() : null;
        return new RawJob(path,
            Employer.name(src, site.tenant()),
            p.path("title").asText(),
            p.path("locationsText").asText(null),
            "",
            "https://" + site.host() + "/en-US/" + site.name() + path,
            posted(p.path("postedOn").asText("")),
            reqId);
    }

    /** "Posted 3 Days Ago", "Posted 30+ Days Ago", "Posted Today": Workday gives text, not a date. */
    static Instant posted(String text) {
        Matcher m = POSTED.matcher(text);
        if (!m.find()) {
            return null;
        }
        String when = m.group(1).toLowerCase();
        long days = when.equals("today") ? 0 : when.equals("yesterday") ? 1 : Long.parseLong(m.group(2));
        return Instant.now().minus(Duration.ofDays(days));
    }

    private record Site(String tenant, String dc, String name) {
        static Site of(SourceRef src) throws IOException {
            String[] parts = src.atsToken() == null ? new String[0] : src.atsToken().split("/");
            if (parts.length != 3) {
                throw new IOException("Workday source needs tenant/wdN/site, got '" + src.atsToken() + "'");
            }
            return new Site(parts[0], parts[1], parts[2]);
        }

        String host() {
            return tenant + "." + dc + ".myworkdayjobs.com";
        }

        String api() {
            return "https://" + host() + "/wday/cxs/" + tenant + "/" + name;
        }
    }
}
