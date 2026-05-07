import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class StressTest {

    @Test
    @Tag("stress")
    void stressTestParsing() throws Exception {
        System.out.println("Starting Stress Test...");
        
        // 1. Create a large temporary file (approx 100k words)
        Path tempFile = Files.createTempFile("stress_corpus", ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            String[] words = {"the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog"};
            for (int i = 0; i < 12000; i++) {
                for (String w : words) {
                    writer.write(w + " ");
                }
                if (i % 10 == 0) writer.write(". "); // Add sentence breaks
                if (i % 100 == 0) writer.newLine();
            }
        }

        System.out.println("Generated stress file: " + tempFile.toAbsolutePath());

        // 2. Mock DBMan to measure only parsing speed (not DB insertion speed)
        DBMan silentDB = new DBMan() {
            @Override public void insertWords(List<WordEntry> list) throws java.sql.SQLException {}
            @Override public void insertBigrams(List<Bigram> list) throws java.sql.SQLException {}
            @Override public void insertTrigrams(List<Trigram> list) throws java.sql.SQLException {}
            @Override public void updateEndCounts(List<String> list) throws java.sql.SQLException {}
            @Override public void commit() throws java.sql.SQLException {}
            @Override public void logImport(ImportedFile file) throws java.sql.SQLException {}
            @Override public Set<String> getImportedFileNames() throws java.sql.SQLException { return new java.util.HashSet<>(); }
        };

        CorpusParser parser = new CorpusParser(silentDB);
        
        long startTime = System.currentTimeMillis();
        parser.parseFiles(java.util.Collections.singletonList(tempFile.toFile()));
        long endTime = System.currentTimeMillis();

        System.out.println("Stress Test Result:");
        System.out.println("Parsed 100,000+ words in: " + (endTime - startTime) + "ms");
        
        Files.deleteIfExists(tempFile);
    }
}
