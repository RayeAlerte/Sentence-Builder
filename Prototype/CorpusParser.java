import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

public class CorpusParser {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/BuilderWords"; 
    private static final String USER = "sentencebuilder";
    private static final String PASS = "Yo457S<DWL.D";

    public static boolean includeGutenberg = false;
    public static boolean processGutenbergMarkers = true;
    public static volatile boolean cancelRequested = false; // Safe exit flag

    private static final int BATCH_SIZE_LIMIT = 5000;

    public static void parseDataSources() throws Exception {
        cancelRequested = false; // Reset flag on start
        
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            conn.setAutoCommit(false); 
            Set<String> alreadyImported = getPreviouslyImportedFiles(conn);

            Path rootData = Paths.get("./DataSources");
            if (Files.exists(rootData)) {
                Files.list(rootData)
                     .filter(Files::isRegularFile)
                     .filter(p -> p.toString().endsWith(".txt"))
                     .filter(p -> !alreadyImported.contains(p.toFile().getName())) // Unique Check
                     .forEach(path -> {
                         if (!cancelRequested) processFile(path.toFile(), conn, false);
                     });
            }

            if (includeGutenberg && !cancelRequested) {
                Path gutenbergData = Paths.get("./DataSources/Gutenberg");
                if (Files.exists(gutenbergData)) {
                    Files.list(gutenbergData)
                         .filter(Files::isRegularFile)
                         .filter(p -> p.toString().endsWith(".txt"))
                         .filter(p -> !alreadyImported.contains(p.toFile().getName())) // Unique Check
                         .forEach(path -> {
                             if (!cancelRequested) processFile(path.toFile(), conn, true);
                         });
                }
            }
        }
    }

    private static Set<String> getPreviouslyImportedFiles(Connection conn) throws SQLException {
        Set<String> files = new HashSet<>();
        String query = "SELECT file_name FROM ImportedFiles";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                files.add(rs.getString("file_name"));
            }
        }
        return files;
    }

    private static void processFile(File file, Connection conn, boolean isGutenbergFile) {
        int wordCount = 0;
        int batchCounter = 0;
        String w1 = null; 
        String w2 = null; 

        Pattern pattern = Pattern.compile("([a-zA-Z]+(?:['\\-][a-zA-Z]+)*)|([.!?:]|--|—|\\.\\.\\.)");

        String insertWordSQL = "INSERT INTO WordCorpus (word, total_count, start_count, end_count) VALUES (?, 1, ?, 0) ON DUPLICATE KEY UPDATE total_count = total_count + 1, start_count = start_count + VALUES(start_count)";
        String insertBigramSQL = "INSERT INTO Bigrams (word1, word2, frequency) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE frequency = frequency + 1";
        String insertTrigramSQL = "INSERT INTO Trigrams (word1, word2, word3, frequency) VALUES (?, ?, ?, 1) ON DUPLICATE KEY UPDATE frequency = frequency + 1";
        String updateEndCountSQL = "UPDATE WordCorpus SET end_count = end_count + 1 WHERE word = ?";

        try (
            PreparedStatement psWord = conn.prepareStatement(insertWordSQL);
            PreparedStatement psBigram = conn.prepareStatement(insertBigramSQL);
            PreparedStatement psTrigram = conn.prepareStatement(insertTrigramSQL);
            PreparedStatement psEnd = conn.prepareStatement(updateEndCountSQL);
            BufferedReader br = new BufferedReader(new FileReader(file))
        ) {
            String line;
            boolean isStartOfSentence = true;
            boolean readActive = !(isGutenbergFile && processGutenbergMarkers); 

            while ((line = br.readLine()) != null) {
                if (cancelRequested) break; // Check for safe exit command

                if (isGutenbergFile && processGutenbergMarkers) {
                    if (line.contains("*** START OF THE PROJECT GUTENBERG EBOOK")) { readActive = true; continue; }
                    if (line.contains("*** END OF THE PROJECT GUTENBERG EBOOK")) break; 
                }

                if (!readActive) continue;

                line = line.replaceAll("[\"_]", "").toLowerCase();
                Matcher matcher = pattern.matcher(line);
                
                while (matcher.find()) {
                    if (matcher.group(1) != null) {
                        String token = matcher.group(1);
                        wordCount++;
                        batchCounter++;

                        psWord.setString(1, token);
                        psWord.setInt(2, isStartOfSentence ? 1 : 0);
                        psWord.addBatch();

                        if (w1 != null) {
                            psBigram.setString(1, w1);
                            psBigram.setString(2, token);
                            psBigram.addBatch();

                            if (w2 != null) {
                                psTrigram.setString(1, w2);
                                psTrigram.setString(2, w1);
                                psTrigram.setString(3, token);
                                psTrigram.addBatch();
                            }
                        }

                        isStartOfSentence = false;
                        w2 = w1;
                        w1 = token;

                    } else if (matcher.group(2) != null) {
                        if (w1 != null) {
                            psEnd.setString(1, w1);
                            psEnd.addBatch();
                            batchCounter++;
                        }
                        isStartOfSentence = true;
                        w1 = null; 
                        w2 = null; 
                    }

                    if (batchCounter >= BATCH_SIZE_LIMIT) {
                        psWord.executeBatch();
                        psBigram.executeBatch();
                        psTrigram.executeBatch();
                        psEnd.executeBatch();
                        batchCounter = 0;
                    }
                }
            }
            
            // Execute remaining batch regardless of cancellation to ensure safe transaction commit
            psWord.executeBatch();
            psBigram.executeBatch();
            psTrigram.executeBatch();
            psEnd.executeBatch();

            if (!cancelRequested) logImport(conn, file.getName(), wordCount);
            conn.commit();

        } catch (Exception e) {
            e.printStackTrace();
            try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        }
    }

    private static void logImport(Connection conn, String fileName, int count) throws SQLException {
        String sql = "INSERT INTO ImportedFiles (file_name, word_count) VALUES (?, ?) ON DUPLICATE KEY UPDATE word_count = VALUES(word_count), import_date = CURRENT_TIMESTAMP";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, fileName);
            ps.setInt(2, count);
            ps.executeUpdate();
        }
    }
}