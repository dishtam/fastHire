package com.fasthire.scrape;

public record SourceRef(long id, String region, String url, String atsType, String atsToken, String label) {
    public SourceRef(long id, String region, String url, String atsType, String atsToken) {
        this(id, region, url, atsType, atsToken, null);
    }
}
