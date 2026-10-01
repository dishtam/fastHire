package com.fasthire.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasthire.profile.Profile;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeywordFilterTest {
    private final Profile profile = new Profile("A", "Backend", new LinkedHashSet<>(
        List.of("java", "spring boot", "kafka", "c++")), "");
    private final KeywordFilter filter = new KeywordFilter(List.of("intern", "sales"), 2);

    @Test void titleHitPasses() {
        KeywordResult r = filter.evaluate(profile, "Senior Java Developer", "Work on payments.");
        assertThat(r.pass()).isTrue();
        assertThat(r.matched()).containsExactly("java");
    }

    @Test void twoBodyHitsPass() {
        assertThat(filter.evaluate(profile, "Engineer", "Spring Boot and Kafka services").pass()).isTrue();
    }

    @Test void oneBodyHitOnlyFails() {
        assertThat(filter.evaluate(profile, "Engineer", "Some Kafka exposure").pass()).isFalse();
    }

    @Test void wholeWordsOnly() {
        assertThat(filter.evaluate(profile, "Frontend Dev", "JavaScript and typescript").matched()).isEmpty();
    }

    @Test void handlesSymbolsAndExclusions() {
        assertThat(filter.evaluate(profile, "C++ Engineer", "").pass()).isTrue();
        assertThat(filter.evaluate(profile, "Java Intern", "java").pass()).isFalse();
    }
}
