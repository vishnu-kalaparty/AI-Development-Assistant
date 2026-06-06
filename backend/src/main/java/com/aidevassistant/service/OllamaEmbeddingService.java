package com.aidevassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final String baseUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaEmbeddingService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.embedding-model:nomic-embed-text}") String model,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://localhost:11434" : baseUrl.trim();
        this.model = model == null || model.isBlank() ? "mxbai-embed-large" : model.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    }

    public String modelName() {
        return model;
    }

    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "model", model,
                    "prompt", text
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/embeddings"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Ollama embedding failed: HTTP {}", response.statusCode());
                return List.of();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embedding = root.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                return List.of();
            }
            List<Float> vector = new ArrayList<>(embedding.size());
            for (JsonNode value : embedding) {
                vector.add((float) value.asDouble());
            }
            return vector;
        } catch (Exception e) {
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            log.warn("Ollama embedding request failed ({}): {}", baseUrl, detail);
            return List.of();
        }
    }
}
