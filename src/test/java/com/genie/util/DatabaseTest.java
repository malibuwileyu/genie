package com.genie.util;

import com.genie.core.ContextCapture.Context;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Database operations
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseTest {
    
    @BeforeAll
    void setup() {
        // Initialize database (will auto-reconnect if needed)
        Database.initialize();
    }
    
    // Don't close - other tests may need it and it auto-reconnects anyway
    
    // ==================== Context Tests ====================
    
    @Test
    @Order(1)
    @DisplayName("Can save a context")
    void canSaveContext() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.clipboardContent = "Test clipboard content";
        ctx.activeWindow = "Test Window - Chrome";
        ctx.userNote = "Test note";
        
        assertThatCode(() -> Database.saveContext(ctx)).doesNotThrowAnyException();
        // ID should be set after save
        assertThat(ctx.id).isGreaterThan(0);
    }
    
    @Test
    @Order(2)
    @DisplayName("Can retrieve recent contexts")
    void canGetRecentContexts() {
        // Save a few contexts first
        for (int i = 0; i < 3; i++) {
            Context ctx = new Context();
            ctx.timestamp = LocalDateTime.now();
            ctx.clipboardContent = "Content " + i;
            ctx.activeWindow = "Window " + i;
            Database.saveContext(ctx);
        }
        
        List<Context> contexts = Database.getRecentContexts(5);
        
        assertThat(contexts).isNotEmpty();
        assertThat(contexts.size()).isLessThanOrEqualTo(5);
    }
    
    @Test
    @Order(3)
    @DisplayName("Context retrieval respects limit")
    void contextRetrievalRespectsLimit() {
        List<Context> contexts = Database.getRecentContexts(2);
        assertThat(contexts.size()).isLessThanOrEqualTo(2);
    }
    
    @Test
    @Order(4)
    @DisplayName("Contexts are ordered by timestamp descending")
    void contextsAreOrdered() {
        List<Context> contexts = Database.getRecentContexts(10);
        
        if (contexts.size() > 1) {
            for (int i = 0; i < contexts.size() - 1; i++) {
                assertThat(contexts.get(i).timestamp)
                    .isAfterOrEqualTo(contexts.get(i + 1).timestamp);
            }
        }
    }
    
    @Test
    @Order(5)
    @DisplayName("Context with null values saves correctly")
    void contextWithNullValuesSaves() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.clipboardContent = null;
        ctx.activeWindow = null;
        ctx.userNote = null;
        
        assertThatCode(() -> Database.saveContext(ctx)).doesNotThrowAnyException();
        assertThat(ctx.id).isGreaterThan(0);
    }
    
    // ==================== Wish Tests ====================
    
    @Test
    @Order(10)
    @DisplayName("Can save a wish")
    void canSaveWish() {
        long wishId = Database.saveWish("How do neural networks work?");
        assertThat(wishId).isGreaterThan(0);
    }
    
    @Test
    @Order(11)
    @DisplayName("Can update wish with research article")
    void canUpdateWishResearch() {
        long wishId = Database.saveWish("What is quantum computing?");
        assertThat(wishId).isGreaterThan(0);
        
        String article = "# Quantum Computing\n\nQuantum computing uses qubits...";
        assertThatCode(() -> Database.updateWishResearch(wishId, article))
            .doesNotThrowAnyException();
        
        // Verify it was updated
        List<Database.Wish> wishes = Database.getWishes(false);
        Database.Wish updatedWish = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(updatedWish).isNotNull();
        assertThat(updatedWish.researchArticle).isEqualTo(article);
        assertThat(updatedWish.isResearched).isTrue();
    }
    
    @Test
    @Order(12)
    @DisplayName("Can get all wishes")
    void canGetAllWishes() {
        List<Database.Wish> wishes = Database.getWishes(false);
        assertThat(wishes).isNotEmpty();
    }
    
    @Test
    @Order(13)
    @DisplayName("Can filter to only unresearched wishes")
    void canFilterUnresearchedWishes() {
        // Save a wish without research
        long unresearchedId = Database.saveWish("Unresearched wish " + System.currentTimeMillis());
        assertThat(unresearchedId).isGreaterThan(0);
        
        List<Database.Wish> unresearched = Database.getWishes(true);
        
        // All returned wishes should be unresearched
        assertThat(unresearched).allMatch(w -> !w.isResearched);
    }
    
    @Test
    @Order(14)
    @DisplayName("Wish text is preserved correctly")
    void wishTextIsPreserved() {
        String specialText = "How does UTF-8 work? 日本語 émojis 🎉";
        long wishId = Database.saveWish(specialText);
        assertThat(wishId).isGreaterThan(0);
        
        List<Database.Wish> wishes = Database.getWishes(false);
        Database.Wish saved = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(saved).isNotNull();
        assertThat(saved.wishText).isEqualTo(specialText);
    }
    
    @Test
    @Order(15)
    @DisplayName("Wishes are ordered by timestamp descending")
    void wishesAreOrderedCorrectly() {
        List<Database.Wish> wishes = Database.getWishes(false);
        
        if (wishes.size() > 1) {
            for (int i = 0; i < wishes.size() - 1; i++) {
                assertThat(wishes.get(i).timestamp)
                    .isAfterOrEqualTo(wishes.get(i + 1).timestamp);
            }
        }
    }
    
    // ==================== New Method Tests ====================
    
    @Test
    @Order(20)
    @DisplayName("Can update wish text")
    void canUpdateWishText() {
        long wishId = Database.saveWish("Original wish text");
        assertThat(wishId).isGreaterThan(0);
        
        String newText = "Updated wish text";
        Database.updateWishText(wishId, newText);
        
        // Verify update
        List<Database.Wish> wishes = Database.getWishes(false);
        Database.Wish updated = wishes.stream()
            .filter(w -> w.id == wishId)
            .findFirst()
            .orElse(null);
        
        assertThat(updated).isNotNull();
        assertThat(updated.wishText).isEqualTo(newText);
    }
    
    @Test
    @Order(21)
    @DisplayName("Can get context by ID")
    void canGetContextById() {
        // First save a context
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.clipboardContent = "Test for getById";
        ctx.activeWindow = "Test Window";
        Database.saveContext(ctx);
        
        assertThat(ctx.id).isGreaterThan(0);
        
        // Now retrieve by ID
        Context retrieved = Database.getContextById(ctx.id);
        
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id).isEqualTo(ctx.id);
        assertThat(retrieved.clipboardContent).isEqualTo("Test for getById");
    }
    
    @Test
    @Order(22)
    @DisplayName("getContextById returns null for nonexistent ID")
    void getContextByIdReturnsNullForNonexistent() {
        Context result = Database.getContextById(-99999);
        assertThat(result).isNull();
    }
    
    @Test
    @Order(23)
    @DisplayName("Can update context AI summary")
    void canUpdateContextAiSummary() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.activeWindow = "Test";
        Database.saveContext(ctx);
        
        String summary = "AI-generated summary of the context";
        Database.updateContextAiSummary(ctx.id, summary);
        
        // Verify
        Context retrieved = Database.getContextById(ctx.id);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.aiSummary).isEqualTo(summary);
    }
    
    @Test
    @Order(24)
    @DisplayName("getContextCount returns correct count")
    void getContextCountWorks() {
        int initialCount = Database.getContextCount();
        
        // Add a context
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.activeWindow = "Count Test";
        Database.saveContext(ctx);
        
        int newCount = Database.getContextCount();
        assertThat(newCount).isEqualTo(initialCount + 1);
    }
    
    @Test
    @Order(25)
    @DisplayName("getWishCount returns correct count")
    void getWishCountWorks() {
        int initialCount = Database.getWishCount();
        
        // Add a wish
        Database.saveWish("Count test wish " + System.currentTimeMillis());
        
        int newCount = Database.getWishCount();
        assertThat(newCount).isEqualTo(initialCount + 1);
    }
    
    @Test
    @Order(26)
    @DisplayName("vacuum runs without error")
    void vacuumRunsWithoutError() {
        assertThatCode(Database::vacuum).doesNotThrowAnyException();
    }
    
    @Test
    @Order(27)
    @DisplayName("isReady returns true after initialization")
    void isReadyReturnsTrue() {
        assertThat(Database.isReady()).isTrue();
    }
    
    @Test
    @Order(28)
    @DisplayName("Context with new fields saves correctly")
    void contextWithNewFieldsSaves() {
        Context ctx = new Context();
        ctx.timestamp = LocalDateTime.now();
        ctx.clipboardContent = "Test";
        ctx.activeWindow = "Test Window";
        ctx.windowStack = "[{\"app\":\"Chrome\",\"title\":\"Test\"}]";
        ctx.browserTabs = "[{\"browser\":\"Chrome\",\"url\":\"https://test.com\",\"title\":\"Test\"}]";
        ctx.screenshotPath = "/tmp/test_screenshot.png";
        ctx.aiSummary = "AI generated summary";
        ctx.userNote = "User note";
        
        Database.saveContext(ctx);
        
        assertThat(ctx.id).isGreaterThan(0);
        
        // Retrieve and verify
        Context retrieved = Database.getContextById(ctx.id);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.windowStack).isEqualTo(ctx.windowStack);
        assertThat(retrieved.browserTabs).isEqualTo(ctx.browserTabs);
        assertThat(retrieved.screenshotPath).isEqualTo(ctx.screenshotPath);
        assertThat(retrieved.aiSummary).isEqualTo(ctx.aiSummary);
    }
}
