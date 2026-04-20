import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.sql.*;
import java.util.*;

public class SentenceBuilderApp extends Application {

    DBMan dbMan;
    CorpusParser corpusParser;
    Stage primaryStage;

    private BorderPane root;
    private StackPane  contentArea;

    private Button btnDashboard;
    private Button btnImport;
    private Button btnGenerate;
    private Button btnAutoComplete;
    private Button btnWordBrowser;
    private Button btnReports;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        dbMan = new DBMan();
        try {
            dbMan.connect();
        } catch (SQLException e) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not connect to database:\n" + e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        corpusParser = new CorpusParser(dbMan);

        stage.setTitle("Sentence Builder");
        stage.setOnCloseRequest(e -> {
            try { dbMan.disconnect(); } catch (SQLException ex) { ex.printStackTrace(); }
        });

        root        = new BorderPane();
        contentArea = new StackPane();

        root.setLeft(buildSidebar());
        root.setCenter(contentArea);

        showDashboard();

        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    // SIDEBAR
    private VBox buildSidebar() {
        VBox sb = new VBox();
        sb.setPrefWidth(180);
        sb.setStyle("-fx-background-color: #f0f0f0; " +
                    "-fx-border-color: #cccccc; " +
                    "-fx-border-width: 0 1 0 0;");

        Label title = new Label("Sentence\nBuilder");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 20 15 15 15;");
        title.setWrapText(true);

        btnDashboard    = navButton("Dashboard");
        btnImport       = navButton("Import Text");
        btnGenerate     = navButton("Generate");
        btnAutoComplete = navButton("Auto-complete");
        btnWordBrowser  = navButton("Word Browser");
        btnReports      = navButton("Reports");

        btnDashboard.setOnAction(e    -> showDashboard());
        btnImport.setOnAction(e       -> showImport());
        btnGenerate.setOnAction(e     -> showPlaceholder("Generate",     btnGenerate));
        btnAutoComplete.setOnAction(e -> showPlaceholder("Auto-complete", btnAutoComplete));
        btnWordBrowser.setOnAction(e  -> showPlaceholder("Word Browser",  btnWordBrowser));
        btnReports.setOnAction(e      -> showPlaceholder("Reports",       btnReports));

        sb.getChildren().addAll(
                title, new Separator(),
                btnDashboard, btnImport, btnGenerate,
                btnAutoComplete, btnWordBrowser, btnReports);
        return sb;
    }

    private Button navButton(String label) {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(navStyle(false));
        btn.setOnMouseEntered(e -> { if (!isActive(btn)) btn.setStyle(navStyle(true));  });
        btn.setOnMouseExited(e  -> { if (!isActive(btn)) btn.setStyle(navStyle(false)); });
        return btn;
    }

    private boolean isActive(Button btn) {
        return btn.getStyle().contains("-fx-font-weight: bold");
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
               "-fx-padding: 10 15 10 12;" +
               "-fx-font-size: 13px;" +
               "-fx-font-weight: bold;" +
               "-fx-cursor: hand;";
    }

    private void setActive(Button active) {
        for (Button b : new Button[]{btnDashboard, btnImport, btnGenerate,
                                      btnAutoComplete, btnWordBrowser, btnReports})
            b.setStyle(navStyle(false));
        active.setStyle(navStyleActive());
    }

