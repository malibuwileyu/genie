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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings dialog for configuring Genie
 */
public class SettingsDialog {
    
    private static final Logger logger = LoggerFactory.getLogger(SettingsDialog.class);
    
    private static Stage stage;
    
    public static void show() {
        Platform.runLater(SettingsDialog::createAndShow);
    }
    
    private static void createAndShow() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        
        stage = new Stage();
        stage.setTitle("⚙️ Genie Settings");
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1a2e;");
        
        // Title
        Label title = new Label("⚙️ Settings");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#e0e0e0"));
        
        // OpenAI API Key section
        VBox apiSection = new VBox(8);
        
        Label apiLabel = new Label("OpenAI API Key");
        apiLabel.setTextFill(Color.web("#aaaaaa"));
        apiLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        PasswordField apiKeyField = new PasswordField();
        apiKeyField.setPromptText("sk-...");
        apiKeyField.setPrefWidth(400);
        apiKeyField.setStyle("""
            -fx-background-color: #2a2a4a;
            -fx-text-fill: #ffffff;
            -fx-prompt-text-fill: #666666;
            -fx-background-radius: 6;
            -fx-padding: 10;
            """);
        
        // Load existing key (masked)
        String existingKey = Config.getOpenAiApiKey();
        if (existingKey != null && !existingKey.isEmpty()) {
            apiKeyField.setText(existingKey);
        }
        
        Label apiHelp = new Label("Get your API key from platform.openai.com/api-keys");
        apiHelp.setTextFill(Color.web("#666666"));
        apiHelp.setFont(Font.font("System", 11));
        
        // Test button
        Button testBtn = new Button("Test Connection");
        testBtn.setStyle("""
            -fx-background-color: #3a3a5a;
            -fx-text-fill: #cccccc;
            -fx-background-radius: 6;
            -fx-padding: 8 16;
            -fx-cursor: hand;
            """);
        
        Label testResult = new Label("");
        testResult.setFont(Font.font("System", 11));
        
        testBtn.setOnAction(e -> {
            String key = apiKeyField.getText().trim();
            if (key.isEmpty()) {
                testResult.setText("Please enter an API key");
                testResult.setTextFill(Color.web("#ef4444"));
                return;
            }
            
            testBtn.setDisable(true);
            testResult.setText("Testing...");
            testResult.setTextFill(Color.web("#888888"));
            
            // Save temporarily for test
            Config.setOpenAiApiKey(key);
            
            new Thread(() -> {
                try {
                    OpenAIClient client = new OpenAIClient();
                    boolean success = client.testConnection();
                    
                    Platform.runLater(() -> {
                        if (success) {
                            testResult.setText("✓ Connection successful!");
                            testResult.setTextFill(Color.web("#4ade80"));
                        } else {
                            testResult.setText("✗ Connection failed");
                            testResult.setTextFill(Color.web("#ef4444"));
                        }
                        testBtn.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        testResult.setText("✗ Error: " + ex.getMessage());
                        testResult.setTextFill(Color.web("#ef4444"));
                        testBtn.setDisable(false);
                    });
                }
            }).start();
        });
        
        HBox testRow = new HBox(12, testBtn, testResult);
        testRow.setAlignment(Pos.CENTER_LEFT);
        
        apiSection.getChildren().addAll(apiLabel, apiKeyField, apiHelp, testRow);
        
        // Separator
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #3a3a5a;");
        
        // Hotkeys info (read-only for now)
        VBox hotkeySection = new VBox(8);
        
        Label hotkeyLabel = new Label("Hotkeys");
        hotkeyLabel.setTextFill(Color.web("#aaaaaa"));
        hotkeyLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        GridPane hotkeyGrid = new GridPane();
        hotkeyGrid.setHgap(20);
        hotkeyGrid.setVgap(8);
        
        addHotkeyRow(hotkeyGrid, 0, "Save Context", "Ctrl+Option+C");
        addHotkeyRow(hotkeyGrid, 1, "Make a Wish", "Ctrl+Option+W");
        
