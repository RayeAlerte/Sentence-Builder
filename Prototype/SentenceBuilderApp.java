import java.util.*;
import java.sql.*;

public class SentenceBuilderApp {
    // Memory structures
    private Map<String, List<String>> bigramMap = new HashMap<>(); // w1 -> list of w2s sorted by freq
    private Map<String, List<String>> trigramMap = new HashMap<>(); // "w1 w2" -> list of w3s sorted by freq

    public void loadDatabaseIntoMemory(Connection conn) throws SQLException {
        /*
         * IDEAL STORAGE METHOD FOR LARGE DATASETS:
         * If the database grows too large (e.g., millions of n-grams from parsing large books), 
         * loading it entirely into a standard HashMap will cause an OutOfMemoryError. 
         * * The ideal solution is to use an LRU (Least Recently Used) Cache. This limits the map 
         * to a specific size, automatically evicting the oldest, least-used entries when full. 
         * For the generative algorithms, you pre-load only the top X most frequent word combinations 
         * into memory. If the user types a rare word combination that isn't in the cache, the 
         * application would then execute a targeted SQL query on-demand to fetch it.
         */

        System.out.println("Initializing memory structures...");
        final int MAX_CACHE_SIZE = 50000; // Limit memory footprint

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

        // Pre-load only the most frequent Bigrams to prevent memory overflow
        System.out.println("Pre-loading top bigrams...");
        String bigramQuery = "SELECT word1, word2 FROM Bigrams ORDER BY frequency DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(bigramQuery)) {
            ps.setInt(1, MAX_CACHE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String w1 = rs.getString("word1");
                    String w2 = rs.getString("word2");
                    bigramMap.computeIfAbsent(w1, k -> new ArrayList<>()).add(w2);
                }
            }
        }

        // Pre-load only the most frequent Trigrams
        System.out.println("Pre-loading top trigrams...");
        String trigramQuery = "SELECT word1, word2, word3 FROM Trigrams ORDER BY frequency DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(trigramQuery)) {
            ps.setInt(1, MAX_CACHE_SIZE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("word1") + " " + rs.getString("word2");
                    String w3 = rs.getString("word3");
                    trigramMap.computeIfAbsent(key, k -> new ArrayList<>()).add(w3);
                }
            }
        }
        
        System.out.println("Memory initialization complete.");
    }

    public void startCLI() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nSelect Mode: [1] Autocomplete  [2] Generate Sentence  [3] Exit");
            String mode = scanner.nextLine();
            
            if (mode.equals("3")) break;

            System.out.println("Select Algorithm: [1] Frequency (Bigram)  [2] N-Gram Context (Trigram)");
            String algo = scanner.nextLine();

            if (mode.equals("1")) {
                runAutocomplete(scanner, algo);
            } else if (mode.equals("2")) {
                runGeneration(scanner, algo);
            }
        }
    }

    private void runAutocomplete(Scanner scanner, String algo) {
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
            
            // Trigger autocomplete only when space/word boundary is hit [cite: 25]
            String[] words = currentInput.toString().trim().split(" ");
            
            List<String> suggestions = new ArrayList<>();
            if (algo.equals("2") && words.length >= 2) {
                String key = words[words.length - 2] + " " + words[words.length - 1];
                suggestions = trigramMap.getOrDefault(key, new ArrayList<>());
            } else {
                String key = words[words.length - 1];
                suggestions = bigramMap.getOrDefault(key, new ArrayList<>());
            }

            System.out.println("--> Suggestions: " + (suggestions.isEmpty() ? "None" : suggestions.subList(0, Math.min(5, suggestions.size()))));
        }
    }

    private void runGeneration(Scanner scanner, String algo) {
        System.out.print("Enter a starting word: "); 
        String rawInput = scanner.nextLine().trim().toLowerCase();
        
        // BUG FIX: Split by whitespace and isolate the first word
        String[] inputWords = rawInput.split("\\s+");
        if (inputWords.length == 0 || inputWords[0].isEmpty()) {
            System.out.println("Invalid input.");
            return;
        }
        String currentWord = inputWords[0]; 
        
        List<String> sentence = new ArrayList<>();
        sentence.add(currentWord);

        for (int i = 0; i < 15; i++) { // Generate up to 15 words
            String nextWord = null;
            
            if (algo.equals("2") && sentence.size() >= 2) {
                String key = sentence.get(sentence.size() - 2) + " " + sentence.get(sentence.size() - 1);
                List<String> options = trigramMap.get(key);
                if (options != null && !options.isEmpty()) nextWord = options.get(0); // Greedy pick for prototype
            } 
            
            // Fallback to Bigram if Trigram fails or algo is 1
            if (nextWord == null) { 
                List<String> options = bigramMap.get(currentWord);
                if (options != null && !options.isEmpty()) nextWord = options.get(0);
            }

            if (nextWord == null) break; // Dead end
            
            sentence.add(nextWord);
            currentWord = nextWord;
        }

        // Capitalize the first letter of the generated sentence
        if (!sentence.isEmpty()) {
            String firstWord = sentence.get(0);
            sentence.set(0, firstWord.substring(0, 1).toUpperCase() + firstWord.substring(1));
        }

        System.out.println("Generated: " + String.join(" ", sentence) + ".");
    }

    public static void main(String[] args) {
        // Database connection details should match those used when populating the corpus
        final String DB_URL = "jdbc:mysql://localhost:3306/BuilderWords";
        final String USER = "sentencebuilder";
        final String PASS = "Yo457S<DWL.D";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            SentenceBuilderApp app = new SentenceBuilderApp();
            app.loadDatabaseIntoMemory(conn);
            app.startCLI();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}