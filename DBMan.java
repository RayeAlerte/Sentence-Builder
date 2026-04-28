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
							stmt.execute(line);
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
		String sql = "SELECT word FROM WordCorpus WHERE start_count > 0 " +
				"ORDER BY start_count DESC LIMIT ?";
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
		String sql = "SELECT word1, word2 FROM Bigrams ORDER BY frequency DESC LIMIT ?";
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
		String sql = "SELECT word1, word2, word3 FROM Trigrams ORDER BY frequency DESC LIMIT ?";
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
				words.add(w);
			}
		}
		return words;
	}

	public List<Word> getAllWordsSortedAlpha() throws SQLException {
		return queryWords("SELECT word, total_count, start_count, end_count FROM WordCorpus ORDER BY word ASC");
	}

	public List<Word> getAllWordsSortedByFrequency() throws SQLException {
		return queryWords(
				"SELECT word, total_count, start_count, end_count FROM WordCorpus ORDER BY total_count DESC");
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
