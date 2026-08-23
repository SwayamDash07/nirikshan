package com.nirikshan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class GroqChatClient {
    private final RestClient client;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper;

    public GroqChatClient(@Value("${GROQ_API_KEY:}") String apiKey,
                          @Value("${nirikshan.incident-summary.model:openai/gpt-oss-20b}") String model,
                          ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.mapper = mapper;
        this.client = RestClient.builder().baseUrl("https://api.groq.com/openai/v1").build();
    }

    public boolean isConfigured() { return !apiKey.isBlank(); }
    public String model() { return model; }
    public String maskedKey() { return apiKey.length() <= 4 ? "****" : apiKey.substring(0, Math.min(4, apiKey.length())) + "***"; }

    public String complete(String systemPrompt, String userPrompt, int maxCompletionTokens) {
        if (!isConfigured()) throw new IllegalStateException("GROQ_API_KEY is not configured");
        Map<String, Object> body = Map.of("model", model, "temperature", 0.1, "max_completion_tokens", maxCompletionTokens,
                "messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt)));
        return client.post().uri("/chat/completions").header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
    }

    public StreamResult stream(String systemPrompt, String userPrompt, int maxCompletionTokens, Consumer<String> onToken) {
        if (!isConfigured()) throw new IllegalStateException("GROQ_API_KEY is not configured");
        Map<String, Object> body = Map.of("model", model, "temperature", 0.1, "max_completion_tokens", maxCompletionTokens,
                "stream", true, "messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt)));
        return client.post().uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> {
                    if (response.getStatusCode().isError()) {
                        throw new IllegalStateException("Groq streaming request failed with status " + response.getStatusCode().value());
                    }
                    boolean completed = false;
                    StringBuilder contentBuffer = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (!line.startsWith("data:")) continue;
                            String data = line.substring(5).trim();
                            if (data.isBlank() || "[DONE]".equals(data)) continue;
                            JsonNode root = mapper.readTree(data);
                            JsonNode content = root.path("choices").path(0).path("delta").path("content");
                            if (content.isTextual() && !content.asText().isEmpty()) {
                                contentBuffer.append(content.asText());
                                onToken.accept(content.asText());
                            }
                            String finishReason = root.path("choices").path(0).path("finish_reason").asText("");
                            if ("stop".equalsIgnoreCase(finishReason) || "eos".equalsIgnoreCase(finishReason)) completed = true;
                        }
                    }
                    return new StreamResult(contentBuffer.toString(), completed);
                });
    }

    public record StreamResult(String content, boolean completed) { }
}
