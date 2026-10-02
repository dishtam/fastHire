package com.fasthire.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Lever postings API: api.lever.co/v0/postings/{token}?mode=json (EU tenants use api.eu.lever.co, not yet handled). */
@Component
public class LeverJobSource implements JobSource {
    private final Fetcher fetcher;
    private final ObjectMapper mapper = new ObjectMapper();

    public LeverJobSource(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String atsType() {
        return "LEVER";
    }

    @Override
    public List<RawJob> fetch(SourceRef src) throws IOException {
        String body = fetcher.get("https://api.lever.co/v0/postings/" + src.atsToken() + "?mode=json");
        return parse(mapper.readTree(body), src);
    }

    List<RawJob> parse(JsonNode root, SourceRef src) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root) {
            String desc = j.path("descriptionPlain").asText("");
            if (desc.isEmpty()) {
                desc = Html.toText(j.path("description").asText(""));
            }
            for (JsonNode list : j.path("lists")) {
                desc += "\n\n" + list.path("text").asText("") + "\n" + Html.toText(list.path("content").asText(""));
            }
            long created = j.path("createdAt").asLong(0);
            out.add(new RawJob(
                j.path("id").asText(),
                Employer.name(src, src.atsToken()),
                j.path("text").asText(),
                j.path("categories").path("location").asText(null),
                desc.trim(),
                j.path("hostedUrl").asText(null),
                created > 0 ? Instant.ofEpochMilli(created) : null,
                null));
        }
        return out;
    }
}
