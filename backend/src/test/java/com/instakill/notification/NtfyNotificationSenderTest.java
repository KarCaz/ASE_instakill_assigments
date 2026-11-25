package com.instakill.notification;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NtfyNotificationSenderTest {

    private HttpServer server;
    private final CompletableFuture<String[]> received = new CompletableFuture<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/my-topic", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.complete(new String[]{
                    exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Title"), body});
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void postsTitleAndBodyToTopicUrl() throws Exception {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        var sender = new NtfyNotificationSender(new NtfyProperties(true, baseUrl, "my-topic"));

        sender.send("Hello", "line1\nline2").get(5, TimeUnit.SECONDS);

        String[] req = received.get(5, TimeUnit.SECONDS);
        assertThat(req[0]).isEqualTo("POST");
        assertThat(req[1]).isEqualTo("Hello");
        assertThat(req[2]).isEqualTo("line1\nline2");
    }

    @Test
    void disabledSenderDoesNothing() throws Exception {
        var sender = new NtfyNotificationSender(new NtfyProperties(false, "http://127.0.0.1:1", "x"));
        sender.send("t", "m").get(1, TimeUnit.SECONDS); // completes without any connection
    }

    @Test
    void unreachableServerDoesNotThrow() throws Exception {
        var sender = new NtfyNotificationSender(new NtfyProperties(true, "http://127.0.0.1:1", "x"));
        sender.send("t", "m").get(10, TimeUnit.SECONDS); // failure is logged, future still completes normally
    }
}
