package com.fasthire.scrape;

import java.io.IOException;

/** Minimal HTTP GET seam so scrapers can be tested against saved responses. */
@FunctionalInterface
public interface Fetcher {
    String get(String url) throws IOException;
}
