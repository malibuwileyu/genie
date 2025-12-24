package com.genie.voice;

/**
 * Interface for voice capture implementations
 * 
 * Supports both always-listening mode (wake word detection) and push-to-talk
 */
public interface VoiceCapture {
    
    /**
     * Start listening for voice input
     * In always-listening mode, this listens for wake words
     * In push-to-talk mode, this records until stopped
     */
    void startListening();
    
    /**
     * Stop listening for voice input
     */
    void stopListening();
    
    /**
     * Check if currently listening
     */
    boolean isListening();
    
    /**
     * Set callback for when a wish is recognized
     */
    void setOnWishRecognized(WishRecognizedCallback callback);
    
    /**
     * Set callback for partial recognition updates (for UI feedback)
     */
    void setOnPartialResult(PartialResultCallback callback);
    
    /**
     * Check if this implementation is available on the current platform
     */
    boolean isAvailable();
    
    /**
     * Get the name of this voice capture implementation
     */
    String getName();
    
    /**
     * Clean up resources
     */
    void shutdown();
    
    /**
     * Callback for when a complete wish is recognized
     */
    @FunctionalInterface
    interface WishRecognizedCallback {
        void onWishRecognized(String wishText);
    }
    
    /**
     * Callback for partial recognition results (for live feedback)
     */
    @FunctionalInterface
    interface PartialResultCallback {
        void onPartialResult(String partialText);
    }
}

