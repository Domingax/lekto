# Spike: EPUB to structured text and word tokens

**Date:** 2026-10-01
**Issue:** [#10](https://github.com/Domingax/lekto/issues/10) (parent #1)
**Scope:** De-risk the first half of the reader pipeline before feature work:
parse a real EPUB (ZIP + OPF + XHTML) into structured paragraphs that keep their
inline formatting, then segment that text into words with ICU so CJK behaves as
it does in the DOM. The value of a spike is the written outcome as much as the
code, so this report records what worked, what is missing, and what
productionising it will cost.
**Method:** Build the smallest pipeline that proves the claim, behind the seams
`core` already owns, and pin it with tests (`docs/testing.md`). The parser and
segmenter are real code on the fast JVM loop, not sketches.

> **Headline:** The pipeline works. A genuinely awkward EPUB — stored `mimetype`,
> an OPF-in-a-subdirectory, non-linear spine items, XHTML named entities, nested
> inline markup, whitespace runs, a container around a paragraph, and a CJK
> chapter — parses into ordered blocks whose runs preserve `<em>`/`<strong>`,
> and ICU segments the text into words, dictionary-based for Japanese and
> Chinese. The two risky choices are settled: a **lenient HTML parser (jsoup)**
> for XHTML content, and **ICU4J** for segmentation. What remains is bounded,
> ordinary production work — per-span language, a real-book corpus, and the
> non-reflowable/edge features listed below — not a missing capability.

## What was built

The model and the seams are pure Kotlin in `commonMain`; the two JVM-dependent
implementations sit in `jvmMain` so `commonMain` stays free of a platform.

| File | Role |
| ---- | ---- |
| `core/src/commonMain/.../text/StructuredText.kt` | `TextRun`/`TextBlock`/`BlockKind`/`StructuredText` — the blocks the reader renders. |
| `core/src/commonMain/.../text/WordToken.kt` | `WordToken` (surface + offsets) and the `TextSegmenter` seam. |
| `core/src/commonMain/.../text/BookTextParser.kt` | The parser seam: bytes in, `StructuredText` out. |
| `core/src/jvmMain/.../epub/EpubParser.kt` | Orchestration: archive → container → OPF → spine → blocks. |
| `core/src/jvmMain/.../epub/EpubArchive.kt` | ZIP reading and relative-href resolution. |
| `core/src/jvmMain/.../epub/OpfDocument.kt` | Namespace-aware, XXE-hardened DOM parse of `container.xml` and the OPF. |
| `core/src/jvmMain/.../epub/XhtmlBlocks.kt` | jsoup walk of one content document into blocks and runs. |
| `core/src/jvmMain/.../text/IcuTextSegmenter.kt` | ICU4J word segmentation; drops whitespace and punctuation. |
| `testkit/src/jvmMain/.../EpubFixtures.kt` | Builds real, deliberately awkward EPUB archives in memory. |

Tests: `EpubParserTest`, `EpubGoldenTest`, `EpubArchiveTest`,
`EpubTokenisationTest` and `IcuTextSegmenterTest` in `core/src/jvmTest`, and the
extraction golden at `core/src/jvmTest/resources/golden/awkward-epub.txt`.

## What worked

- **ZIP → OPF → spine → XHTML is straightforward.** `java.util.zip` reads the
  container; a namespace-aware DOM parse reads `container.xml` and the OPF; the
  manifest and spine resolve to a linear reading order. Non-linear items
  (`cover`, `nav`) are dropped without special-casing the book.
- **A lenient HTML parser is the right tool for content documents.** Real EPUB
  XHTML carries named entities (`&nbsp;`, `&mdash;`), unclosed tags and XHTML5
  the author never declared. jsoup's HTML parser accepts all of it; a strict XML
  parser rejects the book on the first undeclared entity. jsoup is MIT and
  works on JVM and Android.
- **Inline formatting survives as runs.** `<em>`, `<strong>`, `<b>`, `<i>`,
  `<code>` become `InlineStyle`s on the run, and nested markup
  (`<strong>nested <em>inline</em></strong>`) produces `{STRONG}` and
  `{STRONG, EMPHASIS}` runs. Whitespace is collapsed — including the
  non-breaking space `&nbsp;` decodes to — and numeric entities (`&#233;`) are
  decoded.
- **ICU segments as required.** ICU4J's word break iterator is dictionary-based
  for scripts without spaces: `日本語の文章です` becomes `日本語 · の · 文章 ·
  です` and `我看着你好` becomes `我看 · 着 · 你 · 你好`, not per character. The
  same API is `android.icu.text.BreakIterator` on Android (ADR-0007), so the
  two clients will agree. Word offsets map back into the block text.
- **The seams hold.** The parser and segmenter are injected, not reached for:
  `BookTextParser` and `TextSegmenter` let the domain stay free of jsoup and ICU,
  so the reader consumes `StructuredText`/`WordToken` and never learns the
  source format. This is also what keeps the Android implementation a drop-in.

## What is missing

Ordered roughly by how soon it will bite.

| Gap | Effect | Notes |
| --- | --- | --- |
| **Per-document and per-span language** | The whole book is segmented in the OPF's one `dc:language`. A French novel quoting Japanese is segmented as French. | EPUB carries `xml:lang`/`lang` on `<html>`, and on any element/span. The model needs a language per block (and eventually per run), and the parser must read it. This is the first follow-up. |
| **A real-book corpus** | The golden runs on a generated — though genuinely structured and deliberately awkward — EPUB. | The testing decision prefers generated fixtures and small licence-clean ones. Adding two or three real public-domain books (Standard Ebooks / Gutenberg) to `jvmTest` is cheap and is the honest next step; none is committed here to avoid vendoring third-party content in a spike. |
| **Images, figures, tables, footnotes** | Dropped: `<img>` is skipped, table cells become paragraphs, no figure/figcaption semantics, no footnote/target links. | Fine for a first reader of prose; a fidelity backlog. |
| **Fixed-layout EPUB, media overlays, SVG, MathML** | Not read. Fixed-layout books will render as a single text stream or fail. | A decision, not a bug: the MVP reader is reflowable-first (ADR-0007). |
| **CSS-derived emphasis** | Only tag-based inline styles are detected; `style="font-style: italic"` and a CSS class are ignored. | Rare in EPUBs (they prefer `<em>`), but real. A later pass could read the CSS or the computed style. |
| **Percent-encoded and unusual hrefs; case** | `resolve()` handles `.`/`..` and fragments but does not percent-decode, and lookups are case-sensitive. | Small, well-understood, and testable. |
| **Streaming and huge books** | The whole archive is held in memory. | A few MB per book is fine on a phone; streaming would matter only for very large or many concurrent parses. |
| **Encrypted/DRM'd EPUB, remote resources** | Will fail to read. | Acceptable for MVP; the failure should be a clear message rather than a stack trace. |
| **TXT and PDF** | Not built. | They go behind the same `BookTextParser` seam (PDF best-effort, per ADR-0007 / the spec's out-of-scope note). |
| **Android ICU actual** | Not written; `core` has no Android target yet. | ICU4J is the desktop/JVM implementation; Android should use the platform's `android.icu`. **Consequence today:** because `core` declares only a JVM target, the Android app consumes core's JVM artifact and ICU4J currently reaches `:app:debugRuntimeClasspath` — it would ship in the APK. Adding an Android target (or a platform segmenter in `app/androidMain` over the `TextSegmenter` seam) removes it. jsoup is fine on Android and can stay. |

## Estimated cost to productionise

Treating "productionise" as: reflowable EPUB 3 handled confidently for prose,
with the gaps above closed to the level the MVP needs.

| Work | Estimate |
| --- | --- |
| Per-document/span language through the model and parser | 0.5–1 day |
| Real-book corpus and parsing it green (`jvmTest`) | 1 day |
| Href hardening (percent-decoding, case, missing-entry messages) | 0.5 day |
| Footnote/link targets, `<br>`/`<pre>`/whitespace fidelity, table structure | 2–3 days |
| Error taxonomy (distinct messages for not-a-zip, no-rootfile, no-OPF, encrypted) | 0.5 day |
| TXT parser (trivial) | 0.5 day |
| Android target for `core` and a platform `android.icu` segmenter (also drops ICU4J from the APK) | 2–3 days |
| Performance pass / streaming if a real book proves it necessary | 1–2 days (conditional) |
| **Total** | **≈ 2–2.5 weeks** of focused work, most of it fidelity rather than risk |

The genuinely uncertain work — proving the parser and the segmenter are the
right shape — is done. What is left is breadth, and breadth is estimable.

## Decisions this spike settles

- **Use a lenient HTML parser (jsoup) for EPUB content documents.** It is MIT,
  JVM/Android-safe, and the alternatives are worse: a strict XML parse rejects
  real books, and a hand-rolled tag walk re-implements jsoup badly.
- **Use ICU4J on the JVM/desktop and `android.icu` on Android**, behind the
  `TextSegmenter` seam — the plan ADR-0007's reader-substrate update already
  recorded, now demonstrated with dictionary-based CJK.
- **Keep the parser and segmenter in `jvmMain`, not `commonMain`.** They are
  platform-backed (ZIP, XML, ICU), and `commonMain` stays pure Kotlin for the
  model and the seams. `core/src/jvmMain` and `core/src/jvmTest` were added to
  SonarCloud's source/test paths in the same change.

Both new dependencies are AGPL-compatible (ADR-0011) and are recorded in
`config/dependency-licences.txt`: jsoup is MIT, ICU4J is under the Unicode
licence (a permissive, GPL-compatible licence), and ICU4J's `Unicode-3.0`
identifier is an explicit override because the licence task's normaliser does
not map it.

## How to run it

```sh
./gradlew :core:jvmTest                 # the parser, segmenter and golden tests
./gradlew :core:koverXmlReport          # core's coverage report
```

The golden is regenerated by editing `golden/awkward-epub.txt` **only** when the
extraction is deliberately changed; a change to the parser that alters the text
fails `EpubGoldenTest` until then.
