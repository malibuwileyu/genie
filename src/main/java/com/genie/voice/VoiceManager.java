package com.genie.voice;

import com.genie.util.Config;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages voice capture across different platforms
 * 
 * Automatically selects the best implementation:
 * - macOS: Native SFSpeechRecognizer (best quality)
 * - Other: Vosk (offline, cross-platform)
 */
public class VoiceManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VoiceManager.class);
    
    private static VoiceManager instance;
    
    private VoiceCapture activeCapture;
    private boolean alwaysListeningEnabled = false;
    private VoiceCapture.WishRecognizedCallback wishCallback;
    
    public static synchronized VoiceManager getInstance() {
        if (instance == null) {
            instance = new VoiceManager();
        }
        return instance;
    }
    
    private VoiceManager() {
        // Load preference
        alwaysListeningEnabled = Config.isAlwaysListeningEnabled();
    }
    
    /**
     * Initialize voice capture with the best available implementation
     */
    public void initialize() {
        // Priority order:
        // 1. Whisper (cloud-based, works with Java audio, most reliable)
        // 2. Vosk (offline, cross-platform)
        // Note: MacOS native (SFSpeechRecognizer) doesn't work with spawned processes
        
        // Try Whisper first (requires OpenAI API key)
        WhisperVoiceCapture whisperCapture = new WhisperVoiceCapture();
        if (whisperCapture.isAvailable()) {
            activeCapture = whisperCapture;
            logger.info("Using OpenAI Whisper for voice recognition (cloud-based, most reliable)");
        }
        
        // Fallback to Vosk if no API key
        if (activeCapture == null) {
            VoskVoiceCapture voskCapture = new VoskVoiceCapture();
            if (voskCapture.isAvailable()) {
                activeCapture = voskCapture;
                logger.info("Using Vosk speech recognition (offline, cross-platform)");
            }
        }
        
        if (activeCapture == null) {
            logger.warn("No voice capture implementation available - need OpenAI API key or Vosk model");
            return;
        }
        
        // Set up callbacks
        activeCapture.setOnWishRecognized(this::handleWishRecognized);
        activeCapture.setOnPartialResult(this::handlePartialResult);
        
        // Start if always-listening is enabled
        if (alwaysListeningEnabled) {
            startAlwaysListening();
        }
    }
    
    /**
     * Set callback for when a wish is recognized
     */
    public void setOnWishRecognized(VoiceCapture.WishRecognizedCallback callback) {
        this.wishCallback = callback;
    }
    
    /**
     * Enable or disable always-listening mode
     */
    public void setAlwaysListeningEnabled(boolean enabled) {
        this.alwaysListeningEnabled = enabled;
        Config.setAlwaysListeningEnabled(enabled);
        
        if (enabled) {
            startAlwaysListening();
        } else {
            stopAlwaysListening();
        }
    }
    
    public boolean isAlwaysListeningEnabled() {
        return alwaysListeningEnabled;
    }
    
    /**
     * Start always-listening mode
     */
    public void startAlwaysListening() {
        if (activeCapture != null && !activeCapture.isListening()) {
            activeCapture.startListening();
            logger.info("Always-listening mode started");
        }
    }
    
    /**
     * Stop always-listening mode
     */
    public void stopAlwaysListening() {
        if (activeCapture != null && activeCapture.isListening()) {
            activeCapture.stopListening();
            logger.info("Always-listening mode stopped");
        }
    }
    
    /**
     * Check if voice capture is available
     */
    public boolean isVoiceCaptureAvailable() {
        return activeCapture != null && activeCapture.isAvailable();
    }
    
    /**
     * Get the name of the active voice capture implementation
     */
    public String getActiveImplementationName() {
        return activeCapture != null ? activeCapture.getName() : "None";
    }
    
    /**
     * Check if currently listening
     */
    public boolean isListening() {
        return activeCapture != null && activeCapture.isListening();
    }
    
    private void handleWishRecognized(String wishText) {
        logger.info("Wish recognized: {}", wishText);
        
        Platform.runLater(() -> {
            if (wishCallback != null) {
                wishCallback.onWishRecognized(wishText);
            }
        });
    }
    
    private void handlePartialResult(String partialText) {
        // Could update UI with partial results for feedback
        logger.debug("Partial: {}", partialText);
    }
    
    public void shutdown() {
        if (activeCapture != null) {
            activeCapture.shutdown();
        }
    }
}

