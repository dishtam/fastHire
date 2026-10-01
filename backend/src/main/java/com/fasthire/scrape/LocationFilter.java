package com.fasthire.scrape;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Optional per-source location filter, written in the sheet URL: {@code https://jobs.lever.co/binance?location=Dubai}
 * or {@code ...?location=Bengaluru|Bangalore|India}. A job is kept when its location contains any of the terms.
 * This lets one global company board feed just one region's tab. Eightfold sources pass their own location to
 * the platform, so they are left alone.
 */
final class LocationFilter {
    private LocationFilter() {}

    static List<RawJob> apply(SourceRef ref, List<RawJob> jobs) {
        if ("EIGHTFOLD".equals(ref.atsType())) {
            return jobs;
        }
        List<String> terms = terms(ref.url());
        if (terms.isEmpty()) {
            return jobs;
        }
        return jobs.stream().filter(j -> matches(j.location(), terms)).toList();
    }

    static List<String> terms(String url) {
        if (url == null) {
            return List.of();
        }
        String value = UriComponentsBuilder.fromUriString(url.startsWith("http") ? url : "https://" + url)
            .build().getQueryParams().getFirst("location");
        if (value == null) {
            return List.of();
        }
        String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
        return Arrays.stream(decoded.split("\\|")).map(s -> s.trim().toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty()).toList();
    }

    /** A job with no stated location cannot be confirmed, so it is dropped when a filter is set. */
    static boolean matches(String location, List<String> terms) {
        if (location == null || location.isBlank()) {
            return false;
        }
        String l = location.toLowerCase(Locale.ROOT);
        return terms.stream().anyMatch(l::contains);
    }
}
