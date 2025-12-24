package com.genie.ai;

import com.genie.util.Config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for OpenAI API client
 * 
 * Note: Tests that require actual API calls are conditionally enabled
 * based on whether a VALID API key is configured.
 * 
 * Tests that expect "no key" scenarios are SKIPPED when the OPENAI_API_KEY
 * environment variable is set, because env vars take precedence and cannot
 * be cleared from Java.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAIClientTest {
    
    private String originalConfigKey;
    
    @BeforeAll
    void setup() {
        Config.load();
        // Only save the CONFIG key, not the env var
        originalConfigKey = Config.get(Config.OPENAI_API_KEY);
    }
    
    @AfterAll
    void restore() {
        // Restore original config key
        Config.set(Config.OPENAI_API_KEY, originalConfigKey);
        Config.save();
    }
    
    // Check if env var is set (these tests can't run with env var)
    static boolean hasEnvApiKey() {
        String envKey = System.getenv("OPENAI_API_KEY");
        return envKey != null && !envKey.isEmpty();
    }
    
    @Test
    @Order(1)
    @DisplayName("Client instantiates without error")
    void clientInstantiates() {
        assertThatCode(OpenAIClient::new).doesNotThrowAnyException();
    }
    
    @Test
    @Order(2)
    @DisabledIf("hasEnvApiKey")
    @DisplayName("generateResearchArticle throws when no API key (no env var)")
    void throwsWhenNoApiKey() {
        // Clear config API key
        Config.set(Config.OPENAI_API_KEY, null);
        
        OpenAIClient client = new OpenAIClient();
        assertThatThrownBy(() -> client.generateResearchArticle("test topic"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("API key not configured");
    }
    
    @Test
    @Order(3)
    @DisabledIf("hasEnvApiKey")
    @DisplayName("testConnection returns false when no API key (no env var)")
    void testConnectionFailsWithoutKey() {
        Config.set(Config.OPENAI_API_KEY, null);
        
        OpenAIClient client = new OpenAIClient();
        assertThat(client.testConnection()).isFalse();
    }
    
    @Test
    @Order(4)
    @DisabledIf("hasEnvApiKey")
    @DisplayName("testConnection returns false with invalid key format (no env var)")
    void testConnectionFailsWithInvalidKeyFormat() {
        Config.set(Config.OPENAI_API_KEY, "invalid-key");
        
        OpenAIClient client = new OpenAIClient();
        assertThat(client.testConnection()).isFalse();
    }
    
    // Integration tests - only run if a REAL API key is available
    // The key must start with "sk-" and be longer than 20 chars
    
    static boolean hasValidApiKey() {
        Config.load();
        String key = Config.getOpenAiApiKey();
        return key != null && key.startsWith("sk-") && key.length() > 40;
    }
    
    @Test
    @Order(10)
    @EnabledIf("hasValidApiKey")
    @DisplayName("[Integration] Can generate research article")
    void canGenerateResearchArticle() throws Exception {
        OpenAIClient client = new OpenAIClient();
        
        String article = client.generateResearchArticle("what is recursion in programming");
        
        assertThat(article).isNotNull();
        assertThat(article).isNotEmpty();
        assertThat(article.length()).isGreaterThan(100);
    }
    
    @Test
    @Order(11)
    @EnabledIf("hasValidApiKey")
    @DisplayName("[Integration] testConnection returns true with valid key")
    void testConnectionSucceedsWithValidKey() {
        OpenAIClient client = new OpenAIClient();
        assertThat(client.testConnection()).isTrue();
    }
    
    @Test
    @Order(12)
    @EnabledIf("hasValidApiKey")
    @DisplayName("[Integration] Article contains markdown formatting")
    void articleContainsMarkdown() throws Exception {
        OpenAIClient client = new OpenAIClient();
        
        String article = client.generateResearchArticle("how does HTTP work");
        
        // Should have some markdown elements
        assertThat(article).containsAnyOf("#", "**", "- ", "1.", "`");
    }
}
