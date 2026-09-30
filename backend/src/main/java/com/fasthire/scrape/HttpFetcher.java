package com.fasthire.scrape;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** GET with a User-Agent, a timeout, and retries with backoff on 429/5xx. */
@Component
public class HttpFetcher implements Fetcher {
    private static final int MAX_ATTEMPTS = 3;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String get(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "fastHire/0.1 (personal job search tool)")
                    .header("Accept", "application/json")
                    .GET().build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                int code = res.statusCode();
                if (code == 429 || code >= 500) {
                    last = new IOException("HTTP " + code + " from " + url);
                } else if (code >= 400) {
                    throw new IOException("HTTP " + code + " from " + url);
                } else {
                    return res.body();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted fetching " + url, e);
            }
            if (attempt < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(1000L * attempt * attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted", e);
                }
            }
        }
        throw last;
    }
}
