#!/bin/bash
# 1. Load environment variables if .env exists
if [ -f .env ]; then
    source .env
fi

# 2. Paths
JUNIT="lib/junit-platform-console-standalone-1.11.0.jar"
CP_ENTRIES=".:tests:$JUNIT"
if [ -n "$MYSQL" ]; then
    CP_ENTRIES="$CP_ENTRIES:$MYSQL"
fi
if [ -n "$MYSQLITE" ]; then
    CP_ENTRIES="$CP_ENTRIES:$MYSQLITE"
fi
CP="$CP_ENTRIES"
FX_MODS="javafx.controls"
RESULTS_DIR="test-results"
RAW_LOG="$RESULTS_DIR/latest.txt"
CLEAN_LOG="$RESULTS_DIR/latest_clean.txt"
REPORT="$RESULTS_DIR/report.md"
RUN_DATE=$(date "+%Y-%m-%d %H:%M:%S")

mkdir -p "$RESULTS_DIR"

if [ -z "$FX" ]; then
    echo "⚠️ Warning: FX path not set. Compilation might fail."
    echo "Current FX: $FX"
fi
if [ -z "$MYSQL" ] && [ -z "$MYSQLITE" ]; then
    echo "⚠️ Warning: neither MYSQL nor MYSQLITE classpath set."
    echo "Current MYSQL: $MYSQL"
    echo "Current MYSQLITE: $MYSQLITE"
fi

# 3. Compile all source files and tests
echo "Compiling..."
javac -cp $CP --module-path $FX --add-modules $FX_MODS \
      *.java tests/*.java

if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

echo "Compilation successful. Running tests..."

# 4. Run JUnit and capture output with tee
java -cp $CP --module-path $FX --add-modules $FX_MODS \
     org.junit.platform.console.ConsoleLauncher \
     execute \
     --select-class SentenceBuilderTest \
     --select-class StressTest \
     --select-class ParsingEdgeCaseTest \
     --select-class ParserRobustnessTest \
     --select-class GenerationLogicTest \
     --select-class AutocompleteLogicTest \
     --select-class CancellationTest \
     --select-class TokenizerNormalizationTest \
     --details=verbose 2>&1 | tee "$RAW_LOG"

EXIT_CODE=${PIPESTATUS[0]}

# 5. Strip ANSI colour codes into a clean log for reliable parsing
sed 's/\x1b\[[0-9;]*m//g' "$RAW_LOG" > "$CLEAN_LOG"

# 6. Parse summary stats from the clean bracketed lines at the bottom (portable, no grep -P)
TOTAL=$(awk '/tests found/ {print $1}' "$CLEAN_LOG" | tail -1)
PASSED=$(awk '/tests successful/ {print $1}' "$CLEAN_LOG" | tail -1)
FAILED=$(awk '/tests failed/ {print $1}' "$CLEAN_LOG" | tail -1)
SKIPPED=$(awk '/tests skipped/ {print $1}' "$CLEAN_LOG" | tail -1)
DURATION=$(sed -n 's/.*Test run finished after \([0-9][0-9]* ms\).*/\1/p' "$CLEAN_LOG" | tail -1)
STRESS_MS=$(awk -F': ' '/Parsed 100,000\+ words in:/ {print $2}' "$CLEAN_LOG" | head -1)

# 7. Helper: look up status for a method name via the uniqueId line
# The uniqueId line contains methodName = 'xxx'; the status line is within 10 lines below
test_status() {
    local method="$1"
    local block
    block=$(grep -A10 "methodName = '$method'" "$CLEAN_LOG" | grep "status:")
    if echo "$block" | grep -q "SUCCESSFUL"; then
        echo "✅ PASSED"
    else
        echo "❌ FAILED"
    fi
}

# 8. Write the Markdown report
cat > "$REPORT" << MARKDOWN
# Sentence Builder — Test Report

**Run Date:** $RUN_DATE
**Total Duration:** $DURATION

---

## Summary

