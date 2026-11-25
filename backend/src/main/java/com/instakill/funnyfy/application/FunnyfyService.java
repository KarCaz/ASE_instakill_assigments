package com.instakill.funnyfy.application;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class FunnyfyService {

    private static final String SYSTEM_PROMPT =
            "Always reply with the user's message, but rewritten in a more funny style.";

    private final RestClient restClient;
    private final String endpoint;
    private final String model;

    public FunnyfyService(
            RestClient.Builder restClientBuilder,
            @Value("${local-llm.url:http://localhost:12345/v1/chat/completions}") String endpoint,
            @Value("${local-llm.model:google/gemma-3-270m}") String model
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.endpoint = endpoint;
        this.model = model;
    }

    public String rewrite(String text) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", text)
                ),
                "temperature", 0.7,
                "max_tokens", 256,
                "stream", false
        );

        try {
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);

            String rewritten = response == null
                    ? ""
                    : response.path("choices").path(0).path("message").path("content").asText("").trim();

            if (rewritten.isBlank()) {
                throw new FunnyfyException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "The local language model returned an empty response.", null);
            }
            return rewritten;
        } catch (FunnyfyException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            if (containsTimeout(ex)) {
                throw new FunnyfyException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT,
                        "The local language model took too long to respond. Please try again.", ex);
            }
            throw new FunnyfyException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "Cannot reach LM Studio. Make sure its local server is running on port 12345.", ex);
        } catch (RestClientResponseException ex) {
            throw new FunnyfyException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "LM Studio returned an error (HTTP " + ex.getStatusCode().value() + "). Check the selected model and server logs.", ex);
        } catch (RestClientException ex) {
            throw new FunnyfyException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Could not read a valid response from the local language model.", ex);
        }
    }

    private boolean containsTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
