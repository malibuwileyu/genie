package com.genie.util;

import com.genie.core.ContextCapture.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database for storing contexts and wishes
 */
public class Database {
    
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    
    private static Connection connection;
    private static final String DB_NAME = "genie.db";
    
    public static synchronized void initialize() {
        try {
            // Skip if already initialized and connection is valid
            if (connection != null && !connection.isClosed()) {
                return;
            }
            
            // Store in user's app data directory
            Path dbPath = getDbPath();
            Files.createDirectories(dbPath.getParent());
            
            String url = "jdbc:sqlite:" + dbPath.toString();
            connection = DriverManager.getConnection(url);
            
            createTables();
            
            logger.info("Database initialized at {}", dbPath);
        } catch (Exception e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    /**
     * Ensure database is initialized before any operation
     */
    private static void ensureInitialized() {
        try {
            if (connection == null || connection.isClosed()) {
                logger.info("Database connection null or closed, initializing...");
                initialize();
            }
        } catch (SQLException e) {
            logger.warn("SQLException checking connection, re-initializing", e);
            initialize();
        }
    }
    
    /**
     * Check if database is ready
     */
    public static boolean isReady() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    private static Path getDbPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "Genie", DB_NAME);
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Paths.get(appData, "Genie", DB_NAME);
        } else {
            return Paths.get(home, ".config", "genie", DB_NAME);
        }
    }
    
    private static void createTables() throws SQLException {
        String createContexts = """
            CREATE TABLE IF NOT EXISTS contexts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                clipboard_content TEXT,
                active_window TEXT,
                window_stack TEXT,
                browser_tabs TEXT,
                screenshot_path TEXT,
                ai_summary TEXT,
                user_note TEXT
            )
            """;
        
        // Migration: add new columns if they don't exist
        String addWindowStack = "ALTER TABLE contexts ADD COLUMN window_stack TEXT";
        String addBrowserTabs = "ALTER TABLE contexts ADD COLUMN browser_tabs TEXT";
        String addScreenshotPath = "ALTER TABLE contexts ADD COLUMN screenshot_path TEXT";
        String addAiSummary = "ALTER TABLE contexts ADD COLUMN ai_summary TEXT";
        
        String createWishes = """
            CREATE TABLE IF NOT EXISTS wishes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT NOT NULL,
                wish_text TEXT NOT NULL,
                research_article TEXT,
                is_researched INTEGER DEFAULT 0
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createContexts);
            stmt.execute(createWishes);
            
            // Run migrations (ignore errors if columns already exist)
            try { stmt.execute(addWindowStack); } catch (SQLException ignored) {}
            try { stmt.execute(addBrowserTabs); } catch (SQLException ignored) {}
            try { stmt.execute(addScreenshotPath); } catch (SQLException ignored) {}
            try { stmt.execute(addAiSummary); } catch (SQLException ignored) {}
        }
    }
    
    // Context methods
    
    public static void saveContext(Context context) {
        ensureInitialized();
        
        String sql = """
            INSERT INTO contexts (timestamp, clipboard_content, active_window, window_stack, 
                                  browser_tabs, screenshot_path, ai_summary, user_note)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, context.timestamp.toString());
            pstmt.setString(2, context.clipboardContent);
            pstmt.setString(3, context.activeWindow);
            pstmt.setString(4, context.windowStack);
            pstmt.setString(5, context.browserTabs);
            pstmt.setString(6, context.screenshotPath);
            pstmt.setString(7, context.aiSummary);
            pstmt.setString(8, context.userNote);
            pstmt.executeUpdate();
            
            // SQLite: use last_insert_rowid() instead of getGeneratedKeys()
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    context.id = rs.getLong(1);
                }
            }
            logger.debug("Context saved with id={}", context.id);
        } catch (SQLException e) {
            logger.error("Failed to save context: {}", e.getMessage(), e);
        }
    }
    
    public static void updateContextAiSummary(long contextId, String aiSummary) {
        ensureInitialized();
        
        String sql = "UPDATE contexts SET ai_summary = ? WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, aiSummary);
            pstmt.setLong(2, contextId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update context AI summary", e);
        }
    }
    
    public static List<Context> getRecentContexts(int limit) {
        ensureInitialized();
        
        List<Context> contexts = new ArrayList<>();
        String sql = "SELECT * FROM contexts ORDER BY timestamp DESC LIMIT ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Context ctx = new Context();
                ctx.id = rs.getLong("id");
                ctx.timestamp = LocalDateTime.parse(rs.getString("timestamp"));
                ctx.clipboardContent = rs.getString("clipboard_content");
                ctx.activeWindow = rs.getString("active_window");
                ctx.windowStack = rs.getString("window_stack");
                ctx.browserTabs = rs.getString("browser_tabs");
                ctx.screenshotPath = rs.getString("screenshot_path");
                ctx.aiSummary = rs.getString("ai_summary");
                ctx.userNote = rs.getString("user_note");
                contexts.add(ctx);
            }
        } catch (SQLException e) {
            logger.error("Failed to get contexts", e);
        }
        
        return contexts;
    }
    
    public static Context getContextById(long id) {
        ensureInitialized();
        
        String sql = "SELECT * FROM contexts WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Context ctx = new Context();
                ctx.id = rs.getLong("id");
                ctx.timestamp = LocalDateTime.parse(rs.getString("timestamp"));
                ctx.clipboardContent = rs.getString("clipboard_content");
                ctx.activeWindow = rs.getString("active_window");
                ctx.windowStack = rs.getString("window_stack");
                ctx.browserTabs = rs.getString("browser_tabs");
                ctx.screenshotPath = rs.getString("screenshot_path");
                ctx.aiSummary = rs.getString("ai_summary");
                ctx.userNote = rs.getString("user_note");
                return ctx;
            }
        } catch (SQLException e) {
            logger.error("Failed to get context by id", e);
        }
        
        return null;
    }
    
    // Wish methods
    
    public static long saveWish(String wishText) {
        ensureInitialized();
        
        String sql = """
            INSERT INTO wishes (timestamp, wish_text)
            VALUES (?, ?)
            """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, LocalDateTime.now().toString());
            pstmt.setString(2, wishText);
            pstmt.executeUpdate();
            
            // SQLite: use last_insert_rowid() instead of getGeneratedKeys()
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()")) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    logger.debug("Wish saved with id={}", id);
                    return id;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to save wish: {}", e.getMessage(), e);
        }
        return -1;
    }
    
    public static void updateWishResearch(long wishId, String article) {
        ensureInitialized();
        
        String sql = "UPDATE wishes SET research_article = ?, is_researched = 1 WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, article);
            pstmt.setLong(2, wishId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update wish research", e);
        }
    }
    
    public static void updateWishText(long wishId, String newText) {
        ensureInitialized();
        
        String sql = "UPDATE wishes SET wish_text = ? WHERE id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newText);
            pstmt.setLong(2, wishId);
            pstmt.executeUpdate();
            logger.info("Wish {} text updated", wishId);
        } catch (SQLException e) {
            logger.error("Failed to update wish text", e);
        }
    }
    
    public static List<Wish> getWishes(boolean unresearchedOnly) {
        ensureInitialized();
        
        List<Wish> wishes = new ArrayList<>();
        String sql = unresearchedOnly 
            ? "SELECT * FROM wishes WHERE is_researched = 0 ORDER BY timestamp DESC"
            : "SELECT * FROM wishes ORDER BY timestamp DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Wish wish = new Wish();
                wish.id = rs.getLong("id");
                wish.timestamp = LocalDateTime.parse(rs.getString("timestamp"));
                wish.wishText = rs.getString("wish_text");
                wish.researchArticle = rs.getString("research_article");
                wish.isResearched = rs.getInt("is_researched") == 1;
                wishes.add(wish);
            }
        } catch (SQLException e) {
            logger.error("Failed to get wishes", e);
        }
        
        return wishes;
    }
    
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database", e);
        }
    }
    
    /**
     * Run VACUUM to compact the database and free unused space
     */
    public static void vacuum() {
        ensureInitialized();
        
        try (Statement stmt = connection.createStatement()) {
            logger.info("Running database VACUUM...");
            stmt.execute("VACUUM");
            logger.info("Database VACUUM completed");
        } catch (SQLException e) {
            logger.error("Failed to vacuum database", e);
        }
    }
    
    /**
     * Get count of contexts in database
     */
    public static int getContextCount() {
        ensureInitialized();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM contexts")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count contexts", e);
        }
        return 0;
    }
    
    /**
     * Get count of wishes in database
     */
    public static int getWishCount() {
        ensureInitialized();
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM wishes")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Failed to count wishes", e);
        }
        return 0;
    }
    
    /**
     * Wish data class
     */
    public static class Wish {
        public long id;
        public LocalDateTime timestamp;
        public String wishText;
        public String researchArticle;
        public boolean isResearched;
    }
}

