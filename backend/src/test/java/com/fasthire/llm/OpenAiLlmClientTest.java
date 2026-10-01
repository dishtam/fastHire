package com.fasthire.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiLlmClientTest {
    private final OpenAiLlmClient c = new OpenAiLlmClient("k", "gpt-test", "http://x");

    @Test void buildsJsonModeRequest() throws Exception {
        var n = new ObjectMapper().readTree(c.requestBody("sys", "usr"));
        assertThat(n.path("model").asText()).isEqualTo("gpt-test");
        assertThat(n.path("temperature").asInt()).isZero();
        assertThat(n.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(n.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(n.path("messages").get(1).path("content").asText()).isEqualTo("usr");
    }

    @Test void extractsContent() throws Exception {
        assertThat(c.extractContent("{\"choices\":[{\"message\":{\"content\":\"{\\\"a\\\":1}\"}}]}"))
            .isEqualTo("{\"a\":1}");
    }
}
