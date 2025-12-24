package com.genie.voice;

import com.genie.util.Config;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice capture using Java audio + OpenAI Whisper API
 * 
 * This approach works because:
 * 1. Java handles microphone access (no spawned process issues)
 * 2. Whisper API handles speech-to-text (no local model needed)
 * 3. Simple and reliable
 */
public class WhisperVoiceCapture implements VoiceCapture {
    
    private static final Logger logger = LoggerFactory.getLogger(WhisperVoiceCapture.class);
    
    // Wake phrases (including common STT misinterpretations)
    private static final String[] WAKE_PHRASES = {
        "genie", "hey genie", "ok genie",
        "jeannie", "hey jeannie", "ok jeannie",
        "jeanie", "hey jeanie", "ok jeanie",
        "geni", "hey geni", "ok geni"
    };
    
    private static final String WHISPER_API_URL = "https://api.openai.com/v1/audio/transcriptions";
    
    // Audio settings
    private static final float SAMPLE_RATE = 16000;
    private static final int SAMPLE_SIZE_BITS = 16;
    private static final int CHANNELS = 1;
    private static final int SILENCE_THRESHOLD = 500; // Audio level to detect speech
    private static final int SPEECH_TIMEOUT_MS = 2000; // Max recording time after wake word
    private static final int PRE_BUFFER_MS = 500; // Buffer before speech detection
    
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean wakeWordDetected = new AtomicBoolean(false);
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Gson gson = new Gson();
    
    private Thread captureThread;
    private TargetDataLine audioLine;
    
    private WishRecognizedCallback wishCallback;
    private PartialResultCallback partialCallback;
    
