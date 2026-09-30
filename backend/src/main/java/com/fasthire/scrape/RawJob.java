package com.fasthire.scrape;

import java.time.Instant;

public record RawJob(String externalId, String employerName, String title, String location,
                     String description, String url, Instant postedAt) {}
