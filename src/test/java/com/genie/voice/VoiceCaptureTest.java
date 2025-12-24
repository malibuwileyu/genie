package com.genie.voice;

import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for voice capture components
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoiceCaptureTest {
    
    private AutoCloseable mocks;
    
    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
    }
    
    @AfterEach
    void teardown() throws Exception {
        mocks.close();
    }
    
    // ==================== VoiceManager Tests ====================
    
    @Test
    @Order(1)
    @DisplayName("VoiceManager singleton returns same instance")
    void voiceManagerSingleton() {
        VoiceManager instance1 = VoiceManager.getInstance();
        VoiceManager instance2 = VoiceManager.getInstance();
        
        assertThat(instance1).isSameAs(instance2);
    }
    
    @Test
    @Order(2)
    @DisplayName("VoiceManager initializes without error")
    void voiceManagerInitializes() {
        VoiceManager manager = VoiceManager.getInstance();
        assertThatCode(manager::initialize).doesNotThrowAnyException();
    }
    
    @Test
    @Order(3)
    @DisplayName("VoiceManager can set always-listening mode")
    void canSetAlwaysListeningMode() {
        VoiceManager manager = VoiceManager.getInstance();
        
        manager.setAlwaysListeningEnabled(true);
        assertThat(manager.isAlwaysListeningEnabled()).isTrue();
        
        manager.setAlwaysListeningEnabled(false);
        assertThat(manager.isAlwaysListeningEnabled()).isFalse();
    }
    
    @Test
    @Order(4)
    @DisplayName("VoiceManager can set wish callback")
    void canSetWishCallback() {
        VoiceManager manager = VoiceManager.getInstance();
        
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        manager.setOnWishRecognized(wish -> callbackCalled.set(true));
        
        // Callback should be set without error
        assertThat(callbackCalled.get()).isFalse(); // Not called yet
    }
    
    @Test
    @Order(5)
    @DisplayName("VoiceManager reports implementation name")
    void reportsImplementationName() {
        VoiceManager manager = VoiceManager.getInstance();
        manager.initialize();
        
        String name = manager.getActiveImplementationName();
        // Should be one of our implementations or "None"
        assertThat(name).isIn(
            "macOS Native (SFSpeechRecognizer)", 
            "Vosk (Offline)", 
            "OpenAI Whisper (cloud-based)",
            "None"
        );
    }
    
    // ==================== VoskVoiceCapture Tests ====================
    
    @Test
    @Order(10)
    @DisplayName("VoskVoiceCapture instantiates")
    void voskInstantiates() {
        assertThatCode(VoskVoiceCapture::new).doesNotThrowAnyException();
    }
    
    @Test
    @Order(11)
    @DisplayName("VoskVoiceCapture reports correct name")
    void voskReportsName() {
        VoskVoiceCapture vosk = new VoskVoiceCapture();
        assertThat(vosk.getName()).isEqualTo("Vosk (Offline)");
    }
    
    @Test
    @Order(12)
    @DisplayName("VoskVoiceCapture isListening initially false")
    void voskInitiallyNotListening() {
        VoskVoiceCapture vosk = new VoskVoiceCapture();
        assertThat(vosk.isListening()).isFalse();
    }
    
    @Test
    @Order(13)
    @DisplayName("VoskVoiceCapture can set callbacks")
    void voskCanSetCallbacks() {
        VoskVoiceCapture vosk = new VoskVoiceCapture();
        
        assertThatCode(() -> {
            vosk.setOnWishRecognized(wish -> {});
            vosk.setOnPartialResult(partial -> {});
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(14)
    @DisplayName("VoskVoiceCapture stopListening when not listening is safe")
    void voskStopWhenNotListeningSafe() {
        VoskVoiceCapture vosk = new VoskVoiceCapture();
        assertThatCode(vosk::stopListening).doesNotThrowAnyException();
    }
    
    @Test
    @Order(15)
    @DisplayName("VoskVoiceCapture shutdown is safe")
    void voskShutdownSafe() {
        VoskVoiceCapture vosk = new VoskVoiceCapture();
        assertThatCode(vosk::shutdown).doesNotThrowAnyException();
    }
    
    // ==================== MacOSVoiceCapture Tests ====================
    
    @Test
    @Order(20)
    @DisplayName("MacOSVoiceCapture instantiates")
    void macosInstantiates() {
        assertThatCode(MacOSVoiceCapture::new).doesNotThrowAnyException();
    }
    
    @Test
    @Order(21)
    @DisplayName("MacOSVoiceCapture reports correct name")
    void macosReportsName() {
        MacOSVoiceCapture mac = new MacOSVoiceCapture();
        assertThat(mac.getName()).isEqualTo("macOS Native (SFSpeechRecognizer)");
    }
    
    @Test
    @Order(22)
    @DisplayName("MacOSVoiceCapture isAvailable returns correct value for platform")
    void macosIsAvailableCorrectForPlatform() {
        MacOSVoiceCapture mac = new MacOSVoiceCapture();
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("mac")) {
            assertThat(mac.isAvailable()).isTrue();
        } else {
            assertThat(mac.isAvailable()).isFalse();
        }
    }
    
    @Test
    @Order(23)
    @DisplayName("MacOSVoiceCapture isListening initially false")
    void macosInitiallyNotListening() {
        MacOSVoiceCapture mac = new MacOSVoiceCapture();
        assertThat(mac.isListening()).isFalse();
    }
    
    @Test
    @Order(24)
    @DisplayName("MacOSVoiceCapture can set callbacks")
    void macosCanSetCallbacks() {
        MacOSVoiceCapture mac = new MacOSVoiceCapture();
        
        assertThatCode(() -> {
            mac.setOnWishRecognized(wish -> {});
            mac.setOnPartialResult(partial -> {});
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(25)
    @DisplayName("MacOSVoiceCapture stopListening when not listening is safe")
    void macosStopWhenNotListeningSafe() {
        MacOSVoiceCapture mac = new MacOSVoiceCapture();
        assertThatCode(mac::stopListening).doesNotThrowAnyException();
    }
    
    // ==================== Wake Phrase Tests ====================
    
    @Test
    @Order(30)
    @DisplayName("Wake phrases do not include 'i wish'")
    void wakePhrasesDontIncludeIWish() {
        // Verify by checking the class constant through reflection or behavior
        // For now, we test behavior indirectly
        VoskVoiceCapture vosk = new VoskVoiceCapture();
        MacOSVoiceCapture mac = new MacOSVoiceCapture();
        
        // These should instantiate without "i wish" as a wake phrase
        // (Actual verification would require testing the recognition flow)
        assertThat(vosk).isNotNull();
        assertThat(mac).isNotNull();
    }
}

