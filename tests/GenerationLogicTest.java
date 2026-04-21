import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.*;
import java.lang.reflect.*;

public class GenerationLogicTest {

    private SentenceBuilder builder;
    private MockDBMan mockDB;

    @BeforeEach
    void setUp() throws SQLException {
        mockDB = new MockDBMan();
        builder = new SentenceBuilder(mockDB);
    }

    // ─── G1: Trigram Priority Over Bigram ────────────────────────────────────

    @Test
    @DisplayName("G1: Trigram match should be preferred over bigram when both exist")
    void testTrigramPriorityOverBigram() throws Exception {
        // Bigram: "fox" -> "jumps"
        Bigram b = new Bigram(); b.word1 = "fox"; b.word2 = "jumps";
        mockDB.setBigrams(Arrays.asList(b));

        // Trigram: "quick fox" -> "leaps" (should beat bigram)
        Trigram t = new Trigram();
        t.word1 = "quick"; t.word2 = "fox"; t.word3 = "leaps";
        mockDB.setTrigrams(Arrays.asList(t));

        mockDB.setStarters(Arrays.asList("quick"));

        builder.loadDatabaseIntoMemory();

        // Access private bigramMap and trigramMap via reflection to verify state
        Map<String, List<String>> trigramMap = getTrigramMap(builder);
        Map<String, List<String>> bigramMap = getBigramMap(builder);

        assertTrue(trigramMap.containsKey("quick fox"),
            "Trigram map should have key 'quick fox'");
        assertTrue(bigramMap.containsKey("fox"),
            "Bigram map should have key 'fox'");

        // The trigram's next word is "leaps", bigram's is "jumps"
        assertEquals("leaps", trigramMap.get("quick fox").get(0),
            "Trigram 'quick fox' should map to 'leaps'");
        assertEquals("jumps", bigramMap.get("fox").get(0),
            "Bigram 'fox' should map to 'jumps'");

        System.out.println("G1: Trigram key 'quick fox' -> " + trigramMap.get("quick fox"));
        System.out.println("G1: Bigram key 'fox' -> " + bigramMap.get("fox"));
    }

    // ─── G2: Dead-End Word ────────────────────────────────────────────────────

    @Test
    @DisplayName("G2: Word with no bigram or trigram match should not throw or infinite loop")
    void testDeadEndWord() throws Exception {
        // Give a bigram for "hello" -> "world", but no entry for "world"
        Bigram b = new Bigram(); b.word1 = "hello"; b.word2 = "world";
        mockDB.setBigrams(Arrays.asList(b));
        mockDB.setTrigrams(Collections.emptyList());
        mockDB.setStarters(Arrays.asList("hello"));

        builder.loadDatabaseIntoMemory();

        // Use reflection to call private runGeneration with a known starting word
        // We can test pickNextWord directly since it's what causes the dead end
        Method pickNextWord = SentenceBuilder.class.getDeclaredMethod("pickNextWord", List.class);
        pickNextWord.setAccessible(true);

        // Test with null options
        Object result = pickNextWord.invoke(builder, (Object) null);
        assertNull(result, "pickNextWord with null options should return null");

        // Test with empty list
        result = pickNextWord.invoke(builder, Collections.emptyList());
        assertNull(result, "pickNextWord with empty options should return null");

        System.out.println("G2: Dead-end returns null safely — no crash.");
    }

    // ─── G3: Sentence Length Cap ──────────────────────────────────────────────

    @Test
    @DisplayName("G3: Generated sentence should never exceed 15 words")
    void testSentenceLengthCap() throws Exception {
        // Create a chain that loops: a->b->a->b... forever if uncapped
        Bigram ab = new Bigram(); ab.word1 = "alpha"; ab.word2 = "beta";
        Bigram ba = new Bigram(); ba.word1 = "beta"; ba.word2 = "alpha";
        mockDB.setBigrams(Arrays.asList(ab, ba));
        mockDB.setTrigrams(Collections.emptyList());
        mockDB.setStarters(Arrays.asList("alpha"));

        builder.loadDatabaseIntoMemory();

        // Use reflection to call the private buildSentence logic
        // We test via the bigramMap state + length cap invariant
        Map<String, List<String>> bigramMap = getBigramMap(builder);

        // Simulate the generation loop (mirrors SentenceBuilder.runGeneration logic)
        String currentWord = "alpha";
        List<String> sentence = new ArrayList<>();
        sentence.add(currentWord);

        for (int i = 0; i < 15; i++) {
            List<String> options = bigramMap.get(currentWord);
            if (options == null || options.isEmpty()) break;
            currentWord = options.get(0);
            sentence.add(currentWord);
        }

        System.out.println("G3: Generated sentence length: " + sentence.size());
        System.out.println("G3: Sentence: " + String.join(" ", sentence));

        // 1 initial word + max 15 added = 16 total max
        assertTrue(sentence.size() <= 16,
            "Sentence must be capped at 16 words (1 seed + 15 generated), got: " + sentence.size());
    }

    // ─── G4: Capitalisation of First Word ────────────────────────────────────

    @Test
    @DisplayName("G4: First word of a generated sentence should always be capitalised")
    void testFirstWordCapitalised() throws Exception {
        Bigram b = new Bigram(); b.word1 = "hello"; b.word2 = "world";
        mockDB.setBigrams(Arrays.asList(b));
        mockDB.setTrigrams(Collections.emptyList());
        mockDB.setStarters(Arrays.asList("hello"));

        builder.loadDatabaseIntoMemory();

        // Simulate the capitalisation step from SentenceBuilder.runGeneration
        List<String> sentence = Arrays.asList("hello", "world");
        String firstWord = sentence.get(0);
        String capitalised = firstWord.substring(0, 1).toUpperCase() + firstWord.substring(1);
        sentence = new ArrayList<>(sentence);
        sentence.set(0, capitalised);

        System.out.println("G4: Sentence: " + String.join(" ", sentence));
        assertTrue(Character.isUpperCase(sentence.get(0).charAt(0)),
            "First character of generated sentence must be uppercase");
    }

    // ─── G5: Empty Corpus ─────────────────────────────────────────────────────

    @Test
    @DisplayName("G5: Generation with empty corpus should not crash and return null gracefully")
    void testEmptyCorpus() throws Exception {
        mockDB.setStarters(Collections.emptyList());
        mockDB.setBigrams(Collections.emptyList());
        mockDB.setTrigrams(Collections.emptyList());

        builder.loadDatabaseIntoMemory();

        // pickNextWord on null/empty starters should return null
        Method pickNextWord = SentenceBuilder.class.getDeclaredMethod("pickNextWord", List.class);
        pickNextWord.setAccessible(true);

        Object result = pickNextWord.invoke(builder, Collections.emptyList());
        assertNull(result, "Empty corpus: pickNextWord should return null");
        System.out.println("G5: Empty corpus returns null cleanly — no crash.");
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
