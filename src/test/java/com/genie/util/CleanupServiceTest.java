package com.genie.util;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CleanupService
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CleanupServiceTest {
    
    @BeforeAll
    void setup() {
        Database.initialize();
    }
    
    @Test
    @Order(1)
    @DisplayName("CleanupService is a singleton")
    void isSingleton() {
        CleanupService instance1 = CleanupService.getInstance();
        CleanupService instance2 = CleanupService.getInstance();
        
        assertThat(instance1).isSameAs(instance2);
    }
    
    @Test
    @Order(2)
    @DisplayName("CleanupService starts without error")
    void startsWithoutError() {
        CleanupService service = CleanupService.getInstance();
        assertThatCode(service::start).doesNotThrowAnyException();
    }
    
    @Test
    @Order(3)
    @DisplayName("CleanupService stops without error")
    void stopsWithoutError() {
        CleanupService service = CleanupService.getInstance();
        service.start();
        
        assertThatCode(service::stop).doesNotThrowAnyException();
    }
    
    @Test
    @Order(4)
    @DisplayName("performCleanup runs without error")
    void cleanupRunsWithoutError() {
        CleanupService service = CleanupService.getInstance();
        assertThatCode(service::performCleanup).doesNotThrowAnyException();
    }
    
    @Test
    @Order(5)
    @DisplayName("getDiskUsage returns valid stats")
    void getDiskUsageReturnsStats() {
        CleanupService service = CleanupService.getInstance();
        CleanupService.DiskUsageStats stats = service.getDiskUsage();
        
        assertThat(stats).isNotNull();
        assertThat(stats.screenshotCount).isGreaterThanOrEqualTo(0);
        assertThat(stats.screenshotSizeBytes).isGreaterThanOrEqualTo(0);
        assertThat(stats.databaseSizeBytes).isGreaterThanOrEqualTo(0);
    }
    
    @Test
    @Order(6)
    @DisplayName("DiskUsageStats formats bytes correctly")
    void formatsBytes() {
        CleanupService.DiskUsageStats stats = new CleanupService.DiskUsageStats();
        
        stats.screenshotSizeBytes = 500;
        assertThat(stats.getScreenshotSizeFormatted()).isEqualTo("500 B");
        
        stats.screenshotSizeBytes = 1500;
        assertThat(stats.getScreenshotSizeFormatted()).contains("KB");
        
        stats.screenshotSizeBytes = 2 * 1024 * 1024;
        assertThat(stats.getScreenshotSizeFormatted()).contains("MB");
    }
    
    @Test
    @Order(7)
    @DisplayName("Multiple start calls are safe")
    void multipleStartsSafe() {
        CleanupService service = CleanupService.getInstance();
        
        assertThatCode(() -> {
            service.start();
            service.start();
            service.start();
        }).doesNotThrowAnyException();
        
        service.stop();
    }
    
    @Test
    @Order(8)
    @DisplayName("Multiple stop calls are safe")
    void multipleStopsSafe() {
        CleanupService service = CleanupService.getInstance();
        service.start();
        
        assertThatCode(() -> {
            service.stop();
            service.stop();
            service.stop();
        }).doesNotThrowAnyException();
    }
}

