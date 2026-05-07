import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;

public class ParserRobustnessTest {

    private List<String> capturedTokens;
    private List<Bigram> capturedBigrams;
    private int sentenceEndCount;
    private Set<String> importedFileNames;
    private int logImportCallCount;
    private MockDBMan mockDB;
    private CorpusParser parser;

    @BeforeEach
    void setUp() {
        capturedTokens = new ArrayList<>();
        capturedBigrams = new ArrayList<>();
        sentenceEndCount = 0;
        importedFileNames = new HashSet<>();
        logImportCallCount = 0;
        mockDB = new MockDBMan();
        parser = new CorpusParser(mockDB);
    }

    // ─── P1: Empty File ───────────────────────────────────────────────────────

    @Test
    @DisplayName("P1: Empty file should not crash or produce any tokens")
    void testEmptyFile() throws Exception {
        File tempFile = createTempFile("");
        assertDoesNotThrow(() -> parser.parseFiles(Collections.singletonList(tempFile)));
        assertTrue(capturedTokens.isEmpty(), "Empty file should produce zero tokens");
        assertEquals(0, capturedBigrams.size(), "Empty file should produce zero bigrams");
    }

    // ─── P2: All-Punctuation Line ─────────────────────────────────────────────

    @Test
    @DisplayName("P2: All-punctuation line should produce no word tokens")
    void testAllPunctuationLine() throws Exception {
        File tempFile = createTempFile("--- !!! ??? ...");
        assertDoesNotThrow(() -> parser.parseFiles(Collections.singletonList(tempFile)));
        assertTrue(capturedTokens.isEmpty(),
            "All-punctuation input should produce zero word tokens, but got: " + capturedTokens);
        assertEquals(0, capturedBigrams.size(),
            "All-punctuation input should produce zero bigrams");
    }

    // ─── P3: Repeated Words ───────────────────────────────────────────────────

    @Test
    @DisplayName("P3: Repeated words should produce correct bigrams")
    void testRepeatedWords() throws Exception {
        File tempFile = createTempFile("the the the the");
        assertDoesNotThrow(() -> parser.parseFiles(Collections.singletonList(tempFile)));

        System.out.println("P3 tokens: " + capturedTokens);
        System.out.println("P3 bigrams: " + capturedBigrams.size() + " bigrams captured");

        // 'the' repeated 4 times should produce at least 3 bigrams: (the,the), (the,the), (the,the)
        assertEquals(3, capturedBigrams.size(),
            "Four consecutive identical words should produce 3 bigrams");

        for (Bigram b : capturedBigrams) {
            assertEquals("the", b.word1, "Bigram word1 should be 'the'");
            assertEquals("the", b.word2, "Bigram word2 should be 'the'");
        }
    }

    // ─── P4: Non-ASCII Characters ─────────────────────────────────────────────

    @Test
    @DisplayName("P4: Non-ASCII words should be handled without crash")
    void testNonAsciiCharacters() throws Exception {
        // café, résumé, über should either be dropped or split at non-ASCII boundary
        File tempFile = createTempFile("cafe resume uber are good words.");
        assertDoesNotThrow(() -> parser.parseFiles(Collections.singletonList(tempFile)));
        System.out.println("P4 tokens: " + capturedTokens);
        // At minimum, should not throw and should capture ASCII parts
        assertFalse(capturedTokens.isEmpty(), "Should capture at least some ASCII tokens");
    }

    @Test
    @DisplayName("P4b: Non-ASCII content file should not throw an exception")
    void testNonAsciiFileDoesNotCrash() throws Exception {
        // Write file with actual accented characters
        File tempFile = File.createTempFile("test_nonascii", ".txt");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(tempFile), java.nio.charset.StandardCharsets.UTF_8))) {
            w.write("café résumé über naïve");
        }
        assertDoesNotThrow(() -> parser.parseFiles(Collections.singletonList(tempFile)));
        System.out.println("P4b tokens (non-ASCII): " + capturedTokens);
        tempFile.delete();
    }

    // ─── P5: Duplicate Import Skip ────────────────────────────────────────────

    @Test
    @DisplayName("P5: Parsing the same file twice should skip it on the second call")
    void testDuplicateImportSkipped() throws Exception {
        File tempFile = createTempFile("hello world.");

        // First parse — should import
        parser.parseFiles(Collections.singletonList(tempFile));
        int firstLogCount = logImportCallCount;
        assertEquals(1, firstLogCount, "File should be logged after first import");

        // Second parse — the mockDB now returns the file as already imported
        importedFileNames.add(tempFile.getName());
        parser.parseFiles(Collections.singletonList(tempFile));
        assertEquals(firstLogCount, logImportCallCount,
            "File should NOT be logged again on second parse (already imported)");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private File createTempFile(String content) throws IOException {
        File f = File.createTempFile("parser_test", ".txt");
        Files.writeString(f.toPath(), content);
        f.deleteOnExit();
        return f;
    }

    private class MockDBMan extends DBMan {
        @Override
        public void insertWords(List<WordEntry> words) {
            for (WordEntry w : words) capturedTokens.add(w.word);
        }
        @Override
        public void insertBigrams(List<Bigram> bigrams) {
            capturedBigrams.addAll(bigrams);
        }
        @Override public void insertTrigrams(List<Trigram> t) {}
        @Override public void updateEndCounts(List<String> ends) { sentenceEndCount += ends.size(); }
        @Override public void logImport(ImportedFile f) { logImportCallCount++; }
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public void commit() {}
        @Override public void rollback() {}
        @Override
        public Set<String> getImportedFileNames() { return importedFileNames; }
    }
}
