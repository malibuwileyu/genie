package com.genie.ui;

import com.genie.core.ContextCapture.Context;
import com.genie.core.ContextRestorer;
import com.genie.core.ContextRestorer.RestoreResult;
import com.genie.util.Database;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

/**
 * Enhanced popup for Context Resurrection
 * 
 * Save view shows:
 * - Screenshot thumbnail
 * - Active window
 * - Browser tabs count
 * - Clipboard preview
 * - AI summary (when ready)
 * 
 * Restore view shows same + Restore button
 */
public class ContextPopup {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextPopup.class);
    
    private static Stage stage;
    private static Context currentContext;
    private static Label summaryLabel;
    
    /**
     * Show confirmation that context was saved
     */
    public static void showSaveConfirmation(Context context) {
        Platform.runLater(() -> {
            currentContext = context;
            createEnhancedPopup("Context Saved! 📸", context, true, false);
        });
    }
    
    /**
     * Update the summary label when AI analysis completes
     */
    public static void updateSummary(Context context) {
        Platform.runLater(() -> {
            if (summaryLabel != null && stage != null && stage.isShowing()) {
                if (context.aiSummary != null) {
                    summaryLabel.setText(context.aiSummary);
                }
            }
        });
    }
    
    /**
     * Show the most recent saved context for restoration
     */
    public static void showRestore() {
        Platform.runLater(() -> {
            List<Context> contexts = Database.getRecentContexts(1);
            if (contexts.isEmpty()) {
                showNoContextMessage();
                return;
            }
            currentContext = contexts.get(0);
            createEnhancedPopup("Resume Where You Left Off 🔄", currentContext, false, true);
        });
    }
    
    /**
     * Show a list of recent contexts to choose from
     */
    public static void showContextList() {
        Platform.runLater(() -> {
            List<Context> contexts = Database.getRecentContexts(10);
            if (contexts.isEmpty()) {
                showNoContextMessage();
                return;
            }
            createContextListPopup(contexts);
        });
    }
    
    private static void createEnhancedPopup(String title, Context context, boolean autoClose, boolean showRestore) {
        if (stage != null && stage.isShowing()) {
            stage.close();
        }
        
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);
            -fx-background-radius: 16;
            -fx-border-color: #4a4a6a;
            -fx-border-radius: 16;
            -fx-border-width: 1;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 30, 0, 0, 8);
            """);
        
        // Header with title
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#f0f0f0"));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button closeX = new Button("×");
        closeX.setStyle("""
            -fx-background-color: transparent;
            -fx-text-fill: #888888;
            -fx-font-size: 20px;
            -fx-cursor: hand;
            """);
        closeX.setOnAction(e -> stage.close());
        
        header.getChildren().addAll(titleLabel, spacer, closeX);
        
        // Main content - two columns
        HBox mainContent = new HBox(20);
        
        // Left column - screenshot
        VBox leftColumn = new VBox(12);
        leftColumn.setAlignment(Pos.TOP_CENTER);
        
        File screenshotFile = context.getScreenshotFile();
        if (screenshotFile != null) {
            try {
                Image img = new Image(new FileInputStream(screenshotFile), 300, 200, true, true);
                ImageView imageView = new ImageView(img);
                imageView.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);");
                
                // Click to view full size
                imageView.setOnMouseClicked(e -> showFullScreenshot(screenshotFile));
                imageView.setStyle("-fx-cursor: hand;");
                
                leftColumn.getChildren().add(imageView);
                
                Label hint = new Label("Click to enlarge");
                hint.setTextFill(Color.web("#666666"));
                hint.setFont(Font.font("System", 10));
                leftColumn.getChildren().add(hint);
            } catch (Exception e) {
                logger.warn("Failed to load screenshot", e);
            }
        }
        
        // Right column - details
        VBox rightColumn = new VBox(12);
        rightColumn.setMinWidth(280);
        
        // Timestamp
        HBox timeRow = createInfoRow("⏰", "Time", context.getFormattedTime());
        
        // Active window
        HBox windowRow = createInfoRow("🪟", "Window", 
            context.activeWindow != null ? truncate(context.activeWindow, 40) : "Unknown");
        
        // Browser tabs
        if (context.getTabCount() > 0) {
            HBox tabsRow = createInfoRow("🌐", "Browser Tabs", context.getTabCount() + " tabs open");
            rightColumn.getChildren().add(tabsRow);
        }
        
        // Windows
        if (context.getWindowCount() > 0) {
            HBox windowsRow = createInfoRow("📚", "Windows", context.getWindowCount() + " visible");
            rightColumn.getChildren().add(windowsRow);
        }
        
        // AI Summary
        VBox summarySection = new VBox(6);
        Label summaryTitle = new Label("🤖 AI Summary");
        summaryTitle.setTextFill(Color.web("#aaaaaa"));
        summaryTitle.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        summaryLabel = new Label(context.aiSummary != null ? context.aiSummary : "Analyzing your context...");
        summaryLabel.setTextFill(Color.web("#e0e0e0"));
        summaryLabel.setWrapText(true);
        summaryLabel.setMaxWidth(280);
        summaryLabel.setFont(Font.font("System", 12));
        summaryLabel.setStyle("-fx-background-color: #2a2a4a; -fx-padding: 10; -fx-background-radius: 6;");
        
        summarySection.getChildren().addAll(summaryTitle, summaryLabel);
        
        rightColumn.getChildren().addAll(timeRow, windowRow, summarySection);
        
        // Clipboard preview (collapsible)
        if (context.clipboardContent != null && !context.clipboardContent.isEmpty()) {
            TitledPane clipboardPane = new TitledPane();
            clipboardPane.setText("📋 Clipboard (" + context.clipboardContent.length() + " chars)");
            clipboardPane.setExpanded(false);
            clipboardPane.setStyle("""
                -fx-text-fill: #cccccc;
                """);
            
            TextArea clipboardContent = new TextArea(truncate(context.clipboardContent, 500));
            clipboardContent.setEditable(false);
            clipboardContent.setWrapText(true);
            clipboardContent.setPrefRowCount(4);
            clipboardContent.setStyle("""
                -fx-control-inner-background: #2a2a4a;
                -fx-text-fill: #cccccc;
                -fx-font-family: monospace;
                -fx-font-size: 11px;
                """);
            
            clipboardPane.setContent(clipboardContent);
            rightColumn.getChildren().add(clipboardPane);
        }
        
        mainContent.getChildren().addAll(leftColumn, rightColumn);
        
        // Action buttons
        HBox buttonRow = new HBox(12);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setPadding(new Insets(12, 0, 0, 0));
        
        if (showRestore) {
            Button restoreBtn = new Button("🔄 Restore Context");
            restoreBtn.setStyle("""
                -fx-background-color: #4CAF50;
                -fx-text-fill: #ffffff;
                -fx-background-radius: 8;
                -fx-padding: 10 20;
                -fx-cursor: hand;
                -fx-font-weight: bold;
                """);
            restoreBtn.setOnAction(e -> {
                RestoreResult result = ContextRestorer.restoreContext(context);
                showRestoreResult(result);
            });
            buttonRow.getChildren().add(restoreBtn);
        }
        
        Button closeBtn = new Button(autoClose ? "Got it!" : "Close");
        closeBtn.setStyle("""
            -fx-background-color: #6a4c93;
            -fx-text-fill: #ffffff;
            -fx-background-radius: 8;
            -fx-padding: 10 20;
            -fx-cursor: hand;
            """);
        closeBtn.setOnAction(e -> stage.close());
        buttonRow.getChildren().add(closeBtn);
        
        root.getChildren().addAll(header, mainContent, buttonRow);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        // ESC to close
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
        
        // Auto-close for save confirmation (10 seconds to read the content)
        if (autoClose) {
            new Thread(() -> {
                try {
                    Thread.sleep(10000); // 10 seconds to read
                    Platform.runLater(() -> {
                        if (stage != null && stage.isShowing()) {
                            stage.close();
                        }
                    });
                } catch (InterruptedException ignored) {}
            }).start();
        }
        
        logger.info("Enhanced context popup shown");
    }
    
    private static void showFullScreenshot(File screenshotFile) {
        try {
            Stage fullStage = new Stage();
            fullStage.initStyle(StageStyle.UNDECORATED);
            fullStage.setAlwaysOnTop(true);
            
            Image img = new Image(new FileInputStream(screenshotFile));
            ImageView imageView = new ImageView(img);
            
            // Scale to fit screen with some margin
            double maxWidth = java.awt.Toolkit.getDefaultToolkit().getScreenSize().getWidth() * 0.8;
            double maxHeight = java.awt.Toolkit.getDefaultToolkit().getScreenSize().getHeight() * 0.8;
            imageView.setFitWidth(maxWidth);
            imageView.setFitHeight(maxHeight);
            imageView.setPreserveRatio(true);
            
            StackPane pane = new StackPane(imageView);
            pane.setStyle("-fx-background-color: rgba(0,0,0,0.9); -fx-padding: 20;");
            
            Scene scene = new Scene(pane);
            scene.setFill(Color.TRANSPARENT);
            
            // Click or ESC to close
            scene.setOnMouseClicked(e -> fullStage.close());
            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) fullStage.close();
            });
            
            fullStage.setScene(scene);
            fullStage.centerOnScreen();
            fullStage.show();
        } catch (Exception e) {
            logger.error("Failed to show full screenshot", e);
        }
    }
    
    private static void showRestoreResult(RestoreResult result) {
        // Close the context popup first
        if (stage != null && stage.isShowing()) {
            stage.close();
        }
        
        // Show a non-blocking styled result popup
        Stage resultStage = new Stage();
        resultStage.initStyle(StageStyle.UNDECORATED);
        resultStage.setAlwaysOnTop(true);
        
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.CENTER);
        root.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);
            -fx-background-radius: 16;
            -fx-border-color: #4a4a6a;
            -fx-border-radius: 16;
            -fx-border-width: 1;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 5);
            """);
        
        Label emoji = new Label(result.success ? "✅" : "⚠️");
        emoji.setFont(Font.font("System", 48));
        
        Label title = new Label(result.success ? "Context Restored!" : "Partial Restore");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#e0e0e0"));
        
        Label message = new Label(result.message);
        message.setTextFill(Color.web("#aaaaaa"));
        message.setWrapText(true);
        message.setMaxWidth(300);
        message.setFont(Font.font("System", 13));
        
        Button okBtn = new Button("Got it!");
        okBtn.setStyle("""
            -fx-background-color: #6a4c93;
            -fx-text-fill: #ffffff;
            -fx-background-radius: 8;
            -fx-padding: 10 24;
            -fx-cursor: hand;
            """);
        okBtn.setOnAction(e -> resultStage.close());
        
        root.getChildren().addAll(emoji, title, message, okBtn);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        
        resultStage.setScene(scene);
        resultStage.centerOnScreen();
        resultStage.show();
        
        // Auto-close after 5 seconds
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                Platform.runLater(() -> {
                    if (resultStage.isShowing()) {
                        resultStage.close();
                    }
                });
            } catch (InterruptedException ignored) {}
        }).start();
        
        logger.info("Restore result: {}", result.message);
    }
    
    private static void createContextListPopup(List<Context> contexts) {
        if (stage != null && stage.isShowing()) {
            stage.close();
        }
        
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);
            -fx-background-radius: 16;
            -fx-border-color: #4a4a6a;
            -fx-border-radius: 16;
            -fx-border-width: 1;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 30, 0, 0, 8);
            """);
        
        Label title = new Label("📚 Saved Contexts");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#f0f0f0"));
        
        VBox contextList = new VBox(8);
        for (Context ctx : contexts) {
            HBox row = new HBox(16);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12, 16, 12, 16));
            row.setStyle("""
                -fx-background-color: #2a2a4a;
                -fx-background-radius: 8;
                -fx-cursor: hand;
                """);
            
            // Time
            Label time = new Label(ctx.getFormattedTime());
            time.setTextFill(Color.web("#888888"));
            time.setMinWidth(100);
            time.setFont(Font.font("System", 12));
            
            // Window
            Label window = new Label(ctx.activeWindow != null ? truncate(ctx.activeWindow, 30) : "Unknown");
            window.setTextFill(Color.web("#cccccc"));
            window.setFont(Font.font("System", 12));
            
            // Stats
            Label stats = new Label(String.format("%d tabs, %d windows", ctx.getTabCount(), ctx.getWindowCount()));
            stats.setTextFill(Color.web("#666666"));
            stats.setFont(Font.font("System", 10));
            
            row.getChildren().addAll(time, window, stats);
            
            // Click to show details
            row.setOnMouseClicked(e -> {
                stage.close();
                currentContext = ctx;
                createEnhancedPopup("Context Details", ctx, false, true);
            });
            
            // Hover effect
            row.setOnMouseEntered(e -> row.setStyle("""
                -fx-background-color: #3a3a5a;
                -fx-background-radius: 8;
                -fx-cursor: hand;
                """));
            row.setOnMouseExited(e -> row.setStyle("""
                -fx-background-color: #2a2a4a;
                -fx-background-radius: 8;
                -fx-cursor: hand;
                """));
            
            contextList.getChildren().add(row);
        }
        
        ScrollPane scrollPane = new ScrollPane(contextList);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(350);
        scrollPane.setStyle("-fx-background: #1a1a2e; -fx-background-color: transparent;");
        
        Button closeBtn = new Button("Close");
        closeBtn.setStyle("""
            -fx-background-color: #3a3a5a;
            -fx-text-fill: #cccccc;
            -fx-background-radius: 8;
            -fx-padding: 10 20;
            -fx-cursor: hand;
            """);
        closeBtn.setOnAction(e -> stage.close());
        
        HBox buttonRow = new HBox(closeBtn);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        
        root.getChildren().addAll(title, scrollPane, buttonRow);
        
        Scene scene = new Scene(root, 500, 450);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
    
    private static void showNoContextMessage() {
        if (stage != null && stage.isShowing()) {
            stage.close();
        }
        
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        
        VBox root = new VBox(16);
        root.setPadding(new Insets(30));
        root.setAlignment(Pos.CENTER);
        root.setStyle("""
            -fx-background-color: linear-gradient(to bottom, #1a1a2e, #16213e);
            -fx-background-radius: 16;
            -fx-border-color: #4a4a6a;
            -fx-border-radius: 16;
            -fx-border-width: 1;
            """);
        
        Label emoji = new Label("🧞");
        emoji.setFont(Font.font(48));
        
        Label message = new Label("No saved contexts yet.\nPress Ctrl+Shift+` to save your first context!");
        message.setTextFill(Color.web("#cccccc"));
        message.setFont(Font.font("System", 14));
        message.setStyle("-fx-text-alignment: center;");
        
        Button closeBtn = new Button("OK");
        closeBtn.setStyle("""
            -fx-background-color: #6a4c93;
            -fx-text-fill: #ffffff;
            -fx-background-radius: 8;
            -fx-padding: 10 24;
            -fx-cursor: hand;
            """);
        closeBtn.setOnAction(e -> stage.close());
        
        root.getChildren().addAll(emoji, message, closeBtn);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
    
    private static HBox createInfoRow(String emoji, String label, String value) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        
        Label emojiLabel = new Label(emoji);
        emojiLabel.setFont(Font.font(14));
        
        Label labelNode = new Label(label + ":");
        labelNode.setTextFill(Color.web("#888888"));
        labelNode.setFont(Font.font("System", FontWeight.BOLD, 12));
        labelNode.setMinWidth(70);
        
        Label valueNode = new Label(value);
        valueNode.setTextFill(Color.web("#e0e0e0"));
        valueNode.setFont(Font.font("System", 12));
        
        row.getChildren().addAll(emojiLabel, labelNode, valueNode);
        return row;
    }
    
    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
    
    public static void hide() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });
    }
}
