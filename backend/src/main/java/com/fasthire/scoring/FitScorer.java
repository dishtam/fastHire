package com.fasthire.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasthire.llm.LlmClient;
import com.fasthire.profile.Profile;
import java.io.IOException;
import org.springframework.stereotype.Component;

/** Asks the LLM for a 0-100 fit score and a one-line reason; validates the reply and retries once. */
@Component
public class FitScorer {
    static final int MAX_DESCRIPTION_CHARS = 6000;
    static final String SYSTEM = """
        You score how well a job matches a candidate. Use only the facts in the candidate profile; \
        never assume skills or experience that are not listed.
        Reply with JSON only: {"score": <integer 0-100>, "reason": "<one sentence, max 25 words>"}.
        Scale: 85-100 strong match on role and core skills; 65-84 good match with minor gaps; \
        40-64 partial; below 40 poor match or wrong seniority/domain.""";

    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();

    public FitScorer(LlmClient llm) {
        this.llm = llm;
    }

    public String model() {
        return llm.modelName();
    }

    public FitScore score(Profile profile, String title, String employer, String location, String description)
            throws IOException {
        String user = "CANDIDATE PROFILE\n" + profile.promptText()
            + "\n\nJOB\nTitle: " + title + "\nEmployer: " + nz(employer) + "\nLocation: " + nz(location)
            + "\nDescription:\n" + truncate(nz(description));
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String reply = llm.completeJson(SYSTEM, user);
            try {
                return parse(reply);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last;
    }

    FitScore parse(String reply) throws IOException {
        JsonNode n = mapper.readTree(reply);
        JsonNode score = n.path("score");
        String reason = n.path("reason").asText("").trim();
        if (!score.isNumber() || score.asInt() < 0 || score.asInt() > 100 || reason.isEmpty()) {
            throw new IOException("Invalid score reply: " + reply);
        }
        return new FitScore(score.asInt(), reason);
    }

    private static String truncate(String s) {
        return s.length() > MAX_DESCRIPTION_CHARS ? s.substring(0, MAX_DESCRIPTION_CHARS) : s;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