    @Override
    public boolean isAvailable() {
        // Available if we have an API key and microphone access
        if (!Config.hasOpenAiApiKey()) {
            logger.info("Whisper voice capture not available - no OpenAI API key");
            return false;
        }
        
        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            logger.warn("Whisper voice capture not available - no microphone", e);
            return false;
        }
    }
    
    @Override
    public String getName() {
        return "OpenAI Whisper (cloud-based)";
    }
    
    @Override
    public void startListening() {
        if (listening.get()) {
            return;
        }
        
        listening.set(true);
        wakeWordDetected.set(false);
        
        captureThread = new Thread(this::captureLoop, "WhisperVoiceCapture");
        captureThread.setDaemon(true);
        captureThread.start();
        
        logger.info("Whisper voice capture started - listening for: {}", String.join(", ", WAKE_PHRASES));
    }
    
    private void captureLoop() {
        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            audioLine = (TargetDataLine) AudioSystem.getLine(info);
            audioLine.open(format);
            audioLine.start();
            
            logger.info("Microphone opened - listening...");
            
            ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            long lastSpeechTime = 0;
            boolean recording = false;
            int silentFrames = 0;
            int consecutiveErrors = 0;
            
            while (listening.get()) {
                try {
                    int bytesRead = audioLine.read(buffer, 0, buffer.length);
                    if (bytesRead <= 0) continue;
                    
                    consecutiveErrors = 0; // Reset on successful read
                    
                    // Calculate audio level
                    int level = calculateAudioLevel(buffer, bytesRead);
                    
                    if (level > SILENCE_THRESHOLD) {
                        // Speech detected
                        if (!recording) {
                            logger.debug("Speech detected (level: {})", level);
                            recording = true;
                            audioBuffer.reset();
                        }
                        audioBuffer.write(buffer, 0, bytesRead);
                        lastSpeechTime = System.currentTimeMillis();
                        silentFrames = 0;
                    } else if (recording) {
                        // Still recording but silence
                        silentFrames++;
                        audioBuffer.write(buffer, 0, bytesRead);
                        
                        // End recording after ~1 second of silence or max time
                        long elapsed = System.currentTimeMillis() - lastSpeechTime;
                        if (silentFrames > 15 || elapsed > SPEECH_TIMEOUT_MS) {
                            // Process the recorded audio
                            byte[] audioData = audioBuffer.toByteArray();
                            if (audioData.length > 8000) { // At least 0.25 seconds
                                processAudio(audioData);
                            }
                            recording = false;
                            audioBuffer.reset();
                        }
                    }
                } catch (Exception loopError) {
                    consecutiveErrors++;
                    logger.warn("Error reading audio (attempt {}): {}", consecutiveErrors, loopError.getMessage());
                    
                    if (consecutiveErrors >= 5) {
                        logger.error("Too many consecutive audio errors, stopping voice capture");
                        javafx.application.Platform.runLater(() -> {
                            com.genie.ui.ErrorToast.show(
                                "Voice Capture Error",
                                "Microphone access was interrupted. Voice commands are disabled.\nTry restarting Genie or check microphone permissions.",
                                com.genie.ui.ErrorToast.ToastType.WARNING
                            );
                        });
                        break;
                    }
                    
                    // Brief pause before retry
                    try { Thread.sleep(100); } catch (InterruptedException ie) { break; }
                }
            }
            
        } catch (LineUnavailableException e) {
            logger.error("Microphone not available", e);
            javafx.application.Platform.runLater(() -> {
                com.genie.ui.ErrorToast.showMicrophonePermissionDenied();
            });
        } catch (SecurityException e) {
            logger.error("Microphone access denied by security policy", e);
            javafx.application.Platform.runLater(() -> {
                com.genie.ui.ErrorToast.showMicrophonePermissionDenied();
            });
        } catch (Exception e) {
            if (listening.get()) {
                logger.error("Error in voice capture loop", e);
                javafx.application.Platform.runLater(() -> {
                    com.genie.ui.ErrorToast.showError(
                        "Voice Capture Error", 
                        "An unexpected error occurred: " + e.getMessage()
                    );
                });
            }
        } finally {
            if (audioLine != null) {
                audioLine.stop();
                audioLine.close();
            }
        }
    }
    
    private void processAudio(byte[] audioData) {
        try {
            // Convert to WAV format for Whisper
            Path tempFile = Files.createTempFile("genie_audio_", ".wav");
            writeWavFile(tempFile, audioData);
            
            // Send to Whisper API
            String transcript = transcribeWithWhisper(tempFile);
            Files.deleteIfExists(tempFile);
            
            if (transcript == null || transcript.isEmpty()) {
                return;
            }
            
            String lowerTranscript = transcript.toLowerCase().trim();
            logger.info("Heard: \"{}\"", lowerTranscript);
            
            // Check for wake phrase
            for (String wake : WAKE_PHRASES) {
                if (lowerTranscript.contains(wake)) {
                    logger.info("Wake phrase detected: '{}'", wake);
                    
                    // Extract the wish (text after wake phrase)
                    int idx = lowerTranscript.indexOf(wake) + wake.length();
                    String wish = lowerTranscript.substring(idx).trim();
                    
                    // Remove common filler words at start
                    wish = wish.replaceFirst("^(,|\\.|i wish|i want to know|tell me|what is|what's|about)\\s*", "").trim();
                    
                    if (!wish.isEmpty() && wishCallback != null) {
                        logger.info("Wish captured: '{}'", wish);
                        wishCallback.onWishRecognized(wish);
                    } else {
                        // Wake word detected but no wish content yet
                        if (partialCallback != null) {
                            partialCallback.onPartialResult("Listening for your wish...");
                        }
                    }
                    break;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error processing audio", e);
        }
    }
    
    private String transcribeWithWhisper(Path audioFile) {
        return transcribeWithRetry(audioFile, 2); // Allow 2 retries
    }
    
    private String transcribeWithRetry(Path audioFile, int retriesLeft) {
        try {
            String apiKey = Config.getOpenAiApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                logger.warn("No OpenAI API key configured");
                return null;
            }
            
            RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "audio.wav",
                    RequestBody.create(Files.readAllBytes(audioFile), MediaType.parse("audio/wav")))
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "en")
                .build();
            
            Request request = new Request.Builder()
                .url(WHISPER_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();
            
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    int code = response.code();
                    String errorBody = response.body() != null ? response.body().string() : "";
                    logger.error("Whisper API error: {} - {}", code, errorBody);
                    
                    // Retry on rate limit or server errors
                    if ((code == 429 || code >= 500) && retriesLeft > 0) {
                        logger.info("Retrying Whisper API call...");
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return transcribeWithRetry(audioFile, retriesLeft - 1);
                    }
                    
                    // Show error toast for auth errors (but only once)
                    if (code == 401) {
                        javafx.application.Platform.runLater(() -> {
                            com.genie.ui.ErrorToast.showError(
                                "Invalid API Key",
                                "Your OpenAI API key is invalid. Please update it in Settings."
                            );
                        });
                    }
                    
                    return null;
                }
                
                String body = response.body().string();
                JsonObject json = gson.fromJson(body, JsonObject.class);
                return json.get("text").getAsString();
            }
            
        } catch (java.net.SocketTimeoutException | java.net.UnknownHostException e) {
            logger.warn("Network error calling Whisper API: {}", e.getMessage());
            
            if (retriesLeft > 0) {
                logger.info("Retrying Whisper API call after network error...");
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                return transcribeWithRetry(audioFile, retriesLeft - 1);
            }
            
            // Network error - voice features temporarily unavailable
            // Don't spam the user with toasts, just log
            logger.error("Whisper API unavailable due to network issues");
            return null;
            
        } catch (Exception e) {
            logger.error("Error calling Whisper API", e);
            return null;
        }
    }
    
    private void writeWavFile(Path path, byte[] audioData) throws IOException {
        AudioFormat format = getAudioFormat();
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
             AudioInputStream ais = new AudioInputStream(bais, format, audioData.length / format.getFrameSize())) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, path.toFile());
        }
    }
    
    private int calculateAudioLevel(byte[] buffer, int bytesRead) {
        long sum = 0;
        for (int i = 0; i < bytesRead - 1; i += 2) {
            int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
            sum += Math.abs(sample);
        }
        return (int) (sum / (bytesRead / 2));
    }
    
    private AudioFormat getAudioFormat() {
        return new AudioFormat(
            SAMPLE_RATE,
            SAMPLE_SIZE_BITS,
            CHANNELS,
            true,  // signed
            false  // little endian
        );
    }
    
    @Override
    public void stopListening() {
        listening.set(false);
        
        if (audioLine != null) {
            audioLine.stop();
        }
        
        if (captureThread != null) {
            captureThread.interrupt();
        }
        
        logger.info("Whisper voice capture stopped");
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
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}

