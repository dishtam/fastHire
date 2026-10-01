package com.fasthire.llm;

import java.io.IOException;

/** Provider-neutral completion call. OpenAI today; swap the bean to change provider. */
public interface LlmClient {
    /** Returns the model's JSON-object reply for the given prompts. */
    String completeJson(String system, String user) throws IOException;

    String modelName();
}
