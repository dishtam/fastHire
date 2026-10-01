package com.fasthire.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Sends via the Telegram Bot API; without a token it just logs the digest. */
@Component
public class TelegramNotifier implements Notifier {
    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final String token;
    private final String chatId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public TelegramNotifier(@Value("${fasthire.telegram.bot-token:}") String token,
                            @Value("${fasthire.telegram.chat-id:}") String chatId) {
        this.token = token;
        this.chatId = chatId;
    }

    @Override
    public void send(String text) throws IOException {
        if (token.isBlank() || chatId.isBlank()) {
            log.info("Telegram not configured; digest follows:\n{}", text);
            return;
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                mapper.writeValueAsString(Map.of("chat_id", chatId, "text", text, "disable_web_page_preview", true))))
            .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                throw new IOException("Telegram HTTP " + res.statusCode() + ": " + res.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted sending Telegram message", e);
        }
    }
}
