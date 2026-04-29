import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.sql.*;
import java.util.*;

public class SentenceBuilderApp extends Application {

    DBMan dbMan;
    CorpusParser corpusParser;
    SentenceBuilder sentenceBuilder;
    Reporter reporter;
    Stage primaryStage;

    // Track whether SentenceBuilder has been loaded into memory
    private boolean modelLoaded = false;

    private BorderPane root;
    private StackPane contentArea;

    private Button btnDashboard;
    private Button btnImport;
    private Button btnGenerate;
    private Button btnAutoComplete;
    private Button btnReports;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        dbMan = new DBMan();
        try 
        {
            dbMan.connect();
        } 
        catch (SQLException e) 
        {
            new Alert(Alert.AlertType.ERROR,
                    "Could not connect to database:\n" + e.getMessage()).showAndWait();
            Platform.exit();
            return;
        }

        corpusParser = new CorpusParser(dbMan);
        reporter = new Reporter(dbMan);
        sentenceBuilder = new SentenceBuilder(dbMan, reporter);

        stage.setTitle("Sentence Builder");
        stage.setOnCloseRequest(e -> {
            try {
                dbMan.disconnect();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        root = new BorderPane();
        contentArea = new StackPane();

        root.setLeft(buildSidebar());
        root.setCenter(contentArea);

        showDashboard();

        stage.setScene(new Scene(root, 900, 600));
        stage.show();
    }

    // Sidebar
    private VBox buildSidebar() 
    {
        VBox sb = new VBox();
        sb.setPrefWidth(180);
        sb.setStyle("-fx-background-color: #f0f0f0; " +
                    "-fx-border-color: #cccccc; " +
                    "-fx-border-width: 0 1 0 0;");

        Label title = new Label("Sentence\nBuilder");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 20 15 15 15;");
        title.setWrapText(true);

        btnDashboard = navButton("Dashboard");
        btnImport = navButton("Import Text");
        btnGenerate = navButton("Generate");
        btnAutoComplete = navButton("Auto-complete");
        btnReports = navButton("Reports");

        btnDashboard.setOnAction(e -> showDashboard());
        btnImport.setOnAction(e -> showImport());
        btnGenerate.setOnAction(e -> showGenerate());
        btnAutoComplete.setOnAction(e -> showAutoComplete());
        btnReports.setOnAction(e -> showReports());

        sb.getChildren().addAll(
                title, new Separator(),
                btnDashboard, btnImport, btnGenerate,
                btnAutoComplete, btnReports);
        return sb;
    }

    private Button navButton(String label) 
    {
        Button btn = new Button(label);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle(navStyle(false));
        btn.setOnMouseEntered(e -> { if (!isActive(btn)) btn.setStyle(navStyle(true)); });
        btn.setOnMouseExited(e -> { if (!isActive(btn)) btn.setStyle(navStyle(false)); });
        return btn;
    }

    private boolean isActive(Button btn) 
    {
        return btn.getStyle().contains("-fx-font-weight: bold");
    }

    private String navStyle(boolean hover) 
    {
        String bg = hover ? "#e0e0e0" : "transparent";
        return "-fx-background-color: " + bg + ";" +
               "-fx-border-color: transparent;" +
               "-fx-padding: 10 15 10 15;" +
               "-fx-font-size: 13px;" +
               "-fx-cursor: hand;";
    }

    private String navStyleActive() 
    {
        return "-fx-background-color: #ffffff;" +
               "-fx-border-color: transparent;" +
               "-fx-padding: 10 15 10 12;" +
               "-fx-font-size: 13px;" +
               "-fx-font-weight: bold;" +
               "-fx-cursor: hand;";
    }

    private void setActive(Button active)
    {
        for (Button b : new Button[]{btnDashboard, btnImport, btnGenerate,
                                      btnAutoComplete, btnReports})
            b.setStyle(navStyle(false));
        active.setStyle(navStyleActive());
    }

    // Model Loader: Loads bigrams/trigrams into memory once, reuses after that
    private void ensureModelLoaded(Runnable onReady, Label statusLabel) 
    {
        if (modelLoaded) 
        {
            onReady.run();
            return;
        }

        if (statusLabel != null)
            statusLabel.setText("Loading model into memory...");

        Thread loader = new Thread(() -> {
            try {
                sentenceBuilder.loadDatabaseIntoMemory();
                modelLoaded = true;
                Platform.runLater(onReady);
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    if (statusLabel != null)
                        statusLabel.setText("Error loading model: " + e.getMessage());
                });
            }
        });
        loader.setDaemon(true);
        loader.start();
    }

