package com.genie.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Non-blocking toast notifications for errors and warnings
 */
public class ErrorToast {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorToast.class);
    
    public enum ToastType {
        ERROR("#e74c3c", "⚠️"),      // Red
        WARNING("#f39c12", "⚡"),     // Orange
        INFO("#3498db", "ℹ️"),       // Blue
        SUCCESS("#27ae60", "✓");     // Green
        
        final String color;
        final String icon;
        
        ToastType(String color, String icon) {
            this.color = color;
            this.icon = icon;
        }
    }
    
    /**
     * Show an error toast with optional action button
     */
    public static void show(String title, String message, ToastType type) {
        show(title, message, type, null, null);
    }
    
    /**
     * Show a toast with action button
     */
    public static void show(String title, String message, ToastType type, 
                           String actionText, Runnable action) {
        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                stage.initStyle(StageStyle.TRANSPARENT);
                stage.setAlwaysOnTop(true);
                
                VBox container = new VBox(8);
                container.setPadding(new Insets(16));
                container.setAlignment(Pos.CENTER_LEFT);
                container.setStyle(String.format("""
                    -fx-background-color: #2d2d2d;
                    -fx-background-radius: 10;
                    -fx-border-color: %s;
                    -fx-border-width: 0 0 0 4;
                    -fx-border-radius: 10 0 0 10;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 15, 0, 0, 5);
                    """, type.color));
                container.setMinWidth(350);
                container.setMaxWidth(400);
                
                // Title with icon
                HBox titleBox = new HBox(8);
                titleBox.setAlignment(Pos.CENTER_LEFT);
                
                Label iconLabel = new Label(type.icon);
                iconLabel.setStyle("-fx-font-size: 18px;");
                
                Label titleLabel = new Label(title);
                titleLabel.setStyle(String.format("""
                    -fx-font-size: 15px;
                    -fx-font-weight: bold;
                    -fx-text-fill: %s;
                    """, type.color));
                
                titleBox.getChildren().addAll(iconLabel, titleLabel);
                
                // Message
                Label messageLabel = new Label(message);
                messageLabel.setWrapText(true);
                messageLabel.setStyle("""
                    -fx-font-size: 13px;
                    -fx-text-fill: #cccccc;
                    """);
                messageLabel.setMaxWidth(360);
                
                container.getChildren().addAll(titleBox, messageLabel);
                
                // Action button if provided
                if (actionText != null && action != null) {
                    HBox buttonBox = new HBox();
                    buttonBox.setAlignment(Pos.CENTER_RIGHT);
                    
                    Button actionBtn = new Button(actionText);
                    actionBtn.setStyle(String.format("""
                        -fx-background-color: %s;
                        -fx-text-fill: white;
                        -fx-font-size: 12px;
                        -fx-padding: 6 16;
                        -fx-background-radius: 4;
                        -fx-cursor: hand;
                        """, type.color));
                    actionBtn.setOnAction(e -> {
                        stage.close();
                        action.run();
                    });
                    
                    buttonBox.getChildren().add(actionBtn);
                    container.getChildren().add(buttonBox);
                }
                
                // Close button (X)
                Button closeBtn = new Button("×");
                closeBtn.setStyle("""
                    -fx-background-color: transparent;
                    -fx-text-fill: #888888;
                    -fx-font-size: 18px;
                    -fx-padding: 0 4;
                    -fx-cursor: hand;
                    """);
                closeBtn.setOnAction(e -> stage.close());
                
                // Position close button
                HBox topBar = new HBox();
                topBar.setAlignment(Pos.TOP_RIGHT);
                HBox.setHgrow(topBar, Priority.ALWAYS);
                topBar.getChildren().add(closeBtn);
                container.getChildren().add(0, topBar);
                
                Scene scene = new Scene(container);
                scene.setFill(Color.TRANSPARENT);
                stage.setScene(scene);
                
                // Position in top-right corner
                var screenBounds = Screen.getPrimary().getVisualBounds();
                stage.setX(screenBounds.getMaxX() - 420);
                stage.setY(screenBounds.getMinY() + 60);
                
                // Fade in
                container.setOpacity(0);
                stage.show();
                
                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), container);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                fadeIn.play();
                
                // Auto-close after 8 seconds (longer for errors)
                int duration = (type == ToastType.ERROR) ? 10000 : 6000;
                PauseTransition pause = new PauseTransition(Duration.millis(duration));
                pause.setOnFinished(e -> {
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(300), container);
                    fadeOut.setFromValue(1);
                    fadeOut.setToValue(0);
                    fadeOut.setOnFinished(ev -> stage.close());
                    fadeOut.play();
                });
                pause.play();
                
            } catch (Exception e) {
                logger.error("Failed to show error toast", e);
            }
        });
    }
    
    // Convenience methods
    
    public static void showError(String title, String message) {
        show(title, message, ToastType.ERROR);
    }
    
    public static void showError(String title, String message, String actionText, Runnable action) {
        show(title, message, ToastType.ERROR, actionText, action);
    }
    
    public static void showWarning(String title, String message) {
        show(title, message, ToastType.WARNING);
    }
    
    public static void showInfo(String title, String message) {
        show(title, message, ToastType.INFO);
    }
    
    public static void showSuccess(String title, String message) {
        show(title, message, ToastType.SUCCESS);
    }
    
    // Pre-built error messages
    
    public static void showMissingApiKey() {
        show("API Key Required", 
             "OpenAI API key not configured. Voice activation and research features are disabled.\n\nAdd your API key in Settings to enable all features.",
             ToastType.WARNING,
             "Open Settings",
             SettingsDialog::show);
    }
    
    public static void showMicrophonePermissionDenied() {
        show("Microphone Access Denied",
             "Genie needs microphone access for voice commands.\n\nGo to System Settings → Privacy & Security → Microphone and enable access for Genie.",
             ToastType.ERROR,
             "Open Settings",
             () -> {
                 try {
                     Runtime.getRuntime().exec(new String[]{
                         "open", "x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone"
                     });
                 } catch (Exception e) {
                     logger.error("Failed to open system settings", e);
                 }
             });
    }
    
    public static void showNetworkError(String operation) {
        show("Network Error",
             String.format("Failed to %s. Please check your internet connection and try again.", operation),
             ToastType.ERROR);
    }
    
    public static void showNetworkErrorWithRetry(String operation, Runnable retryAction) {
        show("Network Error",
             String.format("Failed to %s. Please check your internet connection.", operation),
             ToastType.ERROR,
             "Retry",
             retryAction);
    }
}

