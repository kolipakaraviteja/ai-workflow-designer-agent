package com.aiworkflow.designer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reports whether the configured OpenAI-compatible chat/embedding endpoint is
 * reachable. GETs {@code {base-url}/models} with a short timeout — no tokens
 * are consumed. DOWN only reports reachability, never the API key.
 */
@Component("llm")
@Slf4j
public class LlmHealthIndicator implements HealthIndicator {

    private final String baseUrl;
    private final String apiKey;
    private final String chatModel;
    private final String embeddingModel;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public LlmHealthIndicator(
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:}") String chatModel,
            @Value("${spring.ai.openai.embedding.options.model:}") String embeddingModel) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public Health health() {
        try {
            String url = baseUrl.endsWith("/") ? baseUrl + "models" : baseUrl + "/models";
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET();
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<Void> response = http.send(request.build(),
                    HttpResponse.BodyHandlers.discarding());

            Health.Builder builder = response.statusCode() < 500 ? Health.up() : Health.down();
            return builder
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("chatModel", chatModel)
                    .withDetail("embeddingModel", embeddingModel)
                    .withDetail("statusCode", response.statusCode())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("chatModel", chatModel)
                    .withDetail("embeddingModel", embeddingModel)
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
