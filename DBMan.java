import java.io.File;
import java.io.FileNotFoundException;
import java.sql.*;
import java.util.*;

public class DBMan {
	private static final String DB_URL = "jdbc:sqlite:BuilderWords.db";
//	private static final String USER = "sentencebuilder";
//	private static final String PASS = "Yo457S<DWL.D";

	private Connection conn;

	public void connect() throws SQLException {
//		conn = DriverManager.getConnection(DB_URL, USER, PASS);
		conn = DriverManager.getConnection(DB_URL);
		conn.setAutoCommit(false);

		migrateIfNecessary();
	}

	private boolean tableExists(String tableName) throws SQLException {
		String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?";
		
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, tableName);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next(); // return true if we found a row (i.e. if the table exists)
			}
		}
	}

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

	// applies database migrations if needed
	private void migrateIfNecessary() throws SQLException {
		List<String> appliedMigrations = new ArrayList<>();
		if (tableExists("Migrations")){
			String sql = "SELECT filename FROM Migrations";
			try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)){
				while (rs.next()){
					appliedMigrations.add(rs.getString("filename"));
				}
			}
		}
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

    public List<UserHistoryEntry> getUserHistory(int limit, String activityType) throws SQLException {
        List<UserHistoryEntry> rows = new ArrayList<>();
        String sql = "SELECT id, activity_type, content, created_at FROM UserHistory " +
                (activityType == null || activityType.isBlank() ? "" : "WHERE activity_type = ? ") +
                "ORDER BY datetime(created_at) DESC, id DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
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

    public void updateWordCountsPreserveEffective(String word, int totalCount, int startCount, int endCount, int boostStartCount)
            throws SQLException {
        Word existing = getWordByText(word);
        if (existing == null) {
            throw new SQLException("Word not found: " + word);
        }

        int effectiveTotal = existing.effectiveTotalCount;
        int adjustedBoostTotal = Math.max(0, effectiveTotal - Math.max(0, totalCount));
        int clampedStart = Math.max(0, Math.min(startCount, Math.max(0, totalCount)));
        int clampedEnd = Math.max(0, Math.min(endCount, Math.max(0, totalCount)));
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

    public void insertLearnedData(String[] words, double strengthMultiplier) {
        // Run on a background thread so the UI doesn't hang during DB writing
        new Thread(() -> {
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

                        int bigramTarget = computeTargetFromContext(loadBigramContextFrequencies(w1), 3, strengthMultiplier);
                        int bigramCurrent = getBigramEffectiveCount(w1, w2);
                        int bigramBoostDelta = Math.max(1, bigramTarget - bigramCurrent);

                        // Standard word (not a starter): absolute +1, optional small boost for retrieval visibility.
                        psWord.setString(1, w2);
                        psWord.setInt(2, 1);
                        psWord.setInt(3, 0);
                        psWord.setInt(4, Math.max(1, bigramBoostDelta / 2));
                        psWord.setInt(5, 0);
                        psWord.executeUpdate();

                        // Bigram absolute +1, dynamic context boost
                        psBigram.setString(1, w1);
                        psBigram.setString(2, w2);
                        psBigram.setInt(3, 1);
                        psBigram.setInt(4, bigramBoostDelta);
                        psBigram.executeUpdate();

                        // Trigram absolute +1, dynamic context boost
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
     */
    private int computeTargetFromContext(List<Integer> frequencies, int coldStartTarget, double strengthMultiplier) {
        if (frequencies == null || frequencies.isEmpty()) {
            return Math.max(1, (int) Math.round(coldStartTarget * strengthMultiplier));
        }

        List<Integer> sorted = new ArrayList<>(frequencies);
        Collections.sort(sorted);
        int n = sorted.size();
        int median = sorted.get(n / 2);
        int p75 = sorted.get((int) Math.floor(0.75 * (n - 1)));
        int top = sorted.get(n - 1);

        int baseline = Math.max(median, p75);
        int cap = Math.max(1, (int) Math.round(top * 0.6));
        int capped = Math.min(baseline, cap);
        return Math.max(1, (int) Math.round(capped * strengthMultiplier));
    }

    private List<Integer> loadStarterContextFrequencies() throws SQLException {
        return loadFrequencyList(
                "SELECT (start_count + boost_start_count) AS freq FROM WordCorpus " +
                        "WHERE (start_count + boost_start_count) > 0 " +
                        "ORDER BY freq DESC LIMIT 200");
    }

    private List<Integer> loadBigramContextFrequencies(String word1) throws SQLException {
        return loadFrequencyList(
                "SELECT (frequency + boost_frequency) AS freq FROM Bigrams " +
                        "WHERE word1 = ? ORDER BY freq DESC LIMIT 200",
                word1);
    }

    private List<Integer> loadTrigramContextFrequencies(String word1, String word2) throws SQLException {
        return loadFrequencyList(
                "SELECT (frequency + boost_frequency) AS freq FROM Trigrams " +
                        "WHERE word1 = ? AND word2 = ? ORDER BY freq DESC LIMIT 200",
                word1, word2);
    }

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

    private int getStarterEffectiveCount(String starter) throws SQLException {
        String sql = "SELECT (start_count + boost_start_count) AS score FROM WordCorpus WHERE word = ?";
        return getSingleScore(sql, starter);
    }

    private int getBigramEffectiveCount(String word1, String word2) throws SQLException {
        String sql = "SELECT (frequency + boost_frequency) AS score FROM Bigrams WHERE word1 = ? AND word2 = ?";
        return getSingleScore(sql, word1, word2);
    }

    private int getTrigramEffectiveCount(String word1, String word2, String word3) throws SQLException {
        String sql = "SELECT (frequency + boost_frequency) AS score FROM Trigrams WHERE word1 = ? AND word2 = ? AND word3 = ?";
        return getSingleScore(sql, word1, word2, word3);
    }

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

	private String scopeFilter(Reporter.ScopeType scope) {
		return switch (scope) {
			case USER_ONLY -> " WHERE (boost_total_count > 0 OR boost_start_count > 0)";
			case CORPUS_ONLY -> " WHERE (boost_total_count = 0 AND boost_start_count = 0)";
			case ALL -> "";
		};
	}

	private String baseWordSelect() {
		return "SELECT word, total_count, start_count, end_count, boost_total_count, boost_start_count, " +
				"(total_count + boost_total_count) AS effective_total_count FROM WordCorpus";
	}

	public List<Word> getAllWordsSortedAlpha(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY word ASC");
	}

	public List<Word> getAllWordsSortedByFrequency(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY total_count DESC");
	}

	public List<Word> getAllWordsSortedByBoostTotal(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY boost_total_count DESC, total_count DESC");
	}

	public List<Word> getAllWordsSortedByBoostStart(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY boost_start_count DESC, start_count DESC");
	}

	public List<Word> getAllWordsSortedByEffectiveTotal(Reporter.ScopeType scope) throws SQLException {
		return queryWords(baseWordSelect() + scopeFilter(scope) + " ORDER BY effective_total_count DESC");
	}

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