    // Dashboard
    private void showDashboard() 
    {
        setActive(btnDashboard);

        VBox page = new VBox(20);
        page.setPadding(new Insets(25));

        Label heading = new Label("Dashboard");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label totalWordsLabel = statValue("...");
        Label uniqueWordsLabel = statValue("...");
        Label filesLabel = statValue("...");

        HBox statsRow = new HBox(15,
                statCard("Total words", totalWordsLabel),
                statCard("Unique words", uniqueWordsLabel),
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

    // Import Text Screen
    private void showImport() 
    {
        setActive(btnImport);

        List<File> selectedFiles = new ArrayList<>();

        VBox page = new VBox(15);
        page.setPadding(new Insets(25));

        Label heading = new Label("Import Text");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Button browseBtn = new Button("Browse...");
        browseBtn.setStyle("-fx-padding: 7 14 7 14;");

        Label selectedLabel = new Label("No files selected.");
        selectedLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        selectedLabel.setWrapText(true);

        HBox browseRow = new HBox(10, browseBtn, selectedLabel);
        browseRow.setAlignment(Pos.CENTER_LEFT);

        Label pendingLabel = new Label("Files queued for import:");
        pendingLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

        ListView<String> pendingList = new ListView<>();
        pendingList.setPrefHeight(130);
        pendingList.setPlaceholder(new Label("No files selected yet."));

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

        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select text files to import");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt"));
            List<File> chosen = chooser.showOpenMultipleDialog(primaryStage);
            if (chosen != null && !chosen.isEmpty()) {
                for (File f : chosen) {
                    if (selectedFiles.stream().noneMatch(sf ->
                            sf.getAbsolutePath().equals(f.getAbsolutePath()))) {
                        selectedFiles.add(f);
                        pendingList.getItems().add(f.getName() + "  — " + f.getParent());
                    }
                }
                selectedLabel.setText(selectedFiles.size() + " file(s) queued.");
                removeBtn.setDisable(false);
            }
        });

        Label statusLabel = new Label("Ready.");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #555555;");
        statusLabel.setWrapText(true);

        Button parseBtn  = new Button("Parse Files");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setDisable(true);
        parseBtn.setStyle("-fx-background-color: #2E5090; -fx-text-fill: white; -fx-padding: 7 16 7 16;");
        cancelBtn.setStyle("-fx-padding: 7 16 7 16;");
        HBox buttonRow = new HBox(10, parseBtn, cancelBtn);

        Label historyLabel = new Label("Import history");
        historyLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        TableView<FileRow> historyTable = buildImportTable();
        loadImportHistory(historyTable);

        page.getChildren().addAll(
                heading, browseRow,
                pendingLabel, pendingList, removeBtn,
                buttonRow, statusLabel,
                historyLabel, historyTable);

        contentArea.getChildren().setAll(page);

        parseBtn.setOnAction(e -> {
            if (selectedFiles.isEmpty()) {
                statusLabel.setText("No files selected — click Browse to pick some.");
                return;
            }

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
                    // Invalidate model so it reloads next time Generate/AC is opened
                    modelLoaded = false;

                    Platform.runLater(() -> {
                        statusLabel.setText(CorpusParser.cancelRequested
                                ? "Import cancelled." : "Import complete!");
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

    private void loadImportHistory(TableView<FileRow> table) 
    {
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

    // Generate Screen
    private void showGenerate() 
    {
        setActive(btnGenerate);

        VBox page = new VBox(15);
        page.setPadding(new Insets(25));

        Label heading = new Label("Generate Sentence");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // Starting word input
        Label startLabel = new Label("Starting word");
        startLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        TextField startField = new TextField();
        startField.setPromptText("e.g. she");
        startField.setMaxWidth(220);

        // Randomness slider
        Label randomLabel = new Label("Randomness (1 = most predictable)");
        randomLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        Slider randomSlider = new Slider(1, 10, sentenceBuilder.randomnessPool);
        randomSlider.setMajorTickUnit(1);
        randomSlider.setMinorTickCount(0);
        randomSlider.setSnapToTicks(true);
        randomSlider.setShowTickLabels(true);
        randomSlider.setMaxWidth(300);
        Label randomValue = new Label("" + (int) randomSlider.getValue());
        randomSlider.valueProperty().addListener((obs, o, n) ->
                randomValue.setText(String.valueOf(n.intValue())));
        HBox sliderRow = new HBox(10, randomSlider, randomValue);
        sliderRow.setAlignment(Pos.CENTER_LEFT);

        // Generate button
        Button generateBtn = new Button("Generate");
        generateBtn.setStyle("-fx-background-color: #2E5090; -fx-text-fill: white; -fx-padding: 7 16 7 16;");

        // Status / output area
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");

        Label resultLabel = new Label("");
        resultLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-wrap-text: true;");
        resultLabel.setWrapText(true);

        VBox resultBox = new VBox(8, resultLabel);
        resultBox.setPadding(new Insets(12));
        resultBox.setStyle("-fx-background-color: #f5f5f5;" +
                           "-fx-border-color: #dddddd;" +
                           "-fx-border-radius: 6;" +
                           "-fx-background-radius: 6;");
        resultBox.setVisible(false);

        // History of generated sentences
        Label histLabel = new Label("Generated sentences this session");
        histLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        ListView<String> histList = new ListView<>();
        histList.setPrefHeight(160);
        histList.setPlaceholder(new Label("No sentences generated yet."));

        page.getChildren().addAll(
                heading,
                startLabel, startField,
                randomLabel, sliderRow,
                generateBtn, statusLabel, resultBox,
                histLabel, histList);

        contentArea.getChildren().setAll(page);

        generateBtn.setOnAction(e -> {
            String startWord = startField.getText().trim().toLowerCase();
            if (startWord.isEmpty()) {
                statusLabel.setText("Enter a starting word first.");
                return;
            }

            int pool = (int) randomSlider.getValue();
            sentenceBuilder.randomnessPool = pool;

            generateBtn.setDisable(true);
            statusLabel.setText("Generating...");
            resultBox.setVisible(false);

            ensureModelLoaded(() -> {
                // Run generation on a background thread so UI stays responsive
                Thread genThread = new Thread(() -> {
                    String sentence = String.join(" ", sentenceBuilder.runGeneration(startWord));
                    Platform.runLater(() -> {
                        resultLabel.setText(sentence);
                        resultBox.setVisible(true);
                        histList.getItems().add(0, sentence);
                        statusLabel.setText("");
                        generateBtn.setDisable(false);
                    });
                });
                genThread.setDaemon(true);
                genThread.start();
            }, statusLabel);
        });

        // Allow Enter key to trigger generation
        startField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) generateBtn.fire();
        });
    }
    
    // Auto Complete Screen
    private void showAutoComplete() {
        setActive(btnAutoComplete);

        VBox page = new VBox(15);
        page.setPadding(new Insets(25));

        Label heading = new Label("Auto-complete");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label instructions = new Label(
                "Type your sentence. Suggestions appear each time you press Space.");
        instructions.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        instructions.setWrapText(true);

        // Main text input
        TextArea inputArea = new TextArea();
        inputArea.setPromptText("Start typing...");
        inputArea.setPrefHeight(100);
        inputArea.setWrapText(true);

        // Suggestion chips row
        Label suggestLabel = new Label("Suggestions:");
        suggestLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

        HBox chipsBox = new HBox(8);
        chipsBox.setAlignment(Pos.CENTER_LEFT);

        // Status label for loading
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");

        page.getChildren().addAll(heading, instructions, inputArea, suggestLabel, chipsBox, statusLabel);
        contentArea.getChildren().setAll(page);

        // Load model then attach the space listener
        ensureModelLoaded(() -> {
            statusLabel.setText("");

            inputArea.setOnKeyReleased(event -> {
                if (event.getCode() != KeyCode.SPACE) return;

                String text = inputArea.getText();
                if (text.isBlank()) {
                    chipsBox.getChildren().clear();
                    return;
                }

                // Get all words typed so far (trim trailing space)
                String[] words = text.trim().split("\\s+");
                if (words.length == 0) return;

                List<String> suggestions = new ArrayList<>();

                // Try trigram first (needs at least 2 words)
                if (words.length >= 2) {
                    String key = words[words.length - 2] + " " + words[words.length - 1];
                    suggestions = sentenceBuilder.trigramMap.getOrDefault(key, new ArrayList<>());
                }

                // Fall back to bigram
                if (suggestions.isEmpty()) {
                    String key = words[words.length - 1];
                    suggestions = sentenceBuilder.bigramMap.getOrDefault(key, new ArrayList<>());
                }

                // Fall back to sentence starters if still nothing
                if (suggestions.isEmpty())
                    suggestions = sentenceBuilder.sentenceStarters;

                // Show up to 5 suggestions
                List<String> top = suggestions.subList(0, Math.min(5, suggestions.size()));
                chipsBox.getChildren().clear();

                for (String word : top) {
                    Button chip = new Button(word);
                    chip.setStyle(
                            "-fx-background-color: #e8f0fe;" +
                            "-fx-text-fill: #1a56c4;" +
                            "-fx-border-color: #b0c8f8;" +
                            "-fx-border-radius: 99;" +
                            "-fx-background-radius: 99;" +
                            "-fx-padding: 4 12 4 12;" +
                            "-fx-cursor: hand;");

                    chip.setOnAction(e -> {
                        // Replace trailing space + append the chosen word + space
                        String current = inputArea.getText();
                        if (current.endsWith(" "))
                            inputArea.setText(current + word + " ");
                        else
                            inputArea.setText(current + " " + word + " ");

                        // Move caret to end
                        inputArea.positionCaret(inputArea.getText().length());
                        chipsBox.getChildren().clear();

                        // Immediately re-suggest for the word just inserted
                        String[] updated = inputArea.getText().trim().split("\\s+");
                        List<String> next = new ArrayList<>();
                        if (updated.length >= 2) {
                            String key = updated[updated.length - 2] + " " + updated[updated.length - 1];
                            next = sentenceBuilder.trigramMap.getOrDefault(key, new ArrayList<>());
                        }
                        if (next.isEmpty() && updated.length >= 1) {
                            next = sentenceBuilder.bigramMap.getOrDefault(
                                    updated[updated.length - 1], new ArrayList<>());
                        }
                        if (!next.isEmpty()) {
                            List<String> nextTop = next.subList(0, Math.min(5, next.size()));
                            for (String s : nextTop) {
                                Button nc = chipButton(s, inputArea, chipsBox);
                                chipsBox.getChildren().add(nc);
                            }
                        }
                    });

                    chipsBox.getChildren().add(chip);
                }
            });
        }, statusLabel);
    }

    private void showReports()
    {
        setActive(btnReports);

        VBox page = new VBox(15);
        page.setPadding(new Insets(25));

        Label heading = new Label("Reports");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label sortLabel = new Label("Sort by:");
        sortLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

        ComboBox<Reporter.SortType> sortBox = new ComboBox<>();
        sortBox.getItems().addAll(Reporter.SortType.values());

        sortBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Reporter.SortType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.displayName());
            }
        });

        sortBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Reporter.SortType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.displayName());
            }
        });

        sortBox.setValue(reporter.getSortType());
        sortBox.setStyle("-fx-font-size: 13px;");

        HBox sortRow = new HBox(10, sortLabel, sortBox);
        sortRow.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("Loading...");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");

        TableView<WordRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPlaceholder(new Label("No words found."));

        TableColumn<WordRow, String> colWord = new TableColumn<>("Word");
        TableColumn<WordRow, String> colTotal = new TableColumn<>("Frequency");

        colWord.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().word));
        colTotal.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().total));

        colWord.setPrefWidth(220);
        colTotal.setPrefWidth(120);

        table.getColumns().add(colWord);
        table.getColumns().add(colTotal);
        page.getChildren().addAll(heading, sortRow, statusLabel, table);
        contentArea.getChildren().setAll(page);

        Runnable loadData = () -> {
            Reporter.SortType selected = sortBox.getValue();
            reporter.setSortType(selected);
            statusLabel.setText("Loading...");
            table.getItems().clear();

            Thread t = new Thread(() -> {
                List<Word> words = reporter.getSortedWords();

                if (words == null) 
                {
                    Platform.runLater(() -> statusLabel.setText("Error loading data."));
                    return;
                }

                List<WordRow> rows = new ArrayList<>();
                for (Word w : words) 
                    rows.add(new WordRow(
                                w.word, 
                                String.format("%,d", w.totalCount)));

                Platform.runLater(() -> {
                    table.getItems().setAll(rows);
                    statusLabel.setText(String.format("%,d words", rows.size()));
                });
            });
            t.setDaemon(true);
            t.start();
        };

        sortBox.setOnAction(e -> loadData.run());
        loadData.run();
    }



    // Helper so chip click logic isn't duplicatedMarket
    private Button chipButton(String word, TextArea inputArea, HBox chipsBox) 
    {
        Button chip = new Button(word);
        chip.setStyle(
                "-fx-background-color: #e8f0fe;" +
                "-fx-text-fill: #1a56c4;" +
                "-fx-border-color: #b0c8f8;" +
                "-fx-border-radius: 99;" +
                "-fx-background-radius: 99;" +
                "-fx-padding: 4 12 4 12;" +
                "-fx-cursor: hand;");
        chip.setOnAction(e -> {
            String current = inputArea.getText();
            inputArea.setText(current.endsWith(" ")
                    ? current + word + " "
                    : current + " " + word + " ");
            inputArea.positionCaret(inputArea.getText().length());
            chipsBox.getChildren().clear();
        });
        return chip;
    }

    // Shared Helpers
    private TableView<FileRow> buildImportTable() 
    {
        TableView<FileRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(180);
        table.setPlaceholder(new Label("No files imported yet."));

        TableColumn<FileRow, String> colName  = new TableColumn<>("File name");
        TableColumn<FileRow, String> colWords = new TableColumn<>("Words");
        TableColumn<FileRow, String> colDate  = new TableColumn<>("Imported");

        colName.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().name));
        colWords.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().words));
        colDate.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().date));

        colName.setPrefWidth(340);
        colWords.setPrefWidth(100);
        colDate.setPrefWidth(140);

        table.getColumns().add(colName);
        table.getColumns().add(colWords);
        table.getColumns().add(colDate);
        return table;
    }

    private List<FileRow> toFileRows(List<ImportedFile> files) 
    {
        List<FileRow> rows = new ArrayList<>();
        for (ImportedFile f : files)
            rows.add(new FileRow(f.fileName,
                    String.format("%,d", f.wordCount), f.importDate));
        return rows;
    }

    private VBox statCard(String labelText, Label valueLabel) 
    {
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

    private Label statValue(String text) 
    {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        return l;
    }

    public static class FileRow 
    {
        String name, words, date;
        FileRow(String name, String words, String date) {
            this.name = name; this.words = words; this.date = date;
        }
    }

    public static class WordRow 
    {
        String word, total;
        WordRow(String word, String total) {
            this.word = word; this.total = total;
        }
    }

}
