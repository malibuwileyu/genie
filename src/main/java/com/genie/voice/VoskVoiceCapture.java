package com.genie.voice;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vosk-based voice capture implementation
 * 
 * Cross-platform, offline speech recognition using Vosk
 * Listens for wake phrase "genie" or "I wish" and captures following speech
 */
public class VoskVoiceCapture implements VoiceCapture {
    
    private static final Logger logger = LoggerFactory.getLogger(VoskVoiceCapture.class);
    
    // Wake phrases to listen for (avoiding common phrases like "i wish")
    // Include common speech-to-text misinterpretations of "genie"
    private static final String[] WAKE_PHRASES = {
        "genie", "hey genie", "ok genie",
        "jeannie", "hey jeannie", "ok jeannie",  // Common STT interpretation
        "jeanie", "hey jeanie", "ok jeanie",
        "geni", "hey geni", "ok geni"
    };
    
    private Model model;
    private Recognizer recognizer;
    private TargetDataLine microphone;
    private Thread captureThread;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean wakeWordDetected = new AtomicBoolean(false);
    
    private WishRecognizedCallback wishCallback;
    private PartialResultCallback partialCallback;
    
    private final Gson gson = new Gson();
    private StringBuilder wishBuffer = new StringBuilder();
    
    // Silence detection
    private static final int SILENCE_THRESHOLD = 500; // Audio level threshold
    private static final int SILENCE_DURATION_MS = 1500; // 1.5 seconds of silence ends capture
    private long lastSoundTime = 0;
    
