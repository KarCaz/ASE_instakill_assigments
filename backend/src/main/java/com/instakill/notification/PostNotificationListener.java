package com.instakill.notification;

import com.instakill.post.domain.PostCreatedEvent;
import com.instakill.user.application.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reacts to {@link PostCreatedEvent}: runs on a background thread, and only
 * after the post's transaction has committed, so a rolled-back post never
 * produces a notification and the HTTP request is never delayed by it.
 */
@Component
public class PostNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(PostNotificationListener.class);

    private final NotificationSender sender;
    private final UserService userService;

    public PostNotificationListener(NotificationSender sender, UserService userService) {
        this.sender = sender;
        this.userService = userService;
    }

    @Async
    @TransactionalEventListener
    public void onPostCreated(PostCreatedEvent event) {
        try {
            String username = userService.getById(event.authorId()).username();
            sender.send("New post on Instakill", format(event, username));
        } catch (RuntimeException e) {
            log.warn("Could not send notification for post {}: {}", event.postId(), e.toString());
        }
    }

    static String format(PostCreatedEvent event, String username) {
        return "User: " + username + "\n"
                + "Post ID: " + event.postId() + "\n"
                + "Created at: " + event.createdAt();
    }
}