        Label hotkeyNote = new Label("Hotkey customization coming soon");
        hotkeyNote.setTextFill(Color.web("#666666"));
        hotkeyNote.setFont(Font.font("System", javafx.scene.text.FontPosture.ITALIC, 11));
        
        hotkeySection.getChildren().addAll(hotkeyLabel, hotkeyGrid, hotkeyNote);
        
        // Separator 2
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color: #3a3a5a;");
        
        // Voice Activation section
        VBox voiceSection = createVoiceSection();
        
        // Buttons
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("""
            -fx-background-color: #3a3a5a;
            -fx-text-fill: #cccccc;
            -fx-background-radius: 6;
            -fx-padding: 10 20;
            -fx-cursor: hand;
            """);
        cancelBtn.setOnAction(e -> stage.close());
        
        Button saveBtn = new Button("Save");
        saveBtn.setStyle("""
            -fx-background-color: #6a4c93;
            -fx-text-fill: #ffffff;
            -fx-background-radius: 6;
            -fx-padding: 10 20;
            -fx-cursor: hand;
            """);
        saveBtn.setOnAction(e -> {
            String oldKey = Config.getOpenAiApiKey();
            boolean hadNoKey = oldKey == null || oldKey.isEmpty() || !oldKey.startsWith("sk-");
            
            String key = apiKeyField.getText().trim();
            Config.setOpenAiApiKey(key);
            Config.save();
            logger.info("Settings saved");
            stage.close();
            
            // If we just added an API key, check for pending wishes
            boolean hasNewKey = key != null && !key.isEmpty() && key.startsWith("sk-");
            if (hadNoKey && hasNewKey) {
                researchPendingWishes();
            }
        });
        
        buttons.getChildren().addAll(cancelBtn, saveBtn);
        
        root.getChildren().addAll(title, apiSection, sep, hotkeySection, sep2, voiceSection, buttons);
        
        // Use computed size - let JavaFX auto-size to fit content
        Scene scene = new Scene(root);
        scene.setFill(Color.web("#1a1a2e")); // Match background to prevent white edges
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        
        stage.setScene(scene);
        stage.setResizable(false); // Fixed size to prevent resize artifacts
        
        // Auto-size to fit content, then show
        stage.sizeToScene();
        stage.show();
        