    // DASHBOARD
    private void showDashboard() {
        setActive(btnDashboard);

        VBox page = new VBox(20);
        page.setPadding(new Insets(25));

        Label heading = new Label("Dashboard");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label totalWordsLabel  = statValue("...");
        Label uniqueWordsLabel = statValue("...");
        Label filesLabel       = statValue("...");

        HBox statsRow = new HBox(15,
                statCard("Total words",    totalWordsLabel),
                statCard("Unique words",   uniqueWordsLabel),
                statCard("Files imported", filesLabel));

        Label tableHeading = new Label("Recently imported files");
        tableHeading.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

        TableView<FileRow> table = buildImportTable();

        page.getChildren().addAll(heading, statsRow, tableHeading, table);
        contentArea.getChildren().setAll(page);

        Thread loader = new Thread(() -> {
            try {
                long tw = dbMan.getTotalWords();
                long uw = dbMan.getUniqueWords();
                long fc = dbMan.numImportedFiles();
                List<FileRow> rows = toFileRows(dbMan.getImportedFiles(10));
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

    // IMPORT TEXT SCREEN
    private void showImport() {
        setActive(btnImport);

        // Queue of files the user has selected but not yet parsed
        List<File> selectedFiles = new ArrayList<>();

        VBox page = new VBox(15);
        page.setPadding(new Insets(25));

        Label heading = new Label("Import Text");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // File picker row
        Button browseBtn = new Button("Browse...");
        browseBtn.setStyle("-fx-padding: 7 14 7 14;");

        Label selectedLabel = new Label("No files selected.");
        selectedLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        selectedLabel.setWrapText(true);

        HBox browseRow = new HBox(10, browseBtn, selectedLabel);
        browseRow.setAlignment(Pos.CENTER_LEFT);

        // Pending files list (files picked but not yet imported)
        Label pendingLabel = new Label("Files queued for import:");
        pendingLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

        ListView<String> pendingList = new ListView<>();
        pendingList.setPrefHeight(130);
        pendingList.setPlaceholder(new Label("No files selected yet."));

        // Remove selected file from queue button
        Button removeBtn = new Button("Remove selected");
        removeBtn.setStyle("-fx-padding: 5 12 5 12;");
        removeBtn.setDisable(true);

        removeBtn.setOnAction(e -> {
            int idx = pendingList.getSelectionModel().getSelectedIndex();
            if (idx >= 0) {
                selectedFiles.remove(idx);
                pendingList.getItems().remove(idx);
                selectedLabel.setText(selectedFiles.size() + " file(s) queued.");
                removeBtn.setDisable(selectedFiles.isEmpty());
            }
        });

        pendingList.getSelectionModel().selectedIndexProperty().addListener(
                (obs, o, n) -> removeBtn.setDisable(n.intValue() < 0));

        // Browse button opens FileChooser
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select text files to import");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt"));

            List<File> chosen = chooser.showOpenMultipleDialog(primaryStage);
            if (chosen != null && !chosen.isEmpty()) {
                for (File f : chosen) {
                    // Don't add duplicates to the queue
                    if (selectedFiles.stream().noneMatch(sf -> sf.getAbsolutePath()
                            .equals(f.getAbsolutePath()))) {
                        selectedFiles.add(f);
                        pendingList.getItems().add(f.getName() + "  — " + f.getParent());
                    }
                }
                selectedLabel.setText(selectedFiles.size() + " file(s) queued.");
                removeBtn.setDisable(false);
            }
        });

        // Status label
        Label statusLabel = new Label("Ready.");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #555555;");
        statusLabel.setWrapText(true);

        // Parse / Cancel buttons 
        Button parseBtn  = new Button("Parse Files");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setDisable(true);
        parseBtn.setStyle("-fx-background-color: #2E5090; -fx-text-fill: white; -fx-padding: 7 16 7 16;");
        cancelBtn.setStyle("-fx-padding: 7 16 7 16;");
        HBox buttonRow = new HBox(10, parseBtn, cancelBtn);

        // Import history table
        Label historyLabel = new Label("Import history");
        historyLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        TableView<FileRow> historyTable = buildImportTable();
        loadImportHistory(historyTable);

        page.getChildren().addAll(
                heading,
                browseRow,
                pendingLabel, pendingList, removeBtn,
                buttonRow, statusLabel,
                historyLabel, historyTable);

        contentArea.getChildren().setAll(page);

        // Parse button
        parseBtn.setOnAction(e -> {
            if (selectedFiles.isEmpty()) {
                statusLabel.setText("No files selected — click Browse to pick some.");
                return;
            }

            // Snapshot the list so the background thread has its own copy
            List<File> filesToParse = new ArrayList<>(selectedFiles);

            parseBtn.setDisable(true);
            browseBtn.setDisable(true);
            removeBtn.setDisable(true);
            cancelBtn.setDisable(false);
            statusLabel.setText("Starting...");

            corpusParser.setOnProgress(msg ->
                    Platform.runLater(() -> statusLabel.setText(msg)));

            Thread parseThread = new Thread(() -> {
                try {
                    corpusParser.parseFiles(filesToParse);

                    Platform.runLater(() -> {
                        statusLabel.setText(CorpusParser.cancelRequested
                                ? "Import cancelled." : "Import complete!");
                        // Clear the queue
                        selectedFiles.clear();
                        pendingList.getItems().clear();
                        selectedLabel.setText("No files selected.");
                        parseBtn.setDisable(false);
                        browseBtn.setDisable(false);
                        removeBtn.setDisable(true);
                        cancelBtn.setDisable(true);
                        loadImportHistory(historyTable);
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                        parseBtn.setDisable(false);
                        browseBtn.setDisable(false);
                        cancelBtn.setDisable(true);
                    });
                }
            });
            parseThread.setDaemon(true);
            parseThread.start();
        });

        cancelBtn.setOnAction(e -> {
            CorpusParser.cancelRequested = true;
            cancelBtn.setDisable(true);
            statusLabel.setText("Cancelling — finishing current batch...");
        });
    }

    private void loadImportHistory(TableView<FileRow> table) {
        Thread t = new Thread(() -> {
            try {
                List<FileRow> rows = toFileRows(dbMan.getImportedFiles(20));
                Platform.runLater(() -> table.getItems().setAll(rows));
            } catch (SQLException e) {
                Platform.runLater(() ->
                        table.setPlaceholder(new Label("Error loading history.")));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // PLACEHOLDER
    private void showPlaceholder(String name, Button btn) {
        setActive(btn);

        VBox page = new VBox(12);
        page.setPadding(new Insets(25));
        page.setAlignment(Pos.TOP_LEFT);

        Label heading = new Label(name);
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label sub = new Label("This screen is coming soon.");
        sub.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");

        page.getChildren().addAll(heading, sub);
        contentArea.getChildren().setAll(page);
    }

    // SHARED HELPERS
    private TableView<FileRow> buildImportTable() {
        TableView<FileRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(180);
        table.setPlaceholder(new Label("No files imported yet."));

        TableColumn<FileRow, String> colName  = new TableColumn<>("File name");
        TableColumn<FileRow, String> colWords = new TableColumn<>("Words");
        TableColumn<FileRow, String> colDate  = new TableColumn<>("Imported");

        colName.setCellValueFactory(d  ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().name));
        colWords.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().words));
        colDate.setCellValueFactory(d  ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().date));

        colName.setPrefWidth(340);
        colWords.setPrefWidth(100);
        colDate.setPrefWidth(140);

        table.getColumns().addAll(colName, colWords, colDate);
        return table;
    }

    private List<FileRow> toFileRows(List<ImportedFile> files) {
        List<FileRow> rows = new ArrayList<>();
        for (ImportedFile f : files)
            rows.add(new FileRow(f.fileName,
                    String.format("%,d", f.wordCount), f.importDate));
        return rows;
    }

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

    public static class FileRow {
        String name, words, date;
        FileRow(String name, String words, String date) {
            this.name = name; this.words = words; this.date = date;
        }
    }
}
