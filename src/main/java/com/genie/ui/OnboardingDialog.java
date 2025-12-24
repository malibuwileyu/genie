package com.genie.ui;

import com.genie.util.Config;
import com.genie.util.PermissionChecker;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * First-launch onboarding dialog to guide users through permission setup
 */
public class OnboardingDialog {
    
    private static final Logger logger = LoggerFactory.getLogger(OnboardingDialog.class);
    private static final String ONBOARDING_COMPLETED_KEY = "onboarding.completed";
    
    private static Stage stage;
    private static Label micStatus;
    private static Label accessibilityStatus;
    private static Label screenRecStatus;
    private static Button continueBtn;
    private static Timeline refreshTimer;
    
    /**
     * Show onboarding if not already completed
     * @return true if onboarding was shown, false if already completed
     */
    public static boolean showIfNeeded() {
        if (!PermissionChecker.isMacOS()) {
            return false; // Only needed on macOS
        }
        
        String completed = Config.get(ONBOARDING_COMPLETED_KEY);
        if ("true".equals(completed)) {
            // Already completed, but do a quick check
            var status = PermissionChecker.checkAll();
            if (!status.allGranted()) {
                // Some permissions were revoked, show a warning
                Platform.runLater(() -> {
                    ErrorToast.showWarning(
                        "Permissions Changed",
                        "Some permissions were revoked. Some features may not work.\nOpen Settings to fix."
                    );
                });
            }
            return false;
        }
        
        Platform.runLater(OnboardingDialog::createAndShow);
        return true;
    }
    
    /**
     * Force show the onboarding dialog (for testing or re-setup)
     */
    public static void show() {
        Platform.runLater(OnboardingDialog::createAndShow);
    }
    
    private static void createAndShow() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("Welcome to Genie");
        stage.setAlwaysOnTop(true);
        
