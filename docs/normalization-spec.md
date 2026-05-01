# Token Normalization Spec

This document defines the canonical tokenization behavior used by both corpus parsing and user-input learning/suggestions.

## Goals

- Maximize consistency between imported corpus data and live user input.
- Preserve meaningful word identity (`what's` vs `whats`).
- Reduce DB fragmentation from Unicode punctuation variants.
- Keep predictable sentence boundary behavior for autocomplete and generation.

## Rules (Complete)

1. Normalize to Unicode NFKC and lowercase all input.
2. Normalize punctuation variants:
   - `’`/`‘` -> `'`
   - `“`/`”` -> `"`
   - `–` -> `-`
   - `—` kept as em-dash boundary marker
   - `…` -> `...`
3. Suppress URL/email noise:
   - `https://...`, `http://...`, `www...` -> removed from lexical stream
   - `name@example.com` -> removed from lexical stream
4. Collapse dotted acronyms/initials before tokenization:
   - `U.S.A.` -> `usa`
   - `J.K.` -> `jk`
5. Lexical token class:
   - `[letters|digits]+` with optional internal apostrophe/hyphen joins
   - preserves `what's`, `state-of-the-art`, `gpt-4`, `v2`
6. Corpus-line cleaning additionally strips `"` and `_`.
7. Autocomplete context uses only the current sentence after latest `. ! ?`.
8. Sentence boundaries in corpus parser:
   - `...`, `--`, `—`, `!`, `?`, `:` and standalone `.` boundaries.

## Complete Tokenizer Ruleset Table

| Category | Input Example | Canonical Text Stage | Token Output | Boundary Behavior | Rationale |
|---|---|---|---|---|---|
| Case fold | `Hello` | `hello` | `hello` | none | Stable key identity |
| Smart apostrophe | `what’s` | `what's` | `what's` | none | Contraction consistency |
| Plain apostrophe | `what's` | `what's` | `what's` | none | Preserve grammatical signal |
| Apostrophe-less form | `whats` | `whats` | `whats` | none | Keep distinct from `what's` |
| Trailing apostrophe | `dogs'` | `dogs'` | `dogs` | none | Avoid dangling punctuation token |
| Smart double quotes | `“hello”` | `"hello"` | `hello` | none | Style-insensitive quoting |
| En dash | `co–op` | `co-op` | `co-op` | none | Dash normalization |
| Hyphenated word | `state-of-the-art` | same | `state-of-the-art` | none | Preserve phrase identity |
| Em dash | `hello—world` | same | `hello`, `world` | boundary | Avoid cross-clause n-grams |
| Double dash | `hello -- world` | same | `hello`, `world` | boundary | Same as em dash |
| Ellipsis glyph | `wait… now` | `wait... now` | `wait`, `now` | boundary | Canonical sentence break |
| Ellipsis dots | `wait... now` | same | `wait`, `now` | boundary | Same behavior as glyph |
| Colon | `note: this` | same | `note`, `this` | boundary | Clause break boundary |
| Exclamation | `wow! yes` | same | `wow`, `yes` | boundary | Sentence break |
| Question mark | `why? because` | same | `why`, `because` | boundary | Sentence break |
| Standalone period | `end. start` | same | `end`, `start` | boundary | Sentence break |
| Dotted acronym | `U.S.A.` | `usa` | `usa` | no internal split | Canonical acronym token |
| Dotted initials | `J.K.` | `jk` | `jk` | no internal split | Canonical initials token |
| Plain acronym | `NASA` | `nasa` | `nasa` | none | Lowercased canonical form |
| Unicode letters | `café naïve` | same | `café`, `naïve` | none | Preserve lexical fidelity |
| Alphanumeric token | `v2` | same | `v2` | none | Keep common modern token |
| Hyphen alnum | `gpt-4` | same | `gpt-4` | none | Keep model/version term intact |
| Decimal-like | `3.14` | same | `3`, `14` | period boundary | Current numeric policy |
| Underscore word | `well_known` | same | `well`, `known` | none | Underscore not lexical join |
| Symbol separator | `rock&roll` | same | `rock`, `roll` | none | Symbol treated as separator |
| Slash phrase | `and/or` | same | `and`, `or` | none | Slash not lexical join |
| URL noise | `https://a.co/x` | removed | none | none | Prevent noisy tokens |
| Email noise | `me@test.com` | removed | none | none | Prevent noisy tokens |

## Known Limitations

- Decimal numbers split into numeric segments around period boundaries.
- Slash-connected technical tokens (`R&D`, `and/or`) are separator-based rather than preserved compounds.
- Hashtag/mention semantics are not explicitly modeled.

