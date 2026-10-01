package com.fasthire.resume;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RewriteGuardTest {
    private static final String ORIGINAL = "Cut API p95 latency from 800ms to 220ms by adding Redis caching";
    private static final List<String> TAGS = List.of("performance", "redis", "java");

    private static boolean ok(String rewrite) {
        return RewriteGuard.faithfulBullet(ORIGINAL, TAGS, rewrite);
    }

    @Test void identicalAndLightRephraseAreFaithful() {
        assertThat(ok(ORIGINAL)).isTrue();
        assertThat(ok("Reduced API p95 latency from 800ms to 220ms through Redis caching")).isTrue();
        assertThat(ok("Added Redis caching, cutting API p95 latency from 800ms to 220ms")).isTrue();
    }

    @Test void addedTechnologyIsRejected() {
        assertThat(ok("Cut API p95 latency from 800ms to 220ms by adding Redis caching on Kubernetes")).isFalse();
        assertThat(ok("Cut API p95 latency by adding Redis and Kafka caching")).isFalse();
    }

    @Test void changedOrAddedNumbersAreRejected() {
        assertThat(ok("Cut API p95 latency from 900ms to 220ms by adding Redis caching")).isFalse();
        assertThat(ok("Cut API p95 latency by 73% by adding Redis caching")).isFalse();
    }

    @Test void overlongOrEmptyRewritesAreRejected() {
        assertThat(ok("")).isFalse();
        assertThat(ok(ORIGINAL + " and also improved many other things across the whole platform")).isFalse();
    }

    @Test void capitalisedSentenceStartsAreNotMistakenForTechnologies() {
        assertThat(RewriteGuard.faithfulBullet("Built services in Java. Wrote tests with JUnit", List.of(),
            "Wrote tests with JUnit. Built services in Java")).isTrue();
    }

    @Test void compoundTermsMatchTheirParts() {
        assertThat(RewriteGuard.faithfulBullet("Added localization (i18n/l10n) support", List.of(),
            "Added i18n support")).isTrue();
        assertThat(RewriteGuard.faithfulBullet("Added A11y fixes", List.of(), "Added A11y and WCAG fixes")).isFalse();
    }

    @Test void summaryMayUseAnyTrueFactButNothingElse() {
        String profile = "Backend engineer with 4 years in Java and Spring Boot. Kafka Redis AWS";
        assertThat(RewriteGuard.faithfulSummary("Java engineer with 4 years of Kafka and AWS experience", profile)).isTrue();
        assertThat(RewriteGuard.faithfulSummary("Java engineer with 7 years of experience", profile)).isFalse();
        assertThat(RewriteGuard.faithfulSummary("Java engineer experienced in Kubernetes", profile)).isFalse();
    }
}
