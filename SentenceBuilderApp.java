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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
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
    private Button btnEdit;
    private Button btnLogs;
    private String lastRememberedAutocompleteInput = "";
    private String lastLoggedAutocompleteInput = "";

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        dbMan = new DBMan();
        try 
        {
            dbMan.connect();
            logEvent("APP_START", "SentenceBuilderApp started");
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

        stage.setScene(new Scene(root, 900, 750));
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
        btnEdit = navButton("Edit");
        btnLogs = navButton("Logs");

        btnDashboard.setOnAction(e -> showDashboard());
        btnImport.setOnAction(e -> showImport());
        btnGenerate.setOnAction(e -> showGenerate());
        btnAutoComplete.setOnAction(e -> showAutoComplete());
        btnReports.setOnAction(e -> showReports());
        btnEdit.setOnAction(e -> showEdit());
        btnLogs.setOnAction(e -> showLogs());

        sb.getChildren().addAll(
                title, new Separator(),
                btnDashboard, btnImport, btnGenerate,
                btnAutoComplete, btnReports, btnEdit, btnLogs);
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
                                      btnAutoComplete, btnReports, btnEdit, btnLogs})
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
        logEvent("MODEL_LOAD_START", "Loading n-gram model into memory");

        Thread loader = new Thread(() -> {
            try {
                sentenceBuilder.loadDatabaseIntoMemory();
                modelLoaded = true;
                logEvent("MODEL_LOAD_DONE", "Model load complete");
                Platform.runLater(onReady);
            } catch (SQLException e) {
                logEvent("MODEL_LOAD_ERROR", e.getMessage());
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
        logEvent("VIEW_DASHBOARD", "Opened dashboard");

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
                List<FileRow> rows = toFileRows(dbMan.getImportedFiles(10), true);
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
            logEvent("IMPORT_START", "Queued files: " + filesToParse.size());
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
                        logEvent(CorpusParser.cancelRequested ? "IMPORT_CANCELLED" : "IMPORT_DONE",
                                CorpusParser.cancelRequested ? "User cancelled import" : "Import completed");
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
                    logEvent("IMPORT_ERROR", ex.getMessage());
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
            logEvent("IMPORT_CANCEL_REQUEST", "Cancellation requested");
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
        logEvent("VIEW_GENERATE", "Opened generate tab");

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

        Label lengthLabel = new Label("Generation length (words added)");
        lengthLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        Spinner<Integer> lengthSpinner = new Spinner<>(1, 50, 15);
        lengthSpinner.setEditable(true);
        lengthSpinner.setMaxWidth(120);

        CheckBox rememberInputBox = new CheckBox("Remember New Input");
        rememberInputBox.setSelected(true);

        Label learningLabel = new Label("Learning strength");
        learningLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        ComboBox<String> learningBox = new ComboBox<>();
        learningBox.getItems().addAll("Gentle", "Balanced", "Strong");
        learningBox.setValue("Balanced");
        learningBox.setMaxWidth(140);

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
        Label histLabel = new Label("Generated sentence history");
        histLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        ListView<String> histList = new ListView<>();
        histList.setPrefHeight(160);
        histList.setPlaceholder(new Label("No sentences generated yet."));

        page.getChildren().addAll(
                heading,
                startLabel, startField,
                randomLabel, sliderRow,
                lengthLabel, lengthSpinner,
                rememberInputBox,
                learningLabel, learningBox,
                generateBtn, statusLabel, resultBox,
                histLabel, histList);

        contentArea.getChildren().setAll(page);
        loadGenerationHistory(histList, statusLabel);

        generateBtn.setOnAction(e -> {
            String startWord = startField.getText().trim().toLowerCase();
            if (startWord.isEmpty()) {
                statusLabel.setText("Enter a starting word first.");
                return;
            }

            int pool = (int) randomSlider.getValue();
            sentenceBuilder.randomnessPool = pool;
            int generationLength = lengthSpinner.getValue();
            boolean rememberNewInput = rememberInputBox.isSelected();
            SentenceBuilder.LearningStrength learningStrength = mapLearningStrength(learningBox.getValue());
            logEvent("GENERATE_REQUEST",
                    "start=" + startWord + ", len=" + generationLength + ", remember=" + rememberNewInput);

            generateBtn.setDisable(true);
            statusLabel.setText("Generating...");
            resultBox.setVisible(false);

            ensureModelLoaded(() -> {
                // Run generation on a background thread so UI stays responsive
                Thread genThread = new Thread(() -> {
                    List<String> generatedWords = sentenceBuilder.runGeneration(
                            startWord, generationLength, rememberNewInput, learningStrength);
                    if (generatedWords == null || generatedWords.isEmpty()) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Could not generate a sentence.");
                            generateBtn.setDisable(false);
                        });
                        return;
                    }
                    String sentence = String.join(" ", generatedWords);
                    if (!sentence.matches(".*[.!?]$")) {
                        sentence = sentence + ".";
                    }
                    final String sentenceToShow = sentence;
                    Platform.runLater(() -> {
                        resultLabel.setText(sentenceToShow);
                        resultBox.setVisible(true);
                        histList.getItems().add(0, sentenceToShow);
                        logEvent("GENERATE_RESULT", sentenceToShow);
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
                "Type your sentence. Suggestions appear each time you press Space or comma.");
        instructions.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        instructions.setWrapText(true);

        CheckBox rememberInputBox = new CheckBox("Remember New Input");
        rememberInputBox.setSelected(true);

        Label learningLabel = new Label("Learning strength");
        learningLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        ComboBox<String> learningBox = new ComboBox<>();
        learningBox.getItems().addAll("Gentle", "Balanced", "Strong");
        learningBox.setValue("Balanced");
        learningBox.setMaxWidth(140);

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

        page.getChildren().addAll(
                heading, instructions, rememberInputBox, learningLabel, learningBox,
                inputArea, suggestLabel, chipsBox, statusLabel);
        contentArea.getChildren().setAll(page);

        // Load model then attach the space listener
        ensureModelLoaded(() -> {
            statusLabel.setText("");

            inputArea.setOnKeyReleased(event -> {
                String text = inputArea.getText();
                if (text.isBlank()) {
                    chipsBox.getChildren().clear();
                    return;
                }

                if (text.matches("(?s).*[.!?]\\s*$")) {
                    String finalInput = text.trim().toLowerCase();
                    if (rememberInputBox.isSelected() && !finalInput.isEmpty() && !finalInput.equals(lastRememberedAutocompleteInput)) {
                        sentenceBuilder.rememberUserInput(finalInput, mapLearningStrength(learningBox.getValue()));
                        lastRememberedAutocompleteInput = finalInput;
                    }
                    if (!finalInput.isEmpty() && !finalInput.equals(lastLoggedAutocompleteInput)) {
                        try {
                            dbMan.logUserActivity("AUTOCOMPLETE", finalInput);
                            lastLoggedAutocompleteInput = finalInput;
                        } catch (SQLException ignored) {
                        }
                    }
                    // Terminal punctuation starts a new sentence context for the next query.
                    chipsBox.getChildren().clear();
                }

                boolean triggerOnSpace = event.getCode() == KeyCode.SPACE;
                boolean triggerOnComma = ",".equals(event.getText());
                if (!triggerOnSpace && !triggerOnComma) return;

                List<String> suggestions = sentenceBuilder.getSuggestionsForInput(text);

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
                        List<String> next = sentenceBuilder.getSuggestionsForInput(String.join(" ", updated));
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
        Label scopeLabel = new Label("Scope:");
        scopeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");

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

        ComboBox<Reporter.ScopeType> scopeBox = new ComboBox<>();
        scopeBox.getItems().addAll(Reporter.ScopeType.values());
        scopeBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Reporter.ScopeType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.displayName());
            }
        });
        scopeBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Reporter.ScopeType item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.displayName());
            }
        });
        scopeBox.setValue(reporter.getScopeType());
        scopeBox.setStyle("-fx-font-size: 13px;");

        Button exportBtn = new Button("Export CSV");
        exportBtn.setStyle("-fx-padding: 6 12 6 12;");

        HBox sortRow = new HBox(10, sortLabel, sortBox, scopeLabel, scopeBox, exportBtn);
        sortRow.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("Loading...");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");

        TableView<WordRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPlaceholder(new Label("No words found."));

        TableColumn<WordRow, String> colWord = new TableColumn<>("Word");
        TableColumn<WordRow, String> colTotal = new TableColumn<>("Frequency");
        TableColumn<WordRow, String> colStart = new TableColumn<>("Starts");
        TableColumn<WordRow, String> colEnd = new TableColumn<>("Ends");
        TableColumn<WordRow, String> colBoostTotal = new TableColumn<>("Boost Total");
        TableColumn<WordRow, String> colBoostStart = new TableColumn<>("Boost Starts");
        TableColumn<WordRow, String> colEffectiveTotal = new TableColumn<>("Effective Total");

        colWord.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().word));
        colTotal.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().total));
        colStart.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().starts));
        colEnd.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().ends));
        colBoostTotal.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().boostTotal));
        colBoostStart.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().boostStarts));
        colEffectiveTotal.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().effectiveTotal));

        colWord.setPrefWidth(220);
        colTotal.setPrefWidth(120);
        colStart.setPrefWidth(100);
        colEnd.setPrefWidth(100);
        colBoostTotal.setPrefWidth(110);
        colBoostStart.setPrefWidth(110);
        colEffectiveTotal.setPrefWidth(130);

        table.getColumns().add(colWord);
        table.getColumns().add(colTotal);
        table.getColumns().add(colStart);
        table.getColumns().add(colEnd);
        table.getColumns().add(colBoostTotal);
        table.getColumns().add(colBoostStart);
        table.getColumns().add(colEffectiveTotal);
        page.getChildren().addAll(heading, sortRow, statusLabel, table);
        contentArea.getChildren().setAll(page);

        Runnable loadData = () -> {
            Reporter.SortType selected = sortBox.getValue();
            reporter.setSortType(selected);
            Reporter.ScopeType scope = scopeBox.getValue();
            reporter.setScopeType(scope);
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
                                String.format("%,d", w.totalCount),
                                String.format("%,d", w.startCount),
                                String.format("%,d", w.endCount),
                                String.format("%,d", w.boostTotalCount),
                                String.format("%,d", w.boostStartCount),
                                String.format("%,d", w.effectiveTotalCount)));

                Platform.runLater(() -> {
                    table.getItems().setAll(rows);
                    statusLabel.setText(String.format("%,d words", rows.size()));
                });
            });
            t.setDaemon(true);
            t.start();
        };

        sortBox.setOnAction(e -> loadData.run());
        scopeBox.setOnAction(e -> loadData.run());
        exportBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save report as CSV");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
            chooser.setInitialFileName("sentence-builder-report.csv");
            File file = chooser.showSaveDialog(primaryStage);
            if (file == null) return;

            try {
                exportWordRowsToCsv(file, table.getItems());
                logEvent("REPORT_EXPORT", "rows=" + table.getItems().size() + ", file=" + file.getAbsolutePath());
                statusLabel.setText("Exported " + table.getItems().size() + " rows to " + file.getName());
            } catch (IOException ex) {
                logEvent("REPORT_EXPORT_ERROR", ex.getMessage());
                statusLabel.setText("Export failed: " + ex.getMessage());
            }
        });
        loadData.run();
    }

    private void showEdit() {
        setActive(btnEdit);
        // View-level event for user workflow auditing in the Logs tab.
        logEvent("VIEW_EDIT", "Opened edit tab");

        VBox page = new VBox(12);
        page.setPadding(new Insets(25));

        Label heading = new Label("Edit Word Frequencies");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        Label note = new Label("Update counts while preserving effective total (total + boost total).");
        note.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        note.setWrapText(true);

        TextField wordField = new TextField();
        wordField.setPromptText("Enter word to edit");
        wordField.setMaxWidth(260);
        Button lookupBtn = new Button("Lookup");
        HBox lookupRow = new HBox(10, wordField, lookupBtn);
        lookupRow.setAlignment(Pos.CENTER_LEFT);

        TextField totalField = new TextField();
        TextField startField = new TextField();
        TextField endField = new TextField();
        TextField boostTotalField = new TextField();
        TextField boostStartField = new TextField();
        TextField effectiveField = new TextField();
        boostTotalField.setEditable(false);
        effectiveField.setEditable(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Total count"), totalField);
        grid.addRow(1, new Label("Start count"), startField);
        grid.addRow(2, new Label("End count"), endField);
        grid.addRow(3, new Label("Boost total"), boostTotalField);
        grid.addRow(4, new Label("Boost starts"), boostStartField);
        grid.addRow(5, new Label("Effective total"), effectiveField);

        Button saveBtn = new Button("Save");
        saveBtn.setDisable(true);
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");
        HBox actionRow = new HBox(10, saveBtn, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        page.getChildren().addAll(heading, note, lookupRow, grid, actionRow);
        contentArea.getChildren().setAll(page);

        final String[] selectedWord = {null};

        Runnable clearFields = () -> {
            totalField.clear();
            startField.clear();
            endField.clear();
            boostTotalField.clear();
            boostStartField.clear();
            effectiveField.clear();
        };

        lookupBtn.setOnAction(e -> {
            String word = wordField.getText().trim().toLowerCase();
            if (word.isEmpty()) {
                statusLabel.setText("Enter a word to lookup.");
                saveBtn.setDisable(true);
                clearFields.run();
                return;
            }
            try {
                Word row = dbMan.getWordByText(word);
                if (row == null) {
                    statusLabel.setText("Word not found.");
                    saveBtn.setDisable(true);
                    selectedWord[0] = null;
                    clearFields.run();
                    return;
                }
                selectedWord[0] = row.word;
                totalField.setText(String.valueOf(row.totalCount));
                startField.setText(String.valueOf(row.startCount));
                endField.setText(String.valueOf(row.endCount));
                boostTotalField.setText(String.valueOf(row.boostTotalCount));
                boostStartField.setText(String.valueOf(row.boostStartCount));
                effectiveField.setText(String.valueOf(row.effectiveTotalCount));
                statusLabel.setText("Loaded: " + row.word);
                saveBtn.setDisable(false);
                // Logged to preserve a trace of manual data-access actions.
                logEvent("EDIT_LOOKUP", row.word);
            } catch (SQLException ex) {
                statusLabel.setText("Lookup failed: " + ex.getMessage());
                saveBtn.setDisable(true);
            }
        });

        saveBtn.setOnAction(e -> {
            if (selectedWord[0] == null) return;
            try {
                int total = Integer.parseInt(totalField.getText().trim());
                int start = Integer.parseInt(startField.getText().trim());
                int end = Integer.parseInt(endField.getText().trim());
                int boostStart = Integer.parseInt(boostStartField.getText().trim());

                dbMan.updateWordCountsPreserveEffective(selectedWord[0], total, start, end, boostStart);
                Word updated = dbMan.getWordByText(selectedWord[0]);
                boostTotalField.setText(String.valueOf(updated.boostTotalCount));
                effectiveField.setText(String.valueOf(updated.effectiveTotalCount));
                statusLabel.setText("Saved.");
                modelLoaded = false; // reload in-memory model on next generate/autocomplete usage
                logEvent("EDIT_SAVE",
                        selectedWord[0] + " total=" + updated.totalCount + " start=" + updated.startCount +
                                " end=" + updated.endCount + " boostStart=" + updated.boostStartCount);
            } catch (NumberFormatException ex) {
                statusLabel.setText("Enter valid integer values.");
            } catch (SQLException ex) {
                statusLabel.setText("Save failed: " + ex.getMessage());
            }
        });

        wordField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) lookupBtn.fire();
        });
    }

    private void showLogs() {
        setActive(btnLogs);

        VBox page = new VBox(15);
        page.setPadding(new Insets(25));

        Label heading = new Label("Application Logs");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label filterLabel = new Label("Activity type:");
        filterLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        ComboBox<String> typeBox = new ComboBox<>();
        typeBox.getItems().add("ALL");
        typeBox.setValue("ALL");
        typeBox.setStyle("-fx-font-size: 13px;");

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-padding: 6 12 6 12;");

        HBox controls = new HBox(10, filterLabel, typeBox, refreshBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label statusLabel = new Label("Loading...");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #888888;");

        TableView<LogRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPlaceholder(new Label("No logs found."));

        TableColumn<LogRow, String> colTime = new TableColumn<>("Timestamp");
        TableColumn<LogRow, String> colType = new TableColumn<>("Type");
        TableColumn<LogRow, String> colContent = new TableColumn<>("Content");
        colTime.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().time));
        colType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().type));
        colContent.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().content));
        colTime.setPrefWidth(170);
        colType.setPrefWidth(150);
        colContent.setPrefWidth(500);
        table.getColumns().add(colTime);
        table.getColumns().add(colType);
        table.getColumns().add(colContent);

        page.getChildren().addAll(heading, controls, statusLabel, table);
        contentArea.getChildren().setAll(page);

        Runnable loadTypes = () -> {
            Thread t = new Thread(() -> {
                try {
                    List<String> types = dbMan.getUserHistoryActivityTypes();
                    Platform.runLater(() -> {
                        String current = typeBox.getValue();
                        typeBox.getItems().setAll("ALL");
                        typeBox.getItems().addAll(types);
                        if (current != null && typeBox.getItems().contains(current)) {
                            typeBox.setValue(current);
                        } else {
                            typeBox.setValue("ALL");
                        }
                    });
                } catch (SQLException ignored) {
                }
            });
            t.setDaemon(true);
            t.start();
        };

        Runnable loadLogs = () -> {
            statusLabel.setText("Loading...");
            table.getItems().clear();
            Thread t = new Thread(() -> {
                try {
                    String selected = typeBox.getValue();
                    String filter = (selected == null || "ALL".equals(selected)) ? null : selected;
                    List<DBMan.UserHistoryEntry> rows = dbMan.getUserHistory(500, filter);
                    List<LogRow> mapped = new ArrayList<>();
                    for (DBMan.UserHistoryEntry e : rows) {
                        mapped.add(new LogRow(e.createdAt, e.activityType, e.content));
                    }
                    Platform.runLater(() -> {
                        table.getItems().setAll(mapped);
                        statusLabel.setText(String.format("%,d logs", mapped.size()));
                    });
                } catch (SQLException e) {
                    Platform.runLater(() -> statusLabel.setText("Error loading logs."));
                }
            });
            t.setDaemon(true);
            t.start();
        };

        refreshBtn.setOnAction(e -> {
            loadTypes.run();
            loadLogs.run();
        });
        typeBox.setOnAction(e -> loadLogs.run());

        loadTypes.run();
        loadLogs.run();
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
        return toFileRows(files, false);
    }

    private List<FileRow> toFileRows(List<ImportedFile> files, boolean truncateNames) 
    {
        List<FileRow> rows = new ArrayList<>();
        for (ImportedFile f : files)
            rows.add(new FileRow(truncateNames ? truncateTail(f.fileName, 25) : f.fileName,
                    String.format("%,d", f.wordCount), f.importDate));
        return rows;
    }

    private String truncateTail(String value, int keepTailChars) {
        if (value == null) return "";
        if (keepTailChars <= 0 || value.length() <= keepTailChars) return value;
        return "..." + value.substring(value.length() - keepTailChars);
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

    private SentenceBuilder.LearningStrength mapLearningStrength(String uiValue) {
        if (uiValue == null) return SentenceBuilder.LearningStrength.BALANCED;
        return switch (uiValue) {
            case "Gentle" -> SentenceBuilder.LearningStrength.GENTLE;
            case "Strong" -> SentenceBuilder.LearningStrength.STRONG;
            default -> SentenceBuilder.LearningStrength.BALANCED;
        };
    }

    private void exportWordRowsToCsv(File file, List<WordRow> rows) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("word,total_count,start_count,end_count,boost_total_count,boost_start_count,effective_total_count");
            for (WordRow row : rows) {
                writer.println(String.join(",",
                        csvEscape(row.word),
                        row.totalRaw,
                        row.startsRaw,
                        row.endsRaw,
                        row.boostTotalRaw,
                        row.boostStartsRaw,
                        row.effectiveTotalRaw));
            }
        }
    }

    private void loadGenerationHistory(ListView<String> histList, Label statusLabel) {
        Thread t = new Thread(() -> {
            try {
                List<DBMan.UserHistoryEntry> rows = dbMan.getUserHistory(300, "GENERATION");
                List<String> sentences = new ArrayList<>();
                for (DBMan.UserHistoryEntry e : rows) {
                    if (e.content != null && !e.content.isBlank()) {
                        sentences.add(e.content);
                    }
                }
                Platform.runLater(() -> histList.getItems().setAll(sentences));
            } catch (SQLException e) {
                Platform.runLater(() -> statusLabel.setText("Could not load generation history."));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void logEvent(String activityType, String content) {
        // Thin UI wrapper around DB logging so every tab can emit consistent audit events.
        try {
            dbMan.logUserActivity(activityType, content);
        } catch (SQLException ignored) {
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
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
        String word, total, starts, ends, boostTotal, boostStarts, effectiveTotal;
        String totalRaw, startsRaw, endsRaw, boostTotalRaw, boostStartsRaw, effectiveTotalRaw;
        WordRow(String word, String total, String starts, String ends,
                String boostTotal, String boostStarts, String effectiveTotal) {
            this.word = word; this.total = total;
            this.starts = starts; this.ends = ends;
            this.boostTotal = boostTotal; this.boostStarts = boostStarts; this.effectiveTotal = effectiveTotal;
            this.totalRaw = total.replace(",", "");
            this.startsRaw = starts.replace(",", "");
            this.endsRaw = ends.replace(",", "");
            this.boostTotalRaw = boostTotal.replace(",", "");
            this.boostStartsRaw = boostStarts.replace(",", "");
            this.effectiveTotalRaw = effectiveTotal.replace(",", "");
        }
    }

    public static class LogRow {
        String time, type, content;
        LogRow(String time, String type, String content) {
            this.time = time;
            this.type = type;
            this.content = content;
        }
    }

}
