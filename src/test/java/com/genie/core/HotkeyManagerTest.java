package com.genie.core;

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for HotkeyManager
 * 
 * Note: Actual hotkey registration requires native hooks which may not work
 * in all CI environments. These tests focus on callback management.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HotkeyManagerTest {
    
    private HotkeyManager hotkeyManager;
    
    @BeforeEach
    void setup() {
        hotkeyManager = new HotkeyManager();
    }
    
    @AfterEach
    void cleanup() {
        if (hotkeyManager != null) {
            try {
                hotkeyManager.unregister();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }
    
    @Test
    @Order(1)
    @DisplayName("HotkeyManager instantiates")
    void instantiates() {
        assertThat(hotkeyManager).isNotNull();
    }
    
    @Test
    @Order(2)
    @DisplayName("Can set save context callback")
    void canSetSaveContextCallback() {
        AtomicBoolean called = new AtomicBoolean(false);
        
        assertThatCode(() -> {
            hotkeyManager.setOnSaveContext(() -> called.set(true));
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(3)
    @DisplayName("Can set make wish callback")
    void canSetMakeWishCallback() {
        AtomicBoolean called = new AtomicBoolean(false);
        
        assertThatCode(() -> {
            hotkeyManager.setOnMakeWish(() -> called.set(true));
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(4)
    @DisplayName("Can set both callbacks")
    void canSetBothCallbacks() {
        AtomicInteger counter = new AtomicInteger(0);
        
        hotkeyManager.setOnSaveContext(counter::incrementAndGet);
        hotkeyManager.setOnMakeWish(counter::incrementAndGet);
        
        // Just verifying no exception - callbacks won't be called without key events
        assertThat(counter.get()).isEqualTo(0);
    }
    
    @Test
    @Order(5)
    @DisplayName("Unregister when not registered is safe")
    void unregisterWhenNotRegisteredIsSafe() {
        // Create a fresh manager that was never registered
        HotkeyManager fresh = new HotkeyManager();
        
        assertThatCode(fresh::unregister).doesNotThrowAnyException();
    }
    
    @Test
    @Order(6)
    @DisplayName("Register does not throw")
    void registerDoesNotThrow() {
        // This may fail in headless/CI environments but shouldn't throw
        // uncaught exceptions
        assertThatCode(hotkeyManager::register).doesNotThrowAnyException();
    }
    
    @Test
    @Order(7)
    @DisplayName("Multiple unregister calls are safe")
    void multipleUnregisterSafe() {
        hotkeyManager.register();
        
        assertThatCode(() -> {
            hotkeyManager.unregister();
            hotkeyManager.unregister();
            hotkeyManager.unregister();
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(8)
    @DisplayName("Can re-register after unregister")
    void canReRegister() {
        hotkeyManager.register();
        hotkeyManager.unregister();
        
        assertThatCode(hotkeyManager::register).doesNotThrowAnyException();
    }
}

