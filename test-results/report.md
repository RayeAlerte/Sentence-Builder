# Sentence Builder — Test Report

**Run Date:** 2026-04-21 14:30:40
**Total Duration:** 

---

## Summary

| Metric | Value |
|---|---|
| Total Tests | 23 |
| ✅ Passed | 23 |
| ❌ Failed | 0 |
| ⏭ Skipped | 0 |
| ⚡ Stress Parse (100k words) | 419ms |

---

## Test Results by Suite

### Sentence Builder Core (SentenceBuilderTest)
| Test | Status |
|---|---|
| testMemoryLoading | ✅ PASSED |
| testNextWordLogic | ✅ PASSED |

### Stress Test (StressTest)
| Test | Status |
|---|---|
| stressTestParsing (100k words in ) | ✅ PASSED |

### Parsing Edge Cases (ParsingEdgeCaseTest)
| Test | Status |
|---|---|
| Ellipsis handling (... = 1 boundary) | ✅ PASSED |
| Apostrophe handling (john's vs dogs') | ✅ PASSED |
| Decimal & abbreviation boundaries | ✅ PASSED |
| Hyphen doubling (well-known -> well + known) | ✅ PASSED |

### Parser Robustness (ParserRobustnessTest)
| Test | Status |
|---|---|
| P1: Empty file — no crash, no tokens | ✅ PASSED |
| P2: All-punctuation — no word tokens | ✅ PASSED |
| P3: Repeated words — correct bigrams | ✅ PASSED |
| P4: Non-ASCII words — no crash | ✅ PASSED |
| P4b: Non-ASCII file — no crash | ✅ PASSED |
| P5: Duplicate import skip | ✅ PASSED |

### Generation Logic (GenerationLogicTest)
| Test | Status |
|---|---|
| G1: Trigram priority over bigram | ✅ PASSED |
| G2: Dead-end word — no crash | ✅ PASSED |
| G3: Sentence length cap (max 16 words) | ✅ PASSED |
| G4: First word capitalised | ✅ PASSED |
| G5: Empty corpus — no crash | ✅ PASSED |

### Autocomplete Logic (AutocompleteLogicTest)
| Test | Status |
|---|---|
| A1: Trigram miss → bigram fallback | ✅ PASSED |
| A2: Bigram miss → starters fallback | ✅ PASSED |
| A3: Suggestion cap at 5 | ✅ PASSED |

### Cancellation (CancellationTest)
| Test | Status |
|---|---|
| C1: Mid-parse cancel — no logImport | ✅ PASSED |
| C1b: Pre-parse cancel — \[BUG\] flag reset | ✅ PASSED |

---

## Known Issues & Bugs

| ID | Severity | Description | Location |
|---|---|---|---|
| BUG-1 | ⚠️ Medium | `parseFiles()` resets `cancelRequested = false` unconditionally on entry, making pre-cancellation impossible | `CorpusParser.java` line 43 |
| BUG-2 | ⚠️ Medium | Trailing apostrophes stripped inconsistently: `dogs'` → `dogs` but `john's` → `john's` | `CorpusParser.java` regex |
| BUG-3 | ⚠️ Medium | Decimals (45.67) and abbreviations (U.S.A.) trigger false sentence boundaries (count: 5) | `CorpusParser.java` regex |
| BUG-4 | ℹ️ Low | Non-ASCII characters silently truncated: `café` → `caf`, `résumé` → fragments | `CorpusParser.java` (~line 94, platform-default FileReader charset) |

---

## Raw Log

See `test-results/latest.txt` for the full verbose JUnit output.
