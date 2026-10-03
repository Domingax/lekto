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

## Update — EPUB→tokens spike (issue #10)

The first reader spike ran before feature work; its outcome is recorded in
`docs/research/epub-to-tokens-spike.md`. It confirms this decision and sharpens
where the code lives:

- The **model and the seams** — `StructuredText`/`TextBlock`/`TextRun`,
  `WordToken`, `BookTextParser`, `TextSegmenter` — live in `core`'s `commonMain`
  and are pure Kotlin.
- The **implementations** — the ZIP/OPF/XHTML parser and the ICU4J segmenter —
  live in a JVM source set (`core/src/jvmMain`), because they are backed by the
  platform: `java.util.zip`, a DOM parser, jsoup and ICU4J. "In `commonMain`"
  above means "in the shared domain, shared by both clients"; a JVM source set
  is the only shared set that exists until `core` declares an Android target.
- **Android still segments with `android.icu.text.BreakIterator`**, as this ADR
  requires, behind the same `TextSegmenter` seam. Until `core` has an Android
  target the Android app consumes the JVM artifact, so ICU4J would reach the
  APK; adding that target (or a platform segmenter in the application) removes
  it.
- **jsoup** (MIT) parses the content documents rather than a strict XML parser:
  real EPUB XHTML carries named entities and unclosed tags that a strict parser
  rejects.

Neither the substrate nor the "own EPUB pipeline" decision changed, so no
superseding ADR is written; this update records where the pipeline landed.

## Update — paginated reader spike (issue #11)

The second reader spike — the paginator plus the `WordToken` layer this ADR's
reader-substrate update named as the MVP workstream — ran next; its outcome is
`docs/research/paginated-reader-spike.md`. It confirms the substrate and sharpens
the plan:

- The word layer is built on `AnnotatedString` + `SpanStyle` + `LinkAnnotation.
  Clickable`: per-word colour, tap handling and a focusable accessibility node,
  with no custom hit-testing. The prediction that this is compositional work on
  first-party APIs holds.
- Pagination measures a chapter once and cuts at line boundaries into character
  ranges the UI renders as slices. `SelectionContainer` sits over a **non-lazy**
  page, so every visible word is composed and selectable; the documented
  lazy-layout selection caveat is avoided within a page, at the cost of no
  cross-page selection and no lazy scrolling.
- The recorded limitations stand and are now measured: no keep-with-next, no
  hyphenation, a whole-chapter layout whose cost grows with the chapter
  (~0.3 s for ~58k characters), and the desktop a11y limits (macOS yes; Windows
  Java Access Bridge, opt-in; Linux none).
- Per-level non-colour indicators and cross-page selection are unfinished; both
  are recorded as productionising work in the spike report.

The substrate and the own-EPUB-pipeline decisions do not change.

## Update — Android host tests (issue #49)

`core` now declares an Android target, so the platform-backed code — the EPUB
parser, the ICU4J segmenter, the directory-backed vault — is shared by the JVM
and Android targets in a `jvmSharedMain` source set instead of being a JVM-only
artifact Android happened to consume. The target exists so a **Robolectric host
lane** can run that code on a simulated Android runtime (`docs/testing.md#android-host-lane`);
it is applied only when an Android SDK is available, so a JVM-only checkout still
builds and tests without one.

The target does **not** yet switch segmentation to `android.icu`: the shared
segmenter is still ICU4J, so ICU4J remains on the Android classpath. The
`android.icu` actual this ADR requires is still open (ticket #16); when it lands
it replaces ICU4J for Android and drops it from the APK, as the spike update
above anticipated.
