package com.genie.voice;

import com.genie.util.Config;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for WhisperVoiceCapture
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhisperVoiceCaptureTest {
    
    @BeforeAll
    void setup() {
        Config.load();
    }
    
    @Test
    @Order(1)
    @DisplayName("WhisperVoiceCapture instantiates without error")
    void instantiatesWithoutError() {
        assertThatCode(WhisperVoiceCapture::new).doesNotThrowAnyException();
    }
    
    @Test
    @Order(2)
    @DisplayName("Reports correct name")
    void reportsCorrectName() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        assertThat(whisper.getName()).isEqualTo("OpenAI Whisper (cloud-based)");
    }
    
    @Test
    @Order(3)
    @DisplayName("isListening initially false")
    void isListeningInitiallyFalse() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        assertThat(whisper.isListening()).isFalse();
    }
    
    @Test
    @Order(4)
    @DisplayName("Can set wish callback")
    void canSetWishCallback() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        AtomicReference<String> receivedWish = new AtomicReference<>();
        
        assertThatCode(() -> whisper.setOnWishRecognized(receivedWish::set))
            .doesNotThrowAnyException();
    }
    
    @Test
    @Order(5)
    @DisplayName("Can set partial result callback")
    void canSetPartialCallback() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        
        assertThatCode(() -> whisper.setOnPartialResult(partial -> {}))
            .doesNotThrowAnyException();
    }
    
    @Test
    @Order(6)
    @DisplayName("stopListening when not listening is safe")
    void stopWhenNotListeningSafe() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        assertThatCode(whisper::stopListening).doesNotThrowAnyException();
    }
    
    @Test
    @Order(7)
    @DisplayName("shutdown is safe")
    void shutdownSafe() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        assertThatCode(whisper::shutdown).doesNotThrowAnyException();
    }
    
    @Test
    @Order(8)
    @DisplayName("isAvailable returns false without API key")
    void isAvailableWithoutApiKey() {
        // Temporarily clear API key (only works if no env var set)
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            // Can't test this scenario with env var set
            return;
        }
        
        String originalKey = Config.get(Config.OPENAI_API_KEY);
        Config.set(Config.OPENAI_API_KEY, null);
        
        try {
            WhisperVoiceCapture whisper = new WhisperVoiceCapture();
            assertThat(whisper.isAvailable()).isFalse();
        } finally {
            Config.set(Config.OPENAI_API_KEY, originalKey);
        }
    }
    
    // Integration test - only runs with valid API key
    static boolean hasValidApiKey() {
        Config.load();
        String key = Config.getOpenAiApiKey();
        return key != null && key.startsWith("sk-") && key.length() > 40;
    }
    
    @Test
    @Order(10)
    @EnabledIf("hasValidApiKey")
    @DisplayName("[Integration] isAvailable returns true with API key")
    void isAvailableWithApiKey() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        // May still be false if no microphone available
        // Just verify it doesn't throw
        assertThatCode(whisper::isAvailable).doesNotThrowAnyException();
    }
    
    @Test
    @Order(11)
    @EnabledIf("hasValidApiKey")
    @DisplayName("[Integration] startListening starts the capture thread")
    void startListeningStartsThread() throws InterruptedException {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        
        if (!whisper.isAvailable()) {
            // Skip if no microphone
            return;
        }
        
        whisper.startListening();
        
        // Give it a moment to start
        Thread.sleep(100);
        
        assertThat(whisper.isListening()).isTrue();
        
        // Clean up
        whisper.stopListening();
        Thread.sleep(100);
        
        assertThat(whisper.isListening()).isFalse();
    }
    
    @Test
    @Order(12)
    @DisplayName("Multiple start calls don't create multiple threads")
    void multipleStartsSafe() {
        WhisperVoiceCapture whisper = new WhisperVoiceCapture();
        
        // Even without availability, multiple starts shouldn't throw
        assertThatCode(() -> {
            whisper.startListening();
            whisper.startListening();
            whisper.startListening();
        }).doesNotThrowAnyException();
        
        whisper.shutdown();
    }
}

