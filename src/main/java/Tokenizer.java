import java.util.ArrayList;
import java.util.List;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tokenizer {
    private Tokenizer() {}

    // Word tokens start with a letter and may contain letters/digits; internal apostrophes/hyphens preserved.
    // Pure numbers (no leading letter) are excluded. Examples: "what's", "state-of-the-art", "gpt-4", "v2"
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}[\\p{L}\\p{N}]*(?:['\\-][\\p{L}\\p{N}]+)*");

    // Sentence boundary markers used by corpus parser. Tokens must start with a letter; pure numbers excluded.
    public static final Pattern CORPUS_PATTERN = Pattern.compile(
            "(\\p{L}[\\p{L}\\p{N}]*(?:['\\-][\\p{L}\\p{N}]+)*)|(\\.\\.\\.|--|—|[!?:]|\\.(?![\\p{L}])|\\s{2,})");

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern DOTTED_ACRONYM_PATTERN = Pattern.compile("\\b(?:\\p{L}\\.){2,}");

    public static String normalizeText(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase();

        // Normalize common Unicode punctuation variants to canonical ASCII forms.
        normalized = normalized
                .replace('\u2019', '\'') // right single quote
                .replace('\u2018', '\'') // left single quote
                .replace('\u201c', '"')  // left double quote
                .replace('\u201d', '"')  // right double quote
                .replace('\u2013', '-')  // en dash
                .replace('\u2014', '—')  // em dash (kept as boundary marker in corpus pattern)
                .replace("\u2026", "..."); // ellipsis to explicit 3-period boundary

        // Remove structured noise that should not become lexical tokens.
        normalized = URL_PATTERN.matcher(normalized).replaceAll(" ");
        normalized = EMAIL_PATTERN.matcher(normalized).replaceAll(" ");

        // Collapse dotted acronyms/initials to canonical alphanumeric tokens.
        // e.g. "U.S.A." -> "usa", "J.K." -> "jk"
        normalized = collapseDottedAcronyms(normalized);

        return normalized;
    }

    public static String normalizeCorpusLine(String line) {
        String normalized = normalizeText(line);
        return normalized.replaceAll("[\"_]", "");
    }

    public static List<String> tokenizeWords(String text) {
        List<String> tokens = new ArrayList<>();
        String normalized = normalizeText(text);
        Matcher matcher = WORD_PATTERN.matcher(normalized);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    public static List<String> tokenizeCurrentSentenceWords(String text) {
        String normalized = normalizeText(text).trim();
        if (normalized.isEmpty()) return new ArrayList<>();

        String[] sentenceSegments = normalized.split("[.!?]+");
        String currentSentence = sentenceSegments.length == 0
                ? ""
                : sentenceSegments[sentenceSegments.length - 1];

        // Keep apostrophes, hyphens, and digits for consistent token identity with corpus parser.
        currentSentence = currentSentence.replaceAll("[^\\p{L}\\p{N}'\\-\\s]", " ");
        return tokenizeWords(currentSentence);
    }

    private static String collapseDottedAcronyms(String input) {
        Matcher matcher = DOTTED_ACRONYM_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String collapsed = matcher.group().replace(".", "");
            matcher.appendReplacement(sb, Matcher.quoteReplacement(collapsed));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
