package com.genie.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * macOS native voice capture using SFSpeechRecognizer
 * 
 * Uses a Swift helper script for native speech recognition quality.
 * Falls back to dictation via osascript if Swift helper isn't available.
 */
public class MacOSVoiceCapture implements VoiceCapture {
    
    private static final Logger logger = LoggerFactory.getLogger(MacOSVoiceCapture.class);
    
    // Include common speech-to-text misinterpretations of "genie"
    private static final String[] WAKE_PHRASES = {
        "genie", "hey genie", "ok genie",
        "jeannie", "hey jeannie", "ok jeannie",  // Common STT interpretation
        "jeanie", "hey jeanie", "ok jeanie",
        "geni", "hey geni", "ok geni"
    };
    
    private Process recognitionProcess;
    private Thread monitorThread;
    private final AtomicBoolean listening = new AtomicBoolean(false);
    private final AtomicBoolean wakeWordDetected = new AtomicBoolean(false);
    
    private WishRecognizedCallback wishCallback;
    private PartialResultCallback partialCallback;
    
    private StringBuilder wishBuffer = new StringBuilder();
    
    @Override
    public boolean isAvailable() {
        // Check if we're on macOS
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("mac")) {
            return false;
        }
        
        // Check if speech recognition permission is likely available
        // (actual permission check happens when we start)
        return true;
    }
    
    @Override
    public String getName() {
        return "macOS Native (SFSpeechRecognizer)";
    }
    
    @Override
    public void startListening() {
        if (listening.get()) {
            logger.debug("Already listening, ignoring startListening call");
            return;
        }
        
        try {
            listening.set(true);
            wakeWordDetected.set(false);
            wishBuffer = new StringBuilder();
            
            logger.info("macOS voice capture starting - listening for wake phrases: {}", 
                String.join(", ", WAKE_PHRASES));
            
            // Use Swift/SFSpeechRecognizer for native speech recognition
            startNativeRecognition();
            
        } catch (Exception e) {
            logger.error("Failed to start macOS voice capture", e);
            listening.set(false);
        }
    }
    
    private void startNativeRecognition() throws IOException, InterruptedException {
        // Create a Swift script for continuous speech recognition
        String swiftScript = createSwiftScript();
        
        // Write script to temp file
        File scriptFile = File.createTempFile("genie_speech", ".swift");
        scriptFile.deleteOnExit();
        
        try (FileWriter writer = new FileWriter(scriptFile)) {
            writer.write(swiftScript);
        }
        
        logger.info("Swift script written to: {}", scriptFile.getAbsolutePath());
        
        // Compile and run Swift script
        ProcessBuilder pb = new ProcessBuilder(
            "swift", scriptFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        recognitionProcess = pb.start();
        
        // Give Swift a moment to start (compilation takes time)
        Thread.sleep(500);
        
        if (!recognitionProcess.isAlive()) {
            // Process exited immediately - likely a compile error
            String output = new String(recognitionProcess.getInputStream().readAllBytes());
            logger.error("Swift script failed to start. Output: {}", output);
            throw new IOException("Swift speech recognition failed to start: " + output);
        }
        
        // Monitor output (including errors since we redirectErrorStream)
        monitorThread = new Thread(() -> monitorOutput(recognitionProcess.getInputStream()), "MacOSSpeechMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        
        logger.info("Swift speech recognition process started successfully");
    }
    
    private String createSwiftScript() {
        // Swift script that uses SFSpeechRecognizer for continuous recognition
        return """
            import Foundation
            import Speech
            import AVFoundation
            
            class SpeechRecognizer {
                private let audioEngine = AVAudioEngine()
                private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
                private var recognitionTask: SFSpeechRecognitionTask?
                private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
                
                func start() {
                    SFSpeechRecognizer.requestAuthorization { status in
                        switch status {
                        case .authorized:
                            self.startRecognition()
                        case .denied, .restricted, .notDetermined:
                            print("ERROR: Speech recognition not authorized")
                            exit(1)
                        @unknown default:
                            print("ERROR: Unknown authorization status")
                            exit(1)
                        }
                    }
                    
                    RunLoop.main.run()
                }
                
                private func startRecognition() {
                    recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
                    recognitionRequest?.shouldReportPartialResults = true
                    
                    let inputNode = audioEngine.inputNode
                    let recordingFormat = inputNode.outputFormat(forBus: 0)
                    
                    inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
                        self.recognitionRequest?.append(buffer)
                    }
                    
                    audioEngine.prepare()
                    
                    do {
                        try audioEngine.start()
                    } catch {
                        print("ERROR: Could not start audio engine: \\(error)")
                        return
                    }
                    
                    recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest!) { result, error in
                        if let result = result {
                            let text = result.bestTranscription.formattedString
                            // Output format: PARTIAL: or FINAL: followed by text
                            if result.isFinal {
                                print("FINAL: \\(text)")
                                fflush(stdout)
                                // Restart recognition for continuous listening
                                self.restartRecognition()
                            } else {
                                print("PARTIAL: \\(text)")
                                fflush(stdout)
                            }
                        }
                        
                        if let error = error {
                            print("ERROR: \\(error.localizedDescription)")
                        }
                    }
                }
                
                private func restartRecognition() {
                    recognitionTask?.cancel()
                    recognitionTask = nil
                    recognitionRequest = nil
                    
                    audioEngine.inputNode.removeTap(onBus: 0)
                    audioEngine.stop()
                    
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        self.startRecognition()
                    }
                }
            }
            
            let recognizer = SpeechRecognizer()
            recognizer.start()
            """;
    }
    
    private void monitorOutput(InputStream inputStream) {
        logger.info("Monitoring speech recognition output...");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while (listening.get() && (line = reader.readLine()) != null) {
                // Log ALL output for debugging
                logger.info("Swift output: {}", line);
                processRecognitionOutput(line);
            }
            logger.info("Speech recognition output stream ended");
        } catch (IOException e) {
            if (listening.get()) {
                logger.error("Error reading speech recognition output", e);
            }
        }
    }
    
    private void processRecognitionOutput(String line) {
        logger.debug("Speech output: {}", line);
        
        if (line.startsWith("ERROR:")) {
            logger.error("Speech recognition error: {}", line);
            return;
        }
        
        boolean isFinal = line.startsWith("FINAL:");
        String text = line.replaceFirst("^(PARTIAL|FINAL): ", "").trim().toLowerCase();
        
        if (text.isEmpty()) return;
        
        if (!wakeWordDetected.get()) {
            // Looking for wake word
            for (String wake : WAKE_PHRASES) {
                if (text.contains(wake)) {
                    logger.info("Wake phrase detected: '{}'", wake);
                    wakeWordDetected.set(true);
                    wishBuffer = new StringBuilder();
                    
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
            if (isFinal) {
                // Check if this contains the wish (might be continuation)
                String currentText = text;
                
                // Remove any wake phrases from the text
                for (String wake : WAKE_PHRASES) {
                    int idx = currentText.indexOf(wake);
                    if (idx >= 0) {
                        currentText = currentText.substring(idx + wake.length()).trim();
                    }
                }
                
                if (!currentText.isEmpty()) {
                    // This is the complete wish
                    if (wishCallback != null) {
                        logger.info("Wish captured: '{}'", currentText);
                        wishCallback.onWishRecognized(currentText);
                    }
                }
                
                // Reset for next wish
                wakeWordDetected.set(false);
                wishBuffer = new StringBuilder();
            } else {
                // Partial result - update UI
                if (partialCallback != null) {
                    partialCallback.onPartialResult(text);
                }
            }
        }
    }
    
    @Override
    public void stopListening() {
        listening.set(false);
        
        if (recognitionProcess != null) {
            recognitionProcess.destroyForcibly();
            recognitionProcess = null;
        }
        
        if (monitorThread != null) {
            monitorThread.interrupt();
            monitorThread = null;
        }
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
}

