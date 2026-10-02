package com.shopping.cart.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shopping.cart.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** The only class that talks to the Gemini API; all AI features go through here. */
@Component
public class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final String UNAVAILABLE = "The AI service is unavailable right now. Please try again shortly.";

    private final AiProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public GeminiClient(AiProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public String model() {
        return properties.getModel();
    }

    /**
     * Generates a JSON response that matches {@code responseSchema}
     * (Gemini OpenAPI-subset schema with STRING/OBJECT/ARRAY types).
     */
    public JsonNode generateJson(String systemInstruction, String prompt, ObjectNode responseSchema) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI features are not configured.");
        }

        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
        body.putArray("contents").addObject()
                .put("role", "user")
                .putArray("parts").addObject().put("text", prompt);
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.set("responseSchema", responseSchema);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(ENDPOINT,
                        URLEncoder.encode(properties.getModel(), StandardCharsets.UTF_8))))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", properties.getGeminiApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warn("Gemini request failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        }

        if (response.statusCode() == 429) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "The AI service is busy. Please try again in a minute.");
        }
        if (response.statusCode() / 100 != 2) {
            log.warn("Gemini returned HTTP {}: {}", response.statusCode(), truncate(response.body()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        }

        try {
            JsonNode text = mapper.readTree(response.body())
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (!text.isTextual()) {
                log.warn("Gemini returned no candidate text: {}", truncate(response.body()));
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
            }
            return mapper.readTree(text.asText());
        } catch (IOException e) {
            log.warn("Gemini returned invalid JSON: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        }
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
