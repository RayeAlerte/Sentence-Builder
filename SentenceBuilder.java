import java.util.*;
import java.sql.*;

public class SentenceBuilder {
    private DBMan dbMan;
    private Reporter reporter;

    // Memory structures
    /* Made public so it can be accessed in ui */
    public Map<String, List<String>> bigramMap = new HashMap<>();
    public Map<String, List<String>> trigramMap = new HashMap<>();
    public List<String> sentenceStarters = new ArrayList<>();

    // Randomizers
    private Random random = new Random();
    /* Made public so it can be accessed in ui */
    public int randomnessPool = 1; // 1 = Greedy (Top result), >1 = Random from Top N

    public enum LearningStrength {
        GENTLE, BALANCED, STRONG
    }

    enum CLIMode {
        REPORTING, AUTOCOMPLETE, GENERATE, OPTIONS, EXIT;

        public static CLIMode fromInput(int input) {
            return switch (input) {
                case 0 -> REPORTING;
                case 1 -> AUTOCOMPLETE;
                case 2 -> GENERATE;
                case 3 -> OPTIONS;
                case 4 -> EXIT;
                default -> throw new IllegalArgumentException("Invalid mode: " + input);
            };
        }
    }

    private static final int MAX_CACHE_SIZE = 50000;

    public SentenceBuilder(DBMan dbMan, Reporter reporter) {
        this.dbMan = dbMan;
        this.reporter = reporter;
    }

    public void loadDatabaseIntoMemory() throws SQLException {
        /*
         * IDEAL STORAGE METHOD FOR LARGE DATASETS:
         * If the database grows too large (e.g., millions of n-grams from parsing large
         * books), loading it entirely into a standard HashMap will cause an
         * OutOfMemoryError.
         * The ideal solution is to use an LRU (Least Recently Used) Cache. This limits
         * the map
         * to a specific size, automatically evicting the oldest, least-used entries
         * when full.
         * For the generative algorithms, you pre-load only the top X most frequent word
         * combinations into memory. If the user types a rare word combination that
         * isn't in the
         * cache, the application would then execute a targeted SQL query on-demand to
         * fetch it.
         */

        System.out.println("Initializing memory structures...");

        // Initialize Bigram LRU Cache
        this.bigramMap = new LinkedHashMap<String, List<String>>(MAX_CACHE_SIZE + 1, 1.0f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };

        // Initialize Trigram LRU Cache
        this.trigramMap = new LinkedHashMap<String, List<String>>(MAX_CACHE_SIZE + 1, 1.0f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };

        // Pre-load sentence starters
        System.out.println("Pre-loading sentence starters...");
        sentenceStarters = dbMan.getSentenceStarters(500);

        // Pre-load top bigrams and build map
        System.out.println("Pre-loading top bigrams...");
        List<Bigram> bigrams = dbMan.loadBigrams(MAX_CACHE_SIZE);
        for (Bigram b : bigrams) {
            bigramMap.computeIfAbsent(b.word1, k -> new ArrayList<>()).add(b.word2);
        }

        // Pre-load top trigrams and build map
        System.out.println("Pre-loading top trigrams...");
        List<Trigram> trigrams = dbMan.loadTrigrams(MAX_CACHE_SIZE);
        for (Trigram t : trigrams) {
            String key = t.word1 + " " + t.word2;
            trigramMap.computeIfAbsent(key, k -> new ArrayList<>()).add(t.word3);
        }

        System.out.println("Memory initialization complete.");
    }

