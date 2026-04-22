import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.io.*;
import java.nio.file.Files;
import java.sql.SQLException;

public class ParsingEdgeCaseTest {

    private List<String> capturedSentencesStarters;
    private int sentenceEndCount;
    private List<String> capturedTokens;
    private MockDBMan mockDB;
    private CorpusParser parser;

    @BeforeEach
    void setUp() {
        capturedSentencesStarters = new ArrayList<>();
        capturedTokens = new ArrayList<>();
        sentenceEndCount = 0;
        mockDB = new MockDBMan();
        parser = new CorpusParser(mockDB);
    }

    @Test
    void testEllipsisBehavior() throws Exception {
        // Finding #1: Check if '...' is one or three sentence ends
        parseString("Hello... world");
        
        // If it's 3 resets, sentenceEndCount will be 3
        System.out.println("Ellipsis End Count: " + sentenceEndCount);
        assertTrue(sentenceEndCount >= 1, "Should detect at least one sentence end");
    }

    @Test
    void testApostropheHandling() throws Exception {
        // Finding #2 & #3: John's vs Dogs'
        parseString("John's dogs' are happy.");
        
        assertTrue(capturedTokens.contains("john's"), "Should preserve internal apostrophe");
        // If trailing apostrophe is stripped, 'dogs'' becomes 'dogs'
        System.out.println("Tokens found: " + capturedTokens);
    }

    @Test
    void testDecimalAndAbbreviationBoundaries() throws Exception {
        // Finding #4: Decimals and U.S.A.
        parseString("It costs 45.67 in the U.S.A. today.");
        
        // If '45.67' triggers a boundary, sentenceEndCount will be high
        System.out.println("Boundary Count for Decimals/Abbr: " + sentenceEndCount);
    }

    @Test
    void testHyphenDoubling() throws Exception {
        parseString("The well-known project.");
        
        assertTrue(capturedTokens.contains("well-known"), "Should contain full hyphenated word");
        assertTrue(capturedTokens.contains("well"), "Should contain split part 'well'");
        assertTrue(capturedTokens.contains("known"), "Should contain split part 'known'");
    }

    // Helper to run the parser on a string
    private void parseString(String content) throws Exception {
        File tempFile = File.createTempFile("test_case", ".txt");
        Files.writeString(tempFile.toPath(), content);
        parser.parseFiles(Collections.singletonList(tempFile));
        tempFile.delete();
    }

    // Inner Mock to capture parser output
    private class MockDBMan extends DBMan {
        @Override
        public void insertWords(List<WordEntry> words) {
            for (WordEntry w : words) {
                capturedTokens.add(w.word);
                if (w.startCount > 0) capturedSentencesStarters.add(w.word);
            }
        }
        @Override public void insertBigrams(List<Bigram> b) {}
        @Override public void insertTrigrams(List<Trigram> t) {}
        @Override public void updateEndCounts(List<String> ends) { sentenceEndCount += ends.size(); }
        @Override public void logImport(ImportedFile f) {}
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public void commit() {}
        @Override public Set<String> getImportedFileNames() { return new HashSet<>(); }
    }
}
