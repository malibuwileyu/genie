package com.genie.ui;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionListener;
import java.net.URL;

/**
 * Manages the system tray icon and menu
 */
public class TrayManager {
    
    private static final Logger logger = LoggerFactory.getLogger(TrayManager.class);
    
    private SystemTray systemTray;
    private TrayIcon trayIcon;
    
    public void initialize() {
        if (!SystemTray.isSupported()) {
            logger.error("System tray is not supported on this platform");
            return;
        }
        
        systemTray = SystemTray.getSystemTray();
        
        // Load tray icon
        Image image = loadTrayIcon();
        
        // Create popup menu
        PopupMenu popup = createPopupMenu();
        
        // Create tray icon
        trayIcon = new TrayIcon(image, "Genie", popup);
        trayIcon.setImageAutoSize(true);
        
        // Double-click to open main window
        trayIcon.addActionListener(e -> {
            Platform.runLater(this::showMainWindow);
        });
        
        try {
            systemTray.add(trayIcon);
            logger.info("System tray icon added");
        } catch (AWTException e) {
            logger.error("Failed to add tray icon", e);
        }
    }
    
    private Image loadTrayIcon() {
        // Try to load custom icon, fall back to default
        URL iconUrl = getClass().getResource("/icons/tray-icon.png");
        if (iconUrl != null) {
            return Toolkit.getDefaultToolkit().getImage(iconUrl);
        }
        
        // Fallback: create a simple colored square
        // In production, use a proper icon file
        return createDefaultIcon();
    }
    
    private Image createDefaultIcon() {
        // Create a simple 16x16 icon as fallback
        int size = 16;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(new Color(138, 43, 226)); // Purple (genie color)
        g2d.fillOval(0, 0, size, size);
        g2d.dispose();
        return image;
    }
    
    private PopupMenu createPopupMenu() {
        PopupMenu popup = new PopupMenu();
        
        // Save Context
        MenuItem saveContext = new MenuItem("Save Context (Ctrl+Option+C)");
        saveContext.addActionListener(e -> {
            Platform.runLater(this::saveContext);
        });
        popup.add(saveContext);
        
        // Make a Wish
        MenuItem makeWish = new MenuItem("Make a Wish (Ctrl+Option+W)");
        makeWish.addActionListener(e -> {
            Platform.runLater(this::showWishInput);
        });
        popup.add(makeWish);
        
        popup.addSeparator();
        
        // View Contexts
        MenuItem viewContexts = new MenuItem("View Saved Contexts");
        viewContexts.addActionListener(e -> {
            Platform.runLater(this::showContextList);
        });
        popup.add(viewContexts);
        
        // View Wishes
        MenuItem viewWishes = new MenuItem("View Wishes");
        viewWishes.addActionListener(e -> {
            Platform.runLater(this::showWishList);
        });
        popup.add(viewWishes);
        
        popup.addSeparator();
        
        // Settings
        MenuItem settings = new MenuItem("Settings");
        settings.addActionListener(e -> {
            Platform.runLater(this::showSettings);
        });
        popup.add(settings);
        
        popup.addSeparator();
        
        // Quit
        MenuItem quit = new MenuItem("Quit Genie");
        quit.addActionListener(e -> {
            Platform.exit();
            System.exit(0);
        });
        popup.add(quit);
        
        return popup;
    }
    
    public void showNotification(String title, String message) {
        if (trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }
    
    // Action handlers
    
    private void showMainWindow() {
        logger.info("Opening main window...");
        // For now, show context list as main window
        ContextPopup.showContextList();
    }
    
    private void saveContext() {
        logger.info("Saving context...");
        com.genie.core.ContextCapture.Context ctx = com.genie.core.ContextCapture.captureNow();
        ContextPopup.showSaveConfirmation(ctx);
    }
    
    private void showWishInput() {
        logger.info("Opening wish input...");
        WishInputDialog.show();
    }
    
    private void showContextList() {
        logger.info("Opening context list...");
        ContextPopup.showContextList();
    }
    
    private void showWishList() {
        logger.info("Opening wish list...");
        WishListPanel.show();
    }
    
    private void showSettings() {
        logger.info("Opening settings...");
        SettingsDialog.show();
    }
    
    public void shutdown() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
    }
}

