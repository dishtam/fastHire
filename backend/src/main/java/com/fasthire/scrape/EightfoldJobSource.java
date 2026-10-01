package com.fasthire.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Eightfold "PCSX" career sites. The list call is verified against a real response
 * (/api/pcsx/search); the per-job details call is not yet, so enrich() is best effort.
 * The source URL's search (query, location, sort_by) is reused; the Eightfold "domain"
 * is the host minus its first label (jobs.amdocs.com -> amdocs.com) unless the URL has ?domain=.
 */
@Component
public class EightfoldJobSource implements JobSource {
    private static final int MAX_PAGES = 50;
    private static final List<String> PASSTHROUGH = List.of("query", "location", "sort_by");
    private static final List<String> DESCRIPTION_FIELDS =
        List.of("jobDescription", "job_description", "description", "publicDescription");

    private final Fetcher fetcher;
    private final ObjectMapper mapper = new ObjectMapper();

    public EightfoldJobSource(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String atsType() {
        return "EIGHTFOLD";
    }

    @Override
    public List<RawJob> fetch(SourceRef src) throws IOException {
        String host = src.atsToken();
        Map<String, String> params = sourceParams(src.url());
        String domain = params.getOrDefault("domain", defaultDomain(host));
        String employer = capitalize(domain.split("\\.")[0]);

        StringBuilder base = new StringBuilder("https://" + host + "/api/pcsx/search?domain=" + domain);
        for (String k : PASSTHROUGH) {
            if (params.containsKey(k)) {
                base.append('&').append(k).append('=').append(params.get(k));
            }
        }

        List<RawJob> out = new ArrayList<>();
        int start = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode data = mapper.readTree(fetcher.get(base + "&start=" + start)).path("data");
            JsonNode positions = data.path("positions");
            if (!positions.isArray() || positions.isEmpty()) {
                break;
            }
            for (JsonNode p : positions) {
                out.add(toJob(p, host, employer));
            }
            start += positions.size();
            if (start >= data.path("count").asInt(0)) {
                break;
            }
        }
        return out;
    }

    /** Fetches the full description for a job we are about to store. */
    @Override
    public RawJob enrich(SourceRef src, RawJob job) throws IOException {
        String host = src.atsToken();
        String domain = sourceParams(src.url()).getOrDefault("domain", defaultDomain(host));
        JsonNode data = mapper.readTree(fetcher.get(
            "https://" + host + "/api/pcsx/position_details?position_id=" + job.externalId()
                + "&domain=" + domain + "&hl=en")).path("data");
        for (String f : DESCRIPTION_FIELDS) {
            String text = data.path(f).asText("");
            if (!text.isBlank()) {
                return new RawJob(job.externalId(), job.employerName(), job.title(), job.location(),
                    job.description() + "\n\n" + stripPlaceholders(Html.toText(text)), job.url(), job.postedAt());
            }
        }
        return job;
    }

    /** Drops unfilled template tokens like [[reqLocation]]; a line left with only a label ("Location:") is removed. */
    static String stripPlaceholders(String text) {
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            String cleaned = line.replaceAll("\\[\\[[^\\]]*\\]\\]", "").stripTrailing();
            boolean hadToken = !cleaned.equals(line.stripTrailing());
            if (hadToken && (cleaned.isBlank() || cleaned.endsWith(":"))) {
                continue;
            }
            out.append(cleaned).append('\n');
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static RawJob toJob(JsonNode p, String host, String employer) {
        List<String> locs = new ArrayList<>();
        p.path("locations").forEach(l -> locs.add(l.asText()));
        long ts = p.path("postedTs").asLong(0);
        String mode = p.path("workLocationOption").asText("");
        String url = p.path("positionUrl").asText("");
        return new RawJob(
            p.path("id").asText(),
            employer,
            p.path("name").asText(),
            String.join("; ", locs),
            mode.isEmpty() ? "" : "Work mode: " + mode,
            url.isEmpty() ? null : "https://" + host + url,
            ts > 0 ? Instant.ofEpochSecond(ts) : null);
    }

    private static Map<String, String> sourceParams(String url) {
        Map<String, String> out = new LinkedHashMap<>();
        UriComponentsBuilder.fromUriString(url.startsWith("http") ? url : "https://" + url)
            .build().getQueryParams()
            .forEach((k, v) -> out.put(k, v.get(0)));
        return out;
    }

    private static String defaultDomain(String host) {
        int dot = host.indexOf('.');
        return dot >= 0 && host.indexOf('.', dot + 1) >= 0 ? host.substring(dot + 1) : host;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
