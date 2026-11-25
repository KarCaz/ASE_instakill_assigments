package com.instakill.notification;

import java.util.concurrent.CompletableFuture;

/**
 * Port for pushing a short text notification to an external service.
 * Implementations must never block the caller and must not throw: failures
 * are reported through the returned future / logs only.
 */
public interface NotificationSender {

    CompletableFuture<Void> send(String title, String message);
}