| Metric | Value |
|---|---|
| Total Tests | $TOTAL |
| ✅ Passed | $PASSED |
| ❌ Failed | $FAILED |
| ⏭ Skipped | $SKIPPED |
| ⚡ Stress Parse (100k words) | $STRESS_MS |

---

## Test Results by Suite

### Sentence Builder Core (SentenceBuilderTest)
| Test | Status |
|---|---|
| testMemoryLoading | $(test_status "testMemoryLoading") |
| testNextWordLogic | $(test_status "testNextWordLogic") |

### Stress Test (StressTest)
| Test | Status |
|---|---|
| stressTestParsing (100k words in $STRESS_MS) | $(test_status "stressTestParsing") |

### Parsing Edge Cases (ParsingEdgeCaseTest)
| Test | Status |
|---|---|
| Ellipsis handling (... = 1 boundary) | $(test_status "testEllipsisBehavior") |
| Apostrophe handling (john's vs dogs') | $(test_status "testApostropheHandling") |
| Decimal & abbreviation boundaries | $(test_status "testDecimalAndAbbreviationBoundaries") |
| Hyphen doubling (well-known -> well + known) | $(test_status "testHyphenDoubling") |

### Parser Robustness (ParserRobustnessTest)
| Test | Status |
|---|---|
| P1: Empty file — no crash, no tokens | $(test_status "testEmptyFile") |
| P2: All-punctuation — no word tokens | $(test_status "testAllPunctuationLine") |
| P3: Repeated words — correct bigrams | $(test_status "testRepeatedWords") |
| P4: Non-ASCII words — no crash | $(test_status "testNonAsciiCharacters") |
| P4b: Non-ASCII file — no crash | $(test_status "testNonAsciiFileDoesNotCrash") |
| P5: Duplicate import skip | $(test_status "testDuplicateImportSkipped") |

### Generation Logic (GenerationLogicTest)
| Test | Status |
|---|---|
| G1: Trigram priority over bigram | $(test_status "testTrigramPriorityOverBigram") |
| G2: Dead-end word — no crash | $(test_status "testDeadEndWord") |
| G3: Sentence length cap (max 16 words) | $(test_status "testSentenceLengthCap") |
| G4: First word capitalised | $(test_status "testFirstWordCapitalised") |
| G5: Empty corpus — no crash | $(test_status "testEmptyCorpus") |

### Autocomplete Logic (AutocompleteLogicTest)
| Test | Status |
|---|---|
| A1: Trigram miss → bigram fallback | $(test_status "testTrigramFallsToBigram") |
| A2: Bigram miss → starters fallback | $(test_status "testBigramFallsToStarters") |
| A3: Suggestion cap at 5 | $(test_status "testSuggestionCountCap") |

### Cancellation (CancellationTest)
| Test | Status |
|---|---|
| C1: Mid-parse cancel — no logImport | $(test_status "testCancelMidParse") |
| C1b: Pre-parse cancel — \[BUG\] flag reset | $(test_status "testCancelBeforeParse") |

---

## Known Issues & Bugs

| ID | Severity | Description | Location |
|---|---|---|---|
| BUG-1 | ⚠️ Medium | \`parseFiles()\` resets \`cancelRequested = false\` unconditionally on entry, making pre-cancellation impossible | \`CorpusParser.java\` line 43 |
| BUG-2 | ⚠️ Medium | Trailing apostrophes stripped inconsistently: \`dogs'\` → \`dogs\` but \`john's\` → \`john's\` | \`CorpusParser.java\` regex |
| BUG-3 | ⚠️ Medium | Decimals (45.67) and abbreviations (U.S.A.) trigger false sentence boundaries (count: 5) | \`CorpusParser.java\` regex |
| BUG-4 | ℹ️ Low | Non-ASCII characters silently truncated: \`café\` → \`caf\`, \`résumé\` → fragments | \`CorpusParser.java\` (~line 94, platform-default FileReader charset) |

---

## Raw Log

See \`test-results/latest.txt\` for the full verbose JUnit output.
MARKDOWN

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Test Report saved to: $REPORT"
echo "  Raw log saved to:     $RAW_LOG"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

exit $EXIT_CODE
