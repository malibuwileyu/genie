package com.genie.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Checks macOS permissions required for Genie features
 */
public class PermissionChecker {
    
    private static final Logger logger = LoggerFactory.getLogger(PermissionChecker.class);
    
    /**
     * Check if microphone permission is granted
     */
    public static boolean hasMicrophonePermission() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            
            if (!AudioSystem.isLineSupported(info)) {
                return false;
            }
            
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
            
            // Read a tiny bit to confirm access
            byte[] buffer = new byte[1024];
            int read = line.read(buffer, 0, buffer.length);
            
            line.stop();
            line.close();
            
            return read > 0;
        } catch (LineUnavailableException | SecurityException e) {
            logger.debug("Microphone permission check failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.warn("Unexpected error checking microphone permission", e);
            return false;
        }
    }
    
    /**
     * Check if Accessibility permission is granted (for AppleScript automation)
     */
    public static boolean hasAccessibilityPermission() {
        if (!isMacOS()) {
            return true; // Only relevant on macOS
        }
        
        try {
            // Try a simple System Events command that requires Accessibility
            String script = "tell application \"System Events\" to get name of first process whose frontmost is true";
            
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.lines().reduce("", (a, b) -> a + b).toLowerCase();
            
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            int exitCode = completed ? process.exitValue() : -1;
            
            // Check for permission error messages
            if (output.contains("not allowed assistive access") || 
                output.contains("osascript is not allowed") ||
                output.contains("system events got an error")) {
                logger.info("Accessibility permission not granted");
                return false;
            }
            
            return exitCode == 0;
        } catch (Exception e) {
            logger.warn("Error checking Accessibility permission", e);
            return false;
        }
    }
    
    /**
     * Check if Screen Recording permission is likely granted
     * Note: This is harder to detect - we check if screencapture produces a valid image
     */
    public static boolean hasScreenRecordingPermission() {
        if (!isMacOS()) {
            return true; // Only relevant on macOS
        }
        
        try {
            // Try a quick screencapture to temp file
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("genie_perm_check_", ".png");
            
            ProcessBuilder pb = new ProcessBuilder("screencapture", "-x", "-C", tempFile.toString());
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            
            if (!completed || process.exitValue() != 0) {
                java.nio.file.Files.deleteIfExists(tempFile);
                return false;
            }
            
            // Check if file exists and has reasonable size (not just a tiny error image)
            long size = java.nio.file.Files.size(tempFile);
            java.nio.file.Files.deleteIfExists(tempFile);
            
            // A real screenshot should be at least 10KB
            // Without Screen Recording permission, it might be very small or just desktop
            boolean hasPermission = size > 10000;
            
            if (!hasPermission) {
                logger.info("Screen Recording permission may not be granted (screenshot size: {} bytes)", size);
            }
            
            return hasPermission;
        } catch (Exception e) {
            logger.warn("Error checking Screen Recording permission", e);
            return false;
        }
    }
    
    /**
     * Open System Preferences to the Microphone privacy pane
     */
    public static void openMicrophoneSettings() {
        openSystemPreferences("Privacy_Microphone");
    }
    
    /**
     * Open System Preferences to the Accessibility pane
     */
    public static void openAccessibilitySettings() {
        openSystemPreferences("Privacy_Accessibility");
    }
    
    /**
     * Open System Preferences to the Screen Recording pane
     */
    public static void openScreenRecordingSettings() {
        openSystemPreferences("Privacy_ScreenCapture");
    }
    
    private static void openSystemPreferences(String pane) {
        if (!isMacOS()) {
            logger.warn("Cannot open System Preferences on non-macOS");
            return;
        }
        
        try {
            String url = "x-apple.systempreferences:com.apple.preference.security?" + pane;
            Runtime.getRuntime().exec(new String[]{"open", url});
            logger.info("Opened System Preferences: {}", pane);
        } catch (Exception e) {
            logger.error("Failed to open System Preferences", e);
        }
    }
    
    /**
     * Check if running on macOS
     */
    public static boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }
    
    /**
     * Get a summary of all permissions
     */
    public static PermissionStatus checkAll() {
        return new PermissionStatus(
            hasMicrophonePermission(),
            hasAccessibilityPermission(),
            hasScreenRecordingPermission()
        );
    }
    
    /**
     * Permission status container
     */
    public static class PermissionStatus {
        public final boolean microphone;
        public final boolean accessibility;
        public final boolean screenRecording;
        
        public PermissionStatus(boolean microphone, boolean accessibility, boolean screenRecording) {
            this.microphone = microphone;
            this.accessibility = accessibility;
            this.screenRecording = screenRecording;
        }
        
        public boolean allGranted() {
            return microphone && accessibility && screenRecording;
        }
        
        public int grantedCount() {
            int count = 0;
            if (microphone) count++;
            if (accessibility) count++;
            if (screenRecording) count++;
            return count;
        }
        
        @Override
        public String toString() {
            return String.format("Permissions[mic=%s, accessibility=%s, screenRec=%s]", 
                microphone, accessibility, screenRecording);
        }
    }
}

