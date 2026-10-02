package com.fasthire.scrape;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriComponentsBuilder;

/** Reads options a user put in the sheet URL, e.g. https://x.wd1.myworkdayjobs.com/Site?q=java */
final class SourceParams {
    private SourceParams() {}

    /** First of the named query parameters that is present, URL-decoded ('+' becomes a space); else null. */
    static String get(String url, String... names) {
        if (url == null) {
            return null;
        }
        var params = UriComponentsBuilder.fromUriString(url.startsWith("http") ? url : "https://" + url)
            .build().getQueryParams();
        for (String name : names) {
            String v = params.getFirst(name);
            if (v != null && !v.isBlank()) {
                return URLDecoder.decode(v, StandardCharsets.UTF_8).trim();
            }
        }
        return null;
    }
}
