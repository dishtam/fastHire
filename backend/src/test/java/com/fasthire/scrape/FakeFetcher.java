package com.fasthire.scrape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Test Fetcher: serves saved responses by URL substring and records every request. */
class FakeFetcher implements Fetcher {
    final List<String> gets = new ArrayList<>();
    final List<String> posts = new ArrayList<>();
    final List<String> postBodies = new ArrayList<>();
    final Map<String, String> getResponses = new LinkedHashMap<>();
    Function<String, String> postHandler = body -> "{}";

    static String fixture(String name) {
        try (var in = FakeFetcher.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    FakeFetcher onGet(String urlPart, String json) {
        getResponses.put(urlPart, json);
        return this;
    }

    @Override
    public String get(String url) throws IOException {
        gets.add(url);
        for (var e : getResponses.entrySet()) {
            if (url.contains(e.getKey())) {
                return e.getValue();
            }
        }
        throw new IOException("HTTP 404 from " + url);
    }

    @Override
    public String post(String url, String jsonBody) {
        posts.add(url);
        postBodies.add(jsonBody);
        return postHandler.apply(jsonBody);
    }
}
