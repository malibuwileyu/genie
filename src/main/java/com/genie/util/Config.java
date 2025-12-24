package com.genie.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuration management for Genie
 */
public class Config {
    
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    
    private static final String CONFIG_FILE = "genie.properties";
    private static Properties properties = new Properties();
    
    // Config keys
    public static final String OPENAI_API_KEY = "openai.api.key";
    public static final String HOTKEY_SAVE_CONTEXT = "hotkey.save.context";
    public static final String HOTKEY_MAKE_WISH = "hotkey.make.wish";
    public static final String AI_MODEL = "ai.model";
    public static final String VOICE_MODE = "voice.mode"; // "hotkey" or "always_listening"
    public static final String VOICE_ALWAYS_LISTENING = "voice.always_listening";
    
    // Defaults
    private static final String DEFAULT_HOTKEY_SAVE = "Ctrl+Shift+`";
    private static final String DEFAULT_HOTKEY_WISH = "Ctrl+Shift+1";
    private static final String DEFAULT_AI_MODEL = "gpt-4o-mini";
    private static final boolean DEFAULT_ALWAYS_LISTENING = false;
    
    public static void load() {
        Path configPath = getConfigPath();
        
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                properties.load(is);
                logger.info("Config loaded from {}", configPath);
            } catch (IOException e) {
                logger.warn("Failed to load config, using defaults", e);
                setDefaults();
            }
        } else {
            logger.info("No config file found, creating with defaults");
            setDefaults();
            save();
        }
    }
    
    public static void save() {
        Path configPath = getConfigPath();
        
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream os = Files.newOutputStream(configPath)) {
                properties.store(os, "Genie Configuration");
            }
            logger.info("Config saved to {}", configPath);
        } catch (IOException e) {
            logger.error("Failed to save config", e);
        }
    }
    
    private static void setDefaults() {
        properties.setProperty(HOTKEY_SAVE_CONTEXT, DEFAULT_HOTKEY_SAVE);
        properties.setProperty(HOTKEY_MAKE_WISH, DEFAULT_HOTKEY_WISH);
        properties.setProperty(AI_MODEL, DEFAULT_AI_MODEL);
    }
    
    private static Path getConfigPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "Genie", CONFIG_FILE);
        } else if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Paths.get(appData, "Genie", CONFIG_FILE);
        } else {
            return Paths.get(home, ".config", "genie", CONFIG_FILE);
        }
    }
    
    // Getters and setters
    
    public static String get(String key) {
        return properties.getProperty(key);
    }
    
    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    public static void set(String key, String value) {
        if (value == null) {
            properties.remove(key);
        } else {
            properties.setProperty(key, value);
        }
    }
    
    public static String getOpenAiApiKey() {
        // First check environment variable
        String envKey = System.getenv("OPENAI_API_KEY");
        if (envKey != null && !envKey.isEmpty()) {
            return envKey;
        }
        // Then check config
        return get(OPENAI_API_KEY);
    }
    
    public static void setOpenAiApiKey(String apiKey) {
        set(OPENAI_API_KEY, apiKey);
        save();
    }
    
    public static boolean hasOpenAiApiKey() {
        String key = getOpenAiApiKey();
        // Must be non-empty and look like a real key (starts with sk-)
        return key != null && !key.isEmpty() && key.startsWith("sk-") && key.length() > 20;
    }
    
    public static String getAiModel() {
        return get(AI_MODEL, DEFAULT_AI_MODEL);
    }
    
    // Voice activation settings
    
    public static boolean isAlwaysListeningEnabled() {
        String value = get(VOICE_ALWAYS_LISTENING);
        return value != null ? Boolean.parseBoolean(value) : DEFAULT_ALWAYS_LISTENING;
    }
    
    public static void setAlwaysListeningEnabled(boolean enabled) {
        set(VOICE_ALWAYS_LISTENING, String.valueOf(enabled));
        save();
    }
    
    public static String getVoiceMode() {
        return get(VOICE_MODE, "hotkey");
    }
    
    public static void setVoiceMode(String mode) {
        set(VOICE_MODE, mode);
        save();
    }
}

