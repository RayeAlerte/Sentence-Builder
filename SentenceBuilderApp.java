import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.Stage;
import java.sql.*;
import java.util.*;

public class SentenceBuilderApp extends Application {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/BuilderWords";
    private static final String DB_USER = "sentencebuilder";
    private static final String DB_PASS = "Yo457S<DWL.D";

    DBMan dbMan;

    private BorderPane root;
    private VBox sidebar;
    private StackPane contentArea;

    private Button btnDashboard;
    private Button btnImport;
    private Button btnGenerate;
    private Button btnAutoComplete;
    private Button btnWordBrowser;
    private Button btnReports;

    @Override
    public void start(Stage stage) {

        dbMan = new DBMan();
        try {
            dbMan.connect();
        } catch (SQLException e) {
            Platform.exit();
            return;
        }

        stage.setTitle("Sentence Builder");

        root = new BorderPane();
        sidebar = buildSidebar();
        contentArea = new StackPane();

        root.setLeft(sidebar);
        root.setCenter(contentArea);

        showDashboard();

        Scene scene = new Scene(root, 900, 600);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildSidebar() {
        VBox sb = new VBox();
        sb.setPrefWidth(180);
        sb.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 1 0 0;");

        Label title = new Label("Sentence\nBuilder");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 20 15 15 15;");
        title.setWrapText(true);

        Separator sep = new Separator();

        btnDashboard = navButton("Dashboard");
        btnImport = navButton("Import Text");
        btnGenerate = navButton("Generate");
        btnAutoComplete = navButton("Auto-complete");
        btnWordBrowser = navButton("Word Browser");
        btnReports = navButton("Reports");

        btnDashboard.setOnAction(e -> showDashboard());
        btnImport.setOnAction(e -> showPlaceholder("Import Text"));
        btnGenerate.setOnAction(e -> showPlaceholder("Generate"));
        btnAutoComplete.setOnAction(e -> showPlaceholder("Auto-complete"));
        btnWordBrowser.setOnAction(e -> showPlaceholder("Word Browser"));
        btnReports.setOnAction(e -> showPlaceholder("Reports"));

        sb.getChildren().addAll(
                title, sep,
                btnDashboard, btnImport, btnGenerate,
                btnAutoComplete, btnWordBrowser, btnReports);

        return sb;
    }

    private Button navButton(String label) {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(navStyle(false));
        btn.setOnMouseEntered(e -> {
            if (!btn.getStyle().contains("#d0d0d0")) // not already active
                btn.setStyle(navStyle(true));
        });
        btn.setOnMouseExited(e -> {
            if (!btn.getStyle().contains("#d0d0d0"))
                btn.setStyle(navStyle(false));
        });
        return btn;
    }

    private String navStyle(boolean hover) {
        String bg = hover ? "#e0e0e0" : "transparent";
        return "-fx-background-color: " + bg + ";" +
                "-fx-border-color: transparent;" +
                "-fx-padding: 10 15 10 15;" +
                "-fx-font-size: 13px;" +
                "-fx-cursor: hand;";
    }

    private String navStyleActive() {
        return "-fx-background-color: #ffffff;" +
                "-fx-border-color: transparent;" +
                "-fx-border-width: 0 0 0 3;" +
                "-fx-padding: 10 15 10 12;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-cursor: hand;";
    }

    private void setActiveButton(Button active) {
        for (Button b : new Button[] { btnDashboard, btnImport, btnGenerate,
                btnAutoComplete, btnWordBrowser, btnReports }) {
            b.setStyle(navStyle(false));
        }
        active.setStyle(navStyleActive());
    }

    // DASHBOARD SCREEN
    private void showDashboard() {
        setActiveButton(btnDashboard);

        VBox page = new VBox(20);
        page.setPadding(new Insets(25));

        Label heading = new Label("Dashboard");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // Stat cards row
        Label totalWordsLabel = statValue("...");
        Label uniqueWordsLabel = statValue("...");
        Label filesLabel = statValue("...");

        HBox statsRow = new HBox(15,
                statCard("Total words", totalWordsLabel),
                statCard("Unique words", uniqueWordsLabel),
                statCard("Files imported", filesLabel));

        Label tableHeading = new Label("Recently imported files");
        tableHeading.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

        TableView<FileRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(200);
        table.setPlaceholder(new Label("No files imported yet."));

        TableColumn<FileRow, String> colName = new TableColumn<>("File name");
        TableColumn<FileRow, String> colWords = new TableColumn<>("Words");
        TableColumn<FileRow, String> colDate = new TableColumn<>("Imported");

        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().name));
        colWords.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().words));
        colDate.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().date));

        colName.setPrefWidth(340);
        colWords.setPrefWidth(100);
        colDate.setPrefWidth(140);

        table.getColumns().addAll(colName, colWords, colDate);

        page.getChildren().addAll(heading, statsRow, tableHeading, table);
        contentArea.getChildren().setAll(page);

        Thread loader = new Thread(() -> {
            try {

                long totalWords = dbMan.getTotalWords();

                long uniqueWords = dbMan.getUniqueWords();

                long fileCount = dbMan.numImportedFiles();

                // Recent files
                java.util.List<FileRow> rows = new java.util.ArrayList<>();
                List<ImportedFile> imported_files = dbMan.getImportedFiles(10);
                /* TODO:THIS IS REALLY GROSS, JUST REPLACE FileRow LATER */
                for (ImportedFile file : imported_files) {
                    rows.add(new FileRow(file.fileName, Integer.toString(file.wordCount), file.importDate));
                }
                // Push results back to UI thread
                final long tw = totalWords, uw = uniqueWords, fc = fileCount;
                Platform.runLater(() -> {
                    totalWordsLabel.setText(String.format("%,d", tw));
                    uniqueWordsLabel.setText(String.format("%,d", uw));
                    filesLabel.setText(String.valueOf(fc));
                    table.getItems().setAll(rows);
                });

            } catch (SQLException e) {
                Platform.runLater(() -> {
                    totalWordsLabel.setText("DB error");
                    uniqueWordsLabel.setText("DB error");
                    filesLabel.setText("DB error");
                });
            }
        });
        loader.setDaemon(true);
        loader.start();
    }

    // PLACEHOLDER SCREEN (for unbuilt screens)
    private void showPlaceholder(String screenName) {
        switch (screenName) {
            case "Import Text" -> setActiveButton(btnImport);
            case "Generate" -> setActiveButton(btnGenerate);
            case "Auto-complete" -> setActiveButton(btnAutoComplete);
            case "Word Browser" -> setActiveButton(btnWordBrowser);
            case "Reports" -> setActiveButton(btnReports);
        }

        VBox page = new VBox(12);
        page.setPadding(new Insets(25));
        page.setAlignment(Pos.TOP_LEFT);

        Label heading = new Label(screenName);
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label sub = new Label("This screen is coming soon.");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");

        page.getChildren().addAll(heading, sub);
        contentArea.getChildren().setAll(page);
    }

    // HELPERS
    private VBox statCard(String labelText, Label valueLabel) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(15));
        card.setPrefWidth(160);
        card.setStyle("-fx-background-color: #f5f5f5;" +
                "-fx-border-color: #dddddd;" +
                "-fx-border-radius: 6;" +
                "-fx-background-radius: 6;");

        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");

        card.getChildren().addAll(lbl, valueLabel);
        return card;
    }

    private Label statValue(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        return l;
    }

    // Simple data class for the table
    public static class FileRow {
        String name, words, date;

        FileRow(String name, String words, String date) {
            this.name = name;
            this.words = words;
            this.date = date;
        }
    }
}
