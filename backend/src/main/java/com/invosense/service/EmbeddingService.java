package com.invosense.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns invoice text into a vector embedding using Google Gemini's
 * gemini-embedding-001 model. These vectors are what power the
 * "find similar past invoices" (RAG / fraud-check) feature via
 * MongoDB Atlas Vector Search.
 */
@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper;

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    public EmbeddingService(RestTemplate restTemplate, ObjectMapper mapper) {
        this.restTemplate = restTemplate;
        this.mapper = mapper;
    }

    public List<Double> embed(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "models/gemini-embedding-001",
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = apiUrl + "?key=" + apiKey;

        JsonNode response = restTemplate.postForObject(url, request, JsonNode.class);
        List<Double> vector = new ArrayList<>();
        if (response != null && response.has("embedding")) {
            for (JsonNode v : response.get("embedding").get("values")) {
                vector.add(v.asDouble());
            }
        }
        return vector;
    }

    /** Cosine similarity - used as a local fallback if Atlas $vectorSearch isn't available on your tier. */
    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += Math.pow(a.get(i), 2);
            normB += Math.pow(b.get(i), 2);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}