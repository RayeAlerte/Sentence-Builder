import java.util.*;
import java.sql.*;

public class SentenceBuilder {
    private DBMan dbMan;

    // Memory structures
    private Map<String, List<String>> bigramMap = new HashMap<>();
    private Map<String, List<String>> trigramMap = new HashMap<>();
    private List<String> sentenceStarters = new ArrayList<>();

    enum CLIMode {
        REPORTING, AUTOCOMPLETE, GENERATE, EXIT;

        public static CLIMode fromInput(int input) {
            return switch (input) {
                case 0 -> REPORTING;
                case 1 -> AUTOCOMPLETE;
                case 2 -> GENERATE;
                case 3 -> EXIT;
                default -> throw new IllegalArgumentException("Invalid mode: " + input);
            };
        }
    }

    enum Algo {
        BIGRAM, TRIGRAM;

        public static Algo fromInput(int input) {
            return switch (input) {
                case 0 -> BIGRAM;
                case 1 -> TRIGRAM;
                default -> throw new IllegalArgumentException("Invalid algo: " + input);
            };
        }
    }

    enum SortType {
        ALPHA, FREQ;

        public static SortType fromInput(int input) {
            return switch (input) {
                case 0 -> ALPHA;
                case 1 -> FREQ;
                default -> throw new IllegalArgumentException("Invalid sort: " + input);
            };
        }
    }

    private static final int MAX_CACHE_SIZE = 50000;

    public SentenceBuilder(DBMan dbMan) {
        this.dbMan = dbMan;
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
            System.out.println("\nSelect Mode: [0] Reporting [1] Autocomplete [2] Generate Sentence [3] Exit");
            CLIMode mode;
            try {
                int input = Integer.parseInt(scanner.nextLine());
                mode = CLIMode.fromInput(input);
            } catch (Exception e) {
                System.out.println("Please select a valid option");
                continue;
            }

            switch (mode) {
                case REPORTING -> {
                    runReporting(scanner);
                    continue;
                }
                case EXIT -> {
                    break out;
                }
                default -> {
                }
            }

            System.out.println("Select Algorithm: [0] Frequency (Bigram)  [1] N-Gram Context (Trigram)");
            Algo algo;
            try {
                int input = Integer.parseInt(scanner.nextLine());
                algo = Algo.fromInput(input);
            } catch (Exception e) {
                System.out.println("Please select a valid option");
                continue;
            }

            switch (mode) {
                case AUTOCOMPLETE -> runAutocomplete(scanner, algo);
                case GENERATE -> runGeneration(scanner, algo);
                default -> System.out.println("Unhandled mode: " + mode);
            }
        }
    }

    private void runAutocomplete(Scanner scanner, Algo algo) {
        System.out.println("Start typing. End with a space to see suggestions. Type '.', '!', or '?' to quit.");
        StringBuilder currentInput = new StringBuilder();

        while (true) {
            System.out.print("Input: " + currentInput);
            String token = scanner.nextLine().toLowerCase();
            if (token.matches(".*[.!?].*")) {
                System.out.println("Autocomplete ended.");
                break;
            }

            currentInput.append(token).append(" ");

            String[] words = currentInput.toString().trim().split(" ");

            List<String> suggestions = new ArrayList<>();
            if (algo == Algo.TRIGRAM && words.length >= 2) {
                String key = words[words.length - 2] + " " + words[words.length - 1];
                suggestions = trigramMap.getOrDefault(key, new ArrayList<>());
            } else {
                String key = words[words.length - 1];
                suggestions = bigramMap.getOrDefault(key, new ArrayList<>());
            }

            if (suggestions.isEmpty() && words.length <= 1) {
                suggestions = new ArrayList<>(sentenceStarters);
            }

            System.out.println("--> Suggestions: "
                    + (suggestions.isEmpty() ? "None" : suggestions.subList(0, Math.min(5, suggestions.size()))));
        }
    }

    private void runReporting(Scanner scanner) {
        SortType sort = null;
        do {
            System.out.print("Select Sorting Mode [0] Alphabetical [1] Frequency: ");
            try {
                int input = Integer.parseInt(scanner.nextLine());
                sort = SortType.fromInput(input);
            } catch (Exception e) {
                System.out.println("Please select a valid option");
            }
        } while (sort == null);

        try {
            List<Word> words = switch (sort) {
                case ALPHA -> dbMan.getAllWordsSortedAlpha();
                case FREQ -> dbMan.getAllWordsSortedByFrequency();
            };

            System.out.println("\n--- Word Report ---");
            for (Word w : words) {
                System.out.println(w.word +
                        " | total: " + w.totalCount +
                        " | starts: " + w.startCount +
                        " | ends: " + w.endCount);
            }
        } catch (SQLException e) {
            System.out.println("Error fetching report: " + e.getMessage());
        }
    }

    private void runGeneration(Scanner scanner, Algo algo) {
        System.out.print("Enter a starting word: ");
        String rawInput = scanner.nextLine().trim().toLowerCase();

        String[] inputWords = rawInput.split("\\s+");
        if (inputWords.length == 0 || inputWords[0].isEmpty()) {
            System.out.println("Invalid input.");
            return;
        }
        String currentWord = inputWords[0];

        List<String> sentence = new ArrayList<>();
        sentence.add(currentWord);

        for (int i = 0; i < 15; i++) {
            String nextWord = null;

            if (algo == Algo.TRIGRAM && sentence.size() >= 2) {
                String key = sentence.get(sentence.size() - 2) + " " + sentence.get(sentence.size() - 1);
                List<String> options = trigramMap.get(key);
                if (options != null && !options.isEmpty())
                    nextWord = options.get(0);
            }

            if (nextWord == null) {
                List<String> options = bigramMap.get(currentWord);
                if (options != null && !options.isEmpty())
                    nextWord = options.get(0);
            }

            if (nextWord == null)
                break;

            sentence.add(nextWord);
            currentWord = nextWord;
        }

        if (!sentence.isEmpty()) {
            String firstWord = sentence.get(0);
            sentence.set(0, firstWord.substring(0, 1).toUpperCase() + firstWord.substring(1));
        }

        System.out.println("Generated: " + String.join(" ", sentence) + ".");
    }

}
