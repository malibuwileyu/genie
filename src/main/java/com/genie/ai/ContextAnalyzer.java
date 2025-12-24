package com.genie.ai;

import com.genie.core.ContextCapture.Context;
import com.genie.util.Config;
import com.genie.util.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Analyzes captured context using OpenAI Vision API
 * 
 * Uses the screenshot + metadata to generate a natural language summary
 * of what the user was doing when they saved their context.
 */
public class ContextAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextAnalyzer.class);
    
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private static final HttpClient client = HttpClient.newHttpClient();
    
    /**
     * Analyze a context and generate an AI summary
     * This is typically called async after context capture
     */
    public static String analyzeContext(Context context) {
        if (!Config.hasOpenAiApiKey()) {
            logger.warn("Cannot analyze context: No OpenAI API key configured");
            return null;
        }
        
        try {
            String summary = generateSummary(context);
            
            // Save the summary to database
            if (summary != null && context.id > 0) {
                Database.updateContextAiSummary(context.id, summary);
                context.aiSummary = summary;
            }
            
            return summary;
        } catch (Exception e) {
            logger.error("Failed to analyze context", e);
            return null;
        }
    }
    
    /**
     * Generate summary using Vision API
     */
    private static String generateSummary(Context context) throws Exception {
        String apiKey = Config.getOpenAiApiKey();
        
        // Build the prompt with context metadata
        StringBuilder metadataPrompt = new StringBuilder();
        metadataPrompt.append("Active window: ").append(context.activeWindow).append("\n");
        
        if (context.clipboardContent != null && !context.clipboardContent.isEmpty()) {
            String clipPreview = context.clipboardContent.length() > 500 
                ? context.clipboardContent.substring(0, 500) + "..." 
                : context.clipboardContent;
            metadataPrompt.append("Clipboard contains: ").append(clipPreview).append("\n");
        }
        
        if (context.browserTabs != null && !context.browserTabs.equals("[]")) {
            metadataPrompt.append("Browser tabs open: ").append(context.getTabCount()).append("\n");
        }
        
        // Build JSON request body
        String requestBody;
        
        File screenshotFile = context.getScreenshotFile();
        if (screenshotFile != null && screenshotFile.exists()) {
            // Use Vision API with image
            String base64Image = Base64.getEncoder().encodeToString(
                Files.readAllBytes(screenshotFile.toPath())
            );
            
            requestBody = buildVisionRequest(metadataPrompt.toString(), base64Image);
            logger.info("Analyzing context with screenshot using Vision API");
        } else {
            // Fallback to text-only analysis
            requestBody = buildTextOnlyRequest(metadataPrompt.toString());
            logger.info("Analyzing context without screenshot (text-only)");
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OPENAI_API_URL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            logger.error("OpenAI Vision API error: {} - {}", response.statusCode(), response.body());
            throw new RuntimeException("Vision API error: " + response.statusCode());
        }
        
        // Extract content from response using robust parsing
        String responseBody = response.body();
        String content = extractContentFromJson(responseBody);
        
        if (content == null || content.isEmpty()) {
            logger.warn("Empty content in API response");
            return "Could not analyze context.";
        }
        
        logger.info("Context summary generated successfully");
        return content;
    }
    
    /**
     * Robustly extract the content field from OpenAI JSON response
     */
    private static String extractContentFromJson(String json) {
        try {
            // Look for "content": followed by a string value
            // The response format is: "content":"..." or "content": "..."
            int contentStart = json.indexOf("\"content\":");
            if (contentStart == -1) {
                logger.warn("No content field found in response");
                return null;
            }
            
            // Find the opening quote after "content":
            int quoteStart = json.indexOf("\"", contentStart + 10);
            if (quoteStart == -1) {
                // Might be null content
                if (json.substring(contentStart + 10, contentStart + 20).trim().startsWith("null")) {
                    return null;
                }
                return null;
            }
            
            // Find the closing quote (handling escaped quotes)
            StringBuilder content = new StringBuilder();
            boolean escaped = false;
            for (int i = quoteStart + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                
                if (escaped) {
                    // Handle escape sequences
                    switch (c) {
                        case 'n' -> content.append('\n');
                        case 'r' -> content.append('\r');
                        case 't' -> content.append('\t');
                        case '"' -> content.append('"');
                        case '\\' -> content.append('\\');
                        default -> content.append(c);
                    }
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    // End of string
                    break;
                } else {
                    content.append(c);
                }
            }
            
            return content.toString();
        } catch (Exception e) {
            logger.error("Failed to parse JSON response: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Build request body with image for Vision API
     */
    private static String buildVisionRequest(String metadata, String base64Image) {
        String systemPrompt = """
            You are helping a user recall what they were working on when they were interrupted.
            Look at their screenshot and metadata to create a brief, helpful summary.
            
            Format your response as:
            **What you were doing:** [1-2 sentence summary]
            
            **Key details:**
            - [Important thing visible on screen]
            - [What they might have been about to do]
            - [Any relevant clipboard content]
            
            Be specific about what you see (file names, URLs, UI elements).
            Keep it concise but informative - this helps them get back into flow.
            """;
        
        String userPrompt = "Here's my context when I saved it:\n\n" + metadata + 
            "\n\nPlease analyze the screenshot and tell me what I was working on.";
        
        return String.format("""
            {
                "model": "gpt-4o-mini",
                "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": [
                        {"type": "text", "text": "%s"},
                        {"type": "image_url", "image_url": {"url": "data:image/png;base64,%s", "detail": "low"}}
                    ]}
                ],
                "max_tokens": 500
            }
            """,
            escapeJson(systemPrompt),
            escapeJson(userPrompt),
            base64Image
        );
    }
    
    /**
     * Build request body for text-only analysis (no screenshot)
     */
    private static String buildTextOnlyRequest(String metadata) {
        String systemPrompt = """
            You are helping a user recall what they were working on when they were interrupted.
            Based on the metadata provided, create a brief summary to help them resume.
            
            Format your response as:
            **What you were doing:** [1-2 sentence summary based on active window and clipboard]
            
            **Key details:**
            - [Active application/window context]
            - [Relevant clipboard content if any]
            
            Be helpful and concise.
            """;
        
        String userPrompt = "Here's my context when I saved it:\n\n" + metadata;
        
        return String.format("""
            {
                "model": "gpt-4o-mini",
                "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                ],
                "max_tokens": 300
            }
            """,
            escapeJson(systemPrompt),
            escapeJson(userPrompt)
        );
    }
    
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}

