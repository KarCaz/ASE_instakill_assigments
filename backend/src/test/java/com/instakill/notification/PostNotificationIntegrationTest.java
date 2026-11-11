package com.instakill.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PostNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationSender sender;

    @Test
    void creatingAPostSendsNotificationWithUsernamePostIdAndTimestamp() throws Exception {
        String username = "notif" + System.nanoTime();
        MvcResult register = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "email", username + "@example.com", "password", "password"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie token = register.getResponse().getCookie("ACCESS_TOKEN");

        MvcResult created = mockMvc.perform(post("/api/posts")
                        .cookie(token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "hello"))))
                .andExpect(status().isCreated())
                .andReturn();
        String postId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // The listener is asynchronous, so allow a moment for it to run.
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(sender, timeout(5000)).send(anyString(), message.capture());
        assertThat(message.getValue())
                .contains("User: " + username)
                .contains("Post ID: " + postId)
                .contains("Created at: ");
    }
}
