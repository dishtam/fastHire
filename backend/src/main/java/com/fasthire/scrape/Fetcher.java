package com.fasthire.scrape;

import java.io.IOException;

/** Minimal HTTP seam so scrapers can be tested against saved responses. */
@FunctionalInterface
public interface Fetcher {
    String get(String url) throws IOException;

    /** POST with a JSON body (used by Workday). Implementations that do not need it can leave the default. */
    default String post(String url, String jsonBody) throws IOException {
        throw new UnsupportedOperationException("POST not supported by this Fetcher");
    }
}
