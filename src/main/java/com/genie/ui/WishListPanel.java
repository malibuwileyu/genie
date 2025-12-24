package com.genie.ui;

import com.genie.util.Database;
import com.genie.util.Database.Wish;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
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
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Panel showing all wishes and their research articles
 * Uses WebView + Flexmark for beautiful markdown rendering
 */
public class WishListPanel {
    
    private static final Logger logger = LoggerFactory.getLogger(WishListPanel.class);
    
    private static Stage stage;
    private static Parser markdownParser;
    private static HtmlRenderer htmlRenderer;
    
    // Dark theme CSS for the rendered HTML
    private static final String HTML_CSS = """
        <style>
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                font-size: 14px;
                line-height: 1.6;
                color: #e0e0e0;
                background-color: #2a2a4a;
                padding: 16px;
                margin: 0;
            }
            h1 { color: #a78bfa; font-size: 1.6em; border-bottom: 1px solid #4a4a6a; padding-bottom: 8px; }
            h2 { color: #8b5cf6; font-size: 1.3em; margin-top: 20px; }
            h3 { color: #7c3aed; font-size: 1.1em; margin-top: 16px; }
            p { margin: 12px 0; }
            ul, ol { margin: 12px 0; padding-left: 24px; }
            li { margin: 6px 0; }
            code {
                background-color: #1a1a2e;
                padding: 2px 6px;
                border-radius: 4px;
                font-family: 'SF Mono', Monaco, monospace;
                font-size: 0.9em;
                color: #f472b6;
            }
            pre {
                background-color: #1a1a2e;
                padding: 12px;
                border-radius: 8px;
                overflow-x: auto;
            }
            pre code {
                background: none;
                padding: 0;
                color: #e0e0e0;
            }
            blockquote {
                border-left: 3px solid #6a4c93;
                margin: 16px 0;
                padding-left: 16px;
                color: #aaaaaa;
            }
            a { color: #60a5fa; text-decoration: none; }
            a:hover { text-decoration: underline; }
            strong { color: #f0f0f0; }
            em { color: #d4d4d4; }
            hr { border: none; border-top: 1px solid #4a4a6a; margin: 20px 0; }
            table { border-collapse: collapse; width: 100%; margin: 16px 0; }
            th, td { border: 1px solid #4a4a6a; padding: 8px 12px; text-align: left; }
            th { background-color: #3a3a5a; color: #e0e0e0; }
        </style>
        """;
    
    static {
        // Initialize Flexmark parser and renderer
        MutableDataSet options = new MutableDataSet();
        markdownParser = Parser.builder(options).build();
        htmlRenderer = HtmlRenderer.builder(options).build();
    }
    
    public static void show() {
        Platform.runLater(WishListPanel::createAndShow);
    }
    
    private static void createAndShow() {
        if (stage != null && stage.isShowing()) {
            stage.toFront();
            return;
        }
        
        stage = new Stage();
        stage.setTitle("🧞 Your Wishes");
        
        List<Wish> wishes = Database.getWishes(false);
        
        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #1a1a2e;");
        
        // Title
        Label title = new Label("🧞 Your Wishes (" + wishes.size() + ")");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#e0e0e0"));
        
        if (wishes.isEmpty()) {
            Label empty = new Label("No wishes yet!\nSay \"Hey Genie\" or press Ctrl+Option+W to make your first wish.");
            empty.setTextFill(Color.web("#888888"));
            empty.setFont(Font.font("System", 14));
            empty.setStyle("-fx-text-alignment: center;");
            root.getChildren().addAll(title, empty);
        } else {
            // Split pane: list on left, article on right
            SplitPane splitPane = new SplitPane();
            splitPane.setStyle("-fx-background-color: #1a1a2e;");
            
            // Wish list
            VBox wishList = new VBox(8);
            wishList.setPadding(new Insets(10));
            wishList.setStyle("-fx-background-color: #1a1a2e;");
            
            // Article view with WebView for rich rendering
            VBox articleView = new VBox(10);
            articleView.setPadding(new Insets(10));
            articleView.setStyle("-fx-background-color: #2a2a4a;");
            
            Label articleTitle = new Label("Select a wish to view research");
            articleTitle.setTextFill(Color.web("#888888"));
            articleTitle.setFont(Font.font("System", FontWeight.BOLD, 14));
            
            // WebView for markdown rendering
            WebView webView = new WebView();
            webView.setStyle("-fx-background-color: #2a2a4a;");
            VBox.setVgrow(webView, Priority.ALWAYS);
            
            // Initial content
            String initialHtml = wrapHtml("<p style='color: #888888; text-align: center; margin-top: 40px;'>Select a wish to view its research article</p>");
            webView.getEngine().loadContent(initialHtml);
            
            articleView.getChildren().addAll(articleTitle, webView);
            
            for (Wish wish : wishes) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 12, 10, 12));
                row.setStyle("""
                    -fx-background-color: #2a2a4a;
                    -fx-background-radius: 6;
                    -fx-cursor: hand;
                    """);
                
