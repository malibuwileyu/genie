package com.genie.ui;

import com.genie.util.Database;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Toast notification for voice wishes
 * 
 * Shows in top-right corner with:
 * - The captured wish text
 * - Option to edit if misheard
 * - Auto-dismisses after a few seconds
 */
public class WishToast {
    
    private static final Logger logger = LoggerFactory.getLogger(WishToast.class);
    
    private static Stage toastStage;
    private static boolean isPinned = false;
    
    /**
     * Show a toast notification for a captured wish
     * 
     * @param wishText The captured wish text
     * @param wishId The database ID of the saved wish
     */
    public static void show(String wishText, long wishId) {
        Platform.runLater(() -> createAndShow(wishText, wishId));
    }
    
    private static void createAndShow(String wishText, long wishId) {
        // Close any existing toast
        if (toastStage != null && toastStage.isShowing()) {
            toastStage.close();
        }
        
        isPinned = false;
        toastStage = new Stage();
        toastStage.initStyle(StageStyle.TRANSPARENT);
        toastStage.setAlwaysOnTop(true);
        
        VBox root = new VBox(12);
        root.setPadding(new Insets(18, 22, 22, 22));
        root.setStyle("""
            -fx-background-color: linear-gradient(to right, #2d1b4e, #1a1a2e);
            -fx-background-radius: 12;
            -fx-border-color: #6a4c93;
            -fx-border-radius: 12;
            -fx-border-width: 1;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 5);
            """);
        root.setMinWidth(380);
        root.setMaxWidth(420);
        
        // Header
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label emoji = new Label("🧞");
        emoji.setFont(Font.font("System", 24));
        
        Label title = new Label("Wish Captured!");
        title.setFont(Font.font("System", FontWeight.BOLD, 14));
        title.setTextFill(Color.web("#e0e0e0"));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeBtn = new Button("×");
        closeBtn.setStyle("""
            -fx-background-color: transparent;
            -fx-text-fill: #888888;
            -fx-font-size: 16px;
            -fx-cursor: hand;
            """);
        closeBtn.setOnAction(e -> fadeOut());
        
        header.getChildren().addAll(emoji, title, spacer, closeBtn);
        
        // Wish text (editable on click)
        TextArea wishArea = new TextArea(wishText);
        wishArea.setWrapText(true);
        wishArea.setPrefRowCount(3);
        wishArea.setMinHeight(70);
        wishArea.setEditable(false);
        wishArea.setStyle("""
            -fx-control-inner-background: #2a2a4a;
            -fx-text-fill: #ffffff;
            -fx-background-radius: 8;
            -fx-border-radius: 8;
            -fx-font-size: 13px;
            -fx-padding: 8;
            """);
        
        // Hint label
        Label hintLabel = new Label("Click to edit if misheard");
        hintLabel.setTextFill(Color.web("#888888"));
        hintLabel.setFont(Font.font("System", 10));
        
        // Action buttons (hidden initially, shown when editing)
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setVisible(false);
        buttonRow.setManaged(false);
        
        Button saveBtn = new Button("Update");
        saveBtn.setStyle("""
            -fx-background-color: #6a4c93;
            -fx-text-fill: #ffffff;
            -fx-background-radius: 6;
            -fx-padding: 8 16;
            -fx-cursor: hand;
            -fx-font-size: 12px;
            """);
        saveBtn.setMinWidth(70);
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("""
            -fx-background-color: #3a3a5a;
            -fx-text-fill: #cccccc;
            -fx-background-radius: 6;
            -fx-padding: 8 16;
            -fx-cursor: hand;
            -fx-font-size: 12px;
            """);
        cancelBtn.setMinWidth(70);
        
        buttonRow.getChildren().addAll(cancelBtn, saveBtn);
        
        // Click to edit
        wishArea.setOnMouseClicked(e -> {
            if (!wishArea.isEditable()) {
                wishArea.setEditable(true);
                wishArea.setStyle(wishArea.getStyle() + "-fx-border-color: #6a4c93; -fx-border-width: 1;");
                buttonRow.setVisible(true);
                buttonRow.setManaged(true);
                hintLabel.setText("Edit your wish above");
                isPinned = true; // Don't auto-dismiss while editing
            }
        });
        
        cancelBtn.setOnAction(e -> {
            wishArea.setText(wishText);
            wishArea.setEditable(false);
            wishArea.setStyle("""
                -fx-control-inner-background: #2a2a4a;
                -fx-text-fill: #ffffff;
                -fx-background-radius: 8;
                -fx-border-radius: 8;
                -fx-font-size: 13px;
                """);
            buttonRow.setVisible(false);
            buttonRow.setManaged(false);
            hintLabel.setText("Click to edit if misheard");
            isPinned = false;
            scheduleAutoClose();
        });
        
        saveBtn.setOnAction(e -> {
            String updatedText = wishArea.getText().trim();
            if (!updatedText.isEmpty() && !updatedText.equals(wishText)) {
                // Update in database
                Database.updateWishText(wishId, updatedText);
                logger.info("Wish {} updated to: {}", wishId, updatedText);
                
                // Show confirmation
                title.setText("Wish Updated! ✓");
                wishArea.setEditable(false);
                buttonRow.setVisible(false);
                buttonRow.setManaged(false);
                hintLabel.setVisible(false);
            }
            isPinned = false;
            scheduleAutoClose();
        });
        
        root.getChildren().addAll(header, wishArea, hintLabel, buttonRow);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        toastStage.setScene(scene);
        
        // Position in top-right corner
        Screen screen = Screen.getPrimary();
        double screenWidth = screen.getVisualBounds().getWidth();
        double screenHeight = screen.getVisualBounds().getHeight();
        
        toastStage.setX(screenWidth - 420);
        toastStage.setY(60); // Below menu bar
        
        // Fade in
        root.setOpacity(0);
        toastStage.show();
        
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), root);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
        
        // Schedule auto-close
        scheduleAutoClose();
        
        logger.info("Wish toast shown for wish #{}", wishId);
    }
    
    private static void scheduleAutoClose() {
        new Thread(() -> {
            try {
                Thread.sleep(8000); // 8 seconds
                if (!isPinned) {
                    Platform.runLater(WishToast::fadeOut);
                }
            } catch (InterruptedException ignored) {}
        }).start();
    }
    
    private static void fadeOut() {
        if (toastStage == null || !toastStage.isShowing()) return;
        
        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toastStage.getScene().getRoot());
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            if (toastStage != null) {
                toastStage.close();
            }
        });
        fadeOut.play();
    }
}

