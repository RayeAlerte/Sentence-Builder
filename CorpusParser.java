import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.util.function.Consumer;

public class CorpusParser {
    private DBMan dbMan;

    public static boolean includeGutenberg = false;
    public static boolean includeCOCA = false;
    public static boolean processGutenbergMarkers = true;
    public static volatile boolean cancelRequested = false;

    private static final int BATCH_SIZE_LIMIT = 5000;

    //
    private Consumer<String> onProgress = null;


    public CorpusParser(DBMan dbMan) {
        this.dbMan = dbMan;
    }


    public void setOnProgress(Consumer<String> callback)
    {
        this.onProgress = callback;
    }

    private void reportProgress(String message)
    {
        if (onProgress != null)
        {
            onProgress.accept(message);
        }
    }


    public void parseDataSources() throws Exception {
        cancelRequested = false;

        Set<String> alreadyImported = dbMan.getImportedFileNames();

        Path rootData = Paths.get("./DataSources");
        if (Files.exists(rootData)) {
            Files.list(rootData)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .filter(p -> !alreadyImported.contains(p.toFile().getName()))
                    .forEach(path -> {
                        if (!cancelRequested)
                            processFile(path.toFile(), false);
                    });
        }

        if (includeGutenberg && !cancelRequested) {
            Path gutenbergData = Paths.get("./DataSources/Gutenberg");
            if (Files.exists(gutenbergData)) {
                Files.list(gutenbergData)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".txt"))
                        .filter(p -> !alreadyImported.contains(p.toFile().getName()))
                        .forEach(path -> {
                            if (!cancelRequested)
                                processFile(path.toFile(), true);
                        });
            }
        }

        if (includeCOCA && !cancelRequested) {
            Path cocaData = Paths.get("./DataSources/CocaText");
            if (Files.exists(cocaData)) {
                Files.list(cocaData)
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".txt"))
                        .filter(p -> !alreadyImported.contains(p.toFile().getName()))
                        .forEach(path -> {
                            if (!cancelRequested)
                                processFile(path.toFile(), false);
                        });
            }
        }
    }

    private void processFile(File file, boolean isGutenbergFile) {
        int wordCount = 0;
        int batchCounter = 0;
        String w1 = null;
        String w2 = null;

        List<WordEntry> wordBatch = new ArrayList<>();
        List<Bigram> bigramBatch = new ArrayList<>();
        List<Trigram> trigramBatch = new ArrayList<>();
        List<String> endBatch = new ArrayList<>();

        Pattern pattern = Pattern.compile("([a-zA-Z]+(?:['\\-][a-zA-Z]+)*)|([.!?:]|--|—|\\.\\.\\.)");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean isStartOfSentence = true;
            boolean readActive = !(isGutenbergFile && processGutenbergMarkers);

            while ((line = br.readLine()) != null) {
                if (cancelRequested)
                    break;

                if (isGutenbergFile && processGutenbergMarkers) {
                    if (line.contains("*** START OF THE PROJECT GUTENBERG EBOOK")) {
                        readActive = true;
                        continue;
                    }
                    if (line.contains("*** END OF THE PROJECT GUTENBERG EBOOK"))
                        break;
                }

                if (!readActive)
                    continue;

                line = line.replaceAll("[\"_]", "").toLowerCase();
                Matcher matcher = pattern.matcher(line);

                while (matcher.find()) {
                    if (matcher.group(1) != null) {
                        String token = matcher.group(1);
                        wordCount++;
                        batchCounter++;

                        // 1. Store the word as-is
                        WordEntry entry = new WordEntry();
                        entry.word = token;
                        entry.startCount = isStartOfSentence ? 1 : 0;
                        wordBatch.add(entry);

                        // 2. Split hyphenated words and store distinct parts
                        if (token.contains("-")) {
                            String[] splitWords = token.split("-");
                            for (String splitWord : splitWords) {
                                if (!splitWord.isEmpty()) {
                                    WordEntry splitEntry = new WordEntry();
                                    splitEntry.word = splitWord;
                                    splitEntry.startCount = 0;
                                    wordBatch.add(splitEntry);
                                    batchCounter++;
                                }
                            }
                        }

                        // 3. Relational logic using the original complete token
                        if (w1 != null) {
                            Bigram bigram = new Bigram();
                            bigram.word1 = w1;
                            bigram.word2 = token;
                            bigramBatch.add(bigram);

                            if (w2 != null) {
                                Trigram trigram = new Trigram();
                                trigram.word1 = w2;
                                trigram.word2 = w1;
                                trigram.word3 = token;
                                trigramBatch.add(trigram);
                            }
                        }

                        isStartOfSentence = false;
                        w2 = w1;
                        w1 = token;

                    } else if (matcher.group(2) != null) {
                        if (w1 != null) {
                            endBatch.add(w1);
                            batchCounter++;
                        }
                        isStartOfSentence = true;
                        w1 = null;
                        w2 = null;
                    }

                    if (batchCounter >= BATCH_SIZE_LIMIT) {
                        dbMan.insertWords(wordBatch);
                        dbMan.insertBigrams(bigramBatch);
                        dbMan.insertTrigrams(trigramBatch);
                        dbMan.updateEndCounts(endBatch);
                        wordBatch.clear();
                        bigramBatch.clear();
                        trigramBatch.clear();
                        endBatch.clear();
                        batchCounter = 0;

                        final int wc = wordCount;
                        reportProgress("Parsing " + file.getName() + " - " + String.format("%,d", wc) + " words processed...");
                    }
                }
            }

            // Flush remaining batches
            dbMan.insertWords(wordBatch);
            dbMan.insertBigrams(bigramBatch);
            dbMan.insertTrigrams(trigramBatch);
            dbMan.updateEndCounts(endBatch);

            if (!cancelRequested) {
                ImportedFile importedFile = new ImportedFile();
                importedFile.fileName = file.getName();
                importedFile.wordCount = wordCount;
                dbMan.logImport(importedFile);
                reportProgress("Done: " + file.getName() + " (" + String.format("%,d", wordCount) + "words)");
            }

            dbMan.commit();

        }
        catch (Exception e) {
            reportProgress("Error parsing: " + file.getName() + ": " + e.getMessage());
            e.printStackTrace();
            try {
                dbMan.rollback();
            }
            catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
}
