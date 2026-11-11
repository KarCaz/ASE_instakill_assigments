package com.instakill.media.processing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "media.processing")
public record ImageProcessingProperties(
        @DefaultValue("300") int maxWidth,
        @DefaultValue("Instakill Demo") String watermarkText
) {
}