    public void startCLI() {
        Scanner scanner = new Scanner(System.in);
        out: while (true) {
            System.out.println(
                    "\nSelect Mode: [0] Reporting [1] Autocomplete [2] Generate Sentence [3] Options [4] Exit");
            CLIMode mode;
            try {
                int input = Integer.parseInt(scanner.nextLine());
                mode = CLIMode.fromInput(input);
            } catch (Exception e) {
                System.out.println("Please select a valid option");
                continue;
            }

            switch (mode) {
                case AUTOCOMPLETE -> {
                    // runAutocomplete(scanner);
                }
                case GENERATE -> {
                    // runGeneration(scanner);
                }
                case REPORTING -> {
                    runReporting(scanner);
                    continue;
                }
                case OPTIONS -> {
                    System.out.println("Current Randomness Pool Size: " + randomnessPool);
                    System.out.print("Enter new pool size (1 = most predictable, 5+ = more chaotic): ");
                    try {
                        int newPool = Integer.parseInt(scanner.nextLine());
                        if (newPool > 0) {
                            randomnessPool = newPool;
                            System.out.println("Randomness set to " + randomnessPool);
                        } else {
                            System.out.println("Must be at least 1.");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid number.");
                    }
                }
                case EXIT -> {
                    break out;
                }
                default -> System.out.println("Unhandled mode: " + mode);
            }
        }
    }

    public List<String> runAutocomplete(String sentence) {
        StringBuilder currentInput = new StringBuilder();
        Scanner scanner = new Scanner(sentence);
        while (true) {
            System.out.print("Input: " + currentInput);
            String token = scanner.nextLine().toLowerCase();
            if (token.matches(".*[.!?].*")) {
                currentInput.append(token);
                String finalSentence = currentInput.toString().trim();
                
                try {
                    dbMan.logUserActivity("AUTOCOMPLETE", finalSentence);
                } catch (SQLException e) { System.out.println("Log error."); }
                
                learnFromUserInput(finalSentence, LearningStrength.BALANCED);
                System.out.println("Autocomplete ended.");
                break;
            }

            currentInput.append(token).append(" ");
            String[] words = currentInput.toString().trim().split(" ");
            List<String> suggestions = new ArrayList<>();

            // Use the new fetchers instead of getOrDefault
            if (words.length >= 2) {
                suggestions = fetchTrigrams(words[words.length - 2], words[words.length - 1]);
            } 
            if (suggestions.isEmpty() && words.length >= 1) {
                suggestions = fetchBigrams(words[words.length - 1]);
            } 
            if (suggestions.isEmpty() && words.length <= 1) {
                suggestions = new ArrayList<>(sentenceStarters);
            }

            scanner.close();
            return suggestions;
        }
        scanner.close();
        return null;
    }

    private void runReporting(Scanner scanner) {
        Reporter.SortType sort = null;
        do {
            System.out.print("Select Sorting Mode [0] Alphabetical [1] Frequency: ");
            try {
                int input = Integer.parseInt(scanner.nextLine());
                sort = Reporter.SortType.fromInput(input);
            } catch (Exception e) {
                System.out.println("Please select a valid option");
            }
        } while (sort == null);

        reporter.setSortType(sort);
        List<Word> words = reporter.getSortedWords();
        for (Word w : words) {
            System.out.println(w.word +
                    " | total: " + w.totalCount +
                    " | starts: " + w.startCount +
                    " | ends: " + w.endCount);
        }
    }

    public List<String> runGeneration(String og_sentence) {
        return runGeneration(og_sentence, 15, true, LearningStrength.BALANCED);
    }

    public List<String> runGeneration(String og_sentence, int maxGeneratedWords, boolean rememberNewInput) {
        return runGeneration(og_sentence, maxGeneratedWords, rememberNewInput, LearningStrength.BALANCED);
    }

    public List<String> runGeneration(String og_sentence, int maxGeneratedWords, boolean rememberNewInput,
            LearningStrength learningStrength) {
        Scanner scanner = new Scanner(og_sentence);
        String rawInput = scanner.nextLine().trim().toLowerCase();

        String[] inputWords = rawInput.split("\\s+");
        if (inputWords.length == 0 || inputWords[0].isEmpty()) {
            System.out.println("Invalid input.");
            scanner.close();
            return null;
        }
        String currentWord = inputWords[0];

        try { dbMan.logUserActivity("STARTER_WORD", currentWord); } catch (SQLException e) {}
        if (rememberNewInput && !sentenceStarters.contains(currentWord)) {
            learnFromUserInput(currentWord, learningStrength);
        }

        List<String> sentence = new ArrayList<>();
        sentence.add(currentWord);

        for (int i = 0; i < maxGeneratedWords; i++) {
            String nextWord = null;

            if (sentence.size() >= 2) {
                // Use fetchTrigrams
                List<String> options = fetchTrigrams(sentence.get(sentence.size() - 2), sentence.get(sentence.size() - 1));
                nextWord = pickNextWord(options);
            }

            if (nextWord == null) {
                // Use fetchBigrams
                List<String> options = fetchBigrams(currentWord);
                nextWord = pickNextWord(options);
            }

            if (nextWord == null)
                nextWord = pickNextWord(fetchSentenceStarters());

            if (nextWord == null)
                break; // Dead end

            sentence.add(nextWord);
            currentWord = nextWord;
        }

        if (!sentence.isEmpty()) {
            String firstWord = sentence.get(0);
            sentence.set(0, firstWord.substring(0, 1).toUpperCase() + firstWord.substring(1));
        }

        String finalOutput = String.join(" ", sentence) + ".";
        try { dbMan.logUserActivity("GENERATION", finalOutput); } catch (SQLException e) {}
        
        System.out.println("Generated: " + finalOutput);
        
        scanner.close();
        return sentence;
    }

    public List<String> getSuggestionsForInput(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return fetchSentenceStarters();
        }

        // Use only the current sentence context so punctuation can start a new search context.
        String[] sentenceSegments = normalized.split("[.!?]+");
        String currentSentence = sentenceSegments.length == 0
                ? ""
                : sentenceSegments[sentenceSegments.length - 1].trim();

        // Keep only word-like characters used by the corpus parser.
        currentSentence = currentSentence.replaceAll("[^a-zA-Z'\\-\\s]", " ").trim();
        if (currentSentence.isEmpty()) {
            return fetchSentenceStarters();
        }

        String[] words = currentSentence.split("\\s+");
        List<String> suggestions = new ArrayList<>();

        if (words.length >= 2) {
            suggestions = fetchTrigrams(words[words.length - 2], words[words.length - 1]);
        }
        if (suggestions.isEmpty()) {
            suggestions = fetchBigrams(words[words.length - 1]);
        }
        if (suggestions.isEmpty()) {
            suggestions = fetchSentenceStarters();
        }
        return suggestions;
    }

