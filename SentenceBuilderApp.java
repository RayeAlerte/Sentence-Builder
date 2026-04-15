import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class SentenceBuilderApp extends Application {

    DBMan dbMan;
    CorpusParser corpusParser;

    private BorderPane root;
    private VBox       sidebar;
    private StackPane  contentArea;

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
            new Alert(Alert.AlertType.ERROR, "Could not connect to database:\n" + e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        corpusParser = new CorpusParser(dbMan);

        stage.setTitle("Sentence Builder");
        stage.setOnCloseRequest(e -> {
            try { dbMan.disconnect(); } catch (SQLException ex) { ex.printStackTrace(); }
        });

        root        = new BorderPane();
        sidebar     = buildSidebar();
        contentArea = new StackPane();

        root.setLeft(sidebar);
        root.setCenter(contentArea);

        showDashboard();

        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    private VBox buildSidebar() {
        VBox sb = new VBox();
        sb.setPrefWidth(180);
        sb.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 0 1 0 0;");

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
                                      btnAutoComplete, btnWordBrowser, btnReports}) {
            b.setStyle(navStyle(false));
        }
        active.setStyle(navStyleActive());
    }

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

    private void showImport() {
        setActive(btnImport);

        VBox page = new VBox(15);
        page.setPadding(new Insets(25));

        Label heading = new Label("Import Text");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        CheckBox gutenbergCheck = new CheckBox("Include Gutenberg subfolder");
        CheckBox cocaCheck      = new CheckBox("Include COCA subfolder");
        gutenbergCheck.setSelected(CorpusParser.includeGutenberg);
        cocaCheck.setSelected(CorpusParser.includeCOCA);

        // File list showing pending (not yet imported) files
        Label fileLabel = new Label("Files pending import:");
        fileLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

        ListView<String> fileList = new ListView<>();
        fileList.setPrefHeight(150);
        refreshFileList(fileList);

        gutenbergCheck.setOnAction(e -> {
            CorpusParser.includeGutenberg = gutenbergCheck.isSelected();
            refreshFileList(fileList);
        });
        cocaCheck.setOnAction(e -> {
            CorpusParser.includeCOCA = cocaCheck.isSelected();
            refreshFileList(fileList);
        });

        Label statusLabel = new Label("Ready.");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #555555;");
        statusLabel.setWrapText(true);

        // Buttons
        Button parseBtn  = new Button("Parse Text");
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
                gutenbergCheck, cocaCheck,
                fileLabel, fileList,
                buttonRow, statusLabel,
                historyLabel, historyTable);

        contentArea.getChildren().setAll(page);

        // Parse button wires the progress callback and kicks off the background thread
        parseBtn.setOnAction(e -> {
            if (fileList.getItems().isEmpty()
                    || fileList.getItems().get(0).startsWith("No ")
                    || fileList.getItems().get(0).startsWith("./DataSources")) {
                statusLabel.setText("No new files to import.");
                return;
            }

            parseBtn.setDisable(true);
            cancelBtn.setDisable(false);
            gutenbergCheck.setDisable(true);
            cocaCheck.setDisable(true);
            statusLabel.setText("Starting...");

            // Every time CorpusParser calls reportProgress(), update the label
            corpusParser.setOnProgress(msg ->
                    Platform.runLater(() -> statusLabel.setText(msg)));

            Thread parseThread = new Thread(() -> {
                try {
                    corpusParser.parseDataSources();

                    Platform.runLater(() -> {
                        statusLabel.setText(CorpusParser.cancelRequested
                                ? "Import cancelled." : "Import complete!");
                        parseBtn.setDisable(false);
                        cancelBtn.setDisable(true);
                        gutenbergCheck.setDisable(false);
                        cocaCheck.setDisable(false);
                        refreshFileList(fileList);
                        loadImportHistory(historyTable);
                    });

                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + ex.getMessage());
                        parseBtn.setDisable(false);
                        cancelBtn.setDisable(true);
                        gutenbergCheck.setDisable(false);
                        cocaCheck.setDisable(false);
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

    private void refreshFileList(ListView<String> fileList) {
        fileList.getItems().clear();
        Path rootData = Paths.get("./DataSources");

        if (!Files.exists(rootData)) {
            fileList.getItems().add("./DataSources folder not found.");
            return;
        }

        try {
            Set<String> alreadyImported = dbMan.getImportedFileNames();

            addPendingFiles(fileList, rootData, "", alreadyImported);

            if (CorpusParser.includeGutenberg)
                addPendingFiles(fileList, rootData.resolve("Gutenberg"), "Gutenberg/", alreadyImported);

            if (CorpusParser.includeCOCA)
                addPendingFiles(fileList, rootData.resolve("CocaText"), "CocaText/", alreadyImported);

            if (fileList.getItems().isEmpty())
                fileList.getItems().add("No new files to import.");

        } catch (Exception ex) {
            fileList.getItems().add("Error reading DataSources: " + ex.getMessage());
        }
    }

    private void addPendingFiles(ListView<String> list, Path dir,
                                  String prefix, Set<String> alreadyImported) throws Exception {
        if (!Files.exists(dir)) return;
        Files.list(dir)
             .filter(Files::isRegularFile)
             .filter(p -> p.toString().endsWith(".txt"))
             .filter(p -> !alreadyImported.contains(p.getFileName().toString()))
             .map(p -> prefix + p.getFileName().toString())
             .sorted()
             .forEach(list.getItems()::add);
    }

    private void loadImportHistory(TableView<FileRow> table) {
        Thread t = new Thread(() -> {
            try {
                List<FileRow> rows = toFileRows(dbMan.getImportedFiles(20));
                Platform.runLater(() -> table.getItems().setAll(rows));
            } catch (SQLException e) {
                Platform.runLater(() -> table.setPlaceholder(new Label("Error loading history.")));
            }
        });
        t.setDaemon(true);
        t.start();
    }

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

    private TableView<FileRow> buildImportTable() {
        TableView<FileRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(180);
        table.setPlaceholder(new Label("No files imported yet."));

        TableColumn<FileRow, String> colName  = new TableColumn<>("File name");
        TableColumn<FileRow, String> colWords = new TableColumn<>("Words");
        TableColumn<FileRow, String> colDate  = new TableColumn<>("Imported");

        colName.setCellValueFactory(d  -> new javafx.beans.property.SimpleStringProperty(d.getValue().name));
        colWords.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().words));
        colDate.setCellValueFactory(d  -> new javafx.beans.property.SimpleStringProperty(d.getValue().date));

        colName.setPrefWidth(340);
        colWords.setPrefWidth(100);
        colDate.setPrefWidth(140);

        table.getColumns().addAll(colName, colWords, colDate);
        return table;
    }

    private List<FileRow> toFileRows(List<ImportedFile> files) {
        List<FileRow> rows = new ArrayList<>();
        for (ImportedFile f : files)
            rows.add(new FileRow(f.fileName, String.format("%,d", f.wordCount), f.importDate));
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