        logger.info("Settings dialog shown");
    }
    
    private static void addHotkeyRow(GridPane grid, int row, String action, String hotkey) {
        Label actionLabel = new Label(action);
        actionLabel.setTextFill(Color.web("#cccccc"));
        
        Label hotkeyLabel = new Label(hotkey);
        hotkeyLabel.setTextFill(Color.web("#888888"));
        hotkeyLabel.setFont(Font.font("Monospace", 12));
        hotkeyLabel.setStyle("""
            -fx-background-color: #2a2a4a;
            -fx-padding: 4 8;
            -fx-background-radius: 4;
            """);
        
        grid.add(actionLabel, 0, row);
        grid.add(hotkeyLabel, 1, row);
    }
    
    private static VBox createVoiceSection() {
        VBox voiceSection = new VBox(10);
        
        Label voiceLabel = new Label("🎤 Voice Activation");
        voiceLabel.setTextFill(Color.web("#aaaaaa"));
        voiceLabel.setFont(Font.font("System", FontWeight.BOLD, 12));
        
        // Radio buttons for mode selection
        ToggleGroup modeGroup = new ToggleGroup();
        
        RadioButton hotkeyMode = new RadioButton("Hotkey mode (Ctrl+Shift+1 to make a wish)");
        hotkeyMode.setToggleGroup(modeGroup);
        hotkeyMode.setTextFill(Color.web("#cccccc"));
        hotkeyMode.setStyle("-fx-font-size: 12px;");
        
        RadioButton alwaysListeningMode = new RadioButton("Always-listening mode (say \"Hey Genie\" or \"Hey Jeannie\")");
        alwaysListeningMode.setToggleGroup(modeGroup);
        alwaysListeningMode.setTextFill(Color.web("#cccccc"));
        alwaysListeningMode.setStyle("-fx-font-size: 12px;");
        
        // Set current selection
        if (Config.isAlwaysListeningEnabled()) {
            alwaysListeningMode.setSelected(true);
        } else {
            hotkeyMode.setSelected(true);
        }
        
        // Voice engine info
        String os = System.getProperty("os.name");
        String engineInfo;
        if (os.toLowerCase().contains("mac")) {
            engineInfo = "Using: macOS Native (SFSpeechRecognizer) — best quality";
        } else {
            engineInfo = "Using: Vosk (offline) — cross-platform";
        }
        
        Label engineLabel = new Label(engineInfo);
        engineLabel.setTextFill(Color.web("#666666"));
        engineLabel.setFont(Font.font("System", 11));
        
        // Warning about microphone permission (always visible for consistent sizing)
        Label permissionNote = new Label("⚠️ Always-listening requires microphone permission");
        permissionNote.setFont(Font.font("System", 10));
        
        // Set initial visibility based on current setting
        boolean initialAlwaysListening = Config.isAlwaysListeningEnabled();
        permissionNote.setTextFill(initialAlwaysListening ? Color.web("#f59e0b") : Color.web("#444444"));
        
        // Update warning color when mode changes
        modeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isAlwaysListening = newVal == alwaysListeningMode;
            permissionNote.setTextFill(isAlwaysListening ? Color.web("#f59e0b") : Color.web("#444444"));
            
            // Save preference
            Config.setAlwaysListeningEnabled(isAlwaysListening);
            
            // Update VoiceManager
            com.genie.voice.VoiceManager.getInstance().setAlwaysListeningEnabled(isAlwaysListening);
        });
        
        voiceSection.getChildren().addAll(voiceLabel, hotkeyMode, alwaysListeningMode, engineLabel, permissionNote);
        
        return voiceSection;
    }
    
    public static void hide() {
        Platform.runLater(() -> {
            if (stage != null) stage.close();
        });
    }
    
    /**
     * Research any pending wishes that were saved without an API key
     */
    private static void researchPendingWishes() {
        var pendingWishes = Database.getWishes(true); // unresearched only
        
        if (pendingWishes.isEmpty()) {
            return;
        }
        
        int count = pendingWishes.size();
        logger.info("Found {} pending wishes to research", count);
        
        // Notify user
        ErrorToast.showInfo(
            "Researching Pending Wishes",
            String.format("You have %d wish%s waiting to be researched. Starting now...", 
                count, count == 1 ? "" : "es")
        );
        
        // Research in background
        new Thread(() -> {
            OpenAIClient client = new OpenAIClient();
            int success = 0;
            int failed = 0;
            
            for (var wish : pendingWishes) {
                try {
                    String article = client.generateResearchArticle(wish.wishText);
                    Database.updateWishResearch(wish.id, article);
                    success++;
                    logger.info("Researched pending wish {}: {}", wish.id, wish.wishText);
                    
                    // Small delay to avoid rate limiting
                    Thread.sleep(500);
                } catch (Exception e) {
                    failed++;
                    logger.error("Failed to research wish {}: {}", wish.id, e.getMessage());
                }
            }
            
            // Notify completion
            final int s = success;
            final int f = failed;
            Platform.runLater(() -> {
                if (f == 0) {
                    ErrorToast.showSuccess(
                        "Research Complete",
                        String.format("All %d pending wish%s researched!", s, s == 1 ? "" : "es")
                    );
                } else {
                    ErrorToast.showWarning(
                        "Research Partially Complete",
                        String.format("Researched %d wish%s, %d failed. Check View Wishes for details.", 
                            s, s == 1 ? "" : "es", f)
                    );
                }
            });
        }, "PendingWishResearcher").start();
    }
}

