package com.finpilot.ai.client;

import com.finpilot.ai.config.OllamaProperties;
import com.finpilot.ai.dto.ollama.OllamaChatRequest;
import com.finpilot.ai.dto.ollama.OllamaChatResponse;
import com.finpilot.ai.dto.ollama.OllamaTagsResponse;
import com.finpilot.ai.exception.AiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
@Slf4j
public class OllamaClientImpl implements OllamaClient {

    private final OllamaProperties properties;
    private final RestClient restClient;

    @Autowired
    public OllamaClientImpl(OllamaProperties properties) {
        this(properties, createDefaultRestClient(properties));
    }

    public OllamaClientImpl(OllamaProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient createDefaultRestClient(OllamaProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(Math.max(5, properties.getConnectTimeoutSeconds())));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String getModelName() {
        return properties.getModel();
    }

    @Override
    public boolean isModelAvailable() {
        try {
            log.debug("Checking Ollama model availability at {} for model {}", properties.getBaseUrl(), properties.getModel());
            OllamaTagsResponse tags = restClient.get()
                    .uri("/api/tags")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(OllamaTagsResponse.class);

            if (tags == null || tags.getModels() == null) {
                log.warn("Ollama /api/tags returned null models list");
                return false;
            }

            String targetModel = properties.getModel() != null ? properties.getModel().trim() : "";
            boolean matched = tags.getModels().stream()
                    .anyMatch(m -> matchesModel(m.getName(), targetModel) || matchesModel(m.getModel(), targetModel));
            
            if (!matched) {
                log.warn("Target model '{}' not found in available Ollama models: {}", 
                        targetModel, 
                        tags.getModels().stream().map(m -> m.getName() != null ? m.getName() : m.getModel()).toList());
            }
            return matched;
        } catch (Exception e) {
            log.warn("Ollama availability check failed at {}: {}", properties.getBaseUrl(), e.getMessage());
            return false;
        }
    }

    private boolean matchesModel(String availableModel, String targetModel) {
        if (availableModel == null || targetModel.isEmpty()) {
            return false;
        }
        if (availableModel.equalsIgnoreCase(targetModel)) {
            return true;
        }
        if (availableModel.endsWith("/" + targetModel)) {
            return true;
        }
        if (!targetModel.contains(":") && availableModel.startsWith(targetModel + ":")) {
            return true;
        }
        return false;
    }

    @Override
    public OllamaChatResponse chat(OllamaChatRequest request) {
        // Ensure thinking is disabled for fast on-device inference unless explicitly enabled
        if (request.getThink() == null) {
            request.setThink(false);
        }

        try {
            log.info("Sending chat request to Ollama ({}) with model: {}", properties.getBaseUrl(), request.getModel());
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaChatResponse.class);

            log.info("Received successful chat response from Ollama");
            return response;
        } catch (Exception e) {
            if (isTimeoutException(e)) {
                log.error("Ollama chat request timed out after {}s: {}", properties.getTimeoutSeconds(), e.getMessage());
                throw AiException.timeout();
            }
            if (e instanceof HttpClientErrorException.NotFound) {
                log.error("Ollama model not found: {}", properties.getModel());
                throw AiException.modelUnavailable(properties.getModel());
            }
            if (e instanceof HttpClientErrorException || e instanceof HttpServerErrorException) {
                var httpEx = (org.springframework.web.client.HttpStatusCodeException) e;
                log.error("Ollama HTTP error {}: {}", httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
                throw new AiException("OLLAMA_ERROR", "Error communicating with local AI: " + e.getMessage(), org.springframework.http.HttpStatus.BAD_GATEWAY, e);
            }
            if (e instanceof ResourceAccessException) {
                log.error("Ollama connection failed at {}: {}", properties.getBaseUrl(), e.getMessage());
                throw AiException.aiUnavailable();
            }
            log.error("Unexpected error contacting Ollama at {}: {}", properties.getBaseUrl(), e.getMessage(), e);
            throw AiException.aiUnavailable();
        }
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable curr = t;
        while (curr != null) {
            if (curr instanceof SocketTimeoutException
                    || curr instanceof java.util.concurrent.TimeoutException
                    || (curr.getMessage() != null && curr.getMessage().toLowerCase().contains("timed out"))) {
                return true;
            }
            curr = curr.getCause();
        }
        return false;
    }
}