        VBox root = new VBox(24);
        root.setPadding(new Insets(32));
        root.setAlignment(Pos.CENTER);
        root.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);
            -fx-background-radius: 16;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 10);
            """);
        root.setPrefWidth(480);
        
        // Title
        Label emoji = new Label("🧞");
        emoji.setFont(Font.font(48));
        
        Label title = new Label("Welcome to Genie!");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setTextFill(Color.web("#e0e0e0"));
        
        Label subtitle = new Label("Let's set up permissions so all features work perfectly.");
        subtitle.setFont(Font.font("System", 14));
        subtitle.setTextFill(Color.web("#888888"));
        subtitle.setWrapText(true);
        
        VBox header = new VBox(8, emoji, title, subtitle);
        header.setAlignment(Pos.CENTER);
        
        // Permission rows
        VBox permissions = new VBox(16);
        permissions.setPadding(new Insets(16, 0, 16, 0));
        
        // Initial status check
        var status = PermissionChecker.checkAll();
        
        // Microphone
        micStatus = new Label(status.microphone ? "✓" : "○");
        HBox micRow = createPermissionRow(
            "🎤", "Microphone",
            "For \"Hey Genie\" voice commands",
            micStatus,
            status.microphone,
            PermissionChecker::openMicrophoneSettings
        );
        
        // Accessibility
        accessibilityStatus = new Label(status.accessibility ? "✓" : "○");
        HBox accessibilityRow = createPermissionRow(
            "⚙️", "Accessibility",
            "To restore your windows and open browser tabs",
            accessibilityStatus,
            status.accessibility,
            PermissionChecker::openAccessibilitySettings
        );
        
        // Screen Recording
        screenRecStatus = new Label(status.screenRecording ? "✓" : "○");
        HBox screenRecRow = createPermissionRow(
            "🖥️", "Screen Recording",
            "To capture screenshots for AI context analysis",
            screenRecStatus,
            status.screenRecording,
            PermissionChecker::openScreenRecordingSettings
        );
        
        permissions.getChildren().addAll(micRow, accessibilityRow, screenRecRow);
        
        // Note
        Label note = new Label("💡 Tip: After granting permissions, you may need to restart Genie for changes to take effect.");
        note.setFont(Font.font("System", 11));
        note.setTextFill(Color.web("#666666"));
        note.setWrapText(true);
        note.setMaxWidth(400);
        
        // Buttons
        HBox buttons = new HBox(16);
        buttons.setAlignment(Pos.CENTER);
        
        Button skipBtn = new Button("Skip for now");
        skipBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-text-fill: #888888;
            -fx-font-size: 13px;
            -fx-cursor: hand;
            -fx-border-color: #444444;
            -fx-border-radius: 8;
            -fx-padding: 10 20;
            """);
        skipBtn.setOnAction(e -> {
            markCompleted();
            stage.close();
        });
        
        continueBtn = new Button("Continue");
        continueBtn.setStyle("""
            -fx-background-color: linear-gradient(to right, #6a4c93, #8b5cf6);
            -fx-text-fill: white;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-background-radius: 8;
            -fx-padding: 12 32;
            -fx-cursor: hand;
            """);
        continueBtn.setOnAction(e -> {
            markCompleted();
            stage.close();
            
            // Show success or warning based on final status
            var finalStatus = PermissionChecker.checkAll();
            if (finalStatus.allGranted()) {
                ErrorToast.showSuccess("You're all set!", "All permissions granted. Genie is ready to use!");
            } else {
                int missing = 3 - finalStatus.grantedCount();
                ErrorToast.showWarning(
                    "Setup Incomplete",
                    String.format("%d permission%s not granted. Some features may not work.", 
                        missing, missing == 1 ? "" : "s")
                );
            }
        });
        
        updateContinueButton(status);
        
        buttons.getChildren().addAll(skipBtn, continueBtn);
        
        root.getChildren().addAll(header, permissions, note, buttons);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        
        // Start a timer to refresh permission status
        startPermissionRefreshTimer();
        
        logger.info("Onboarding dialog shown");
    }
    
    private static HBox createPermissionRow(String emoji, String name, String description,
                                            Label statusLabel, boolean granted, Runnable openSettings) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12, 16, 12, 16));
        row.setStyle("""
            -fx-background-color: #2a2a4a;
            -fx-background-radius: 10;
            """);
        
        // Emoji
        Label emojiLabel = new Label(emoji);
        emojiLabel.setFont(Font.font(24));
        emojiLabel.setMinWidth(40);
        
        // Text
        VBox textBox = new VBox(2);
        Label nameLabel = new Label(name);
        nameLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        nameLabel.setTextFill(Color.web("#e0e0e0"));
        
        Label descLabel = new Label(description);
        descLabel.setFont(Font.font("System", 12));
        descLabel.setTextFill(Color.web("#888888"));
        
        textBox.getChildren().addAll(nameLabel, descLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        
        // Status / Button
        statusLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        statusLabel.setMinWidth(30);
        statusLabel.setAlignment(Pos.CENTER);
        
        if (granted) {
            statusLabel.setTextFill(Color.web("#4ade80")); // Green checkmark
        } else {
            statusLabel.setTextFill(Color.web("#888888"));
        }
        
        Button grantBtn = new Button("Grant");
        grantBtn.setStyle("""
            -fx-background-color: #3a3a6a;
            -fx-text-fill: #cccccc;
            -fx-font-size: 12px;
            -fx-background-radius: 6;
            -fx-padding: 6 16;
            -fx-cursor: hand;
            """);
        grantBtn.setOnAction(e -> openSettings.run());
        
        if (granted) {
            grantBtn.setVisible(false);
            grantBtn.setManaged(false);
        }
        
        row.getChildren().addAll(emojiLabel, textBox, statusLabel, grantBtn);
        
        return row;
    }
    
    private static void startPermissionRefreshTimer() {
        // Check permissions every 2 seconds
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            var status = PermissionChecker.checkAll();
            
            // Update status labels
            updateStatusLabel(micStatus, status.microphone);
            updateStatusLabel(accessibilityStatus, status.accessibility);
            updateStatusLabel(screenRecStatus, status.screenRecording);
            
            // Update continue button
            updateContinueButton(status);
        }));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
        
        // Stop timer when stage closes
        stage.setOnHidden(e -> {
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
        });
    }
    
    private static void updateStatusLabel(Label label, boolean granted) {
        if (granted) {
            label.setText("✓");
            label.setTextFill(Color.web("#4ade80"));
            
            // Hide the grant button in the same row
            HBox row = (HBox) label.getParent();
            for (var node : row.getChildren()) {
                if (node instanceof Button) {
                    node.setVisible(false);
                    node.setManaged(false);
                }
            }
        }
    }
    
    private static void updateContinueButton(PermissionChecker.PermissionStatus status) {
        if (status.allGranted()) {
            continueBtn.setText("Continue ✓");
            continueBtn.setStyle("""
                -fx-background-color: linear-gradient(to right, #059669, #10b981);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 8;
                -fx-padding: 12 32;
                -fx-cursor: hand;
                """);
        } else {
            continueBtn.setText("Continue");
            continueBtn.setStyle("""
                -fx-background-color: linear-gradient(to right, #6a4c93, #8b5cf6);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-background-radius: 8;
                -fx-padding: 12 32;
                -fx-cursor: hand;
                """);
        }
    }
    
    private static void markCompleted() {
        Config.set(ONBOARDING_COMPLETED_KEY, "true");
        Config.save();
        logger.info("Onboarding marked as completed");
    }
    
    /**
     * Reset onboarding so it shows again on next launch
     */
    public static void reset() {
        Config.set(ONBOARDING_COMPLETED_KEY, null);
        Config.save();
        logger.info("Onboarding reset");
    }
}

