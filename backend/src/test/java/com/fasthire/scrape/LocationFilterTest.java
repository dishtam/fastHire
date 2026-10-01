package com.fasthire.scrape;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LocationFilterTest {
    private static RawJob job(String id, String location) {
        return new RawJob(id, "Acme", "Engineer", location, "", "u", null, id);
    }

    private static SourceRef ref(String url, String type) {
        return new SourceRef(1, "UAE", url, type, "x");
    }

    private final List<RawJob> jobs = List.of(job("1", "Dubai, UAE"), job("2", "Bengaluru, India"), job("3", null),
        job("4", "Remote - UAE"));

    @Test void keepsJobsMatchingAnyTerm() {
        assertThat(LocationFilter.apply(ref("https://jobs.lever.co/acme?location=Dubai", "LEVER"), jobs))
            .extracting(RawJob::externalId).containsExactly("1");
        assertThat(LocationFilter.apply(ref("https://jobs.lever.co/acme?location=Dubai|UAE", "LEVER"), jobs))
            .extracting(RawJob::externalId).containsExactly("1", "4");
    }

    @Test void isCaseInsensitiveAndDecodesUrlEncoding() {
        assertThat(LocationFilter.terms("https://x/y?location=Abu%20Dhabi%7CDUBAI")).containsExactly("abu dhabi", "dubai");
        assertThat(LocationFilter.terms("https://x/y?location=Abu+Dhabi")).containsExactly("abu dhabi");
    }

    @Test void noFilterMeansNoChangeAndUnknownLocationsAreDroppedWhenFiltering() {
        assertThat(LocationFilter.apply(ref("https://jobs.lever.co/acme", "LEVER"), jobs)).hasSize(4);
        assertThat(LocationFilter.apply(ref("https://jobs.lever.co/acme?team=Eng", "LEVER"), jobs)).hasSize(4);
        assertThat(LocationFilter.apply(ref("https://jobs.lever.co/acme?location=India", "LEVER"), jobs))
            .extracting(RawJob::externalId).containsExactly("2"); // job 3 has no location
    }

    @Test void eightfoldSourcesAreLeftAloneBecauseThePlatformFiltersItself() {
        assertThat(LocationFilter.apply(ref("https://jobs.example.com/careers?location=india", "EIGHTFOLD"), jobs)).hasSize(4);
    }
}
