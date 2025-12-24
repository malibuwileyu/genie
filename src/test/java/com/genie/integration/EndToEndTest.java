package com.genie.integration;

import com.genie.ai.OpenAIClient;
import com.genie.core.ContextCapture;
import com.genie.core.ContextCapture.Context;
import com.genie.util.Config;
import com.genie.util.Database;
import com.genie.voice.VoiceManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end integration tests
 * 
 * Tests the complete flow from user action to database storage
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {
    
    @BeforeAll
    void setup() {
        Config.load();
        Database.initialize();
    }
    
    // Don't close - other tests may need it and it auto-reconnects anyway
    
    // ==================== Context Flow Tests ====================
    
    @Test
    @Order(1)
    @DisplayName("[E2E] Context capture → save → retrieve flow")
    void contextCaptureAndRetrieveFlow() {
        // 1. Capture context
        Context captured = ContextCapture.captureNow();
        assertThat(captured).isNotNull();
        assertThat(captured.id).isGreaterThan(0);
        
        // 2. Retrieve from database
        List<Context> recent = Database.getRecentContexts(10);
        
        // 3. Verify captured context is in list
        Context found = recent.stream()
            .filter(c -> c.id == captured.id)
            .findFirst()
            .orElse(null);
        
        assertThat(found).isNotNull();
        assertThat(found.timestamp).isEqualTo(captured.timestamp);
    }
    
    // ==================== Wish Flow Tests ====================
    
    @Test
    @Order(10)
    @DisplayName("[E2E] Wish save → retrieve flow")
    void wishSaveAndRetrieveFlow() {
        String wishText = "How do databases handle concurrent writes? " + System.currentTimeMillis();
        
        // 1. Save wish
        long wishId = Database.saveWish(wishText);
        assertThat(wishId).isGreaterThan(0);
        
        // 2. Retrieve wishes
        List<Database.Wish> wishes = Database.getWishes(false);
        
        // 3. Find our wish
        Database.Wish found = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(found).isNotNull();
        assertThat(found.wishText).isEqualTo(wishText);
        assertThat(found.isResearched).isFalse();
    }
    
    @Test
    @Order(11)
    @DisplayName("[E2E] Wish save → research → retrieve flow")
    void wishWithResearchFlow() {
        String wishText = "What is the CAP theorem? " + System.currentTimeMillis();
        String researchArticle = "# CAP Theorem\n\nThe CAP theorem states that...";
        
        // 1. Save wish
        long wishId = Database.saveWish(wishText);
        assertThat(wishId).isGreaterThan(0);
        
        // 2. Add research
        Database.updateWishResearch(wishId, researchArticle);
        
        // 3. Retrieve and verify
        List<Database.Wish> wishes = Database.getWishes(false);
        Database.Wish found = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(found).isNotNull();
        assertThat(found.isResearched).isTrue();
        assertThat(found.researchArticle).isEqualTo(researchArticle);
    }
    
    // ==================== API Integration Tests ====================
    
    static boolean hasValidApiKey() {
        Config.load();
        String key = Config.getOpenAiApiKey();
        return key != null && key.startsWith("sk-") && key.length() > 40;
    }
    
    @Test
    @Order(20)
    @EnabledIf("hasValidApiKey")
    @DisplayName("[E2E] Wish → AI research → save complete flow")
    void wishToResearchCompleteFlow() throws Exception {
        String wishText = "What is memoization in programming?";
        
        // 1. Save wish
        long wishId = Database.saveWish(wishText);
        assertThat(wishId).isGreaterThan(0);
        
        // 2. Generate research via AI
        OpenAIClient client = new OpenAIClient();
        String article = client.generateResearchArticle(wishText);
        
        // 3. Update wish with research
        Database.updateWishResearch(wishId, article);
        
        // 4. Verify complete flow
        List<Database.Wish> wishes = Database.getWishes(false);
        Database.Wish found = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(found).isNotNull();
        assertThat(found.isResearched).isTrue();
        assertThat(found.researchArticle).isNotEmpty();
        assertThat(found.researchArticle.length()).isGreaterThan(100);
    }
    
    // ==================== Voice Manager Tests ====================
    
    @Test
    @Order(30)
    @DisplayName("[E2E] VoiceManager initialization and mode switching")
    void voiceManagerModeSwitch() {
        VoiceManager manager = VoiceManager.getInstance();
        
        // Initialize
        manager.initialize();
        
        // Switch modes
        manager.setAlwaysListeningEnabled(false);
        assertThat(manager.isAlwaysListeningEnabled()).isFalse();
        
        manager.setAlwaysListeningEnabled(true);
        assertThat(manager.isAlwaysListeningEnabled()).isTrue();
        
        // Clean up - disable listening
        manager.setAlwaysListeningEnabled(false);
    }
    
    @Test
    @Order(31)
    @DisplayName("[E2E] VoiceManager callback integration")
    void voiceManagerCallbackIntegration() {
        VoiceManager manager = VoiceManager.getInstance();
        
        AtomicReference<String> receivedWish = new AtomicReference<>();
        
        manager.setOnWishRecognized(receivedWish::set);
        
        // Callback is set - this test verifies setup doesn't throw
        assertThat(receivedWish.get()).isNull(); // Not called yet
    }
    
    // ==================== Config Persistence Tests ====================
    
    @Test
    @Order(40)
    @DisplayName("[E2E] Config changes persist across load/save")
    void configPersistence() {
        // Set a value
        boolean original = Config.isAlwaysListeningEnabled();
        Config.setAlwaysListeningEnabled(!original);
        Config.save();
        
        // Reload config
        Config.load();
        
        // Verify persisted
        assertThat(Config.isAlwaysListeningEnabled()).isEqualTo(!original);
        
        // Restore original
        Config.setAlwaysListeningEnabled(original);
        Config.save();
    }
    
    // ==================== Error Handling Tests ====================
    
    @Test
    @Order(50)
    @DisplayName("[E2E] System handles empty wish")
    void handlesEmptyWish() {
        // Empty wish still saves (validation should be in UI)
        long wishId = Database.saveWish("");
        assertThat(wishId).isGreaterThan(0);
    }
    
    @Test
    @Order(51)
    @DisplayName("[E2E] System handles very long wish text")
    void handlesLongWishText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longText.append("This is a long wish text. ");
        }
        
        long wishId = Database.saveWish(longText.toString());
        assertThat(wishId).isGreaterThan(0);
        
        // Verify it was saved completely
        List<Database.Wish> wishes = Database.getWishes(false);
        Database.Wish found = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(found).isNotNull();
        assertThat(found.wishText.length()).isEqualTo(longText.length());
    }
    
    @Test
    @Order(52)
    @DisplayName("[E2E] System handles special characters in wish")
    void handlesSpecialCharacters() {
        String specialWish = "How do I handle SQL injection? DROP TABLE; --' OR 1=1;";
        
        long wishId = Database.saveWish(specialWish);
        assertThat(wishId).isGreaterThan(0);
        
        List<Database.Wish> wishes = Database.getWishes(false);
        Database.Wish found = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(found).isNotNull();
        assertThat(found.wishText).isEqualTo(specialWish);
    }
}
