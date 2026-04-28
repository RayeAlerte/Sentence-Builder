
import java.util.*;
import java.sql.*;

public class Reporter {
	private DBMan dbMan;

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

	private SortType type;

	public Reporter(DBMan db) {
		dbMan = db;
		type = SortType.FREQ;
	}

	public void setSortType(SortType s) {
		type = s;
	}

	public SortType getSortType() {
		return type;
	}

	public List<Word> getSortedWords() {
		List<Word> words = null;
		try {
			words = switch (type) {
				case ALPHA -> dbMan.getAllWordsSortedAlpha();
				case FREQ -> dbMan.getAllWordsSortedByFrequency();
			};

		} catch (SQLException e) {
			System.out.println("Error fetching report: " + e.getMessage());
		}
		return words;
	}
}
