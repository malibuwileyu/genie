package com.genie.core;

import com.genie.util.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Context Capture for Context Resurrection
 * 
 * Captures:
 * - Screenshot of current screen (for AI analysis)
 * - Active window title
 * - Window stack (all visible windows in Z-order)
 * - Browser tabs (Chrome, Safari, Arc)
 * - Clipboard contents
 * - Optional: user voice/text note
 */
public class ContextCapture {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextCapture.class);
    
    /**
     * Capture the complete current context and save to database
     */
    public static Context captureNow() {
        return captureNowWithScreenshot(null);
    }
    
    /**
     * Capture context with a pre-captured screenshot path
     * Used when screenshot must be taken immediately before any thread switching
     */
    public static Context captureNowWithScreenshot(String preScreenshotPath) {
        Context context = new Context();
        context.timestamp = LocalDateTime.now();
        
        // Capture everything
        context.clipboardContent = getClipboardContent();
        context.activeWindow = getActiveWindowTitle();
        context.windowStack = getWindowStackJson();
        context.browserTabs = getBrowserTabsJson();
        
        // Use pre-captured screenshot if provided, otherwise capture now
        if (preScreenshotPath != null) {
            context.screenshotPath = preScreenshotPath;
        } else {
            context.screenshotPath = captureScreenshot(context.timestamp);
        }
        
        // Save to database (AI summary will be added async)
        Database.saveContext(context);
        
        logger.info("Context captured: window='{}', {} recent windows, {} active browser tabs, screenshot={}", 
            context.activeWindow,
            context.getWindowCount(),
            context.getTabCount(),
            context.screenshotPath != null ? "yes" : "no"
        );
        
        return context;
    }
    
    /**
     * Capture screenshot and save to app data directory
     * Uses native macOS screencapture for reliable window capture
     */
    private static String captureScreenshot(LocalDateTime timestamp) {
        try {
            Path screenshotsDir = getScreenshotsDirectory();
            Files.createDirectories(screenshotsDir);
            
            String filename = "context_" + timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".png";
            Path screenshotPath = screenshotsDir.resolve(filename);
            
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("mac")) {
                // Use native macOS screencapture - much more reliable
                // -x: no sound, -C: capture cursor, -o: no shadow
                ProcessBuilder pb = new ProcessBuilder(
                    "screencapture", "-x", "-C", screenshotPath.toString()
                );
                Process process = pb.start();
                int exitCode = process.waitFor();
                
                if (exitCode == 0 && Files.exists(screenshotPath)) {
                    logger.debug("Screenshot saved via screencapture to {}", screenshotPath);
                    return screenshotPath.toString();
                } else {
                    logger.warn("screencapture failed with exit code {}", exitCode);
                    // Fall back to Robot
                    return captureWithRobot(screenshotPath);
                }
            } else {
                // Use Java Robot for other platforms
                return captureWithRobot(screenshotPath);
            }
        } catch (Exception e) {
            logger.error("Failed to capture screenshot", e);
            return null;
        }
    }
    
    /**
     * Fallback screenshot using Java Robot (for non-macOS or if screencapture fails)
     */
    private static String captureWithRobot(Path screenshotPath) {
        try {
            Robot robot = new Robot();
            Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage screenshot = robot.createScreenCapture(screenRect);
            ImageIO.write(screenshot, "png", screenshotPath.toFile());
            logger.debug("Screenshot saved via Robot to {}", screenshotPath);
            return screenshotPath.toString();
        } catch (Exception e) {
            logger.error("Robot screenshot failed", e);
            return null;
        }
    }
    
    private static Path getScreenshotsDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "Genie", "screenshots");
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Paths.get(appData, "Genie", "screenshots");
        } else {
            return Paths.get(home, ".config", "genie", "screenshots");
        }
    }
    
    /**
     * Get current clipboard contents (text only for now)
     */
    private static String getClipboardContent() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (Exception e) {
            logger.warn("Failed to get clipboard content", e);
        }
        return null;
    }
    
    /**
     * Get the title of the currently active window
     */
    private static String getActiveWindowTitle() {
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            if (os.contains("mac")) {
                return getActiveWindowMac();
            } else if (os.contains("win")) {
                return getActiveWindowWindows();
            } else {
                return getActiveWindowLinux();
            }
        } catch (Exception e) {
            logger.warn("Failed to get active window title", e);
            return "Unknown";
        }
    }
    
    private static String getActiveWindowMac() {
        try {
            // Get both app name and window title
            String script = """
                tell application "System Events"
                    set frontApp to first process whose frontmost is true
                    set appName to name of frontApp
                    try
                        set windowTitle to name of front window of frontApp
                        return appName & " - " & windowTitle
                    on error
                        return appName
                    end try
                end tell
                """;
            
            return runAppleScript(script);
        } catch (Exception e) {
            logger.warn("Failed to get active window on Mac", e);
            return "Unknown";
        }
    }
    
    private static String getActiveWindowWindows() {
        // TODO: Implement using JNA and user32.dll
        return "Windows app (TODO)";
    }
    
    private static String getActiveWindowLinux() {
        try {
            ProcessBuilder pb = new ProcessBuilder("xdotool", "getactivewindow", "getwindowname");
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            process.waitFor();
            return output;
        } catch (Exception e) {
            logger.warn("Failed to get active window on Linux", e);
            return "Unknown";
        }
    }
    
    /**
     * Get only the most recent 2 windows (frontmost + one behind)
     * Excludes minimized windows and helper processes
     * Returns JSON array: [{"app": "Chrome", "title": "GitHub"}, ...]
     */
    private static String getWindowStackJson() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (!os.contains("mac")) {
            return "[]"; // TODO: implement for other platforms
        }
        
        try {
            // AppleScript to get only frontmost app and the one behind it
            // This captures "what you're working on" not "everything open"
            String script = """
                set windowList to ""
                set windowCount to 0
                tell application "System Events"
                    -- Get processes ordered by most recent focus
                    set orderedProcesses to every process whose visible is true and background only is false
                    repeat with proc in orderedProcesses
                        if windowCount >= 2 then exit repeat
                        set appName to name of proc
                        -- Skip helper/background apps
                        if appName is not in {"Finder", "SystemUIServer", "Dock", "Spotlight", "Control Center"} then
                            try
                                set wins to windows of proc
                                if (count of wins) > 0 then
                                    set frontWin to item 1 of wins
                                    set winTitle to name of frontWin
                                    set winTitle to my replaceText(winTitle, "\\"", "'")
                                    set windowList to windowList & "{\\"app\\":\\"" & appName & "\\",\\"title\\":\\"" & winTitle & "\\"},"
                                    set windowCount to windowCount + 1
                                end if
                            end try
                        end if
                    end repeat
                end tell
                if windowList is not "" then
                    set windowList to text 1 thru -2 of windowList
                end if
                return "[" & windowList & "]"
                
                on replaceText(theText, searchStr, replaceStr)
                    set oldDelims to AppleScript's text item delimiters
                    set AppleScript's text item delimiters to searchStr
                    set textItems to text items of theText
                    set AppleScript's text item delimiters to replaceStr
                    set theText to textItems as text
                    set AppleScript's text item delimiters to oldDelims
                    return theText
                end replaceText
                """;
            
            String result = runAppleScript(script);
            if (result == null || result.isEmpty()) {
                return "[]";
            }
            return result;
        } catch (Exception e) {
            logger.warn("Failed to get window stack", e);
            return "[]";
        }
    }
    
    /**
     * Get ONLY the active tab from each browser (the one you're looking at)
     * Not all 40 tabs - just the 1-3 you actually care about
     * Returns JSON array: [{"browser": "Chrome", "url": "...", "title": "..."}, ...]
     */
    private static String getBrowserTabsJson() {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (!os.contains("mac")) {
            return "[]"; // TODO: implement for other platforms
        }
        
        List<String> tabs = new ArrayList<>();
        
        // Only get ACTIVE tab from each browser
        String chromeTab = getActiveTabChrome();
        if (chromeTab != null) tabs.add(chromeTab);
        
        String safariTab = getActiveTabSafari();
        if (safariTab != null) tabs.add(safariTab);
        
        String arcTab = getActiveTabArc();
        if (arcTab != null) tabs.add(arcTab);
        
        return "[" + String.join(",", tabs) + "]";
    }
    
    /**
     * Get only the active (currently visible) tab from Chrome
     */
    private static String getActiveTabChrome() {
        try {
            String script = """
                tell application "System Events"
                    if not (exists process "Google Chrome") then return ""
                end tell
                tell application "Google Chrome"
                    if (count of windows) = 0 then return ""
                    set activeTab to active tab of front window
                    set tabUrl to URL of activeTab
                    set tabTitle to title of activeTab
                    set tabTitle to my replaceText(tabTitle, "\\"", "'")
                    return "{\\"browser\\":\\"Chrome\\",\\"url\\":\\"" & tabUrl & "\\",\\"title\\":\\"" & tabTitle & "\\"}"
                end tell
                
                on replaceText(theText, searchStr, replaceStr)
                    set oldDelims to AppleScript's text item delimiters
                    set AppleScript's text item delimiters to searchStr
                    set textItems to text items of theText
                    set AppleScript's text item delimiters to replaceStr
                    set theText to textItems as text
                    set AppleScript's text item delimiters to oldDelims
                    return theText
                end replaceText
                """;
            
            String result = runAppleScript(script);
            if (result != null && !result.isEmpty() && result.contains("browser")) {
                return result;
            }
        } catch (Exception e) {
            logger.debug("Failed to get Chrome active tab", e);
        }
        return null;
    }
    
    /**
     * Get only the active (currently visible) tab from Safari
     */
    private static String getActiveTabSafari() {
        try {
            String script = """
                tell application "System Events"
                    if not (exists process "Safari") then return ""
                end tell
                tell application "Safari"
                    if (count of windows) = 0 then return ""
                    set activeTab to current tab of front window
                    set tabUrl to URL of activeTab
                    set tabTitle to name of activeTab
                    set tabTitle to my replaceText(tabTitle, "\\"", "'")
                    return "{\\"browser\\":\\"Safari\\",\\"url\\":\\"" & tabUrl & "\\",\\"title\\":\\"" & tabTitle & "\\"}"
                end tell
                
                on replaceText(theText, searchStr, replaceStr)
                    set oldDelims to AppleScript's text item delimiters
                    set AppleScript's text item delimiters to searchStr
                    set textItems to text items of theText
                    set AppleScript's text item delimiters to replaceStr
                    set theText to textItems as text
                    set AppleScript's text item delimiters to oldDelims
                    return theText
                end replaceText
                """;
            
            String result = runAppleScript(script);
            if (result != null && !result.isEmpty() && result.contains("browser")) {
                return result;
            }
        } catch (Exception e) {
            logger.debug("Failed to get Safari active tab", e);
        }
        return null;
    }
    
    /**
     * Get only the active (currently visible) tab from Arc
     */
    private static String getActiveTabArc() {
        try {
            String script = """
                tell application "System Events"
                    if not (exists process "Arc") then return ""
                end tell
                tell application "Arc"
                    if (count of windows) = 0 then return ""
                    set activeTab to active tab of front window
                    set tabUrl to URL of activeTab
                    set tabTitle to title of activeTab
                    set tabTitle to my replaceText(tabTitle, "\\"", "'")
                    return "{\\"browser\\":\\"Arc\\",\\"url\\":\\"" & tabUrl & "\\",\\"title\\":\\"" & tabTitle & "\\"}"
                end tell
                
                on replaceText(theText, searchStr, replaceStr)
                    set oldDelims to AppleScript's text item delimiters
                    set AppleScript's text item delimiters to searchStr
                    set textItems to text items of theText
                    set AppleScript's text item delimiters to replaceStr
                    set theText to textItems as text
                    set AppleScript's text item delimiters to oldDelims
                    return theText
                end replaceText
                """;
            
            String result = runAppleScript(script);
            if (result != null && !result.isEmpty() && result.contains("browser")) {
                return result;
            }
        } catch (Exception e) {
            logger.debug("Failed to get Arc active tab", e);
        }
        return null;
    }
    
    /**
     * Run AppleScript and return the result
     */
    private static String runAppleScript(String script) {
        try {
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.lines().collect(Collectors.joining("\n")).trim();
            
            process.waitFor();
            return result;
        } catch (Exception e) {
            logger.warn("AppleScript execution failed", e);
            return null;
        }
    }
    
    /**
     * Context data class with all captured information
     */
    public static class Context {
        public long id;
        public LocalDateTime timestamp;
        public String clipboardContent;
        public String activeWindow;
        public String windowStack;      // JSON array of windows
        public String browserTabs;      // JSON array of browser tabs
        public String screenshotPath;   // Path to screenshot file
        public String aiSummary;        // AI-generated summary
        public String userNote;         // Optional user note
        
        public String getFormattedTime() {
            return timestamp.format(DateTimeFormatter.ofPattern("MMM d, h:mm a"));
        }
        
        /**
         * Get count of windows in stack
         */
        public int getWindowCount() {
            if (windowStack == null || windowStack.equals("[]")) return 0;
            return windowStack.split("\\},").length;
        }
        
        /**
         * Get count of browser tabs
         */
        public int getTabCount() {
            if (browserTabs == null || browserTabs.equals("[]")) return 0;
            return browserTabs.split("\\},").length;
        }
        
        /**
         * Get screenshot file if it exists
         */
        public File getScreenshotFile() {
            if (screenshotPath == null) return null;
            File file = new File(screenshotPath);
            return file.exists() ? file : null;
        }
        
        @Override
        public String toString() {
            return String.format("Context[%s, window=%s, %d windows, %d tabs]", 
                getFormattedTime(), activeWindow, getWindowCount(), getTabCount());
        }
    }
}
