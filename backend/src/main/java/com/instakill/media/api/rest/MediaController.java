package com.instakill.media.api.rest;

import com.instakill.common.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal media upload: stores a file on local disk and returns a URL that
 * {@code MediaWebConfig} serves back under /media/**.
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "webm");

    private final Path mediaRoot;

    public MediaController(@Value("${media.base-path:media}") String basePath) {
        this.mediaRoot = Path.of(basePath).toAbsolutePath().normalize();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaResponse upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file type. Allowed extensions: "
                    + String.join(", ", ALLOWED_EXTENSIONS.stream().sorted().toList()));
        }
        String contentType = file.getContentType();
        if (contentType == null
                || !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new BadRequestException("Only image and video uploads are allowed");
        }

        // Generated name only: the client-supplied name never reaches the filesystem path.
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(mediaRoot);
            Path target = mediaRoot.resolve(filename).normalize();
            if (!target.startsWith(mediaRoot)) {
                throw new BadRequestException("Invalid file name");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store uploaded file", e);
        }
        return new MediaResponse("/media/" + filename);
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public record MediaResponse(String mediaUrl) {
    }
}
