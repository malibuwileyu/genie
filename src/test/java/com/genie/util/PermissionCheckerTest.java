package com.genie.util;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for PermissionChecker
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PermissionCheckerTest {
    
    @Test
    @Order(1)
    @DisplayName("isMacOS returns correct value")
    void isMacOSReturnsCorrectValue() {
        String os = System.getProperty("os.name").toLowerCase();
        boolean expected = os.contains("mac");
        
        assertThat(PermissionChecker.isMacOS()).isEqualTo(expected);
    }
    
    @Test
    @Order(2)
    @DisplayName("hasMicrophonePermission returns boolean")
    void hasMicrophonePermissionReturnsBoolean() {
        // Just verify it doesn't throw - actual permission may vary
        assertThatCode(() -> {
            boolean result = PermissionChecker.hasMicrophonePermission();
            assertThat(result).isIn(true, false);
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(3)
    @DisplayName("hasAccessibilityPermission returns boolean on macOS")
    void hasAccessibilityPermissionReturnsBoolean() {
        if (!PermissionChecker.isMacOS()) {
            // On non-macOS, always returns true
            assertThat(PermissionChecker.hasAccessibilityPermission()).isTrue();
            return;
        }
        
        // On macOS, returns true or false based on actual permission
        assertThatCode(() -> {
            boolean result = PermissionChecker.hasAccessibilityPermission();
            assertThat(result).isIn(true, false);
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(4)
    @DisplayName("hasScreenRecordingPermission returns boolean on macOS")
    void hasScreenRecordingPermissionReturnsBoolean() {
        if (!PermissionChecker.isMacOS()) {
            // On non-macOS, always returns true
            assertThat(PermissionChecker.hasScreenRecordingPermission()).isTrue();
            return;
        }
        
        // On macOS, returns true or false based on actual permission
        assertThatCode(() -> {
            boolean result = PermissionChecker.hasScreenRecordingPermission();
            assertThat(result).isIn(true, false);
        }).doesNotThrowAnyException();
    }
    
    @Test
    @Order(5)
    @DisplayName("checkAll returns PermissionStatus object")
    void checkAllReturnsStatus() {
        PermissionChecker.PermissionStatus status = PermissionChecker.checkAll();
        
        assertThat(status).isNotNull();
        assertThat(status.grantedCount()).isBetween(0, 3);
    }
    
    @Test
    @Order(6)
    @DisplayName("PermissionStatus allGranted works correctly")
    void allGrantedWorks() {
        // Test with mock values
        PermissionChecker.PermissionStatus allTrue = 
            new PermissionChecker.PermissionStatus(true, true, true);
        assertThat(allTrue.allGranted()).isTrue();
        assertThat(allTrue.grantedCount()).isEqualTo(3);
        
        PermissionChecker.PermissionStatus someFalse = 
            new PermissionChecker.PermissionStatus(true, false, true);
        assertThat(someFalse.allGranted()).isFalse();
        assertThat(someFalse.grantedCount()).isEqualTo(2);
        
        PermissionChecker.PermissionStatus allFalse = 
            new PermissionChecker.PermissionStatus(false, false, false);
        assertThat(allFalse.allGranted()).isFalse();
        assertThat(allFalse.grantedCount()).isEqualTo(0);
    }
    
    @Test
    @Order(7)
    @DisplayName("PermissionStatus toString is readable")
    void toStringIsReadable() {
        PermissionChecker.PermissionStatus status = 
            new PermissionChecker.PermissionStatus(true, false, true);
        
        String str = status.toString();
        assertThat(str).contains("Permissions[");
        assertThat(str).contains("mic=true");
        assertThat(str).contains("accessibility=false");
        assertThat(str).contains("screenRec=true");
    }
    
    @Test
    @Order(8)
    @DisplayName("openMicrophoneSettings does not throw")
    void openMicrophoneSettingsDoesNotThrow() {
        // Just verify it doesn't throw - won't actually open settings in test
        if (!PermissionChecker.isMacOS()) {
            assertThatCode(PermissionChecker::openMicrophoneSettings).doesNotThrowAnyException();
        }
        // On macOS, we skip to avoid actually opening System Preferences
    }
    
    @Test
    @Order(9)
    @DisplayName("openAccessibilitySettings does not throw")
    void openAccessibilitySettingsDoesNotThrow() {
        if (!PermissionChecker.isMacOS()) {
            assertThatCode(PermissionChecker::openAccessibilitySettings).doesNotThrowAnyException();
        }
    }
    
    @Test
    @Order(10)
    @DisplayName("openScreenRecordingSettings does not throw")
    void openScreenRecordingSettingsDoesNotThrow() {
        if (!PermissionChecker.isMacOS()) {
            assertThatCode(PermissionChecker::openScreenRecordingSettings).doesNotThrowAnyException();
        }
    }
}

