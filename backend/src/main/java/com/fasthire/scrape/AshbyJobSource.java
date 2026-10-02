package com.fasthire.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Ashby public job board API: GET api.ashbyhq.com/posting-api/job-board/{slug}?includeCompensation=true.
 * Unauthenticated; returns every published posting, with descriptions, in one response (no paging).
 */
@Component
public class AshbyJobSource implements JobSource {
    private final Fetcher fetcher;
    private final ObjectMapper mapper = new ObjectMapper();

    public AshbyJobSource(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String atsType() {
        return "ASHBY";
    }

    @Override
    public List<RawJob> fetch(SourceRef src) throws IOException {
        String body = fetcher.get("https://api.ashbyhq.com/posting-api/job-board/" + src.atsToken()
            + "?includeCompensation=true");
        return parse(mapper.readTree(body), src);
    }

    List<RawJob> parse(JsonNode root, SourceRef src) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            if (j.has("isListed") && !j.path("isListed").asBoolean(true)) {
                continue; // unlisted postings are not on the public board
            }
            String description = j.path("descriptionPlain").asText("");
            if (description.isBlank()) {
                description = Html.toText(j.path("descriptionHtml").asText(""));
            }
            String pay = j.path("compensation").path("compensationTierSummary").asText("");
            if (!pay.isBlank()) {
                description = "Compensation: " + pay + "\n\n" + description;
            }
            out.add(new RawJob(
                j.path("id").asText(),
                Employer.name(src, src.atsToken()),
                j.path("title").asText(),
                location(j),
                description.trim(),
                j.path("jobUrl").asText(j.path("applyUrl").asText(null)),
                posted(j.path("publishedAt").asText("")),
                null));
        }
        return out;
    }

    /** Primary location plus any secondary ones, so a location filter sees all of them. */
    private static String location(JsonNode j) {
        Set<String> places = new LinkedHashSet<>();
        String primary = j.path("location").asText("");
        if (!primary.isBlank()) {
            places.add(primary);
        }
        for (JsonNode s : j.path("secondaryLocations")) {
            String loc = s.isTextual() ? s.asText() : s.path("location").asText("");
            if (!loc.isBlank()) {
                places.add(loc);
            }
        }
        if (places.isEmpty() && j.path("isRemote").asBoolean(false)) {
            places.add("Remote");
        }
        return places.isEmpty() ? null : String.join("; ", places);
    }

    private static Instant posted(String iso) {
        try {
            return iso.isBlank() ? null : OffsetDateTime.parse(iso).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
