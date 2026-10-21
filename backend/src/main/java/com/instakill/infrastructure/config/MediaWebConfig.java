package com.instakill.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exposes the files saved in the local media directory under /media/**.
 */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final Path mediaRoot;

    public MediaWebConfig(@Value("${media.base-path:media}") String basePath) {
        this.mediaRoot = Path.of(basePath).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            // Create the directory at startup so the resource location always exists.
            Files.createDirectories(mediaRoot);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not create media directory " + mediaRoot, e);
        }
        // The trailing slash is required for Spring to treat the location as a directory.
        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaRoot.toUri().toString());
    }
}
