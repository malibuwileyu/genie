package com.genie.ui;

import com.genie.ai.OpenAIClient;
import com.genie.util.Config;
import com.genie.util.Database;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Popup dialog for capturing a "wish" (curiosity to research later)
 * 
 * Appears when user presses the global hotkey.
 * Simple text input, saves to database, optionally triggers AI research.
 */
public class WishInputDialog {
    
    private static final Logger logger = LoggerFactory.getLogger(WishInputDialog.class);
    
    private static Stage stage;
    private static TextField inputField;
    private static Label statusLabel;
    
    /**
     * Show the wish input dialog
     */
    public static void show() {
        Platform.runLater(() -> {
            if (stage != null && stage.isShowing()) {
                stage.toFront();
                inputField.requestFocus();
                return;
            }
            
            createAndShowDialog();
        });
    }
    
    private static void createAndShowDialog() {
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        
        // Main container
        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setAlignment(Pos.CENTER);
        root.setStyle("""
            -fx-background-color: #1a1a2e;
            -fx-background-radius: 12;
            -fx-border-color: #4a4a6a;
            -fx-border-radius: 12;
            -fx-border-width: 1;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 5);
            """);
        
        // Title
        Label title = new Label("🧞 I wish I knew about...");
        title.setFont(Font.font("System", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#e0e0e0"));
        
        // Input field
        inputField = new TextField();
        inputField.setPromptText("e.g., how database indexes work");
        inputField.setPrefWidth(400);
        inputField.setStyle("""
            -fx-background-color: #2a2a4a;
            -fx-text-fill: #ffffff;
            -fx-prompt-text-fill: #888888;
            -fx-background-radius: 8;
            -fx-border-radius: 8;
            -fx-padding: 12;
            -fx-font-size: 14px;
            """);
        
        // Handle Enter key
        inputField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !inputField.getText().trim().isEmpty()) {
                submitWish();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hide();
            }
        });
        
        // Status label (for feedback)
        statusLabel = new Label("Press Enter to save, Esc to cancel");
        statusLabel.setFont(Font.font("System", 11));
        statusLabel.setTextFill(Color.web("#888888"));
        
        // Buttons
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("""
            -fx-background-color: #3a3a5a;
            -fx-text-fill: #cccccc;
            -fx-background-radius: 6;
            -fx-padding: 8 16;
            -fx-cursor: hand;
            """);
        cancelBtn.setOnAction(e -> hide());
        
        Button saveBtn = new Button("Save Wish");
        saveBtn.setStyle("""
            -fx-background-color: #6a4c93;
            -fx-text-fill: #ffffff;
            -fx-background-radius: 6;
            -fx-padding: 8 16;
            -fx-cursor: hand;
            """);
        saveBtn.setOnAction(e -> {
            if (!inputField.getText().trim().isEmpty()) {
                submitWish();
            }
        });
        
        buttons.getChildren().addAll(cancelBtn, saveBtn);
        
        root.getChildren().addAll(title, inputField, statusLabel, buttons);
        
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        stage.setScene(scene);
        
        // Center on screen
        stage.centerOnScreen();
        stage.show();
        
        // Focus input
        inputField.requestFocus();
        
        logger.info("Wish input dialog shown");
    }
    
    private static void submitWish() {
        String wishText = inputField.getText().trim();
        if (wishText.isEmpty()) return;
        
        // Update UI
        inputField.setDisable(true);
        statusLabel.setText("Saving...");
        statusLabel.setTextFill(Color.web("#aaaaaa"));
        
        // Save to database
        CompletableFuture.runAsync(() -> {
            try {
                long wishId = Database.saveWish(wishText);
                logger.info("Wish saved with ID: {}", wishId);
                
                // Check if we should generate research now or queue for later
                if (Config.hasOpenAiApiKey()) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Generating research article...");
                    });
                    
                    // Generate research in background
                    generateResearch(wishId, wishText);
                }
                
                Platform.runLater(() -> {
                    statusLabel.setText("✓ Wish saved!");
                    statusLabel.setTextFill(Color.web("#4ade80"));
                    
                    // Close after brief delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(800);
                            Platform.runLater(WishInputDialog::hide);
                        } catch (InterruptedException ignored) {}
                    }).start();
                });
                
            } catch (Exception e) {
                logger.error("Failed to save wish", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    statusLabel.setTextFill(Color.web("#ef4444"));
                    inputField.setDisable(false);
                });
            }
        });
    }
    
    private static void generateResearch(long wishId, String wishText) {
        try {
            OpenAIClient client = new OpenAIClient();
            String article = client.generateResearchArticle(wishText);
            Database.updateWishResearch(wishId, article);
            logger.info("Research article generated for wish: {}", wishText);
        } catch (Exception e) {
            logger.error("Failed to generate research for wish: {}", wishText, e);
        }
    }
    
    public static void hide() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.hide();
                inputField.clear();
                inputField.setDisable(false);
                statusLabel.setText("Press Enter to save, Esc to cancel");
                statusLabel.setTextFill(Color.web("#888888"));
            }
        });
    }
}

