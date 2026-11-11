package com.instakill.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables {@code @Async} so slow side effects (notifications) run off the request thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
