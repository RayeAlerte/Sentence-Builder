# Sentence Builder
Developers: Victoria Raye Alerte, Owen Giles, Jonathan Rodriguez, Elliot Algase, Juan Cardoso, Taufeeq Ali

Sentence Builder is a JavaFX application that parses large text corpora into an n-gram database and provides:

- sentence generation from a starter word,
- next-word autocomplete suggestions,
- adaptive user learning with configurable strength,
- word analytics/reporting (including boost/effective metrics),
- event/history logging with a dedicated Logs view.

## Current Architecture

- **Language/UI:** Java + JavaFX
- **Database:** SQLite (`BuilderWords.db`)
- **Core tables:** `WordCorpus`, `Bigrams`, `Trigrams`, `ImportedFiles`, `UserHistory`
- **Migrations:** SQL files in `migrations/` applied on startup
- **Tokenizer:** shared `Tokenizer.java` used by parser + live user input paths

## Key Features

### 1) Import and Parse

- Imports `.txt` files from UI.
- Deduplicates by canonical file path (with legacy basename compatibility).
- Tracks import metadata (`file_name`, `word_count`, `import_date`).
- Uses batching for performance and cancel-safe parsing flow.

### 2) Generation

- Trigram -> bigram -> starter fallback chain.
- Adjustable randomness pool.
- Adjustable generation length.
- Optional "Remember New Input" with learning strength:
  - Gentle / Balanced / Strong

### 3) Autocomplete

- Suggestions trigger on **space or comma**.
- Sentence context resets at terminal punctuation.
- Unknown user phrases can be remembered and learned.

### 4) Learning Model

- Base corpus counts are preserved.
- User adaptation uses separate boost columns:
  - `boost_total_count`, `boost_start_count`, `boost_frequency`
- Ranking uses effective score (`base + boost`).
- Reporting can still show absolute base counts.

### 5) Reporting

- Sort by: alphabetical, frequency, boost totals, boost starts, effective totals.
- Scope filter: all words, user words only, corpus-only words.
- CSV export preserves the exact current table order/filter/sort.

### 6) Logs and History

- Generate history loads from persistent `UserHistory`.
- Logs tab supports filtering by event type and quick refresh.
- Key app workflows are logged via standardized event IDs.

### 7) Edit Panel

- Look up a word and edit frequency fields:
  - total/start/end/boost starts
- Preserves effective total automatically by adjusting boost total.

## Tokenization and Normalization

The canonical tokenizer spec is documented in:

- `docs/normalization-spec.md`

This defines behavior for contractions, smart punctuation, unicode normalization, acronyms, URL/email suppression, and alphanumeric tokens.

## Environment Setup

Create `.env` in project root:

```bash
FX=path to JavaFX /lib
MYSQL=path to mysql-connector-j-version.jar
MYSQLITE= path to sqlite-jdbc-version.jar
```

Notes:
- `MYSQL` is optional for legacy compatibility.
- Current runtime and CI use SQLite.

## Run

- GUI: `./run.sh -gui`
- CLI: `./run.sh`

## Tests

Run full test suite and generate markdown report:

```bash
./test.sh
```

Artifacts:

- `test-results/latest.txt`
- `test-results/latest_clean.txt`
- `test-results/report.md`

## CI

GitHub Actions workflow:

- `.github/workflows/ci.yml`

CI compiles and runs the test suite with JavaFX + SQLite classpath setup.

## Known Limitations / Next Steps

- Parser pre-cancellation behavior is currently tracked as a known issue in tests.
- Numeric/technical token treatment can be further specialized per domain.
- Additional generation algorithms can be added for requirement expansion.
