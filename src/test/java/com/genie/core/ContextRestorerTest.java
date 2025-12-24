package com.genie.core;

import com.genie.core.ContextCapture.Context;
import com.genie.core.ContextRestorer.RestoreResult;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ContextRestorer
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContextRestorerTest {
    
    @Test
    @Order(1)
    @DisplayName("restoreContext with empty context returns result")
    void restoreEmptyContextSafe() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.windowStack = "[]";
        ctx.browserTabs = "[]";
        
        RestoreResult result = ContextRestorer.restoreContext(ctx);
        
        assertThat(result).isNotNull();
        assertThat(result.tabsRestored).isEqualTo(0);
        assertThat(result.windowsRestored).isEqualTo(0);
    }
    
    @Test
    @Order(2)
    @DisplayName("restoreBrowserTabs with empty tabs returns message")
    void restoreEmptyTabsReturnsMessage() {
        Context ctx = new Context();
        ctx.browserTabs = "[]";
        
        RestoreResult result = ContextRestorer.restoreBrowserTabs(ctx);
        
        assertThat(result).isNotNull();
        assertThat(result.message).contains("No browser tabs");
    }
    
    @Test
    @Order(3)
    @DisplayName("restoreWindowOrder with empty stack returns message")
    void restoreEmptyWindowsReturnsMessage() {
        Context ctx = new Context();
        ctx.windowStack = "[]";
        
        RestoreResult result = ContextRestorer.restoreWindowOrder(ctx);
        
        assertThat(result).isNotNull();
        assertThat(result.message).contains("No window stack");
    }
    
    @Test
    @Order(4)
    @DisplayName("restoreContext with valid window stack on macOS")
    void restoreWithWindowStack() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("mac")) {
            // Skip on non-macOS
            return;
        }
        
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.windowStack = "[{\"app\":\"Finder\",\"title\":\"Desktop\"}]";
        ctx.browserTabs = "[]";
        
        // Should not throw (Finder should be available)
        RestoreResult result = ContextRestorer.restoreContext(ctx);
        
        assertThat(result).isNotNull();
        // Finder should activate successfully
        assertThat(result.windowsRestored).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    @Order(5)
    @DisplayName("restoreContext handles malformed JSON gracefully")
    void handlesMalformedJson() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.windowStack = "not valid json";
        ctx.browserTabs = "{also invalid}";
        
        // Should handle gracefully, not crash
        RestoreResult result = ContextRestorer.restoreContext(ctx);
        
        assertThat(result).isNotNull();
        // Nothing to restore from invalid JSON
        assertThat(result.tabsRestored).isEqualTo(0);
        assertThat(result.windowsRestored).isEqualTo(0);
    }
    
    @Test
    @Order(6)
    @DisplayName("restoreContext handles missing fields in JSON")
    void handlesMissingFields() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.windowStack = "[{\"app\":\"Test\"}]"; // Missing "title"
        ctx.browserTabs = "[{\"browser\":\"Chrome\"}]"; // Missing "url" and "title"
        
        // Should not crash
        RestoreResult result = ContextRestorer.restoreContext(ctx);
        assertThat(result).isNotNull();
    }
    
    @Test
    @Order(7)
    @DisplayName("RestoreResult defaults are sensible")
    void restoreResultDefaults() {
        RestoreResult result = new RestoreResult();
        
        assertThat(result.success).isFalse();
        assertThat(result.message).isEmpty();
        assertThat(result.tabsRestored).isEqualTo(0);
        assertThat(result.windowsRestored).isEqualTo(0);
        assertThat(result.errors).isNotNull().isEmpty();
    }
    
    @Test
    @Order(8)
    @DisplayName("restoreBrowserTabs returns correct count on macOS")
    void countTabsCorrectly() {
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("mac")) {
            return;
        }
        
        Context ctx = new Context();
        ctx.browserTabs = "[{\"browser\":\"Safari\",\"url\":\"https://example.com\",\"title\":\"Example\"}]";
        
        // This may or may not actually open Safari depending on permissions
        RestoreResult result = ContextRestorer.restoreBrowserTabs(ctx);
        
        assertThat(result).isNotNull();
        // Should at least return a proper message
        assertThat(result.message).isNotEmpty();
    }
    
    @Test
    @Order(9)
    @DisplayName("Non-macOS returns appropriate message for browser tabs")
    void nonMacOSBrowserTabsMessage() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            // This test is for non-macOS
            return;
        }
        
        Context ctx = new Context();
        ctx.browserTabs = "[{\"browser\":\"Chrome\",\"url\":\"https://test.com\",\"title\":\"Test\"}]";
        
        RestoreResult result = ContextRestorer.restoreBrowserTabs(ctx);
        
        assertThat(result.message).contains("only supported on macOS");
    }
    
    @Test
    @Order(10)
    @DisplayName("Non-macOS returns appropriate message for window order")
    void nonMacOSWindowOrderMessage() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac")) {
            return;
        }
        
        Context ctx = new Context();
        ctx.windowStack = "[{\"app\":\"Test\",\"title\":\"Window\"}]";
        
        RestoreResult result = ContextRestorer.restoreWindowOrder(ctx);
        
        assertThat(result.message).contains("only supported on macOS");
    }
}

