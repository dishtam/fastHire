package com.fasthire.scrape;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Greenhouse public job board API: boards-api.greenhouse.io/v1/boards/{token}/jobs?content=true */
@Component
public class GreenhouseJobSource implements JobSource {
    private final Fetcher fetcher;
    private final ObjectMapper mapper = new ObjectMapper();

    public GreenhouseJobSource(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String atsType() {
        return "GREENHOUSE";
    }

    @Override
    public List<RawJob> fetch(SourceRef src) throws IOException {
        String body = fetcher.get(
            "https://boards-api.greenhouse.io/v1/boards/" + src.atsToken() + "/jobs?content=true");
        return parse(mapper.readTree(body), src.atsToken());
    }

    List<RawJob> parse(JsonNode root, String token) {
        List<RawJob> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            String employer = j.path("company_name").asText(token);
            Instant posted = null;
            String updated = j.path("first_published").asText(j.path("updated_at").asText(""));
            if (!updated.isEmpty()) {
                posted = OffsetDateTime.parse(updated).toInstant();
            }
            out.add(new RawJob(
                j.path("id").asText(),
                employer,
                j.path("title").asText(),
                j.path("location").path("name").asText(null),
                Html.toText(org.jsoup.parser.Parser.unescapeEntities(j.path("content").asText(""), false)),
                j.path("absolute_url").asText(null),
                posted));
        }
        return out;
    }
}
