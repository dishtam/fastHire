package com.fasthire.source;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceClassifierTest {
    private final SourceClassifier c = new SourceClassifier();

    @Test void greenhouse() {
        assertThat(c.classify("https://boards.greenhouse.io/acme/jobs/123"))
            .isEqualTo(new Classification(SourceKind.ATS, "GREENHOUSE", "acme"));
        assertThat(c.classify("https://boards.greenhouse.io/embed/job_board?for=acme").atsToken())
            .isEqualTo("acme");
    }

    @Test void lever() {
        assertThat(c.classify("https://jobs.lever.co/acme"))
            .isEqualTo(new Classification(SourceKind.ATS, "LEVER", "acme"));
    }

    @Test void workdayWithAndWithoutLocale() {
        assertThat(c.classify("https://acme.wd5.myworkdayjobs.com/en-US/External_Careers").atsToken())
            .isEqualTo("acme/wd5/External_Careers");
        assertThat(c.classify("https://acme.wd1.myworkdayjobs.com/Careers").atsToken())
            .isEqualTo("acme/wd1/Careers");
    }

    @Test void eightfoldSavedSearch() {
        Classification r = c.classify(
            "https://jobs.example.com/careers?query=Software+Engineer&start=0&pid=1&sort_by=relevance");
        assertThat(r.atsType()).isEqualTo("EIGHTFOLD");
        assertThat(r.atsToken()).isEqualTo("jobs.example.com");
    }

    @Test void boardsAreEmailOnly() {
        assertThat(c.classify("www.naukri.com").kind()).isEqualTo(SourceKind.BOARD_EMAIL_ONLY);
        assertThat(c.classify("https://in.linkedin.com/jobs").kind()).isEqualTo(SourceKind.BOARD_EMAIL_ONLY);
    }

    @Test void unknownAndGarbageAreUnsupported() {
        assertThat(c.classify("https://careers.custom-site.com/jobs").kind()).isEqualTo(SourceKind.UNSUPPORTED);
        assertThat(c.classify("").kind()).isEqualTo(SourceKind.UNSUPPORTED);
        assertThat(c.classify(null).kind()).isEqualTo(SourceKind.UNSUPPORTED);
    }
}