                // Status indicator
                Label status = new Label(wish.isResearched ? "✅" : "⏳");
                
                // Wish text
                Label wishText = new Label(truncate(wish.wishText, 40));
                wishText.setTextFill(Color.web("#e0e0e0"));
                HBox.setHgrow(wishText, Priority.ALWAYS);
                
                row.getChildren().addAll(status, wishText);
                
                // Click to show article
                row.setOnMouseClicked(e -> {
                    articleTitle.setText("📖 " + wish.wishText);
                    articleTitle.setTextFill(Color.web("#e0e0e0"));
                    
                    if (wish.isResearched && wish.researchArticle != null) {
                        // Convert markdown to HTML and display
                        String html = markdownToHtml(wish.researchArticle);
                        webView.getEngine().loadContent(wrapHtml(html));
                    } else {
                        String pendingHtml = """
                            <div style='text-align: center; margin-top: 40px;'>
                                <p style='font-size: 48px;'>⏳</p>
                                <h2>Research Pending</h2>
                                <p style='color: #888888;'>The AI is generating an article for this topic...</p>
                            </div>
                            """;
                        webView.getEngine().loadContent(wrapHtml(pendingHtml));
                    }
                });
                
                // Hover
                row.setOnMouseEntered(e -> row.setStyle("""
                    -fx-background-color: #3a3a5a;
                    -fx-background-radius: 6;
                    -fx-cursor: hand;
                    """));
                row.setOnMouseExited(e -> row.setStyle("""
                    -fx-background-color: #2a2a4a;
                    -fx-background-radius: 6;
                    -fx-cursor: hand;
                    """));
                
                wishList.getChildren().add(row);
            }
            
            ScrollPane listScroll = new ScrollPane(wishList);
            listScroll.setFitToWidth(true);
            listScroll.setStyle("-fx-background: #1a1a2e; -fx-background-color: #1a1a2e;");
            
            splitPane.getItems().addAll(listScroll, articleView);
            splitPane.setDividerPositions(0.35);
            VBox.setVgrow(splitPane, Priority.ALWAYS);
            
            root.getChildren().addAll(title, splitPane);
        }
        
        Scene scene = new Scene(root, 800, 600);
        scene.setFill(Color.web("#1a1a2e"));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        
        stage.setScene(scene);
        stage.show();
        
        logger.info("Wish list panel shown");
    }
    
    /**
     * Convert markdown to HTML using Flexmark
     */
    private static String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "<p>No content</p>";
        }
        Node document = markdownParser.parse(markdown);
        return htmlRenderer.render(document);
    }
    
    /**
     * Wrap HTML content with full document structure and CSS
     */
    private static String wrapHtml(String bodyContent) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" + HTML_CSS + "</head><body>" + bodyContent + "</body></html>";
    }
    
    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
    
    public static void hide() {
        Platform.runLater(() -> {
            if (stage != null) stage.close();
        });
    }
}
