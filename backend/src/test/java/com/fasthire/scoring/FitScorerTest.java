package com.fasthire.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasthire.llm.LlmClient;
import com.fasthire.profile.Profile;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FitScorerTest {
    private final Profile profile = new Profile("A", "Backend", Set.of("java"), "Java engineer");

    private static class Stub implements LlmClient {
        final Deque<String> replies = new ArrayDeque<>();
        final List<String> users = new java.util.ArrayList<>();
        Stub(String... r) { replies.addAll(List.of(r)); }
        public String completeJson(String system, String user) { users.add(user); return replies.poll(); }
        public String modelName() { return "stub"; }
    }

    @Test void parsesValidReply() throws Exception {
        Stub llm = new Stub("{\"score\": 82, \"reason\": \"Strong Java match.\"}");
        FitScore s = new FitScorer(llm).score(profile, "Java Dev", "Acme", "Dubai", "desc");
        assertThat(s).isEqualTo(new FitScore(82, "Strong Java match."));
        assertThat(llm.users.get(0)).contains("Java engineer", "Java Dev", "Acme", "Dubai");
    }

    @Test void retriesOnceOnMalformedReply() throws Exception {
        Stub llm = new Stub("not json", "{\"score\": 55, \"reason\": \"Partial.\"}");
        assertThat(new FitScorer(llm).score(profile, "t", "e", "l", "d").score()).isEqualTo(55);
        assertThat(llm.users).hasSize(2);
    }

    @Test void rejectsOutOfRangeAfterRetry() {
        Stub llm = new Stub("{\"score\": 150, \"reason\": \"x\"}", "{\"score\": -1, \"reason\": \"x\"}");
        assertThatThrownBy(() -> new FitScorer(llm).score(profile, "t", "e", "l", "d"))
            .isInstanceOf(IOException.class);
    }

    @Test void truncatesLongDescriptions() throws Exception {
        Stub llm = new Stub("{\"score\": 1, \"reason\": \"x\"}");
        new FitScorer(llm).score(profile, "t", "e", "l", "x".repeat(20000));
        assertThat(llm.users.get(0).length()).isLessThan(FitScorer.MAX_DESCRIPTION_CHARS + 500);
    }
}
