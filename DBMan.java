/*
    Written by Owen Giles and Elliot Algase for CS4485.0W1, Sentence Builder, starting April 7th, 2026
*/

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.*;

public class DBMan {
	private static final String DB_URL = "jdbc:sqlite:BuilderWords.db";
//	private static final String USER = "sentencebuilder";
//	private static final String PASS = "Yo457S<DWL.D";

	private Connection conn;

	/**
	 * Opens a connection to the SQLite database and runs any pending schema migrations.
	 * Auto-commit is disabled so all writes are transactional by default.
	 */
	public void connect() throws SQLException {
//		conn = DriverManager.getConnection(DB_URL, USER, PASS);
		conn = DriverManager.getConnection(DB_URL);
		conn.setAutoCommit(false);

		migrateIfNecessary();
	}

	/**
	 * Checks whether a table with the given name exists in the SQLite schema.
	 * Used by migrateIfNecessary() to detect a fresh database with no Migrations table yet.
	 *
	 * @param tableName the table to look up
	 * @return true if the table exists, false otherwise
	 */
	private boolean tableExists(String tableName) throws SQLException {
		String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, tableName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next(); // return true if we found a row (i.e. if the table exists)
			}
		}
	}

	/**
	 * Returns all SQL migration files from the migrations/ directory, sorted alphabetically.
	 * Alphabetical order keeps migrations deterministic. Files should be named with a
	 * numeric prefix (e.g. 001_create_tables.sql) so they apply in the intended sequence.
	 *
	 * @return sorted list of migration files, or an empty list if the directory is missing or empty
	 */
    private static List<File> getMigrations() {
        File dir = new File("migrations/");

		// list the migrations
        File[] filesArray = dir.listFiles();

        List<File> fileList = new ArrayList<>();

        if (filesArray != null) {
            fileList.addAll(Arrays.asList(filesArray));

            Collections.sort(fileList);
        }
        return fileList;
    }

	/**
	 * Executes all SQL statements in the given file against the current connection.
	 * Statements are delimited by semicolons. Empty tokens are skipped.
	 *
	 * If a statement fails with "duplicate column name" the error is swallowed and
	 * execution continues. This allows ADD COLUMN migrations to be safely re-run
	 * on a database where the column was already added in a previous partial run.
	 * All other SQL errors are re-thrown.
	 *
	 * @param sqlFile the file containing semicolon-separated SQL statements to execute
	 */
	private void runSqlFile(File sqlFile) throws SQLException {
		try {
			try (Scanner scanner = new Scanner(sqlFile).useDelimiter(";")) {
				try (Statement stmt = conn.createStatement()) {
					while (scanner.hasNext()) {
						String line = scanner.next().trim();
						if (!line.isEmpty()) {
							try {
								stmt.execute(line);
							} catch (SQLException e) {
								String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
								// Allow partially-applied ADD COLUMN migrations to be rerun safely.
								if (msg.contains("duplicate column name")) {
									continue;
								}
								throw e;
							}
						}
					}
				}
			}
		} catch (FileNotFoundException e){
			// not possible
		}
	}

	/**
	 * Applies any SQL migration files that have not yet been recorded in the Migrations table.
	 *
	 * On a brand-new database the Migrations table won't exist yet, so we skip the
	 * "what's already applied" query and treat every file as pending. Each successfully
	 * executed file is then recorded in Migrations so it won't be re-applied on future runs.
	 * This keeps schema changes incremental and safe to run at startup every time.
	 */
	private void migrateIfNecessary() throws SQLException {
		// Collect the filenames of migrations that have already been applied.
		List<String> appliedMigrations = new ArrayList<>();
		if (tableExists("Migrations")){
			String sql = "SELECT filename FROM Migrations";
			try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)){
				while (rs.next()){
					appliedMigrations.add(rs.getString("filename"));
				}
			}
		}

		// Apply each migration file that isn't already recorded, then mark it as done.
		for (File migration : getMigrations()){
			if (!appliedMigrations.contains(migration.getName())) {
				runSqlFile(migration);
				String sql = "INSERT INTO migrations (filename) VALUES (?)";
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					ps.setString(1, migration.getName());
					ps.executeUpdate();
				}

			}
		}
	}

	public void disconnect() throws SQLException {
		if (conn != null && !conn.isClosed()) {
			conn.close();
		}
	}

	public void commit() throws SQLException {
		conn.commit();
	}

	public void rollback() throws SQLException {
		conn.rollback();
	}

	/**
	 * Returns the set of file names that have previously been imported into the corpus.
	 * Used at import time to skip files that have already been processed.
	 */
	public Set<String> getImportedFileNames() throws SQLException {
		Set<String> files = new HashSet<>();
		String sql = "SELECT file_name FROM ImportedFiles";
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				files.add(rs.getString("file_name"));
			}
		}
		return files;
	}

	/** Returns the total number of files recorded in ImportedFiles. */
	public long numImportedFiles() throws SQLException {
		String sql = "SELECT COUNT(*) FROM ImportedFiles";
		long ret = 0;
		try (Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			if (rs.next())
				ret = rs.getLong(1);
		}
		return ret;
	}

	/**
	 * Records that a file has been imported, storing its name and word count.
	 * If the file was imported before, the existing row is updated with the new
	 * word count and a refreshed import timestamp (upsert).
	 *
	 * @param file the imported file metadata to persist
	 */
	public void logImport(ImportedFile file) throws SQLException {
// old mysql queries
//		String sql = "INSERT INTO ImportedFiles (file_name, word_count) VALUES (?, ?) " +
//				"ON DUPLICATE KEY UPDATE word_count = VALUES(word_count), " +
//				"import_date = CURRENT_TIMESTAMP";
		String sql = "INSERT INTO ImportedFiles (file_name, word_count) VALUES (?, ?) " +
				"ON CONFLICT(file_name) DO UPDATE SET " +
				"word_count = excluded.word_count, " +
				"import_date = CURRENT_TIMESTAMP";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, file.fileName);
			ps.setInt(2, file.wordCount);
			ps.executeUpdate();
		}
	}

	/**
	 * Inserts or increments corpus counts for a batch of words.
	 * For each word, total_count is incremented by 1 and start_count is incremented
	 * by the entry's startCount value (1 if it opened a sentence, 0 otherwise).
	 * Uses batch execution for efficiency on large imports.
	 *
	 * @param words list of words with their sentence-position metadata
	 */
	public void insertWords(List<WordEntry> words) throws SQLException {
// old mysql queries
//		String sql = "INSERT INTO WordCorpus (word, total_count, start_count, end_count) " +
//				"VALUES (?, 1, ?, 0) " +
//				"ON DUPLICATE KEY UPDATE total_count = total_count + 1, " +
//				"start_count = start_count + VALUES(start_count)";
		String sql = "INSERT INTO WordCorpus (word, total_count, start_count, end_count) " +
                "VALUES (?, 1, ?, 0) " +
                "ON CONFLICT(word) DO UPDATE SET " +
                "total_count = WordCorpus.total_count + 1, " +
                "start_count = WordCorpus.start_count + excluded.start_count";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (WordEntry entry : words) {
				ps.setString(1, entry.word);
				ps.setInt(2, entry.startCount);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	/**
	 * Inserts or increments frequency counts for a batch of word pairs (bigrams).
	 * Each (word1, word2) pair's frequency is incremented by 1 on conflict.
	 * Uses batch execution for efficiency on large imports.
	 *
	 * @param bigrams list of word pairs to record
	 */
	public void insertBigrams(List<Bigram> bigrams) throws SQLException {
// old mysql queries
//		String sql = "INSERT INTO Bigrams (word1, word2, frequency) VALUES (?, ?, 1) " +
//				"ON DUPLICATE KEY UPDATE frequency = frequency + 1";
		String sql = "INSERT INTO Bigrams (word1, word2, frequency) VALUES (?, ?, 1) " +
                "ON CONFLICT(word1, word2) DO UPDATE SET frequency = frequency + 1";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (Bigram bigram : bigrams) {
				ps.setString(1, bigram.word1);
				ps.setString(2, bigram.word2);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	/**
	 * Inserts or increments frequency counts for a batch of word triples (trigrams).
	 * Each (word1, word2, word3) triple's frequency is incremented by 1 on conflict.
	 * Uses batch execution for efficiency on large imports.
	 *
	 * @param trigrams list of word triples to record
	 */
	public void insertTrigrams(List<Trigram> trigrams) throws SQLException {
// old mysql queries
//		String sql = "INSERT INTO Trigrams (word1, word2, word3, frequency) VALUES (?, ?, ?, 1) " +
//				"ON DUPLICATE KEY UPDATE frequency = frequency + 1";
		String sql = "INSERT INTO Trigrams (word1, word2, word3, frequency) VALUES (?, ?, ?, 1) " +
                "ON CONFLICT(word1, word2, word3) DO UPDATE SET frequency = frequency + 1";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (Trigram trigram : trigrams) {
				ps.setString(1, trigram.word1);
				ps.setString(2, trigram.word2);
				ps.setString(3, trigram.word3);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	/**
	 * Increments end_count by 1 for each word in the list.
	 * Called after processing a sentence to mark which words appeared at the end of one.
	 * Uses batch execution for efficiency on large imports.
	 *
	 * @param words the words to mark as sentence-enders
	 */
	public void updateEndCounts(List<String> words) throws SQLException {
		String sql = "UPDATE WordCorpus SET end_count = end_count + 1 WHERE word = ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (String word : words) {
				ps.setString(1, word);
				ps.addBatch();
			}
			ps.executeBatch();
		}
	}

	/**
	 * Returns the top sentence-starting words ranked by their combined base and boost start counts.
	 * The merged (start_count + boost_start_count) score means user-reinforced starters
	 * naturally bubble up without overwriting the original corpus data.
	 *
	 * @param limit maximum number of starters to return
	 * @return list of words sorted from most to least frequent sentence starter
	 */
	public List<String> getSentenceStarters(int limit) throws SQLException {
		List<String> starters = new ArrayList<>();
		String sql = "SELECT word FROM WordCorpus WHERE (start_count + boost_start_count) > 0 " +
				"ORDER BY (start_count + boost_start_count) DESC LIMIT ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					starters.add(rs.getString("word"));
				}
			}
		}
		return starters;
	}

	/**
	 * Returns the top bigrams ranked by combined base and boost frequency.
	 * Only word1 and word2 are loaded — frequency values are used only for ordering
	 * and are not needed by the callers (the generator works with ordered lists).
	 *
	 * @param limit maximum number of bigrams to return
	 * @return list of bigrams sorted from most to least frequent
	 */
	public List<Bigram> loadBigrams(int limit) throws SQLException {
		List<Bigram> bigrams = new ArrayList<>();
		String sql = "SELECT word1, word2 FROM Bigrams ORDER BY (frequency + boost_frequency) DESC LIMIT ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Bigram b = new Bigram();
					b.word1 = rs.getString("word1");
					b.word2 = rs.getString("word2");
					bigrams.add(b);
				}
			}
		}
		return bigrams;
	}

	/**
	 * Returns the top trigrams ranked by combined base and boost frequency.
	 * Only the three word columns are loaded — see loadBigrams() for the same rationale.
	 *
	 * @param limit maximum number of trigrams to return
	 * @return list of trigrams sorted from most to least frequent
	 */
	public List<Trigram> loadTrigrams(int limit) throws SQLException {
		List<Trigram> trigrams = new ArrayList<>();
		String sql = "SELECT word1, word2, word3 FROM Trigrams ORDER BY (frequency + boost_frequency) DESC LIMIT ?";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					Trigram t = new Trigram();
					t.word1 = rs.getString("word1");
					t.word2 = rs.getString("word2");
					t.word3 = rs.getString("word3");
					trigrams.add(t);
				}
			}
		}
		return trigrams;
	}

	// ─────────────────────────────────────────
    // User Interaction & Dynamic Learning
    // ─────────────────────────────────────────

    public static class UserHistoryEntry {
        public int id;
        public String activityType;
        public String content;
        public String createdAt;
    }

	/**
	 * Returns up to 50 words that most commonly follow the given word, ranked by
	 * combined base and boost frequency. Used as a fallback when no trigram context
	 * is available i.e. when we only know the immediately preceding word.
	 *
	 * @param word the preceding word to look up followers for
	 * @return ordered list of candidate next words
	 */
    public List<String> getBigramFallback(String word) throws SQLException {
        List<String> options = new ArrayList<>();
        String sql = "SELECT word2 FROM Bigrams WHERE word1 = ? ORDER BY (frequency + boost_frequency) DESC LIMIT 50";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) options.add(rs.getString("word2"));
            }
        }
        return options;
    }

	/**
	 * Returns up to 10 words that most commonly follow the given two-word sequence,
	 * ranked by combined base and boost frequency. Used during sentence generation
	 * when both the previous and second-to-last words are known.
	 *
	 * @param word1 the second-to-last word in the current sequence
	 * @param word2 the last word in the current sequence
	 * @return ordered list of candidate next words
	 */
    public List<String> getTrigramFallback(String word1, String word2) throws SQLException {
        List<String> options = new ArrayList<>();
        String sql = "SELECT word3 FROM Trigrams WHERE word1 = ? AND word2 = ? ORDER BY (frequency + boost_frequency) DESC LIMIT 10";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, word1);
            ps.setString(2, word2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) options.add(rs.getString("word3"));
            }
        }
        return options;
    }

	/**
	 * Appends an entry to the UserHistory audit trail with the given activity type and content.
	 * This table is append-only. Rows are never updated or deleted — so it serves as a
	 * reliable record for the Generate history tab, the Logs tab, and export/report diagnostics.
	 * The commit is issued immediately so the entry is persisted even if the caller crashes
	 * before its own transaction completes.
	 *
	 * @param activityType a short category label (e.g. "generate", "import", "boost")
	 * @param content      the human-readable description of what happened
	 */
    public void logUserActivity(String activityType, String content) throws SQLException {
        // Centralized append-only audit trail used by Generate history, Logs tab, and export/report diagnostics.
        String sql = "INSERT INTO UserHistory (activity_type, content) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, activityType);
            ps.setString(2, content);
            ps.executeUpdate();
            conn.commit(); // Ensure it saves immediately
        }
    }

	/**
	 * Returns recent UserHistory rows, optionally filtered to a specific activity type.
	 * Rows are ordered newest-first using both the timestamp and the auto-increment id
	 * as a tiebreaker, since SQLite datetime resolution is only one second.
	 *
	 * @param limit        maximum number of rows to return
	 * @param activityType if non-null and non-blank, only rows with this type are returned
	 * @return list of history entries, newest first
	 */
    public List<UserHistoryEntry> getUserHistory(int limit, String activityType) throws SQLException {
        List<UserHistoryEntry> rows = new ArrayList<>();

		// The WHERE clause is conditionally included depending on whether a filter type was provided.
        String sql = "SELECT id, activity_type, content, created_at FROM UserHistory " +
                (activityType == null || activityType.isBlank() ? "" : "WHERE activity_type = ? ") +
                "ORDER BY datetime(created_at) DESC, id DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
			// Bind parameters positionally — the activity type param only exists when filtering.
            int paramIndex = 1;
            if (activityType != null && !activityType.isBlank()) {
                ps.setString(paramIndex++, activityType);
            }
            ps.setInt(paramIndex, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserHistoryEntry e = new UserHistoryEntry();
                    e.id = rs.getInt("id");
                    e.activityType = rs.getString("activity_type");
                    e.content = rs.getString("content");
                    e.createdAt = rs.getString("created_at");
                    rows.add(e);
                }
            }
        }
        return rows;
    }

	/**
	 * Returns the distinct set of activity type labels present in UserHistory,
	 * sorted alphabetically. Used to populate filter dropdowns in the Logs tab.
	 */
    public List<String> getUserHistoryActivityTypes() throws SQLException {
        List<String> types = new ArrayList<>();
        String sql = "SELECT DISTINCT activity_type FROM UserHistory WHERE activity_type IS NOT NULL ORDER BY activity_type ASC";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                types.add(rs.getString("activity_type"));
            }
        }
        return types;
    }

	/**
	 * Looks up a single word in WordCorpus and returns all of its count fields,
	 * including the pre-computed effective_total_count (base + boost).
	 *
	 * @param word the word to look up
	 * @return a populated Word object, or null if the word is not in the corpus
	 */
    public Word getWordByText(String word) throws SQLException {
        String sql = "SELECT word, total_count, start_count, end_count, boost_total_count, boost_start_count, " +
                "(total_count + boost_total_count) AS effective_total_count " +
                "FROM WordCorpus WHERE word = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Word w = new Word();
                w.word = rs.getString("word");
                w.totalCount = rs.getInt("total_count");
                w.startCount = rs.getInt("start_count");
                w.endCount = rs.getInt("end_count");
                w.boostTotalCount = rs.getInt("boost_total_count");
                w.boostStartCount = rs.getInt("boost_start_count");
                w.effectiveTotalCount = rs.getInt("effective_total_count");
                return w;
            }
        }
    }

	/**
	 * Updates a word's corpus counts while keeping its effective total frequency unchanged.
	 * This is used by the editor UI when a user manually adjusts raw counts — we don't want
	 * the word to suddenly appear more or less prominent in generation, so we compensate by
	 * adjusting boost_total_count so that (total_count + boost_total_count) stays the same.
	 *
	 * start_count, end_count, and boost_start_count are clamped to valid ranges so they
	 * can never exceed total_count or the adjusted boost total respectively.
	 *
	 * @param word           the word to update
	 * @param totalCount     the new raw corpus count (before boost)
	 * @param startCount     the new sentence-start count
	 * @param endCount       the new sentence-end count
	 * @param boostStartCount the new user-boost sentence-start count
	 */
    public void updateWordCountsPreserveEffective(String word, int totalCount, int startCount, int endCount, int boostStartCount)
            throws SQLException {
        Word existing = getWordByText(word);
        if (existing == null) {
            throw new SQLException("Word not found: " + word);
        }

		// Compute the boost needed so effective total stays where it was before the edit.
        int effectiveTotal = existing.effectiveTotalCount;
        int adjustedBoostTotal = Math.max(0, effectiveTotal - Math.max(0, totalCount));

		// Clamp start/end counts so they cannot exceed the (possibly reduced) total_count.
        int clampedStart = Math.max(0, Math.min(startCount, Math.max(0, totalCount)));
        int clampedEnd = Math.max(0, Math.min(endCount, Math.max(0, totalCount)));

		// Clamp boost_start_count so it can't exceed the adjusted boost total.
        int clampedBoostStart = Math.max(0, Math.min(boostStartCount, adjustedBoostTotal));

        String sql = "UPDATE WordCorpus SET total_count = ?, start_count = ?, end_count = ?, " +
                "boost_total_count = ?, boost_start_count = ? WHERE word = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, totalCount));
            ps.setInt(2, clampedStart);
            ps.setInt(3, clampedEnd);
            ps.setInt(4, adjustedBoostTotal);
            ps.setInt(5, clampedBoostStart);
            ps.setString(6, word);
            ps.executeUpdate();
            conn.commit();
        }
    }

	/**
	 * Records a user-confirmed sentence into the corpus as dynamic learning data.
	 * Rather than just incrementing raw counts by 1, this method computes a context-aware
	 * boost delta for each word, bigram, and trigram so the confirmed phrase rises
	 * meaningfully in generation rankings relative to the surrounding statistical neighborhood.
	 * The strength of the boost is controlled by {@code strengthMultiplier} (mapped from the
	 * Gentle / Balanced / Strong UI setting).
	 *
	 * Runs on a background thread to avoid blocking the UI during DB writes.
	 * Uses a synchronized block on {@code conn} since SQLite allows only one writer at a time.
	 *
	 * @param words              the words of the confirmed sentence, in order
	 * @param strengthMultiplier scaling factor applied to computed boost targets (e.g. 0.5, 1.0, 2.0)
	 */
    public void insertLearnedData(String[] words, double strengthMultiplier) {
        // Run on a background thread so the UI doesn't hang during DB writing
        new Thread(() -> {
			// Prepared SQL for upsert-incrementing words, bigrams, and trigrams.
			// Boost columns are incremented separately from base counts so corpus truth is preserved.
            String wordSql = "INSERT INTO WordCorpus " +
                    "(word, total_count, start_count, end_count, boost_total_count, boost_start_count) " +
                    "VALUES (?, ?, ?, 0, ?, ?) " +
                    "ON CONFLICT(word) DO UPDATE SET " +
                    "total_count = WordCorpus.total_count + excluded.total_count, " +
                    "start_count = WordCorpus.start_count + excluded.start_count, " +
                    "boost_total_count = WordCorpus.boost_total_count + excluded.boost_total_count, " +
                    "boost_start_count = WordCorpus.boost_start_count + excluded.boost_start_count";
            String bigramSql = "INSERT INTO Bigrams (word1, word2, frequency, boost_frequency) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(word1, word2) DO UPDATE SET " +
                    "frequency = Bigrams.frequency + excluded.frequency, " +
                    "boost_frequency = Bigrams.boost_frequency + excluded.boost_frequency";
            String trigramSql = "INSERT INTO Trigrams (word1, word2, word3, frequency, boost_frequency) VALUES (?, ?, ?, ?, ?) " +
                    "ON CONFLICT(word1, word2, word3) DO UPDATE SET " +
                    "frequency = Trigrams.frequency + excluded.frequency, " +
                    "boost_frequency = Trigrams.boost_frequency + excluded.boost_frequency";

            synchronized (conn) {
                try (PreparedStatement psWord = conn.prepareStatement(wordSql);
                     PreparedStatement psBigram = conn.prepareStatement(bigramSql);
                     PreparedStatement psTrigram = conn.prepareStatement(trigramSql)) {

					// --- Sentence-starter word ---
					// Boost delta is how far below the neighborhood target the starter currently sits.
					// We always apply at least +1 so learning is never a no-op.
                    int starterTarget = computeTargetFromContext(loadStarterContextFrequencies(), 3, strengthMultiplier);
                    int starterCurrent = getStarterEffectiveCount(words[0]);
                    int starterBoostDelta = Math.max(1, starterTarget - starterCurrent);

                    // 1. Process starter word: keep absolute counts truthful (+1), boost separately.
                    psWord.setString(1, words[0]);
                    psWord.setInt(2, 1);
                    psWord.setInt(3, 1);
                    psWord.setInt(4, starterBoostDelta);
                    psWord.setInt(5, starterBoostDelta);
                    psWord.executeUpdate();

                    // 2. Process remaining words, bigrams, and trigrams
                    for (int i = 0; i < words.length - 1; i++) {
                        String w1 = words[i];
                        String w2 = words[i + 1];

						// Compute how much this bigram needs to be boosted to reach the local target.
                        int bigramTarget = computeTargetFromContext(loadBigramContextFrequencies(w1), 3, strengthMultiplier);
                        int bigramCurrent = getBigramEffectiveCount(w1, w2);
                        int bigramBoostDelta = Math.max(1, bigramTarget - bigramCurrent);

                        // Standard word (not a starter): absolute +1, optional small boost for retrieval visibility.
                        psWord.setString(1, w2);
                        psWord.setInt(2, 1);
                        psWord.setInt(3, 0);
                        psWord.setInt(4, Math.max(1, bigramBoostDelta / 2)); // half the bigram boost keeps word visible without over-weighting
                        psWord.setInt(5, 0);
                        psWord.executeUpdate();

                        // Bigram absolute +1, dynamic context boost
                        psBigram.setString(1, w1);
                        psBigram.setString(2, w2);
                        psBigram.setInt(3, 1);
                        psBigram.setInt(4, bigramBoostDelta);
                        psBigram.executeUpdate();

                        // Trigram absolute +1, dynamic context boost (only when a third word exists)
                        if (i < words.length - 2) {
                            String w3 = words[i + 2];
                            int trigramTarget = computeTargetFromContext(loadTrigramContextFrequencies(w1, w2), 2, strengthMultiplier);
                            int trigramCurrent = getTrigramEffectiveCount(w1, w2, w3);
                            int trigramBoostDelta = Math.max(1, trigramTarget - trigramCurrent);

                            psTrigram.setString(1, w1);
                            psTrigram.setString(2, w2);
                            psTrigram.setString(3, w3);
                            psTrigram.setInt(4, 1);
                            psTrigram.setInt(5, trigramBoostDelta);
                            psTrigram.executeUpdate();
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                    }
                    System.out.println("Error saving learned data: " + e.getMessage());
                }
            }
        }).start();
    }

	/**
	 * Core dynamic proportional weighting for user learning: maps local corpus statistics to a single
	 * effective-frequency target used when computing boost deltas (starters, bigrams, trigrams).
	 * <p>
	 * With context data, the target reflects how strong follower edges typically are in that neighborhood
	 * (median vs. 75th percentile), capped below the dominant edge so one user's phrase cannot eclipse the
	 * entire Zipf tail at once. {@code strengthMultiplier} scales that target for Gentle / Balanced / Strong UI modes.
	 * With no context (cold corpus), {@code coldStartTarget} supplies a small baseline so learning works from day one.
	 *
	 * @param frequencies       the effective frequency values of competing edges in this neighborhood
	 * @param coldStartTarget   fallback target when the corpus has no context data yet
	 * @param strengthMultiplier UI learning strength scale (e.g. 0.5 = Gentle, 1.0 = Balanced, 2.0 = Strong)
	 * @return the computed boost target, always at least 1
	 */
    private int computeTargetFromContext(List<Integer> frequencies, int coldStartTarget, double strengthMultiplier) {
        if (frequencies == null || frequencies.isEmpty()) {
            return Math.max(1, (int) Math.round(coldStartTarget * strengthMultiplier));
        }

		// Sort to find percentile positions in the local frequency distribution.
        List<Integer> sorted = new ArrayList<>(frequencies);
        Collections.sort(sorted);
        int n = sorted.size();
        int median = sorted.get(n / 2);                              // 50th percentile
        int p75 = sorted.get((int) Math.floor(0.75 * (n - 1)));     // 75th percentile
        int top = sorted.get(n - 1);                                 // dominant edge

		// Baseline is the higher of median and p75 — this tilts toward the stronger half
		// of the distribution, making learning competitive without being extreme.
        int baseline = Math.max(median, p75);

		// Cap at 60% of the top edge so a single confirmed sentence can never dominate.
        int cap = Math.max(1, (int) Math.round(top * 0.6));
        int capped = Math.min(baseline, cap);
        return Math.max(1, (int) Math.round(capped * strengthMultiplier));
    }

	/**
	 * Loads the effective start-count frequencies for the top 200 sentence starters.
	 * Provides the statistical neighborhood used when computing a boost target for a new starter word.
	 */
    private List<Integer> loadStarterContextFrequencies() throws SQLException {
        return loadFrequencyList(
                "SELECT (start_count + boost_start_count) AS freq FROM WordCorpus " +
                        "WHERE (start_count + boost_start_count) > 0 " +
                        "ORDER BY freq DESC LIMIT 200");
    }

	/**
	 * Loads the effective frequencies of the top 200 bigrams that begin with {@code word1}.
	 * Provides the local bigram neighborhood for computing a boost target for a (word1, ?) pair.
	 */
    private List<Integer> loadBigramContextFrequencies(String word1) throws SQLException {
        return loadFrequencyList(
                "SELECT (frequency + boost_frequency) AS freq FROM Bigrams " +
                        "WHERE word1 = ? ORDER BY freq DESC LIMIT 200",
                word1);
    }

	/**
	 * Loads the effective frequencies of the top 200 trigrams that begin with {@code word1, word2}.
	 * Provides the local trigram neighborhood for computing a boost target for a (word1, word2, ?) triple.
	 */
    private List<Integer> loadTrigramContextFrequencies(String word1, String word2) throws SQLException {
        return loadFrequencyList(
                "SELECT (frequency + boost_frequency) AS freq FROM Trigrams " +
                        "WHERE word1 = ? AND word2 = ? ORDER BY freq DESC LIMIT 200",
                word1, word2);
    }

	/**
	 * Executes a frequency-query SQL statement with optional positional parameters and
	 * returns the results as a plain integer list. This is a shared utility used by all
	 * three context-frequency loaders (starters, bigrams, trigrams) to avoid duplication.
	 *
	 * @param sql    a SELECT that returns a single column named "freq"
	 * @param params zero or more string values to bind as positional parameters
	 * @return list of frequency integers in the order returned by the query
	 */
    private List<Integer> loadFrequencyList(String sql, String... params) throws SQLException {
        List<Integer> values = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getInt("freq"));
                }
            }
        }
        return values;
    }

	/** Returns the current effective sentence-start score (start_count + boost_start_count) for the given word, or 0 if not found. */
    private int getStarterEffectiveCount(String starter) throws SQLException {
        String sql = "SELECT (start_count + boost_start_count) AS score FROM WordCorpus WHERE word = ?";
        return getSingleScore(sql, starter);
    }

	/** Returns the current effective frequency (frequency + boost_frequency) for the given bigram, or 0 if not found. */
    private int getBigramEffectiveCount(String word1, String word2) throws SQLException {
        String sql = "SELECT (frequency + boost_frequency) AS score FROM Bigrams WHERE word1 = ? AND word2 = ?";
        return getSingleScore(sql, word1, word2);
    }

	/** Returns the current effective frequency (frequency + boost_frequency) for the given trigram, or 0 if not found. */
    private int getTrigramEffectiveCount(String word1, String word2, String word3) throws SQLException {
        String sql = "SELECT (frequency + boost_frequency) AS score FROM Trigrams WHERE word1 = ? AND word2 = ? AND word3 = ?";
        return getSingleScore(sql, word1, word2, word3);
    }

	/**
	 * Executes a query expected to return a single integer column named "score" and returns it.
	 * Returns 0 if no row is found, which is the correct default for any unseen word/bigram/trigram.
	 *
	 * @param sql    a SELECT that returns at most one row with a column named "score"
	 * @param params positional string parameters to bind
	 * @return the score value, or 0 if the row doesn't exist
	 */
    private int getSingleScore(String sql, String... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("score");
                }
            }
        }
        return 0;
    }

	// ─────────────────────────────────────────
	// Reporter
	// ─────────────────────────────────────────

	/**
	 * Executes an arbitrary word-query SQL statement and maps each row to a Word object.
	 * All Reporter-facing query methods share this helper to avoid duplicating ResultSet
	 * mapping code. The SQL must select the standard WordCorpus columns including the
	 * computed effective_total_count alias.
	 *
	 * @param sql a complete SELECT statement returning the standard WordCorpus columns
	 * @return list of Word objects in the order returned by the query
	 */
	private List<Word> queryWords(String sql) throws SQLException {
		List<Word> words = new ArrayList<>();
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				Word w = new Word();
				w.word = rs.getString("word");
				w.totalCount = rs.getInt("total_count");
				w.startCount = rs.getInt("start_count");
				w.endCount = rs.getInt("end_count");
				w.boostTotalCount = rs.getInt("boost_total_count");
				w.boostStartCount = rs.getInt("boost_start_count");
				w.effectiveTotalCount = rs.getInt("effective_total_count");
				words.add(w);
			}
		}
		return words;
	}

	/**
	 * Returns a SQL WHERE clause fragment that filters words by data origin.
	 * USER_ONLY: only words the user has reinforced (any boost > 0).
	 * CORPUS_ONLY: only words from the original imported corpus (no boost).
	 * ALL: no filter applied.
	 */
	private String scopeFilter(Reporter.ScopeType scope) {
		return switch (scope) {
			case USER_ONLY -> " WHERE (boost_total_count > 0 OR boost_start_count > 0)";
			case CORPUS_ONLY -> " WHERE (boost_total_count = 0 AND boost_start_count = 0)";
			case ALL -> "";
		};
	}

	/** Returns the standard SELECT preamble for WordCorpus queries, including the effective_total_count alias. */
	private String baseWordSelect() {
		return "SELECT word, total_count, start_count, end_count, boost_total_count, boost_start_count, " +
				"(total_count + boost_total_count) AS effective_total_count FROM WordCorpus";
	}

	/** Returns all words matching the given scope, sorted alphabetically. */
	public List<Word> getAllWordsSortedAlpha(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY word ASC");
	}

	/** Returns all words matching the given scope, sorted by raw corpus frequency descending. */
	public List<Word> getAllWordsSortedByFrequency(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY total_count DESC");
	}

	/** Returns all words matching the given scope, sorted by user boost total descending, then raw frequency. */
	public List<Word> getAllWordsSortedByBoostTotal(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY boost_total_count DESC, total_count DESC");
	}

	/** Returns all words matching the given scope, sorted by user boost start-count descending, then raw start-count. */
	public List<Word> getAllWordsSortedByBoostStart(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY boost_start_count DESC, start_count DESC");
	}

	/** Returns all words matching the given scope, sorted by effective total (base + boost) descending. */
	public List<Word> getAllWordsSortedByEffectiveTotal(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY effective_total_count DESC");
	}

	/**
	 * Returns the most recently imported files, up to the given limit.
	 * Used by the Reporter to display a quick summary of recent import activity.
	 *
	 * @param amount maximum number of files to return
	 * @return list of ImportedFile records ordered by import date descending
	 */
	public List<ImportedFile> getImportedFiles(int amount) throws SQLException {
		List<ImportedFile> files = new ArrayList<>();
		String sql = "SELECT file_name, word_count, import_date FROM ImportedFiles ORDER BY import_date DESC LIMIT "
				+ amount;
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				ImportedFile f = new ImportedFile();
				f.fileName = rs.getString("file_name");
				f.wordCount = rs.getInt("word_count");
				f.importDate = rs.getString("import_date");
				files.add(f);
			}
		}
		return files;
	}

	/** Returns all imported file records with no limit. Used for full export and reporting. */
	public List<ImportedFile> getImportedFiles() throws SQLException {
		List<ImportedFile> files = new ArrayList<>();
		String sql = "SELECT file_name, word_count, import_date FROM ImportedFiles";
		try (Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				ImportedFile f = new ImportedFile();
				f.fileName = rs.getString("file_name");
				f.wordCount = rs.getInt("word_count");
				f.importDate = rs.getString("import_date");
				files.add(f);
			}
		}
		return files;
	}

	/** Returns the sum of total_count across all words in the corpus — i.e. the total number of word tokens imported. */
	public long getTotalWords() throws SQLException {
		String sql = "SELECT SUM(total_count) FROM WordCorpus";
		long ret = 0;
		try (Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			if (rs.next())
				ret = rs.getLong(1);
		}
		return ret;
	}

	/** Returns the number of distinct word types in the corpus. */
	public long getUniqueWords() throws SQLException {
		String sql = "SELECT COUNT(*) FROM WordCorpus";
		long ret = 0;
		try (Statement st = conn.createStatement();
				ResultSet rs = st.executeQuery(sql)) {
			if (rs.next())
				ret = rs.getLong(1);
		}
		return ret;
	}
}
