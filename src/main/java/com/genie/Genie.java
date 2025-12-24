package com.genie;

import com.genie.core.HotkeyManager;
import com.genie.ui.OnboardingDialog;
import com.genie.ui.TrayManager;
import com.genie.util.CleanupService;
import com.genie.util.Config;
import com.genie.util.Database;
import com.genie.util.MacOSIntegration;
import com.genie.voice.VoiceManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;

/**
 * Genie - Never lose a thought again
 * 
 * A desktop productivity app that:
 * 1. Saves your mental context when interrupted (Context Resurrection)
 * 2. Captures fleeting curiosities for later research (I Wish)
 * 
 * Built for Code Spring Hackathon 2025
 */
public class Genie extends Application {
    
    private static final Logger logger = LoggerFactory.getLogger(Genie.class);
    
    private TrayManager trayManager;
    private HotkeyManager hotkeyManager;
    private VoiceManager voiceManager;
    private CleanupService cleanupService;
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void init() throws Exception {
        logger.info("Initializing Genie...");
        
        // Hide from Dock on macOS - make this a pure menu bar app
        // Must be called early, before any UI is shown
        MacOSIntegration.hideFromDock();
        
        // Initialize database
        Database.initialize();
        
        // Load config
        Config.load();
        
        // Request microphone permission (needed for voice activation)
        // This triggers macOS permission dialog if not already granted
        requestMicrophonePermission();
    }
    
    /**
     * Request microphone permission by briefly opening an audio line.
     * On macOS, this triggers the system permission dialog if not already granted.
     */
    private void requestMicrophonePermission() {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                
                if (!AudioSystem.isLineSupported(info)) {
                    logger.warn("Audio line not supported - voice features may not work");
                    return;
                }
                
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();
                
                // Read a tiny bit of audio to trigger permission request
                byte[] buffer = new byte[1024];
                line.read(buffer, 0, buffer.length);
                
                line.stop();
                line.close();
                
                logger.info("Microphone permission granted - voice features enabled");
                
            } catch (LineUnavailableException e) {
                logger.warn("Could not access microphone - voice features disabled: {}", e.getMessage());
            } catch (SecurityException e) {
                logger.warn("Microphone access denied - voice features disabled: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Error requesting microphone permission", e);
            }
        }, "MicrophonePermissionRequest").start();
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        logger.info("Starting Genie...");
        
        // Don't exit when all windows closed (we live in system tray)
        Platform.setImplicitExit(false);
        
        // Set up system tray
        trayManager = new TrayManager();
        trayManager.initialize();
        
        // Set up global hotkeys
        hotkeyManager = new HotkeyManager();
        hotkeyManager.register();
        
        // Set up voice capture (if enabled)
        voiceManager = VoiceManager.getInstance();
        voiceManager.setOnWishRecognized(this::handleVoiceWish);
        voiceManager.initialize();
        
        if (voiceManager.isVoiceCaptureAvailable()) {
            logger.info("Voice capture available: {}", voiceManager.getActiveImplementationName());
        } else {
            logger.info("Voice capture not available - using hotkey mode only");
        }
        
        // Start cleanup service (handles old screenshots, etc.)
        cleanupService = CleanupService.getInstance();
        cleanupService.start();
        
        // Show onboarding dialog on first launch (checks macOS permissions)
        boolean showedOnboarding = OnboardingDialog.showIfNeeded();
        
        // Check for API key and show helpful message if missing (but not during onboarding)
        if (!showedOnboarding) {
            checkApiKeyAndNotify();
        }
        
        logger.info("Genie is running in system tray");
    }
    
    /**
     * Check if API key is configured and show appropriate notifications
     */
    private void checkApiKeyAndNotify() {
        Platform.runLater(() -> {
            if (!Config.hasOpenAiApiKey()) {
                // Only show on first launch or after 24 hours
                String lastNotification = Config.get("lastApiKeyNotification", "0");
                long lastTime = Long.parseLong(lastNotification);
                long now = System.currentTimeMillis();
                long oneDayMs = 24 * 60 * 60 * 1000;
                
                if (now - lastTime > oneDayMs) {
                    com.genie.ui.ErrorToast.showMissingApiKey();
                    Config.set("lastApiKeyNotification", String.valueOf(now));
                    Config.save();
                }
            }
        });
    }
    
    @Override
    public void stop() throws Exception {
        logger.info("Shutting down Genie...");
        
        // Cleanup
        if (hotkeyManager != null) {
            hotkeyManager.unregister();
        }
        if (trayManager != null) {
            trayManager.shutdown();
        }
        
        if (voiceManager != null) {
            voiceManager.shutdown();
        }
        
        if (cleanupService != null) {
            cleanupService.stop();
        }
        
        Database.close();
        
        logger.info("Genie shut down cleanly");
    }
    
    /**
     * Handle a wish recognized via voice
     */
    private void handleVoiceWish(String wishText) {
        logger.info("Voice wish received: {}", wishText);
        
        // Save the wish directly (no need to show dialog for voice)
        Platform.runLater(() -> {
            try {
                long wishId = Database.saveWish(wishText);
                logger.info("Voice wish saved with ID: {}", wishId);
                
                // Show toast notification (allows editing if misheard)
                com.genie.ui.WishToast.show(wishText, wishId);
                
                // Generate research in background if API key configured
                if (Config.hasOpenAiApiKey()) {
                    generateResearchWithErrorHandling(wishText, wishId);
                }
            } catch (Exception e) {
                logger.error("Failed to save voice wish", e);
                Platform.runLater(() -> {
                    com.genie.ui.ErrorToast.showError(
                        "Failed to Save Wish",
                        "Your wish couldn't be saved: " + e.getMessage()
                    );
                });
            }
        });
    }
    
    /**
     * Generate research for a wish with proper error handling
     */
    private void generateResearchWithErrorHandling(String wishText, long wishId) {
        new Thread(() -> {
            try {
                com.genie.ai.OpenAIClient client = new com.genie.ai.OpenAIClient();
                String article = client.generateResearchArticle(wishText);
                Database.updateWishResearch(wishId, article);
                logger.info("Research generated for voice wish");
                
                // Notify success (subtle)
                Platform.runLater(() -> {
                    com.genie.ui.ErrorToast.showSuccess(
                        "Research Ready",
                        "Your wish about \"" + truncate(wishText, 30) + "\" has been researched!"
                    );
                });
            } catch (com.genie.ai.OpenAIClient.NetworkException e) {
                logger.error("Network error generating research", e);
                Platform.runLater(() -> {
                    com.genie.ui.ErrorToast.showNetworkErrorWithRetry(
                        "generate research",
                        () -> generateResearchWithErrorHandling(wishText, wishId)
                    );
                });
            } catch (com.genie.ai.OpenAIClient.ApiException e) {
                logger.error("API error generating research: {}", e.getMessage());
                Platform.runLater(() -> {
                    com.genie.ui.ErrorToast.showError(
                        "Research Generation Failed",
                        e.getMessage()
                    );
                });
            } catch (IllegalStateException e) {
                // Missing API key - already handled at startup
                logger.warn("Cannot generate research: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error generating research", e);
                Platform.runLater(() -> {
                    com.genie.ui.ErrorToast.showError(
                        "Research Generation Failed",
                        "An unexpected error occurred. Please try again."
                    );
                });
            }
        }, "ResearchGenerator-" + wishId).start();
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}

