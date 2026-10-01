package com.fasthire.api;

import java.time.Instant;

public record SourceHealth(long id, String region, String url, String label, String kind, String atsType,
                           String status, Instant lastScrapedAt, boolean enabled) {}
