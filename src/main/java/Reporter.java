/*
    Written by Owen Giles for CS4485.0W1, Sentence Builder, starting April 28th, 2026
        NetID: oag220003
*/

import java.util.*;
import java.sql.*;

public class Reporter {
	private DBMan dbMan;

	/**
	 * Determines the order in which words are presented in reports.
	 * fromInput() maps UI dropdown indices to enum values.
	 * displayName() provides the human-readable label shown in that dropdown.
	 */
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
        public String displayName() {
            return switch (this) {
                case ALPHA -> "Alphabetical";
                case FREQ -> "Frequency";
                case BOOST_TOTAL -> "Boost Total";
                case BOOST_START -> "Boost Starts";
                case EFFECTIVE_TOTAL -> "Effective Total";
            };
        }
	}

	/**
	 * Filters which words are included in a report by their data origin.
	 * ALL: every word in the corpus.
	 * USER_ONLY: only words the user has reinforced via dynamic learning (any boost > 0).
	 * CORPUS_ONLY: only words from the original imported corpus (no user boost applied).
	 */
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

	private SortType type;   // current sort order applied to report output
	private ScopeType scope; // current data-origin filter applied to report output

	/**
	 * Creates a Reporter backed by the given database, with default settings
	 * of alphabetical sort and no scope filter (all words).
	 */
	public Reporter(DBMan db) {
		dbMan = db;
		type = SortType.ALPHA;
		scope = ScopeType.ALL;
	}

	public void setSortType(SortType s) { type = s; }
	public SortType getSortType() { return type; }

	public void setScopeType(ScopeType s) { scope = s; }
	public ScopeType getScopeType() { return scope; }

	/**
	 * Returns the full word list from the database, ordered and filtered by the
	 * current sort type and scope. Returns null if a database error occurs
	 * (the error is logged to stdout).
	 */
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
