---
status: accepted — reader-substrate challenge adjudicated, decision upheld (see docs/research/reader-substrate-compose-vs-dom.md)
---

# Android and desktop clients are Kotlin Multiplatform, with our own EPUB pipeline

Lekto's clients are **Kotlin Multiplatform with a Compose Multiplatform UI**: `commonMain`
holds the domain (vault, tokenisation, `(language, lemma)` identity, sync engine, provider
adapters); Android is a first-class native target; desktop is a Compose/JVM target added
second.

We rejected Flutter (framework lock-in, a stale EPUB ecosystem, community TTS/keystore
plugins, and a canvas text model) and Tauri/Capacitor (third-party shells, no first-party
SAF). Kotlin meets every Android requirement **first-party** — SAF, `TextToSpeech`, Android
Keystore — with no plugins.

We also rejected **Readium** as the EPUB engine. Readium's Kotlin toolkit is Android-only
and its KMP conversion is future work, so adopting it would give a different reader on each
platform. Lekto's reader is already custom by necessity (tokenisation, mastery colouring,
our own reading-position format), so we parse EPUB (ZIP + OPF + XHTML) into structured text
in `commonMain` and render it ourselves — one reader for both platforms. A short spike
validates the pipeline before the rest of the work proceeds. See
`docs/research/android-first-stack.md`.

## Update — reader-substrate challenge (adjudicated)

A critique held that the per-word layer is only mature in the DOM and that this should overturn
this decision. The claims were verified against primary sources in
`docs/research/reader-substrate-compose-vs-dom.md`:

- **"Compose has no per-word API" is false.** `getOffsetForPosition`, `getWordBoundary`
  (UAX #29), `getBoundingBox`, `getPathForRange`, `AnnotatedString`/`SpanStyle`,
  `LinkAnnotation.Clickable`, `SelectionContainer`, `TextMeasurer` and `MultiParagraph` are all
  first-party and available in `commonMain`. Only a packaged `WordToken` and a paginator are
  missing — compositional work, not a missing capability.
- **Confirmed and credited:** `foliate-js` really does segment via `Intl.Segmenter` into DOM
  Ranges, and Readium 3.4.0 really does render reflowable EPUB in an **Android WebView** — its
  new "Compose" navigators are a Compose host around one (the POM pulls `androidx.webkit`). The
  mature EPUB path in this ecosystem *is* the DOM — which is exactly why we rejected Readium.
- **Overstated:** DOM pagination is whole-section CSS multi-column, self-described as slow — not
  virtualisation. And the per-word layer is not free in the DOM: `foliate-js` colours via a
  custom SVG overlay with custom hit-testing, and Readest's reader is a large custom React
  subsystem.

Consequences absorbed:

1. **The reader engine is a named MVP workstream with its own spike** — a paginator plus the
   `WordToken` layer — alongside the EPUB→tokens spike. The risk is page-splitting quality
   (images, headings, CJK/RTL, hyphenation) and word-level a11y/TTS mapping, not a missing API.
2. **Desktop accessibility is limited and accepted:** macOS supported, Windows only via Java
   Access Bridge (off by default, must be shipped), **Linux unsupported**.
3. **`SelectionContainer` has a documented caveat with lazy layouts** — text items that are not
   composed are excluded from selection. The reader's selection model must be designed around it.
4. **Use ICU for segmentation everywhere** (`android.icu.text.BreakIterator` on Android, ICU4J on
   desktop) so CJK matches what `Intl.Segmenter` gives the DOM.
