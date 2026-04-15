import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.nio.file.*;
import java.sql.SQLException;

public class CorpusParserApp extends Application {

    private ListView<String> fileListView;
    private Button parseButton;
    private Button safeExitButton;
    private volatile boolean isParsing = false;

    private DBMan dbMan;
    private CorpusParser corpusParser;

    @Override
    public void start(Stage primaryStage) {
        // Initialize DBMan and CorpusParser
        dbMan = new DBMan();
        try {
            dbMan.connect();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Could not connect to database:\n" + e.getMessage());
            Platform.exit();
            return;
        }
        corpusParser = new CorpusParser(dbMan);

        primaryStage.setTitle("Sentence Builder - Data Parser");

        // UI Components
        fileListView = new ListView<>();
        populateFileList();

        CheckBox gutenbergCheck = new CheckBox("Include Gutenberg Subfolder");
        gutenbergCheck.setOnAction(e -> {
            CorpusParser.includeGutenberg = gutenbergCheck.isSelected();
            populateFileList();
        });

        CheckBox cocaCheck = new CheckBox("Include COCA");
        cocaCheck.setOnAction(e -> {
            CorpusParser.includeCOCA = cocaCheck.isSelected();
            populateFileList();
        });

        parseButton = new Button("Parse Text");
        safeExitButton = new Button("Safe Exit");

        // Layout
        HBox buttonBox = new HBox(10, parseButton, safeExitButton);
        VBox root = new VBox(10, new Label("DataSources Directory Contents:"), fileListView, gutenbergCheck, cocaCheck,
                buttonBox);
        root.setPadding(new Insets(15));

        // Event Handlers
        parseButton.setOnAction(e -> startParsing());

        safeExitButton.setOnAction(e -> {
            if (!isParsing) {
                disconnect();
                Platform.exit();
                System.exit(0);
            } else {
                CorpusParser.cancelRequested = true;
                safeExitButton.setText("Exiting gracefully...");
                safeExitButton.setDisable(true);
                parseButton.setDisable(true);
            }
        });

        // Disconnect from DB when window is closed
        primaryStage.setOnCloseRequest(e -> disconnect());

        primaryStage.setScene(new Scene(root, 400, 350));
        primaryStage.show();
    }

    private void disconnect() {
        try {
            if (dbMan != null)
                dbMan.disconnect();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void populateFileList() {
        fileListView.getItems().clear();
        Path rootData = Paths.get("./DataSources");

        if (!Files.exists(rootData)) {
            fileListView.getItems().add("./DataSources folder not found.");
            return;
        }

        try {
            addTxtFilesFromPath(rootData, "");

            if (CorpusParser.includeGutenberg) {
                Path gutenbergData = rootData.resolve("Gutenberg");
                if (Files.exists(gutenbergData)) {
                    addTxtFilesFromPath(gutenbergData, "Gutenberg/");
                }
            }

            if (CorpusParser.includeCOCA) {
                Path cocaData = rootData.resolve("CocaText");
                if (Files.exists(cocaData)) {
                    addTxtFilesFromPath(cocaData, "CocaText/");
                }
            }

            if (fileListView.getItems().isEmpty()) {
                fileListView.getItems().add("No .txt files found in selected paths.");
            }
        } catch (Exception e) {
            fileListView.getItems().add("Error reading ./DataSources");
        }
    }

    private void addTxtFilesFromPath(Path dir, String prefix) throws Exception {
        Files.list(dir)
                .filter(Files::isRegularFile)
                .filter(p -> {
                    try {
                        return !Files.isHidden(p) && !p.getFileName().toString().equals(".DS_Store");
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(p -> p.toString().endsWith(".txt"))
                .map(p -> prefix + p.getFileName().toString())
                .forEach(fileListView.getItems()::add);
    }

    private void startParsing() {
        isParsing = true;
        parseButton.setDisable(true);
        parseButton.setText("Parsing...");

        Thread parserThread = new Thread(() -> {
            try {
                corpusParser.parseDataSources();

                Platform.runLater(() -> {
                    if (CorpusParser.cancelRequested) {
                        disconnect();
                        Platform.exit();
                    } else {
                        isParsing = false;
                        showAlert(Alert.AlertType.INFORMATION, "Success", "Parse complete!");
                        parseButton.setDisable(false);
                        parseButton.setText("Parse Text");
                    }
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Parsing Error", "An error occurred:\n" + ex.getMessage());
                    disconnect();
                    Platform.exit();
                });
            }
        });

        parserThread.setDaemon(true);
        parserThread.start();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
