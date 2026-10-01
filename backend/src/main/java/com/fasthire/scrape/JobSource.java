package com.fasthire.scrape;

import java.io.IOException;
import java.util.List;

/** One implementation per ATS platform, not per company. */
public interface JobSource {
    String atsType();

    List<RawJob> fetch(SourceRef source) throws IOException;

    /** Optional second call for the full description, made only for jobs not yet stored. */
    default RawJob enrich(SourceRef source, RawJob job) throws IOException {
        return job;
    }
}
