package com.genie.core;

import com.genie.core.ContextCapture.Context;
import com.genie.util.Database;
import org.junit.jupiter.api.*;

import java.io.File;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ContextCapture functionality
 * 
 * Note: Tests that call captureNow() trigger actual system calls (screencapture, AppleScript)
 * which may crash in sandboxed/headless environments. These are marked as "may be skipped".
 * 
 * Tests for Context data class methods don't require system access.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContextCaptureTest {
    
    @BeforeAll
    void setup() {
        Database.initialize();
    }
    
    // ==================== Context Data Class Tests (no system access needed) ====================
    
    @Test
    @Order(1)
    @DisplayName("getFormattedTime returns readable string")
    void getFormattedTimeReturnsString() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        
        String formatted = ctx.getFormattedTime();
        
        assertThat(formatted).isNotNull();
        assertThat(formatted).isNotEmpty();
        // Should contain time-like characters (comma from date format)
        assertThat(formatted).contains(",");
    }
    
    @Test
    @Order(2)
    @DisplayName("getWindowCount returns correct count")
    void getWindowCountWorks() {
        Context ctx = new Context();
        
        // Empty array
        ctx.windowStack = "[]";
        assertThat(ctx.getWindowCount()).isEqualTo(0);
        
        // One window
        ctx.windowStack = "[{\"app\":\"Chrome\",\"title\":\"Test\"}]";
        assertThat(ctx.getWindowCount()).isEqualTo(1);
        
        // Two windows
        ctx.windowStack = "[{\"app\":\"Chrome\",\"title\":\"Test\"},{\"app\":\"Cursor\",\"title\":\"Code\"}]";
        assertThat(ctx.getWindowCount()).isEqualTo(2);
    }
    
    @Test
    @Order(3)
    @DisplayName("getTabCount returns correct count")
    void getTabCountWorks() {
        Context ctx = new Context();
        
        // Empty array
        ctx.browserTabs = "[]";
        assertThat(ctx.getTabCount()).isEqualTo(0);
        
        // One tab
        ctx.browserTabs = "[{\"browser\":\"Chrome\",\"url\":\"https://google.com\",\"title\":\"Google\"}]";
        assertThat(ctx.getTabCount()).isEqualTo(1);
    }
    
    @Test
    @Order(4)
    @DisplayName("getScreenshotFile returns null for null path")
    void getScreenshotFileNullPath() {
        Context ctx = new Context();
        ctx.screenshotPath = null;
        
        assertThat(ctx.getScreenshotFile()).isNull();
    }
    
    @Test
    @Order(5)
    @DisplayName("getScreenshotFile returns null for nonexistent file")
    void getScreenshotFileNonexistent() {
        Context ctx = new Context();
        ctx.screenshotPath = "/nonexistent/path/screenshot.png";
        
        assertThat(ctx.getScreenshotFile()).isNull();
    }
    
    @Test
    @Order(6)
    @DisplayName("Context toString is readable")
    void toStringIsReadable() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.activeWindow = "Test Window";
        ctx.windowStack = "[{\"app\":\"Test\",\"title\":\"Window\"}]";
        ctx.browserTabs = "[]";
        
        String str = ctx.toString();
        assertThat(str).contains("Context[");
        assertThat(str).contains("window=");
    }
    
    @Test
    @Order(7)
    @DisplayName("Context fields can be set and retrieved")
    void contextFieldsWork() {
        Context ctx = new Context();
        ctx.id = 123;
        ctx.timestamp = LocalDateTime.of(2025, 12, 20, 12, 0, 0);
        ctx.clipboardContent = "Test clipboard";
        ctx.activeWindow = "Test Window";
        ctx.windowStack = "[{\"app\":\"Chrome\",\"title\":\"Test\"}]";
        ctx.browserTabs = "[{\"browser\":\"Safari\",\"url\":\"https://test.com\",\"title\":\"Test\"}]";
        ctx.screenshotPath = "/path/to/screenshot.png";
        ctx.aiSummary = "AI generated summary";
        ctx.userNote = "User note";
        
        assertThat(ctx.id).isEqualTo(123);
        assertThat(ctx.timestamp.getYear()).isEqualTo(2025);
        assertThat(ctx.clipboardContent).isEqualTo("Test clipboard");
        assertThat(ctx.activeWindow).isEqualTo("Test Window");
        assertThat(ctx.windowStack).contains("Chrome");
        assertThat(ctx.browserTabs).contains("Safari");
        assertThat(ctx.screenshotPath).contains("screenshot");
        assertThat(ctx.aiSummary).isEqualTo("AI generated summary");
        assertThat(ctx.userNote).isEqualTo("User note");
    }
    
    // ==================== captureNowWithScreenshot tests (bypasses system screenshot) ====================
    
    @Test
    @Order(10)
    @DisplayName("captureNowWithScreenshot uses provided screenshot path")
    void captureWithProvidedScreenshot() {
        String fakePath = "/tmp/test_screenshot.png";
        Context ctx = ContextCapture.captureNowWithScreenshot(fakePath);
        
        assertThat(ctx).isNotNull();
        assertThat(ctx.screenshotPath).isEqualTo(fakePath);
        assertThat(ctx.timestamp).isNotNull();
        assertThat(ctx.id).isGreaterThan(0); // Should be saved to DB
    }
    
    @Test
    @Order(11)
    @DisplayName("captureNowWithScreenshot captures window info")
    void captureWithScreenshotGetsWindowInfo() {
        Context ctx = ContextCapture.captureNowWithScreenshot("/tmp/fake.png");
        
        // Should still capture other context info
        assertThat(ctx.activeWindow).isNotNull();
        assertThat(ctx.windowStack).isNotNull();
        assertThat(ctx.browserTabs).isNotNull();
    }
    
    @Test
    @Order(12)
    @DisplayName("captureNowWithScreenshot with null uses auto-capture")
    void captureWithNullScreenshotTriggersCapture() {
        // This test just verifies it doesn't throw
        // In sandboxed environment, the actual screencapture may fail gracefully
        assertThatCode(() -> ContextCapture.captureNowWithScreenshot(null))
            .doesNotThrowAnyException();
    }
}
