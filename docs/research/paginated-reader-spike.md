# Spike: the paginated reader and the word-token layer

**Date:** 2026-10-01
**Issue:** [#11](https://github.com/Domingax/lekto/issues/11) (parent #1)
**Scope:** De-risk the second half of the reader pipeline: a Compose screen that
renders tokenised text paginated to the viewport, with every word coloured by
mastery and tappable, page navigation, and working selection. The value of a
spike is the written outcome as much as the code, so this report states honestly
how good page-splitting is — headings, images, CJK/RTL, hyphenation — how
selection behaves, the accessibility situation, and the performance on a long
chapter.
**Method:** Build the smallest reader that proves the claim on the fast JVM
loop, driven by the `StructuredText`/`WordToken`/`TextSegmenter` seams ticket #10
landed, and pin it with a semantics test and a screenshot golden
(`docs/testing.md`). The paginator and the word layer are real code, not
sketches.

> **Headline:** The word-token layer works, and it lands on first-party Compose
> primitives exactly as ADR-0007 predicted. Every word is a `SpanStyle` colour
> plus a `LinkAnnotation.Clickable`, which gives tap handling *and* a focusable
> accessibility node for free (the semantics tree exposes one `OnClick` node per
> word). Pagination works and is stable: the chapter is laid out once, cut at
> line boundaries, and sliced into pages that cover the text with no gaps. Page
> navigation and selection both work. The three honest gaps are: **no
> keep-with-next** (a heading can end a page), **no hyphenation** (a long word
> overflows rather than breaks), and a **whole-chapter layout** whose cost grows
> with the chapter — it paginated a ~58k-character chapter in ~0.3 s, and
> chunking is the production fix. Images are absent because the parser drops them
> (#10), not because pagination cannot place them.

## What was built

The domain gained one term the reader needs; the reader itself is presentation.

| File | Role |
| ---- | ---- |
| `core/src/commonMain/.../Mastery.kt` | `MasteryLevel` (the CONTEXT.md scale) and the `MasteryLookup` seam. |
| `app/src/commonMain/.../reader/ReaderChapter.kt` | `ReaderChapter` (blocks + title + language) and `ReaderRenderer` (segmenter + mastery + styles). |
| `app/src/commonMain/.../reader/ReaderTokens.kt` | The word-token layer: blocks → one `AnnotatedString`, one colour span and one link per word. |
| `app/src/commonMain/.../reader/MasteryPalette.kt` | The five-level palette and the non-colour underline indicator. |
| `app/src/commonMain/.../reader/ReaderStyles.kt` | The pinned reading typography (18px serif, 1.7 line height). |
| `app/src/commonMain/.../reader/ReaderPage.kt` | `ReaderPage`/`ReaderLayout` and `paginateChapter`: whole-chapter measure, cut at lines. |
| `app/src/commonMain/.../reader/ReaderScreen.kt` | The screen: title, selectable page, page bar with both navigation directions. |
| `app/src/commonMain/.../reader/SampleChapter.kt` | The bundled sample book the app shows until import lands. |
| `testkit/src/commonMain/.../WhitespaceTextSegmenter.kt` | A deterministic `TextSegmenter` for the UI tests. |

Tests:

- `core/.../MasteryLevelTest` — the scale's ordering and the `highlighted` rule.
- `app/desktopTest/.../MasteryPaletteTest` — the four highlighted levels have
  distinct colours and stand out from normal text; known text is uncoloured.
- `app/desktopTest/.../ReaderTextTest` — the word layer, asserted on the
  `AnnotatedString`: every word has the right colour, the right decoration, and a
  working link.
- `app/desktopTest/.../ReaderPaginationTest` — pages are contiguous, ordered and
  cover the whole chapter; a long chapter spans more than one.
- `app/desktopTest/.../ReaderScreenSemanticsTest` — the screen renders the sample,
  navigates forward and back through semantics, and exposes the words as
  clickable (focusable) nodes beyond the two chrome buttons.
- `app/desktopTest/.../MasteryPaletteGoldenTest` — the palette and the page word
  layer as a text-free golden (`goldens/desktop/mastery-palette.png`); its blocks
  are the sample's real tokens, so it moves with tokenisation and colouring.
- `app/desktopTest/.../LongChapterPerformanceTest` — tokenises and paginates a
  ~58k-character chapter and prints the numbers below.

## What worked

- **Per-word colour and tap on first-party primitives.** Building the page as an
  `AnnotatedString` with a `SpanStyle` and a `LinkAnnotation.Clickable` per word
  is exactly the "compositional work, not a missing capability" ADR-0007's
  reader-substrate update predicted. No custom hit-testing, no overlay.
- **Taps and accessibility come together.** `LinkAnnotation.Clickable` does not
  just handle the tap: the semantics tree exposes each word as its own focusable
  node with an `OnClick` action, so a screen reader can reach words one at a
  time. The page's text node still carries the whole visible page.
- **Pagination is stable and gap-free.** Measuring the chapter once and cutting
  at line starts means pages never overlap or drop a character, and a page
  re-rendered from its slice lays out the same because it begins at a line start
  at the same width.
- **Navigation is trivial once pages are ranges.** Previous/Next move an index;
  the buttons are Material buttons with real enabled/disabled semantics, so a
  test drives them and an assistive technology reports their state.
- **A text-free golden is possible again.** The palette and the page's word
  layout are colour blocks drawn from the sample's real tokens, so the golden
  verifies across machines and still moves when tokenisation or a mastery colour
  changes; only a text-bearing golden would need a pinned font, which is not in
  this change.

## What is missing — the honest page-splitting assessment

Ordered by how soon it will bite.

| Gap | Effect | Notes |
| --- | --- | --- |
| **Whole-chapter layout** | Layout cost and memory grow with the chapter; a very long chapter gets slow. | 58k chars → 134 pages in ~0.3 s here. The production path is chunking: measure a bounded run (one page or one block group) and cache, rather than laying out the chapter. |
| **No keep-with-next** | A heading can sit alone at the foot of a page, separated from its paragraph. | The cut only knows line heights; block-aware pagination (keep a heading with the next block, avoid widows/orphans) is the next step. |
| **No hyphenation** | A word longer than the line cannot break; it overflows or clips. | `softWrap` breaks at word boundaries. Compose exposes `Hyphens.Auto` and `LineBreak.Paragraph`; neither is enabled yet. |
| **Images, figures, tables** | Not rendered at all. | A parser gap (#10 drops them), not a paginator gap: the model has no image block to place. Pagination would need a non-text block kind and a height. |
| **CJK and RTL unexercised** | Segmentation is proven (ICU, #10); line breaking relies on Compose's UAX #14 and bidi, which the UI tests do not cover. | The right follow-up is a UI test with a CJK and an RTL chapter, not new code. |
| **Paragraph split across pages** | A paragraph starts at the foot of one page and continues on the next. | Expected for a reflowable reader; widow/orphan control is part of the block-aware pass above. |
| **Swipe page-turn** | Not implemented; navigation is buttons. | Deliberately: a horizontal drag on the text fights selection. Swipe belongs in the margins or a tap-zone model, and both need a decision (see selection). |

## Selection, characterised

- **Within a page, selection works and is exact.** The page is one non-lazy
  `Box` inside a `SelectionContainer`, so every visible word is composed and can
  be dragged across and copied. There is no virtualisation on the page, so the
  documented lazy-layout caveat does not apply inside it.
- **Across pages, selection stops at the page boundary.** Each page is a
  separate composition; there is no selection model that spans them. Copy is
  therefore per page. A cross-page selection needs either a custom selection
  model or a rendered-but-clipped single layout.
- **The lazy alternative is the caveat ADR-0007 recorded.** A `LazyColumn` of
  paragraphs (the natural choice for smooth scrolling a whole chapter) excludes
  items that are not composed from selection, and "select all" will not expand to
  them. The spike chose a per-page layout precisely to keep selection complete
  within the reading surface; a future chunked/scrolling reader must chunk by
  page, or design the selection model around the lazy boundary.
- **A horizontal drag conflicts with selection.** Selection is a press-drag on
  the text; so is a naive swipe-to-turn. The spike leaves swipe to buttons rather
  than pick one gesture to break.
- **Not automatically tested, and why.** Selection is provided by
  `SelectionContainer` and behaves as above when the app is run, but the desktop
  Compose test API in this version exposes no pointer-gesture injection
  (`performTouchInput`/`performMouseInput`), so a headless drag cannot drive a
  selection and assert its range. A selection test needs either an instrumented
  run on Android or a newer test API; the characterisation here is by hand.

## Accessibility, characterised

- **Android and desktop (non-Linux) get a real story.** Each word is a link with
  focus and an `OnClick`, and the chrome is semantic buttons with enabled state.
  This is more than "the whole page is one node". `ReaderScreenSemanticsTest`
  asserts the words are clickable nodes beyond the two chrome buttons, so the
  claim is pinned rather than prose.
- **Not yet word-level labelled.** The words are reachable but carry no
  `contentDescription` ("word — level n") and no role override; the UX spec asks
  for both. Traversing one focus node per word is also verbose, so a word-level
  mode or a coarser text node with a custom action is a design choice still open.
- **Desktop limits stand as recorded.** macOS supported; Windows via Java Access
  Bridge (opt-in, must ship `jdk.accessibility`); **Linux unsupported**. The
  reader inherits this from Compose desktop; nothing here changes it.
- **Contrast and colour-blindness are only half done.** The palette exists and
  every highlighted level has an underline (a non-colour cue), but all four
  highlighted levels share that one underline. A *distinct* non-colour shape per
  level needs a custom `TextDecoration` renderer; it is unfinished, and the
  current palette's WCAG AA contrast on the light theme is unverified.

## Performance on a long chapter

`LongChapterPerformanceTest`, one run on the development machine, 400 paragraphs:

```
chars=57888 words=10400 pages=134 tokenise=157ms paginate=297ms
```

- **Tokenisation** (ICU4J word segmentation over the whole chapter) is ~0.16 s
  for ~10k words; it is O(n) and not the bottleneck.
- **Pagination** (one whole-chapter layout plus the line walk) is ~0.3 s for
  ~58k characters. It is also O(n), but the constant is the text engine's, and a
  multi-hundred-thousand-character chapter would pay it in time and in the size
  of the `TextLayoutResult` held for the chapter. The fix is chunking, not a
  faster loop: measure one page at a time (binary-search the character count that
  fits a bounded height) or one block-run at a time, and cache by viewport. That
  is the first thing to build when the reader grows past a demo.

These are indicative, not a benchmark: the test asserts only that the work
finishes and produces pages, because a wall-clock assertion tight enough to be
meaningful would be flaky on shared CI.

## Decisions this spike settles

- **Build the word layer on `AnnotatedString` + `LinkAnnotation.Clickable`.** It
  gives per-word colour, tap handling and an accessibility node with no custom
  hit-testing. This is the shape the production word layer should keep.
- **Paginate by measuring and cutting at line boundaries, from one layout per
  unit.** The page is a character range; the UI renders a slice. The unit is the
  whole chapter in the spike and becomes a chunk in production.
- **Add `MasteryLevel` to the domain, keep the palette in `app`.** The level and
  its highlight rule are domain facts; the colours are presentation. This matches
  CONTEXT.md and ADR-0006's plan to key mastery on `(language, lemma)` later —
  `MasteryLookup` takes the surface form for now and can widen behind the seam.
- **Keep `SelectionContainer` over a non-lazy page.** It is the only way to keep
  selection complete; the cost is no cross-page selection and no lazy scrolling.

Neither ADR-0007's substrate decision nor any earlier decision is reversed, so no
superseding ADR is written; this outcome confirms the substrate and records where
the reader landed.

## Estimated cost to productionise

Treating "productionise" as a reader that scrolls or pages a real book without
degradation, with the accessibility the spec promises.

| Work | Estimate |
| --- | --- |
| Chunked pagination (bounded measure + cache) and scroll/lazy integration | 2–4 days |
| Block-aware pagination: keep-with-next, widow/orphan, paragraph spacing | 1–2 days |
| Hyphenation and `LineBreak` policy (`Hyphens.Auto`) | 0.5–1 day |
| Image/figure block kind through the model, parser and paginator | 2–3 days (parser-led) |
| Word-level accessibility: labels, roles, a traversal strategy | 1–2 days |
| Non-colour indicator per level + a contrast pass on three themes | 1 day |
| CJK/RTL UI tests and any fixes they surface | 1–2 days |
| Swipe/gesture model that coexists with selection | 1 day |
| **Total** | **≈ 9–15 days**, most of it fidelity and accessibility, not risk |

The uncertain half — that the word layer and pagination are buildable on
first-party Compose — is settled. What is left is bounded refinement.

## How to run it

```sh
./gradlew :core:jvmTest :app:desktopTest        # the domain and UI suites
./gradlew :app:recordRoborazziDesktop           # update the palette golden
./gradlew :app:verifyRoborazziDesktop           # the golden CI lane
./gradlew :app:run                              # the reader, with the sample chapter
```

A change to a mastery colour or the page word layout fails
`MasteryPaletteTest` or `MasteryPaletteGoldenTest` until the golden is
deliberately re-recorded.
