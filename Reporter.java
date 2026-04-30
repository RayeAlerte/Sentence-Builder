
import java.util.*;
import java.sql.*;

public class Reporter {
	private DBMan dbMan;

	enum SortType {
		ALPHA, FREQ, BOOST_TOTAL, BOOST_START, EFFECTIVE_TOTAL;

		public static SortType fromInput(int input) {
			return switch (input) {
				case 0 -> ALPHA;
				case 1 -> FREQ;
				case 2 -> BOOST_TOTAL;
				case 3 -> BOOST_START;
				case 4 -> EFFECTIVE_TOTAL;
				default -> throw new IllegalArgumentException("Invalid sort: " + input);
			};
		}

        // For the drop down menu thingy
        public String displayName()
        {
            return switch (this)
            {
                case ALPHA -> "Alphabetical";
                case FREQ -> "Frequency";
                case BOOST_TOTAL -> "Boost Total";
                case BOOST_START -> "Boost Starts";
                case EFFECTIVE_TOTAL -> "Effective Total";
            };
        }
                
	}

	enum ScopeType {
		ALL, USER_ONLY, CORPUS_ONLY;

		public String displayName() {
			return switch (this) {
				case ALL -> "All words";
				case USER_ONLY -> "User words only";
				case CORPUS_ONLY -> "Corpus-only words";
			};
		}
	}

	private SortType type;
	private ScopeType scope;

	public Reporter(DBMan db) {
		dbMan = db;
		type = SortType.ALPHA;
		scope = ScopeType.ALL;
	}

	public void setSortType(SortType s) {
		type = s;
	}

	public SortType getSortType() {
		return type;
	}

	public void setScopeType(ScopeType s) {
		scope = s;
	}

	public ScopeType getScopeType() {
		return scope;
	}

	public List<Word> getSortedWords() {
		List<Word> words = null;
		try {
			words = switch (type) {
				case ALPHA -> dbMan.getAllWordsSortedAlpha(scope);
				case FREQ -> dbMan.getAllWordsSortedByFrequency(scope);
				case BOOST_TOTAL -> dbMan.getAllWordsSortedByBoostTotal(scope);
				case BOOST_START -> dbMan.getAllWordsSortedByBoostStart(scope);
				case EFFECTIVE_TOTAL -> dbMan.getAllWordsSortedByEffectiveTotal(scope);
			};

		} catch (SQLException e) {
			System.out.println("Error fetching report: " + e.getMessage());
		}
		return words;
	}
}
