package com.genie.util;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledIf;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Config
 * 
 * Note: Tests that manipulate API keys are skipped when OPENAI_API_KEY env var
 * is set, because env vars take precedence and cannot be modified from Java.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfigTest {
    
    private static String originalConfigApiKey;
    private static boolean originalAlwaysListening;
    private static String originalVoiceMode;
    
    @BeforeAll
    static void setup() {
        Config.load();
        // Save original CONFIG values (not env var - we can't change that)
        originalConfigApiKey = Config.get(Config.OPENAI_API_KEY);
        originalAlwaysListening = Config.isAlwaysListeningEnabled();
        originalVoiceMode = Config.getVoiceMode();
    }
    
    @AfterAll
    static void restore() {
        // Restore original config values
        Config.set(Config.OPENAI_API_KEY, originalConfigApiKey);
        Config.setAlwaysListeningEnabled(originalAlwaysListening);
        Config.setVoiceMode(originalVoiceMode != null ? originalVoiceMode : "hotkey");
        Config.save();
    }
    
    // Check if env var is set (some tests can't run with env var)
    static boolean hasEnvApiKey() {
        String envKey = System.getenv("OPENAI_API_KEY");
        return envKey != null && !envKey.isEmpty();
    }
    
    @Test
    @Order(1)
    @DisplayName("Config loads without error")
    void configLoadsWithoutError() {
        assertThatCode(Config::load).doesNotThrowAnyException();
    }
    
    @Test
    @Order(2)
    @DisplayName("Can set and get values")
    void canSetAndGetValues() {
        Config.set("test.key", "test.value");
        assertThat(Config.get("test.key")).isEqualTo("test.value");
    }
    
    @Test
    @Order(3)
    @DisplayName("Get returns default when key not found")
    void getReturnsDefault() {
        String result = Config.get("nonexistent.key", "default");
        assertThat(result).isEqualTo("default");
    }
    
    @Test
    @Order(4)
    @DisplayName("Can set null value (removes key)")
    void canSetNullValue() {
        Config.set("temp.key", "value");
        assertThat(Config.get("temp.key")).isEqualTo("value");
        
        Config.set("temp.key", null);
        assertThat(Config.get("temp.key")).isNull();
    }
    
    @Test
    @Order(5)
    @DisplayName("Can set and get OpenAI API key in config")
    void canSetAndGetApiKey() {
        String testKey = "sk-test-12345678901234567890";
        Config.setOpenAiApiKey(testKey);
        
        // Test that the CONFIG stores the key correctly
        // (getOpenAiApiKey() checks env var first, so we check config directly)
        assertThat(Config.get(Config.OPENAI_API_KEY)).isEqualTo(testKey);
    }
    
    @Test
    @Order(6)
    @DisabledIf("hasEnvApiKey")
    @DisplayName("hasOpenAiApiKey validates key format (no env var)")
    void hasOpenAiApiKeyValidatesFormat() {
        // These tests only work when env var is NOT set, because
        // getOpenAiApiKey() always returns env var first
        
        // Valid key format
        Config.setOpenAiApiKey("sk-proj-validkey12345678901234567890");
        assertThat(Config.hasOpenAiApiKey()).isTrue();
        
        // Invalid: too short
        Config.setOpenAiApiKey("sk-short");
        assertThat(Config.hasOpenAiApiKey()).isFalse();
        
        // Invalid: wrong prefix
        Config.setOpenAiApiKey("invalid-key-12345678901234567890");
        assertThat(Config.hasOpenAiApiKey()).isFalse();
        
        // Invalid: empty
        Config.setOpenAiApiKey("");
        assertThat(Config.hasOpenAiApiKey()).isFalse();
    }
    
    @Test
    @Order(7)
    @DisplayName("Can set and get always-listening mode")
    void canSetAlwaysListeningMode() {
        Config.setAlwaysListeningEnabled(true);
        assertThat(Config.isAlwaysListeningEnabled()).isTrue();
        
        Config.setAlwaysListeningEnabled(false);
        assertThat(Config.isAlwaysListeningEnabled()).isFalse();
    }
    
    @Test
    @Order(8)
    @DisplayName("Can set and get voice mode")
    void canSetVoiceMode() {
        Config.setVoiceMode("always_listening");
        assertThat(Config.getVoiceMode()).isEqualTo("always_listening");
        
        Config.setVoiceMode("hotkey");
        assertThat(Config.getVoiceMode()).isEqualTo("hotkey");
    }
    
    @Test
    @Order(9)
    @DisplayName("Default AI model is gpt-4o-mini")
    void defaultAiModel() {
        assertThat(Config.getAiModel()).isEqualTo("gpt-4o-mini");
    }
    
    @Test
    @Order(10)
    @DisplayName("Config saves without error")
    void configSavesWithoutError() {
        assertThatCode(Config::save).doesNotThrowAnyException();
    }
}
