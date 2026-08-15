package com.nirikshan.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GroqChatClient {
    private final RestClient client;
    private final String apiKey;
    private final String model;

    public GroqChatClient(@Value("${GROQ_API_KEY:}") String apiKey,
                          @Value("${nirikshan.incident-summary.model:openai/gpt-oss-120b}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
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
}
