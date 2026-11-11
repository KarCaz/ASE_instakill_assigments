package com.instakill.post.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a post has been created. Other modules (e.g. notifications)
 * react to it without {@code PostService} knowing about them.
 */
public record PostCreatedEvent(UUID postId, UUID authorId, Instant createdAt) {
    public PostCreatedEvent {
        if (postId == null || authorId == null || createdAt == null) {
            throw new IllegalArgumentException("postId, authorId and createdAt must not be null");
        }
    }
}
