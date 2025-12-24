package com.genie.core;

import com.genie.core.ContextCapture.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Restores a saved context by:
 * - Reopening browser tabs
 * - Bringing apps to front in the saved order
 * - Optionally restoring clipboard
 */
public class ContextRestorer {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextRestorer.class);
    
    /**
     * Restore browser tabs from a saved context
     */
    public static RestoreResult restoreBrowserTabs(Context context) {
        RestoreResult result = new RestoreResult();
        
        if (context.browserTabs == null || context.browserTabs.equals("[]")) {
            result.message = "No browser tabs to restore";
            return result;
        }
        
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("mac")) {
            result.message = "Tab restoration only supported on macOS";
            return result;
        }
        
        // Parse tabs from JSON
        List<BrowserTab> tabs = parseTabsJson(context.browserTabs);
        
        int chromeCount = 0;
        int safariCount = 0;
        int arcCount = 0;
        
        for (BrowserTab tab : tabs) {
            try {
                boolean success = openTabInBrowser(tab);
                if (success) {
                    switch (tab.browser.toLowerCase()) {
                        case "chrome" -> chromeCount++;
                        case "safari" -> safariCount++;
                        case "arc" -> arcCount++;
                    }
                    result.tabsRestored++;
                }
            } catch (Exception e) {
                logger.warn("Failed to restore tab: {}", tab.url, e);
                result.errors.add("Failed to open: " + tab.title);
            }
        }
        
        result.success = result.tabsRestored > 0;
        result.message = String.format("Restored %d tabs (Chrome: %d, Safari: %d, Arc: %d)", 
            result.tabsRestored, chromeCount, safariCount, arcCount);
        
        logger.info(result.message);
        return result;
    }
    
    /**
     * Bring apps from window stack to front in order
     */
    public static RestoreResult restoreWindowOrder(Context context) {
        RestoreResult result = new RestoreResult();
        
        if (context.windowStack == null || context.windowStack.equals("[]")) {
            result.message = "No window stack to restore";
            return result;
        }
        
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("mac")) {
            result.message = "Window restoration only supported on macOS";
            return result;
        }
        
        // Parse windows from JSON
        List<WindowInfo> windows = parseWindowsJson(context.windowStack);
        
        // Activate apps in reverse order (so first in list ends up on top)
        for (int i = windows.size() - 1; i >= 0; i--) {
            WindowInfo window = windows.get(i);
            try {
                activateApp(window.app);
                result.windowsRestored++;
            } catch (Exception e) {
                logger.warn("Failed to activate app: {}", window.app, e);
            }
        }
        
        result.success = result.windowsRestored > 0;
        result.message = String.format("Activated %d applications", result.windowsRestored);
        
        logger.info(result.message);
        return result;
    }
    
    /**
     * Full context restoration
     */
    public static RestoreResult restoreContext(Context context) {
        RestoreResult result = new RestoreResult();
        
        // Restore browser tabs first
        RestoreResult tabResult = restoreBrowserTabs(context);
        result.tabsRestored = tabResult.tabsRestored;
        result.errors.addAll(tabResult.errors);
        
        // Then restore window order (to bring the right app to front)
        RestoreResult windowResult = restoreWindowOrder(context);
        result.windowsRestored = windowResult.windowsRestored;
        result.errors.addAll(windowResult.errors);
        
        result.success = tabResult.success || windowResult.success;
        result.message = String.format("Restored %d tabs, activated %d apps", 
            result.tabsRestored, result.windowsRestored);
        
        return result;
    }
    
    /**
     * Open a tab in the appropriate browser
     */
    private static boolean openTabInBrowser(BrowserTab tab) {
        String script = switch (tab.browser.toLowerCase()) {
            case "chrome" -> String.format("""
                tell application "Google Chrome"
                    activate
                    open location "%s"
                end tell
                """, escapeForAppleScript(tab.url));
            
            case "safari" -> String.format("""
                tell application "Safari"
                    activate
                    open location "%s"
                end tell
                """, escapeForAppleScript(tab.url));
            
            case "arc" -> String.format("""
                tell application "Arc"
                    activate
                    open location "%s"
                end tell
                """, escapeForAppleScript(tab.url));
            
            default -> null;
        };
        
        if (script == null) {
            logger.warn("Unknown browser: {}", tab.browser);
            return false;
        }
        
        return runAppleScript(script) != null;
    }
    
    /**
     * Activate (bring to front) an application
     */
    private static void activateApp(String appName) {
        String script = String.format("""
            tell application "%s"
                activate
            end tell
            """, escapeForAppleScript(appName));
        
        runAppleScript(script);
    }
    
    /**
     * Parse browser tabs JSON
     */
    private static List<BrowserTab> parseTabsJson(String json) {
        List<BrowserTab> tabs = new ArrayList<>();
        
        // Simple regex-based parsing for our known JSON format
        Pattern pattern = Pattern.compile(
            "\\{\"browser\":\"([^\"]+)\",\"url\":\"([^\"]+)\",\"title\":\"([^\"]*)\"\\}");
        Matcher matcher = pattern.matcher(json);
        
        while (matcher.find()) {
            BrowserTab tab = new BrowserTab();
            tab.browser = matcher.group(1);
            tab.url = matcher.group(2);
            tab.title = matcher.group(3);
            tabs.add(tab);
        }
        
        return tabs;
    }
    
    /**
     * Parse windows JSON
     */
    private static List<WindowInfo> parseWindowsJson(String json) {
        List<WindowInfo> windows = new ArrayList<>();
        
        Pattern pattern = Pattern.compile(
            "\\{\"app\":\"([^\"]+)\",\"title\":\"([^\"]*)\"\\}");
        Matcher matcher = pattern.matcher(json);
        
        while (matcher.find()) {
            WindowInfo window = new WindowInfo();
            window.app = matcher.group(1);
            window.title = matcher.group(2);
            windows.add(window);
        }
        
        return windows;
    }
    
    /**
     * Run AppleScript
     */
    private static String runAppleScript(String script) {
        try {
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.lines().collect(Collectors.joining("\n")).trim();
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.debug("AppleScript returned non-zero: {} - {}", exitCode, result);
                return null;
            }
            return result;
        } catch (Exception e) {
            logger.warn("AppleScript execution failed", e);
            return null;
        }
    }
    
    /**
     * Escape string for use in AppleScript
     */
    private static String escapeForAppleScript(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"");
    }
    
    // Data classes
    
    public static class BrowserTab {
        public String browser;
        public String url;
        public String title;
    }
    
    public static class WindowInfo {
        public String app;
        public String title;
    }
    
    public static class RestoreResult {
        public boolean success = false;
        public String message = "";
        public int tabsRestored = 0;
        public int windowsRestored = 0;
        public List<String> errors = new ArrayList<>();
    }
}

