package com.genie.core;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages global hotkeys using JNativeHook
 * 
 * Hotkeys (using Ctrl+Option to avoid conflicts with Cursor/IDEs):
 * - Ctrl + Option + C: Save context
 * - Ctrl + Option + W: Make a wish
 */
public class HotkeyManager implements NativeKeyListener {
    
    private static final Logger logger = LoggerFactory.getLogger(HotkeyManager.class);
    
    // Track modifier state
    private boolean ctrlPressed = false;
    private boolean altPressed = false; // Option on Mac
    private boolean metaPressed = false; // Cmd on Mac
    
    // Callbacks
    private Runnable onSaveContext;
    private Runnable onMakeWish;
    
    public void register() {
        try {
            // Disable JNativeHook logging (it's very verbose)
            java.util.logging.Logger jnhLogger = java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
            jnhLogger.setLevel(java.util.logging.Level.OFF);
            jnhLogger.setUseParentHandlers(false);
            
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            
            logger.info("Global hotkeys registered");
        } catch (NativeHookException e) {
            logger.error("Failed to register global hotkeys", e);
        }
    }
    
    public void unregister() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
            logger.info("Global hotkeys unregistered");
        } catch (NativeHookException e) {
            logger.error("Failed to unregister global hotkeys", e);
        }
    }
    
    public void setOnSaveContext(Runnable callback) {
        this.onSaveContext = callback;
    }
    
    public void setOnMakeWish(Runnable callback) {
        this.onMakeWish = callback;
    }
    
    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        int keyCode = e.getKeyCode();
        
        // Track modifiers
        if (keyCode == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = true;
        } else if (keyCode == NativeKeyEvent.VC_ALT) {
            altPressed = true; // Option on Mac
        } else if (keyCode == NativeKeyEvent.VC_META) {
            metaPressed = true;
        }
        
        // Check for hotkeys: Ctrl + Option + key (avoids Cursor/IDE conflicts)
        if (ctrlPressed && altPressed) {
            if (keyCode == NativeKeyEvent.VC_C) {
                logger.info("Hotkey triggered: Save Context (Ctrl+Option+C)");
                if (onSaveContext != null) {
                    Platform.runLater(onSaveContext);
                } else {
                    Platform.runLater(this::defaultSaveContext);
                }
            } else if (keyCode == NativeKeyEvent.VC_W) {
                logger.info("Hotkey triggered: Make a Wish (Ctrl+Option+W)");
                if (onMakeWish != null) {
                    Platform.runLater(onMakeWish);
                } else {
                    Platform.runLater(this::defaultMakeWish);
                }
            }
        }
    }
    
    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        int keyCode = e.getKeyCode();
        
        // Track modifier release
        if (keyCode == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = false;
        } else if (keyCode == NativeKeyEvent.VC_ALT) {
            altPressed = false;
        } else if (keyCode == NativeKeyEvent.VC_META) {
            metaPressed = false;
        }
    }
    
    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // Not used
    }
    
    // Default handlers if no callbacks set
    
    private void defaultSaveContext() {
        logger.info("Capturing context...");
        
        // CRITICAL: Take screenshot IMMEDIATELY, right here in the native hook thread
        // Don't wait, don't switch threads - just capture NOW before anything can change
        String screenshotPath = captureScreenshotNow();
        
        // Now do everything else in background
        new Thread(() -> {
            ContextCapture.Context ctx = ContextCapture.captureNowWithScreenshot(screenshotPath);
            
            // Show popup on JavaFX thread
            Platform.runLater(() -> {
                com.genie.ui.ContextPopup.showSaveConfirmation(ctx);
            });
            
            // Generate AI summary
            try {
                String summary = com.genie.ai.ContextAnalyzer.analyzeContext(ctx);
                if (summary != null) {
                    logger.info("AI summary generated for context");
                    Platform.runLater(() -> {
                        com.genie.ui.ContextPopup.updateSummary(ctx);
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to generate AI summary", e);
            }
        }, "ContextCaptureThread").start();
    }
    
    /**
     * Capture screenshot immediately - called directly from native hook thread
     */
    private String captureScreenshotNow() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String home = System.getProperty("user.home");
            
            // Create screenshots directory
            java.nio.file.Path screenshotsDir;
            if (os.contains("mac")) {
                screenshotsDir = java.nio.file.Paths.get(home, "Library", "Application Support", "Genie", "screenshots");
            } else {
                screenshotsDir = java.nio.file.Paths.get(home, ".config", "genie", "screenshots");
            }
            java.nio.file.Files.createDirectories(screenshotsDir);
            
            String filename = "context_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".png";
            java.nio.file.Path screenshotPath = screenshotsDir.resolve(filename);
            
            if (os.contains("mac")) {
                // Use screencapture with -x (no sound) and capture immediately
                ProcessBuilder pb = new ProcessBuilder("screencapture", "-x", screenshotPath.toString());
                Process p = pb.start();
                p.waitFor();
                
                if (java.nio.file.Files.exists(screenshotPath)) {
                    logger.info("Screenshot captured to {}", screenshotPath);
                    return screenshotPath.toString();
                }
            }
        } catch (Exception e) {
            logger.error("Failed to capture screenshot", e);
        }
        return null;
    }
    
    private void defaultMakeWish() {
        logger.info("Opening wish input...");
        com.genie.ui.WishInputDialog.show();
    }
}

