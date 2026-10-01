package com.fasthire.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OpenAI Chat Completions with JSON mode and temperature 0. */
@Component
public class OpenAiLlmClient implements LlmClient {
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public OpenAiLlmClient(
            @Value("${fasthire.openai.api-key:}") String apiKey,
            @Value("${fasthire.openai.model:gpt-4o-mini}") String model,
            @Value("${fasthire.openai.base-url:https://api.openai.com/v1}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public String completeJson(String system, String user) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IOException("OPENAI_API_KEY is not set");
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody(system, user)))
            .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                throw new IOException("OpenAI HTTP " + res.statusCode() + ": " + truncate(res.body()));
            }
            return extractContent(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted calling OpenAI", e);
        }
    }

    String requestBody(String system, String user) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("temperature", 0);
        body.putObject("response_format").put("type", "json_object");
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        return body.toString();
    }

    String extractContent(String responseBody) throws IOException {
        JsonNode content = mapper.readTree(responseBody).path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IOException("OpenAI returned no content: " + truncate(responseBody));
        }
        return content.asText();
    }

    private static String truncate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