    @Override
    public boolean isAvailable() {
        // Check if Vosk model exists
        Path modelPath = getModelPath();
        if (modelPath == null || !Files.exists(modelPath)) {
            logger.warn("Vosk model not found at expected location. Download from https://alphacephei.com/vosk/models");
            return false;
        }
        
        // Check if microphone is available
        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            logger.error("Microphone not available", e);
            return false;
        }
    }
    
    @Override
    public String getName() {
        return "Vosk (Offline)";
    }
    
    @Override
    public void startListening() {
        if (listening.get()) {
            return;
        }
        
        try {
            // Initialize model
            Path modelPath = getModelPath();
            if (modelPath == null || !Files.exists(modelPath)) {
                logger.error("Vosk model not found");
                return;
            }
            
            model = new Model(modelPath.toString());
            recognizer = new Recognizer(model, 16000);
            
            // Set up microphone
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();
            
            listening.set(true);
            wakeWordDetected.set(false);
            wishBuffer = new StringBuilder();
            
            // Start capture thread
            captureThread = new Thread(this::captureLoop, "VoskCapture");
            captureThread.setDaemon(true);
            captureThread.start();
            
            logger.info("Vosk voice capture started - listening for wake words: {}", String.join(", ", WAKE_PHRASES));
            
        } catch (Exception e) {
            logger.error("Failed to start Vosk voice capture", e);
            cleanup();
        }
    }
    
    @Override
    public void stopListening() {
        listening.set(false);
        cleanup();
    }
    
    @Override
    public boolean isListening() {
        return listening.get();
    }
    
    @Override
    public void setOnWishRecognized(WishRecognizedCallback callback) {
        this.wishCallback = callback;
    }
    
    @Override
    public void setOnPartialResult(PartialResultCallback callback) {
        this.partialCallback = callback;
    }
    
    @Override
    public void shutdown() {
        stopListening();
    }
    
    private void captureLoop() {
        byte[] buffer = new byte[4096];
        
        while (listening.get()) {
            try {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead <= 0) continue;
                
                // Check audio level for silence detection
                int audioLevel = calculateAudioLevel(buffer, bytesRead);
                if (audioLevel > SILENCE_THRESHOLD) {
                    lastSoundTime = System.currentTimeMillis();
                }
                
                // Feed to recognizer
                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    // Final result
                    String result = recognizer.getResult();
                    handleRecognitionResult(result, true);
                } else {
                    // Partial result
                    String partial = recognizer.getPartialResult();
                    handleRecognitionResult(partial, false);
                }
                
                // Check for silence timeout when capturing wish
                if (wakeWordDetected.get()) {
                    long silenceDuration = System.currentTimeMillis() - lastSoundTime;
                    if (silenceDuration > SILENCE_DURATION_MS && wishBuffer.length() > 0) {
                        // Silence detected, finalize wish
                        finalizeWish();
                    }
                }
                
            } catch (Exception e) {
                if (listening.get()) {
                    logger.error("Error in capture loop", e);
                }
            }
        }
    }
    
    private void handleRecognitionResult(String jsonResult, boolean isFinal) {
        try {
            JsonObject json = gson.fromJson(jsonResult, JsonObject.class);
            String text = null;
            
            if (json.has("text")) {
                text = json.get("text").getAsString().trim().toLowerCase();
            } else if (json.has("partial")) {
                text = json.get("partial").getAsString().trim().toLowerCase();
            }
            
            if (text == null || text.isEmpty()) return;
            
            if (!wakeWordDetected.get()) {
                // Looking for wake word
                for (String wake : WAKE_PHRASES) {
                    if (text.contains(wake)) {
                        logger.info("Wake phrase detected: '{}'", wake);
                        wakeWordDetected.set(true);
                        wishBuffer = new StringBuilder();
                        lastSoundTime = System.currentTimeMillis();
                        
                        // Extract text after wake phrase
                        int idx = text.indexOf(wake) + wake.length();
                        if (idx < text.length()) {
                            String afterWake = text.substring(idx).trim();
                            if (!afterWake.isEmpty()) {
                                wishBuffer.append(afterWake);
                            }
                        }
                        break;
                    }
                }
            } else {
                // Capturing wish content
                if (isFinal && !text.isEmpty()) {
                    wishBuffer.append(" ").append(text);
                    
                    if (partialCallback != null) {
                        partialCallback.onPartialResult(wishBuffer.toString().trim());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.debug("Failed to parse recognition result: {}", jsonResult);
        }
    }
    
    private void finalizeWish() {
        String wish = wishBuffer.toString().trim();
        if (!wish.isEmpty() && wishCallback != null) {
            logger.info("Wish captured: '{}'", wish);
            wishCallback.onWishRecognized(wish);
        }
        
        // Reset for next wish
        wakeWordDetected.set(false);
        wishBuffer = new StringBuilder();
        recognizer.reset();
    }
    
    private int calculateAudioLevel(byte[] buffer, int length) {
        long sum = 0;
        for (int i = 0; i < length; i += 2) {
            if (i + 1 < length) {
                short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
                sum += Math.abs(sample);
            }
        }
        return (int) (sum / (length / 2));
    }
    
    private AudioFormat getAudioFormat() {
        return new AudioFormat(
            16000,  // Sample rate (Vosk requires 16kHz)
            16,     // Sample size in bits
            1,      // Channels (mono)
            true,   // Signed
            false   // Little endian
        );
    }
    
    private Path getModelPath() {
        // Check multiple locations for the model
        String[] possiblePaths = {
            "vosk-model-small-en-us-0.15",
            "models/vosk-model-small-en-us-0.15",
            System.getProperty("user.home") + "/.genie/vosk-model-small-en-us-0.15",
            System.getProperty("user.home") + "/Library/Application Support/Genie/vosk-model-small-en-us-0.15"
        };
        
        for (String path : possiblePaths) {
            Path p = Paths.get(path);
            if (Files.exists(p)) {
                return p;
            }
        }
        
        return null;
    }
    
    private void cleanup() {
        if (microphone != null) {
            microphone.stop();
            microphone.close();
            microphone = null;
        }
        
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        
        if (model != null) {
            model.close();
            model = null;
        }
        
        if (captureThread != null) {
            captureThread.interrupt();
            captureThread = null;
        }
    }
}

