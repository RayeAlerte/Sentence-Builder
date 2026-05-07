import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

public class TokenizerNormalizationTest {

    @Test
    @DisplayName("N1: Smart apostrophes normalize to ASCII apostrophe")
    void testSmartApostropheNormalization() {
        List<String> tokens = Tokenizer.tokenizeWords("What’s going on?");
        assertEquals(List.of("what's", "going", "on"), tokens);
    }

    @Test
    @DisplayName("N2: whats remains distinct from what's")
    void testWhatsVsWhatsApostropheDistinct() {
        List<String> tokensA = Tokenizer.tokenizeWords("what's");
        List<String> tokensB = Tokenizer.tokenizeWords("whats");
        assertEquals(List.of("what's"), tokensA);
        assertEquals(List.of("whats"), tokensB);
        assertNotEquals(tokensA.get(0), tokensB.get(0));
    }

    @Test
    @DisplayName("N3: Hyphenated words remain cohesive tokens")
    void testHyphenatedTokenPreserved() {
        List<String> tokens = Tokenizer.tokenizeWords("State-of-the-art model");
        assertEquals(List.of("state-of-the-art", "model"), tokens);
    }

    @Test
    @DisplayName("N4: Current sentence context is used for suggestions")
    void testCurrentSentenceContextExtraction() {
        List<String> tokens = Tokenizer.tokenizeCurrentSentenceWords("Hello world. What's next");
        assertEquals(List.of("what's", "next"), tokens);
    }

    @Test
    @DisplayName("N5: Corpus and user-input tokenization align for contractions")
    void testCorpusAndLearningTokenParityForContractions() {
        String corpusLine = Tokenizer.normalizeCorpusLine("What's up?");
        java.util.regex.Matcher matcher = Tokenizer.CORPUS_PATTERN.matcher(corpusLine);
        java.util.List<String> corpusTokens = new java.util.ArrayList<>();
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                corpusTokens.add(matcher.group(1));
            }
        }

        List<String> learningTokens = Tokenizer.tokenizeWords("What's up?");
        assertEquals(learningTokens, corpusTokens);
    }

    @Test
    @DisplayName("N6: Dotted acronyms collapse to canonical token")
    void testDottedAcronymCollapse() {
        List<String> tokens = Tokenizer.tokenizeWords("U.S.A. wins");
        assertEquals(List.of("usa", "wins"), tokens);
    }

    @Test
    @DisplayName("N7: Unicode letters are preserved as lexical tokens")
    void testUnicodeLetterPreservation() {
        List<String> tokens = Tokenizer.tokenizeWords("Café naïve résumé");
        assertEquals(List.of("café", "naïve", "résumé"), tokens);
    }

    @Test
    @DisplayName("N8: URL and email noise are removed from lexical tokens")
    void testUrlAndEmailSuppression() {
        List<String> tokens = Tokenizer.tokenizeWords("Visit https://example.com or email me@test.com now");
        assertEquals(List.of("visit", "or", "email", "now"), tokens);
    }

    @Test
    @DisplayName("N9: Alphanumeric hyphen tokens are preserved")
    void testAlphaNumericHyphenToken() {
        List<String> tokens = Tokenizer.tokenizeWords("Use gpt-4 and v2 today");
        assertEquals(List.of("use", "gpt-4", "and", "v2", "today"), tokens);
    }
}
