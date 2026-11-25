package com.instakill.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Sends notifications to ntfy.sh with a non-blocking HTTP request
 * ({@link HttpClient#sendAsync}). See https://docs.ntfy.sh/publish/.
 */
@Component
public class NtfyNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NtfyNotificationSender.class);

    private final NtfyProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public NtfyNotificationSender(NtfyProperties properties) {
        this.properties = properties;
    }

    @Override
    public CompletableFuture<Void> send(String title, String message) {
        if (!properties.enabled()) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Title", title)
                    .header("Tags", "camera")
                    .POST(HttpRequest.BodyPublishers.ofString(message, StandardCharsets.UTF_8))
                    .build();
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .handle((response, error) -> {
                        if (error != null) {
                            log.warn("ntfy notification failed: {}", error.toString());
                        } else if (response.statusCode() >= 300) {
                            log.warn("ntfy notification rejected with HTTP {}", response.statusCode());
                        } else {
                            log.debug("ntfy notification sent");
                        }
                        return null;
                    });
        } catch (RuntimeException e) {
            // Bad URL/header etc. A notification problem must never break post creation.
            log.warn("Could not build ntfy request: {}", e.toString());
            return CompletableFuture.completedFuture(null);
        }
    }

    private String url() {
        String base = properties.baseUrl().endsWith("/")
                ? properties.baseUrl().substring(0, properties.baseUrl().length() - 1)
                : properties.baseUrl();
        return base + "/" + properties.topic();
    }
}
