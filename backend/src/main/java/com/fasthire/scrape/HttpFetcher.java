package com.fasthire.scrape;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** GET/POST with a User-Agent, a timeout, and retries with backoff on 429/5xx. */
@Component
public class HttpFetcher implements Fetcher {
    private static final int MAX_ATTEMPTS = 3;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public String get(String url) throws IOException {
        return send(url, () -> base(url).GET().build());
    }

    @Override
    public String post(String url, String jsonBody) throws IOException {
        return send(url, () -> base(url).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build());
    }

    private static HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "fastHire/0.1 (personal job search tool)")
            .header("Accept", "application/json")
            .header("Accept-Language", "en-US");
    }

    private String send(String url, Supplier<HttpRequest> request) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> res = client.send(request.get(), HttpResponse.BodyHandlers.ofString());
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
