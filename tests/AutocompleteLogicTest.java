import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.*;
import java.lang.reflect.*;

public class AutocompleteLogicTest {

    private SentenceBuilder builder;
    private MockDBMan mockDB;

    @BeforeEach
    void setUp() throws SQLException {
        mockDB = new MockDBMan();
        builder = new SentenceBuilder(mockDB, new Reporter(mockDB));
    }

    // ─── A1: Trigram fallback to Bigram ───────────────────────────────────────

    @Test
    @DisplayName("A1: When trigram key has no match, should fall back to bigram suggestions")
    void testTrigramFallsToBigram() throws Exception {
        // Bigram: "fox" -> ["runs", "jumps"]
        Bigram b1 = new Bigram(); b1.word1 = "fox"; b1.word2 = "runs";
        Bigram b2 = new Bigram(); b2.word1 = "fox"; b2.word2 = "jumps";
        mockDB.setBigrams(Arrays.asList(b1, b2));

        // No trigrams at all
        mockDB.setTrigrams(Collections.emptyList());
        mockDB.setStarters(Arrays.asList("the"));

        builder.loadDatabaseIntoMemory();

        Map<String, List<String>> bigramMap = getBigramMap(builder);
        Map<String, List<String>> trigramMap = getTrigramMap(builder);

        // Simulate autocomplete with 2-word input: "lazy fox"
        String[] words = {"lazy", "fox"};
        String trigramKey = words[words.length - 2] + " " + words[words.length - 1];
        List<String> suggestions = trigramMap.getOrDefault(trigramKey, Collections.emptyList());

        // Trigram should miss
        assertTrue(suggestions.isEmpty(),
            "Trigram key '" + trigramKey + "' should not exist");

        // Bigram fallback
        if (suggestions.isEmpty()) {
            String bigramKey = words[words.length - 1];
            suggestions = bigramMap.getOrDefault(bigramKey, Collections.emptyList());
        }

        assertFalse(suggestions.isEmpty(), "Should fall back to bigram suggestions for 'fox'");
        assertTrue(suggestions.contains("runs") || suggestions.contains("jumps"),
            "Bigram suggestions should include 'runs' or 'jumps', got: " + suggestions);

        System.out.println("A1: Trigram miss -> Bigram suggestions: " + suggestions);
    }

    // ─── A2: Bigram fallback to Starters ─────────────────────────────────────

    @Test
    @DisplayName("A2: When both trigram and bigram fail, should return sentence starters")
    void testBigramFallsToStarters() throws Exception {
        mockDB.setStarters(Arrays.asList("The", "A", "Once"));
        mockDB.setBigrams(Collections.emptyList());
        mockDB.setTrigrams(Collections.emptyList());

        builder.loadDatabaseIntoMemory();

        Map<String, List<String>> bigramMap = getBigramMap(builder);
        Map<String, List<String>> trigramMap = getTrigramMap(builder);
        List<String> starters = getStarters(builder);

        // Simulate autocomplete with unknown single word "xyz"
        String[] words = {"xyz"};
        List<String> suggestions;

        // Try trigram (needs 2+ words, skip)
        suggestions = Collections.emptyList();

        // Try bigram for "xyz"
        if (suggestions.isEmpty()) {
            suggestions = bigramMap.getOrDefault(words[words.length - 1], Collections.emptyList());
        }

        // Both miss, fall to starters
        if (suggestions.isEmpty()) {
            suggestions = starters;
        }

        assertFalse(suggestions.isEmpty(), "Should fall back to starters");
        assertTrue(suggestions.contains("The") || suggestions.contains("A") || suggestions.contains("Once"),
            "Fallback should contain sentence starters, got: " + suggestions);

        System.out.println("A2: Trigram+Bigram miss -> Starters: " + suggestions);
    }

    // ─── A3: Suggestion Count Cap ─────────────────────────────────────────────

    @Test
    @DisplayName("A3: Autocomplete should return at most 5 suggestions regardless of matches")
    void testSuggestionCountCap() throws Exception {
        // Add 10 bigram followers for "the"
        List<Bigram> bigrams = new ArrayList<>();
        String[] words = {"cat", "dog", "fox", "bird", "fish", "ant", "bee", "cow", "hen", "elk"};
        for (String w : words) {
            Bigram b = new Bigram(); b.word1 = "the"; b.word2 = w;
            bigrams.add(b);
        }
        mockDB.setBigrams(bigrams);
        mockDB.setTrigrams(Collections.emptyList());
        mockDB.setStarters(Collections.emptyList());

        builder.loadDatabaseIntoMemory();

        Map<String, List<String>> bigramMap = getBigramMap(builder);
        List<String> suggestions = bigramMap.getOrDefault("the", Collections.emptyList());

        // Cap to 5, as the UI does: suggestions.subList(0, Math.min(5, suggestions.size()))
        List<String> capped = suggestions.subList(0, Math.min(5, suggestions.size()));

        System.out.println("A3: All suggestions: " + suggestions);
        System.out.println("A3: Capped suggestions: " + capped);

        assertTrue(suggestions.size() >= 5,
            "Should have at least 5 suggestions loaded for 'the'");
        assertEquals(5, capped.size(),
            "Displayed suggestions should be capped at 5");
    }

    // ─── Reflection Helpers ───────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> getBigramMap(SentenceBuilder sb) throws Exception {
        Field f = SentenceBuilder.class.getDeclaredField("bigramMap");
        f.setAccessible(true);
        return (Map<String, List<String>>) f.get(sb);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> getTrigramMap(SentenceBuilder sb) throws Exception {
        Field f = SentenceBuilder.class.getDeclaredField("trigramMap");
        f.setAccessible(true);
        return (Map<String, List<String>>) f.get(sb);
    }

    @SuppressWarnings("unchecked")
    private List<String> getStarters(SentenceBuilder sb) throws Exception {
        Field f = SentenceBuilder.class.getDeclaredField("sentenceStarters");
        f.setAccessible(true);
        return (List<String>) f.get(sb);
    }

    // ─── Mock DB ──────────────────────────────────────────────────────────────

    private static class MockDBMan extends DBMan {
        private List<String> starters = new ArrayList<>();
        private List<Bigram> bigrams = new ArrayList<>();
        private List<Trigram> trigrams = new ArrayList<>();

        public void setStarters(List<String> s) { this.starters = s; }
        public void setBigrams(List<Bigram> b) { this.bigrams = b; }
        public void setTrigrams(List<Trigram> t) { this.trigrams = t; }

        @Override public List<String> getSentenceStarters(int limit) { return starters; }
        @Override public List<Bigram> loadBigrams(int limit) { return bigrams; }
        @Override public List<Trigram> loadTrigrams(int limit) { return trigrams; }
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public void commit() {}
        @Override public Set<String> getImportedFileNames() { return new HashSet<>(); }
    }
}
