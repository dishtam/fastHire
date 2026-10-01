package com.fasthire.drafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasthire.llm.LlmClient;
import com.fasthire.profile.Profile;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MessageDrafterTest {
    private final Profile profile = new Profile("Alex Sample", "Backend", Set.of("java"), "Java engineer");

    private static class Stub implements LlmClient {
        final Deque<String> replies = new ArrayDeque<>();
        final List<String> users = new ArrayList<>();
        Stub(String... r) { replies.addAll(List.of(r)); }
        public String completeJson(String system, String user) { users.add(user); return replies.poll(); }
        public String modelName() { return "stub"; }
    }

    @Test void draftsBothMessagesAndPassesJobReference() throws Exception {
        Stub llm = new Stub("{\"hiring_manager\": \"Hi, Job ID 213469 ...\", \"employee\": \"Hi, would you refer me?\"}");
        DraftPair p = new MessageDrafter(llm).draft(profile, "Fiber Engineer", "Amdocs", "Pune", "Job ID 213469", "desc");
        assertThat(p.hiringManager()).contains("213469");
        assertThat(p.employee()).contains("refer");
        assertThat(llm.users.get(0)).contains("Alex Sample", "Job reference: Job ID 213469", "Amdocs");
    }

    @Test void retriesWhenMessageTooLongOrMissing() throws Exception {
        String longMsg = "x".repeat(MessageDrafter.MAX_HM_CHARS + 1);
        Stub llm = new Stub("{\"hiring_manager\": \"" + longMsg + "\", \"employee\": \"ok\"}",
            "{\"hiring_manager\": \"short\", \"employee\": \"\"}",
            "{\"hiring_manager\": \"fine\", \"employee\": \"fine\"}");
        MessageDrafter d = new MessageDrafter(llm);
        assertThatThrownBy(() -> d.draft(profile, "t", "e", "l", "r", "d")).isInstanceOf(IOException.class);
        assertThat(d.draft(profile, "t", "e", "l", "r", "d").employee()).isEqualTo("fine");
    }
}
