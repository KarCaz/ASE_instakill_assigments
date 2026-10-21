package com.instakill.media.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerTest {

    @TempDir
    static Path mediaDir;

    @DynamicPropertySource
    static void mediaProperties(DynamicPropertyRegistry registry) {
        registry.add("media.base-path", () -> mediaDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie token;

    @BeforeEach
    void registerUser() throws Exception {
        String unique = "media" + System.nanoTime();
        String payload = objectMapper.writeValueAsString(
                new Auth(unique, unique + "@example.com", "password"));
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();
        token = result.getResponse().getCookie("ACCESS_TOKEN");
        assertThat(token).isNotNull();
    }

    @Test
    void uploadStoresFileAndServesItBack() throws Exception {
        byte[] bytes = "fake-mp4-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "clip.MP4", "video/mp4", bytes);

        MvcResult result = mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        String mediaUrl = json.get("mediaUrl").asText();
        assertThat(mediaUrl).startsWith("/media/").endsWith(".mp4");

        String filename = mediaUrl.substring("/media/".length());
        assertThat(Files.readAllBytes(mediaDir.resolve(filename))).isEqualTo(bytes);

        mockMvc.perform(get(mediaUrl))
                .andExpect(status().isOk())
                .andExpect(content().bytes(bytes));
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void disallowedExtensionIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "script.sh", "text/plain", "x".getBytes());
        mockMvc.perform(multipart("/api/media").file(file).cookie(token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", "x".getBytes());
        mockMvc.perform(multipart("/api/media").file(file))
                .andExpect(status().isUnauthorized());
    }

    private record Auth(String username, String email, String password) {
    }
}
