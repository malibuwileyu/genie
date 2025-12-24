package com.genie.ai;

import com.genie.util.Config;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI API client for generating research articles
 */
public class OpenAIClient {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAIClient.class);
    
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 1000;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public OpenAIClient() {
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }
    
    /**
     * Exception thrown for network-related errors (recoverable)
     */
    public static class NetworkException extends IOException {
        public NetworkException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown for API-related errors (may not be recoverable)
     */
    public static class ApiException extends IOException {
        private final int statusCode;
        
        public ApiException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
        
        public int getStatusCode() {
            return statusCode;
        }
        
        public boolean isRetryable() {
            // 429 = rate limit, 500+ = server errors
            return statusCode == 429 || statusCode >= 500;
        }
    }
    
    /**
     * Generate a research article for a given topic
     * 
     * @param topic The topic to research (from "I wish I knew about...")
     * @return A 2-3 paragraph ELI5-style article
     */
    public String generateResearchArticle(String topic) throws IOException {
        return generateResearchArticleWithRetry(topic, MAX_RETRIES);
    }
    
    private String generateResearchArticleWithRetry(String topic, int retriesLeft) throws IOException {
        String apiKey = Config.getOpenAiApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OpenAI API key not configured. Please add your API key in Settings.");
        }
        
        String model = Config.getAiModel();
        
        String systemPrompt = """
            You are a helpful research assistant. When given a topic, write a clear, 
            concise explanation that:
            
            1. Is 2-3 paragraphs long
            2. Uses simple language (ELI5 - Explain Like I'm 5, but for adults)
            3. Covers the key concepts
            4. Includes a practical example if relevant
            5. Ends with 2-3 "Learn more" links to authoritative sources
            
            Format the response in Markdown.
            """;
        
        String userPrompt = "I wish I knew more about: " + topic;
        
        // Build request body
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", 1000);
        requestBody.addProperty("temperature", 0.7);
        
        JsonArray messages = new JsonArray();
        
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);
        
        requestBody.add("messages", messages);
        
        // Make request
        RequestBody body = RequestBody.create(gson.toJson(requestBody), JSON);
        Request request = new Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();
        
        logger.info("Requesting research article for: {} (retries left: {})", topic, retriesLeft);
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                int code = response.code();
                logger.error("OpenAI API error: {} - {}", code, errorBody);
                
                ApiException apiError = new ApiException(code, parseErrorMessage(errorBody, code));
                
                if (apiError.isRetryable() && retriesLeft > 0) {
                    int delay = INITIAL_RETRY_DELAY_MS * (MAX_RETRIES - retriesLeft + 1);
                    logger.info("Retrying after {}ms...", delay);
                    try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return generateResearchArticleWithRetry(topic, retriesLeft - 1);
                }
                
                throw apiError;
            }
            
            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);
            
            String content = json
                .getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
            
            logger.info("Research article generated successfully");
            return content;
        } catch (SocketTimeoutException | UnknownHostException e) {
            logger.warn("Network error: {}", e.getMessage());
            
            if (retriesLeft > 0) {
                int delay = INITIAL_RETRY_DELAY_MS * (MAX_RETRIES - retriesLeft + 1);
                logger.info("Retrying after {}ms...", delay);
                try { Thread.sleep(delay); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return generateResearchArticleWithRetry(topic, retriesLeft - 1);
            }
            
            throw new NetworkException("Network error while generating research. Please check your internet connection.", e);
        }
    }
    
    private String parseErrorMessage(String errorBody, int statusCode) {
        try {
            JsonObject json = gson.fromJson(errorBody, JsonObject.class);
            if (json.has("error") && json.getAsJsonObject("error").has("message")) {
                return json.getAsJsonObject("error").get("message").getAsString();
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        
        return switch (statusCode) {
            case 401 -> "Invalid API key. Please check your API key in Settings.";
            case 403 -> "Access denied. Your API key may not have access to this model.";
            case 429 -> "Rate limit exceeded. Please wait a moment and try again.";
            case 500, 502, 503 -> "OpenAI servers are experiencing issues. Please try again later.";
            default -> "API error (code " + statusCode + ")";
        };
    }
    
    /**
     * Test the API connection with a simple request
     */
    public boolean testConnection() {
        try {
            String result = generateResearchArticle("testing API connection");
            return result != null && !result.isEmpty();
        } catch (Exception e) {
            logger.error("API connection test failed", e);
            return false;
        }
    }
}

