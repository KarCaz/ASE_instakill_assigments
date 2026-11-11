package com.instakill.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the public ntfy.sh notification service.
 * Anyone who knows the topic name can read it, so pick a unique one.
 */
@ConfigurationProperties(prefix = "notification.ntfy")
public record NtfyProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("https://ntfy.sh") String baseUrl,
        @DefaultValue("instakill-demo-change-me") String topic
) {
}
