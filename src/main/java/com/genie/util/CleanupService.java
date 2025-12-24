package com.genie.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Memory/disk cleanup service for long-running sessions
 * 
 * Handles:
 * - Old screenshot cleanup (>7 days)
 * - Database optimization
 * - Temp file cleanup
 */
public class CleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(CleanupService.class);
    
    // Configuration
    private static final int SCREENSHOT_RETENTION_DAYS = 7;
    private static final int CLEANUP_INTERVAL_HOURS = 6;
    
    private static CleanupService instance;
    private ScheduledExecutorService scheduler;
    
    private CleanupService() {
        // Private constructor for singleton
    }
    
    public static synchronized CleanupService getInstance() {
        if (instance == null) {
            instance = new CleanupService();
        }
        return instance;
    }
    
    /**
     * Start the cleanup service
     */
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GenieCleanupService");
            t.setDaemon(true);
            return t;
        });
        
        // Run immediately on startup, then every 6 hours
        scheduler.scheduleAtFixedRate(
            this::performCleanup,
            0,  // Initial delay
            CLEANUP_INTERVAL_HOURS,
            TimeUnit.HOURS
        );
        
        logger.info("Cleanup service started (runs every {} hours)", CLEANUP_INTERVAL_HOURS);
    }
    
    /**
     * Stop the cleanup service
     */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }
        logger.info("Cleanup service stopped");
    }
    
    /**
     * Perform all cleanup tasks
     */
    public void performCleanup() {
        try {
            logger.info("Starting cleanup...");
            
            int screenshotsDeleted = cleanupOldScreenshots();
            int tempFilesDeleted = cleanupTempFiles();
            
            // Compact database if needed
            optimizeDatabase();
            
            logger.info("Cleanup complete: {} screenshots, {} temp files removed", 
                screenshotsDeleted, tempFilesDeleted);
                
        } catch (Exception e) {
            logger.error("Cleanup failed", e);
        }
    }
    
    /**
     * Delete screenshots older than retention period
     */
    private int cleanupOldScreenshots() {
        Path screenshotsDir = getScreenshotsDirectory();
        
        if (!Files.exists(screenshotsDir)) {
            return 0;
        }
        
        int deleted = 0;
        Instant cutoff = Instant.now().minus(SCREENSHOT_RETENTION_DAYS, ChronoUnit.DAYS);
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(screenshotsDir, "*.png")) {
            for (Path file : stream) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attrs.creationTime().toInstant().isBefore(cutoff)) {
                        Files.delete(file);
                        deleted++;
                        logger.debug("Deleted old screenshot: {}", file.getFileName());
                    }
                } catch (IOException e) {
                    logger.warn("Failed to delete screenshot {}: {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to scan screenshots directory", e);
        }
        
        if (deleted > 0) {
            logger.info("Cleaned up {} old screenshots (>{} days)", deleted, SCREENSHOT_RETENTION_DAYS);
        }
        
        return deleted;
    }
    
    /**
     * Clean up temp files from voice recording
     */
    private int cleanupTempFiles() {
        int deleted = 0;
        
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, "genie_audio_*.wav")) {
                for (Path file : stream) {
                    try {
                        // Delete if older than 1 hour
                        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                        if (attrs.creationTime().toInstant().isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
                            Files.delete(file);
                            deleted++;
                        }
                    } catch (IOException e) {
                        // Ignore - file might be in use
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to scan temp directory", e);
        }
        
        return deleted;
    }
    
    /**
     * Optimize database (vacuum if needed)
     */
    private void optimizeDatabase() {
        try {
            // Only vacuum if DB is larger than 10MB
            Path dbPath = getDatabasePath();
            if (Files.exists(dbPath)) {
                long sizeBytes = Files.size(dbPath);
                if (sizeBytes > 10 * 1024 * 1024) { // 10MB
                    logger.info("Database size is {}MB, running VACUUM", sizeBytes / 1024 / 1024);
                    Database.vacuum();
                }
            }
        } catch (Exception e) {
            logger.warn("Database optimization failed", e);
        }
    }
    
    /**
     * Get disk usage statistics
     */
    public DiskUsageStats getDiskUsage() {
        DiskUsageStats stats = new DiskUsageStats();
        
        try {
            // Screenshots
            Path screenshotsDir = getScreenshotsDirectory();
            if (Files.exists(screenshotsDir)) {
                stats.screenshotCount = (int) Files.list(screenshotsDir)
                    .filter(p -> p.toString().endsWith(".png"))
                    .count();
                stats.screenshotSizeBytes = Files.walk(screenshotsDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
                    })
                    .sum();
            }
            
            // Database
            Path dbPath = getDatabasePath();
            if (Files.exists(dbPath)) {
                stats.databaseSizeBytes = Files.size(dbPath);
            }
        } catch (IOException e) {
            logger.warn("Failed to calculate disk usage", e);
        }
        
        return stats;
    }
    
    private Path getScreenshotsDirectory() {
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
    
    private Path getDatabasePath() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "Genie", "genie.db");
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Paths.get(appData, "Genie", "genie.db");
        } else {
            return Paths.get(home, ".config", "genie", "genie.db");
        }
    }
    
    /**
     * Disk usage statistics
     */
    public static class DiskUsageStats {
        public int screenshotCount;
        public long screenshotSizeBytes;
        public long databaseSizeBytes;
        
        public String getScreenshotSizeFormatted() {
            return formatBytes(screenshotSizeBytes);
        }
        
        public String getDatabaseSizeFormatted() {
            return formatBytes(databaseSizeBytes);
        }
        
        public String getTotalSizeFormatted() {
            return formatBytes(screenshotSizeBytes + databaseSizeBytes);
        }
        
        private static String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        }
    }
}

