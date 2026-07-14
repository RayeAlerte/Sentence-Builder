# Sentence Builder Presentation Script

## Timing Plan (6 Minutes Total)

Total presentation time: about 6 minutes (6 speakers, about 55-65 seconds each)

- Speaker 1: What it is and why, about 1 minute
- Speaker 2: What users can do (CLI + GUI), about 1 minute
- Speaker 3: Parsing pipeline + database schema, about 1 minute
- Speaker 4: Autocomplete + generation logic, about 1 minute
- Speaker 5: Tech stack + testing and reliability, about 1 minute
- Speaker 6: Challenges, next steps, wrap-up, about 1 minute

## Speaker 1: Introduction and Problem

Hi everyone, our project is Sentence Builder. It is a local text analysis and sentence suggestion tool: you import text files, it learns common word patterns from that text, and then it can suggest next words or generate a short sentence that matches the style of what you imported.

We wanted to answer a simple question: how far can you get with language prediction without a heavy ML model or an external AI API? Our approach is n-grams. A bigram is a two-word sequence, and a trigram is a three-word sequence. By counting these sequences, we can estimate what word is likely to come next based on context.

At a high level: import text, parse it, store statistics, then use those stats for reporting, autocomplete, and generation. Speaker 2 will cover what the app looks like for a user.

## Speaker 2: Project Overview and User Flow

Sentence Builder runs two ways: a CLI and a JavaFX GUI.

In the CLI, you pick modes: reporting, autocomplete, generate sentence, options, or exit. Reporting lets you view the word list sorted alphabetically or by frequency. Autocomplete lets you type and get next-word suggestions. Generation takes a starting word and builds a short sentence from there.

In the GUI, the sidebar includes Dashboard and Import Text. The Dashboard shows key stats like total words, unique words, and number of imported files, plus a table of recent imports. Import Text lets you browse for `.txt` files, queue multiple files, start parsing, cancel parsing, and see import history.

The point is the same across both: your output reflects patterns from the text you imported, not a random word list. Speaker 3 will explain the parser and database.

## Speaker 3: Data Parsing and Database Design

The core pipeline is: parse text into tokens, then store counts and relationships in a MySQL/MariaDB database using JDBC.

The parser reads each file line-by-line, normalizes case, and uses a regex to detect either a word token or sentence-ending punctuation. As it walks through tokens, it records:

- Single word counts, plus whether a word starts or ends a sentence
- Bigrams: word1 -> word2
- Trigrams: word1 + word2 -> word3

Those map directly to tables: `WordCorpus` (totals, starts, ends), `Bigrams` and `Trigrams` (with frequencies), plus `ImportedFiles` so we can skip re-importing the same file. A key performance strategy is batching inserts and committing after work is done, instead of doing one SQL operation per token.

Speaker 4 will explain how we use these n-grams for autocomplete and generation.

## Speaker 4: Generation and Autocomplete Strategy

For autocomplete and generation we use a simple priority strategy: use the most context you have first.

Autocomplete:

1. If there are 2+ words, try trigram suggestions based on the last two words.
2. If that misses, fall back to bigram suggestions based on the last word.
3. If that also misses, fall back to common sentence starters.

Generation is similar: starting from a seed word, it repeatedly chooses the next word using trigram context if possible, then bigram, then starters as a last resort, until it hits a length limit or a dead end.

We also expose a "randomness pool" option. Pool size 1 is the most predictable behavior, and larger pool sizes pick randomly from the top N results for more variety.

Speaker 5 will cover the stack and how we kept it stable and testable.

## Speaker 5: User Interface, Testing, and Reliability

Technology-wise, the project is Java for the core logic, MySQL/MariaDB for storage, JDBC for database access, and JavaFX for the desktop GUI. There is also a CLI entry point, so the app can be used without the GUI.

For reliability and UX, the GUI runs long tasks like importing and parsing on background threads so the interface stays responsive, and it supports cancellation via a shared cancel flag.

For correctness, we used JUnit tests to cover parsing edge cases, generation behavior, autocomplete fallbacks, cancellation, and a stress test for larger inputs. We also have a `test.sh` script that compiles the code, runs the test suites, and generates a Markdown test report, which makes it quick to validate changes.

Speaker 6 will wrap up with challenges, improvements, and the close.

## Speaker 6: Challenges, Improvements, and Conclusion

The hardest part is that real text is messy: punctuation, hyphenation, apostrophes, abbreviations, and Unicode all create parsing edge cases. We handled several of these, and we used tests to catch cases where behavior is tricky.

Performance was the other big constraint. We addressed it by batching database writes and by pre-loading common n-grams into memory so suggestions are fast.

Next steps are straightforward: finish the remaining GUI screens (Generate, Auto-complete, Word Browser, Reports), improve parsing around tricky tokens, and start saving generated sentences into `UserHistory` so users can review previous results.

To wrap up: Sentence Builder is a Java + MySQL desktop and CLI tool that learns n-gram patterns from imported text and uses them for reporting, autocomplete, and sentence generation. Thanks, and we are happy to take questions.

## Quick Demo Cues (Optional)

If you are doing a live demo during the presentation, use this order:

1. Show the README or explain setup briefly: JavaFX, MySQL/MariaDB, and the MySQL connector.
2. Open the GUI with `./run.sh -gui`.
3. Show the dashboard stats.
4. Go to Import Text and explain file queueing, parsing, import history, and cancellation.
5. Switch to the CLI or describe the CLI modes: reporting, autocomplete, generation, and options.
6. Mention that automated tests can be run with `./test.sh`.

## One-Sentence Backup (If You Are Over Time)

- Speaker 1: "Sentence Builder learns next-word patterns from imported text using n-grams."
- Speaker 2: "It works in both a CLI and a JavaFX GUI."
- Speaker 3: "A parser turns text into word, bigram, and trigram counts stored in MySQL."
- Speaker 4: "Autocomplete and generation use trigram-first, then bigram, then starter fallback."
- Speaker 5: "We used Java, JDBC, JavaFX, and JUnit tests for stability."
- Speaker 6: "Next steps are finishing GUI screens and improving edge-case parsing."