    public void rememberUserInput(String userSentence) {
        learnFromUserInput(userSentence, LearningStrength.BALANCED);
    }

    public void rememberUserInput(String userSentence, LearningStrength learningStrength) {
        learnFromUserInput(userSentence, learningStrength);
    }

    private List<String> fetchSentenceStarters() {
        try {
            List<String> starters = dbMan.getSentenceStarters(500);
            if (!starters.isEmpty()) {
                sentenceStarters = new ArrayList<>(starters);
            }
        } catch (SQLException e) {
            System.out.println("DB Error: " + e.getMessage());
        }
        return new ArrayList<>(sentenceStarters);
    }

    // ─────────────────────────────────────────
    // Dynamic Fetching/Selection & Learning Helpers
    // ─────────────────────────────────────────

    // Helper method for selecting the next word based on randomness settings
    /* Made public so it can be accessed in ui */
    public String pickNextWord(List<String> options) {
        if (options == null || options.isEmpty())
            return null;
        int bound = Math.min(options.size(), randomnessPool);
        return options.get(random.nextInt(bound));
    }

    private List<String> fetchBigrams(String word) {
        List<String> options = bigramMap.get(word);
        if (options != null && !options.isEmpty()) return options;

        try {
            options = dbMan.getBigramFallback(word);
            if (!options.isEmpty()) bigramMap.put(word, options); // Hot-load LRU
        } catch (SQLException e) {
            System.out.println("DB Error: " + e.getMessage());
        }
        return options != null ? options : new ArrayList<>();
    }

    private List<String> fetchTrigrams(String word1, String word2) {
        String key = word1 + " " + word2;
        List<String> options = trigramMap.get(key);
        if (options != null && !options.isEmpty()) return options;

        try {
            options = dbMan.getTrigramFallback(word1, word2);
            if (!options.isEmpty()) trigramMap.put(key, options); // Hot-load LRU
        } catch (SQLException e) {
            System.out.println("DB Error: " + e.getMessage());
        }
        return options != null ? options : new ArrayList<>();
    }

    private void learnFromUserInput(String userSentence, LearningStrength learningStrength) {
        String[] words = userSentence.replaceAll("[^a-zA-Z ]", "").toLowerCase().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return;

        // 1. Hot-Load Memory Caches
        String starter = words[0];
        if (!sentenceStarters.contains(starter)) {
            sentenceStarters.add(0, starter);
        }

        for (int i = 0; i < words.length - 1; i++) {
            String w1 = words[i];
            String w2 = words[i + 1];
            
            bigramMap.computeIfAbsent(w1, k -> new ArrayList<>()).remove(w2);
            bigramMap.get(w1).add(0, w2);

            if (i < words.length - 2) {
                String w3 = words[i + 2];
                String triKey = w1 + " " + w2;
                trigramMap.computeIfAbsent(triKey, k -> new ArrayList<>()).remove(w3);
                trigramMap.get(triKey).add(0, w3);
            }
        }

        // 2. Send to DBMan for context-aware weighted UPSERT
        dbMan.insertLearnedData(words, toStrengthMultiplier(learningStrength));
    }

    private double toStrengthMultiplier(LearningStrength learningStrength) {
        return switch (learningStrength) {
            case GENTLE -> 0.8;
            case BALANCED -> 1.0;
            case STRONG -> 1.25;
        };
    }

}
