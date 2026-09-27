# Reader substrate: Compose vs. the DOM for Lekto's per-word layer

**Date:** 2026-09-27
**Scope:** Adjudicate a critic's claims that Lekto's per-word, tap-and-colour layer is *only* mature in the DOM, and that this should overturn [ADR-0007](../adr/0007-android-desktop-are-kotlin-multiplatform.md) (Kotlin Multiplatform + Compose Multiplatform, Android first, Compose/JVM desktop second). Lekto's targets are **native Compose on Android and Compose/JVM on desktop**; a browser target is **closed** ([ADR-0008](../adr/0008-web-out-of-scope.md)). The reader must render tokenised text: every word a coloured, tappable, selectable span, paginated, for books of 300+ pages ([UX spec](../ux-design-specification.md), `WordToken`).
**Method:** Primary sources only — repositories, source files, Maven Central POMs, vendor docs (Android Developers, JetBrains, Kotlin, Oracle, Unicode/ICU, MDN) and release notes, fetched directly. Every claim carries a URL. Anything I could not check from a primary source is marked **[unverified]** or **[unchecked]**.

> **Headline:** The critique is **half right, and its central factual claim is false.** Confirmed: `foliate-js` really does segment words into DOM `Range`s via `Intl.Segmenter`, and the mature Kotlin EPUB toolkit (Readium 3.4.0) really does render EPUB in an **Android WebView**, even in its new "Compose" navigators (their POM pulls `androidx.webkit`). That is real evidence for the DOM substrate. But the claim that **"Compose has no packaged/verified per-word API" is false**: Compose ships first-party `getOffsetForPosition`, `getWordBoundary` (UAX #29), `getBoundingBox`, `getPathForRange`, `AnnotatedString`/`SpanStyle`, **`LinkAnnotation.Clickable`** (clickable text ranges) and `SelectionContainer`, plus `TextMeasurer`/`MultiParagraph` for pagination. And "paginated virtualisation is solved in the DOM" is **overstated**: `foliate-js` lays out an entire spine section with CSS multi-column and calls itself "slow"; it does not virtualise within a section. Android and desktop Compose are **Stable** with real selection and accessibility (Linux-desktop a11y is the exception). Two production open-source Compose readers — **Book's Story** (Android, no WebView) and **IReader** (KMP Android + desktop, paged + infinite-scroll reader in `commonMain`) — render long text natively. **The stack decision survives.** What Lekto must build is a bounded, own-it reader engine (a paginator + a word-token layer + selection/a11y semantics), not a capability Compose lacks.

---

## Claim 1 — "The per-word, tap-and-colour layer is mature only in the DOM; `foliate-js` segments text into DOM Ranges word-by-word via `Intl.Segmenter`, colouring/tapping via `<span>` + event delegation, and paginated-text virtualisation is a solved problem."

**Verdict: Partly true.** The segmentation half is correct; the colouring/tap mechanism and the "solved virtualisation" half are not what `foliate-js` actually does.

**What `foliate-js` is.** A pure-JavaScript, MIT-licensed library for rendering e-books in the browser: EPUB, MOBI/KF8, FB2, CBZ and an experimental PDF.js adapter. It is modular — book parsers (`epub.js`, `mobi.js`, …), renderers (`fixed-layout.js`, `paginator.js`), and utilities (`overlayer.js`, `search.js`, `tts.js`, `text-walker.js`). It has no build step and is consumed as a git submodule. ([README](https://github.com/johnfactotum/foliate-js), [README raw](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/README.md).)

**Segmentation — Confirmed.** `text-walker.js` joins the text-node strings of a `Range`/`Document`, lets a function split/match them, and maps the results back to DOM `Range`s via `range.setStart`/`setEnd`; the README states it is used exactly this way: "you can join all the text nodes together, use `Intl.Segmenter` to segment the string into words, and get the results in DOM Ranges." ([text-walker.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/text-walker.js).) Two modules actually call `Intl.Segmenter`:

- `tts.js#getSegmenter(lang, granularity)` constructs `new Intl.Segmenter(lang, { granularity })` and uses `isWordLike` to skip non-words ([tts.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/tts.js)).
- `search.js#segmenterSearch` and `searchMatcher` use `Intl.Segmenter`/`Intl.Collator` for whole-word and diacritic-insensitive search ([search.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/search.js)).

So the specific statement "segments text into DOM Ranges word-by-word via `Intl.Segmenter`" is **confirmed**, for search and TTS.

**Colouring/tapping — False as stated.** `foliate-js` does **not** colour words with `<span>` + event delegation. Its annotation/highlight mechanism is `overlayer.js`: an absolutely-positioned SVG element whose child shapes are drawn from a `Range`'s `getClientRects()`. The README is explicit: "The overlay has no event listeners by default. It only provides a `.hitTest(event)` method, that can be used to do hit tests. Currently it does this with the client rects of `Range`s, not the element returned by `draw()`." ([README](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/README.md), [overlayer.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/overlayer.js).) That is an **SVG overlay over ranges**, which is a different (and still custom) mechanism from per-word `<span>`s. The `<span>`+event-delegation description is how *some* language readers might do it; it is not `foliate-js`'s design and should not be attributed to it.

**Paginated virtualisation — Overstated.** The paginator renders **one spine section at a time** into a sandboxed `<iframe>` and paginates the *whole* section with CSS multi-column (`columnize()` sets `column-width`, `column-gap`, `column-fill: auto`, `height`, `overflow: hidden` on `documentElement`), then expands the iframe to `pageCount × viewport` and pages by scrolling the container ([paginator.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/paginator.js)). The README itself says it "uses the same pagination strategy as Epub.js: it uses CSS multi-column. As such it shares much of the same limitations (**it's slow**, some CSS styles do not work as expected, and other bugs)" and "currently there's no support for continuous scrolling". This is *section-by-section* rendering (so memory stays bounded per chapter), but it is **not** virtualisation of a long section; it lays the section out in full. "Virtualisation is a solved problem" is therefore **not** supported by this source.

**Net:** the DOM genuinely offers an ICU-grade segmenter (`Intl.Segmenter`) and a Range-based annotation/hit-test model; it does **not** offer a free per-word span layer, and its pagination is a documented-slow CSS-multicol strategy, not virtualisation.

---

## Claim 2 — "Compose has no packaged/verified per-word API."

**Verdict: False.** Compose ships a complete first-party toolkit for turning a tap into a word and for styling/annotating arbitrary character ranges. What is *not* packaged is a turnkey `WordToken` component and a paginator — those are compositional work on top of these primitives.

All of the following are in `androidx.compose.ui.text` / `androidx.compose.foundation` and are shared `commonMain` (so they exist on Android **and** Compose/JVM desktop):

| Need | First-party API | Evidence |
|---|---|---|
| Tap point → character offset | `TextLayoutResult.getOffsetForPosition(Offset): Int` | [TextLayoutResult.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextLayoutResult.kt) |
| Offset → word range | `TextLayoutResult.getWordBoundary(offset): TextRange` | same; KDoc: "Word boundaries are defined more precisely in Unicode Standard Annex #29" |
| Word/char → pixel box | `getBoundingBox(offset): Rect`, `fillBoundingBoxes(range, array, …)`, `getRangeForRect(rect, granularity, …)` | [MultiParagraph.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/MultiParagraph.kt) |
| Range → drawable path (highlight, underline, selection) | `getPathForRange(start, end): Path`, `getCursorRect`, `getHorizontalPosition` | TextLayoutResult.kt / MultiParagraph.kt |
| Per-range colour/decoration | `AnnotatedString` + `SpanStyle`/`ParagraphStyle`, `TextLinkStyles` | [LinkAnnotation.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/LinkAnnotation.kt) |
| Clickable ranges | `LinkAnnotation.Clickable(tag, styles, linkInteractionListener)` (or, deprecated, `ClickableText` with `onClick: (Int) -> Unit`) | [LinkAnnotation.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/LinkAnnotation.kt), [ClickableText.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/ClickableText.kt) |
| Multi-paragraph layout | `MultiParagraph` ("Lays out and renders multiple paragraphs at once … supports multiple `ParagraphStyle`s") | MultiParagraph.kt |
| Measure with caching | `TextMeasurer.measure(text, style, constraints, …)`, LRU cache, `cacheSize` (default `8`) | [TextMeasurer.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextMeasurer.kt) |
| Selection across children | `SelectionContainer` (common `foundation`), `SelectionState`, `DisableSelection` | [SelectionContainer.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/SelectionContainer.kt) |
| Line metrics for pagination | `getLineTop/Bottom/Left/Right`, `getLineForOffset`, `getLineForVerticalPosition`, `lineCount` | TextLayoutResult.kt |

`ClickableText` is itself the canonical proof of the tap path: its implementation installs `Modifier.pointerInput { detectTapGestures { pos -> onClick(layoutResult.getOffsetForPosition(pos)) } }` ([source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/ClickableText.kt)). To build `WordToken` you combine that with `getWordBoundary` and one `SpanStyle`/`LinkAnnotation.Clickable` per token.

**What is genuinely missing (and would be built):**
1. **A packaged `WordToken`-style component** — no first-party component renders "every word as a coloured, tappable span". You build it from the primitives above.
2. **A paginator** — no first-party "split this text into viewport-sized pages" container. `MultiParagraph`/`TextMeasurer` give you the measurement, line metrics and overflow signal (`didOverflowHeight`, `didExceedMaxLines`) to write one, but Lekto owns the page-splitting algorithm (including images, headings, hyphenation, RTL/CJK).
3. **A virtualised long-text container** — `LazyColumn`/`LazyRow` exist, but `SelectionContainer`'s KDoc warns: "Use of a lazy layout … within a `SelectionContainer` has undefined behavior on text items that aren't composed. For example, texts that aren't composed will not be included in copy operations and select all will not expand the selection to include them." ([source](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/SelectionContainer.kt).) This is a real seam to design around.
4. **Per-word accessibility** — an `AnnotatedString` produces one text semantics node; making each word independently focusable by TalkBack is not automatic (see Claim 3).

So the accurate statement is: "Compose has all the first-party primitives, but no packaged per-word component or paginator." The critic's wording — "no packaged/verified per-word API" — is **false**; the primitives are both packaged and well-defined.

---

## Claim 3 — "Compose Web is canvas/Wasm, beta, partial a11y."

**Verdict: Confirmed for Compose on the web — but Partly true as an argument against Lekto, because Lekto does not target Compose Web.** The web caveats do not transfer to Android or Compose/JVM desktop; desktop has its own, narrower a11y caveat (Linux).

**The web claim is true.** Kotlin Multiplatform lists **Web based on Kotlin/Wasm = Beta** and **Compose Multiplatform for web = Beta** ([KMP stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)). Compose for web renders into a **canvas** via `ComposeViewport` (the docs discuss embedding HTML only through the newer `WebElementView()` which "overlays the canvas area") ([CMP 1.9 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)). Accessibility on web is only "initial": screen readers can reach description labels and buttons, but "the following features are not yet supported: Accessibility for interop and container views with scrolls and sliders; Traversal indexes", and the roadmap still lists drag-and-drop on mobile browsers, improving a11y, and `TextField` issues ([CMP 1.9 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)). This is already recorded in [web-client-viability](./web-client-viability.md) §6. **But it is irrelevant to ADR-0007/0008**: the browser target is closed, and Android/desktop are separate stable targets.

**Android — real selection and TalkBack.** Compose is Google's Android standard ("Compose-first" per ADR-0007's own citations), and Compose text selection works through `SelectionContainer` (a `commonMain` foundation API). TalkBack drives the Compose **semantics tree**; `contentDescription`, `role`, `stateDescription` and traversal properties are documented first-party ([Compose accessibility guide](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-accessibility.html); [Android Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility)). The UX spec already commits to TalkBack content descriptions ([UX spec](../ux-design-specification.md) §Accessibility). **This part of the critic's claim does not apply.**

**Desktop — real on macOS, conditional on Windows, absent on Linux.** JetBrains documents the status precisely: **macOS fully supported; Windows supported via Java Access Bridge, which is disabled by default and must be enabled (`jabswitch /enable`) and shipped (`modules("jdk.accessibility")`); Linux not supported** ([Compose desktop accessibility](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-accessibility.html)). Text selection exists on desktop (`SelectionContainer` is common code), but the newer context-menu API is only "initial" on desktop ("The implementation is complete for iOS and web, while desktop has initial support") ([CMP 1.9 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)). So a desktop screen-reader story is real on macOS, needs work on Windows, and **is not available on Linux** — a genuine, documented limitation Lekto should record.

**Net:** the claim is true of the *web* target only; scoped to Android + Compose/JVM the a11y picture is strong (Android) and mixed (desktop: Linux gap, Windows opt-in), not "partial canvas a11y".

---

## Claim 4 — "Readium Kotlin Toolkit 3.4.0 renders in an Android WebView — `androidx.webkit` appears in the POM of its new Compose navigators."

**Verdict: Confirmed.** I verified this from Maven Central POMs and the repository's own build files, not from the critic.

**The POMs.** On Maven Central, `org.readium.kotlin-toolkit:readium-navigator-web-reflowable:3.4.0` (packaging `aar`) declares, among its dependencies:

```xml
<dependency><groupId>androidx.compose.foundation</groupId><artifactId>foundation</artifactId><version>1.12.0</version><scope>compile</scope></dependency>
…
<dependency><groupId>androidx.webkit</groupId><artifactId>webkit</artifactId><version>1.17.0</version><scope>runtime</scope></dependency>
<dependency><groupId>org.jsoup</groupId><artifactId>jsoup</artifactId><version>1.23.2</version><scope>runtime</scope></dependency>
```

([readium-navigator-web-reflowable-3.4.0.pom](https://repo1.maven.org/maven2/org/readium/kotlin-toolkit/readium-navigator-web-reflowable/3.4.0/readium-navigator-web-reflowable-3.4.0.pom).) The **legacy** `readium-navigator:3.4.0` (the module containing `EpubNavigatorFragment`) also pulls `androidx.webkit:webkit:1.17.0` ([POM](https://repo1.maven.org/maven2/org/readium/kotlin-toolkit/readium-navigator/3.4.0/readium-navigator-3.4.0.pom)). `androidx.webkit` exists to feature-detect and use `WebView` APIs; the 2.4.0 changelog spells it out — the EPUB navigator sets "the Android web view's `WebSettings.textZoom` property to adjust the font size" ([CHANGELOG.md](https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/CHANGELOG.md), [androidx.webkit](https://developer.android.com/jetpack/androidx/releases/webkit)).

**The repository confirms it.** The module that backs the new navigators, `readium/navigators/web/internals/build.gradle.kts`, enables Compose and declares both `implementation(libs.androidx.webkit)` and `implementation(libs.jsoup)` ([build.gradle.kts](https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/readium/navigators/web/internals/build.gradle.kts)). The README describes them as "the new alpha Compose-based navigators for EPUB" ([README](https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/README.md)); the guide names them `ReflowableWebRendition`/`FixedWebRendition` and warns they are "still experimental and have not been battle tested" ([web-navigators.md](https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/docs/guides/navigator/web-navigators.md)).

**Why it matters.** The most mature EPUB engine in Kotlin — the one [android-first-stack.md](./android-first-stack.md) §2 and ADR-0007 considered and rejected as Android-only — renders reflowable EPUB in a **WebView**, and its *new* Compose API is a Compose **host around that WebView** (`ReflowableWebRendition` wraps the web rendition), not native Compose text. So: on Android, the mature "native EPUB" path is itself DOM; on Compose/JVM desktop, Readium's navigators do not exist at all (the POM is an Android `aar`). This is the critic's strongest single piece of evidence. It confirms the *substrate* point while leaving open whether Lekto should use it — ADR-0007 rejected Readium deliberately because its per-word seam was unverified and it is not shared across platforms.

---

## Claim 5 — "Paginated, virtualised long text is proven in the DOM (Readest)."

**Verdict: Confirmed that Readest is a real, proven, cross-platform DOM reader — but "virtualised" is not accurate, and Readest is not a "web app" in the browser-only sense.**

**What Readest actually is** (from its own repo): an AGPL-3.0, ~24.7k-star open-source e-book reader, "a modern rewrite of Foliate"; built with **Next.js 16 + React 19**, packaged with **Tauri v2** for desktop (Windows/macOS/Linux), Tauri mobile for Android/iOS, and a Next.js/Cloudflare web build ([README](https://github.com/readest/readest)). Its architecture doc states the engine plainly: "EPUB / MOBI / KF8 / FB2 / CBZ / TXT / PDF parsing and rendering is **not** hand-rolled in this repo. The reader sits on top of `packages/foliate-js`, a **forked copy of the Foliate JS engine**." Rendering runs in the browser/webview runtime; the Tauri README even documents a hard dependency on the Windows **Edge WebView2** runtime ([architecture.md](https://raw.githubusercontent.com/readest/readest/main/apps/readest-app/docs/architecture.md), [README](https://github.com/readest/readest)). Chinese segmentation uses `jieba-wasm`; TTS reuses foliate's segmentation ([architecture.md](https://raw.githubusercontent.com/readest/readest/main/apps/readest-app/docs/architecture.md)).

**`foliate-js`** is MIT, pure JS, and is the same library used by Foliate's stable releases; the README calls the library itself "not stable" ([README](https://github.com/johnfactotum/foliate-js)).

**On "virtualised":** as in Claim 1, `foliate-js` paginates a whole spine section with CSS multi-column and is self-described as slow; it is section-by-section, not virtualised. Readest adds features (annotations, RSVP, parallel view, transforms) but the architecture doc does not claim per-section virtualisation ([paginator.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/paginator.js), [architecture.md](https://raw.githubusercontent.com/readest/readest/main/apps/readest-app/docs/architecture.md)). **Confirmed** that a DOM reader for long books is proven and shippable; **not confirmed** that the DOM "solves virtualisation".

---

## 6. Adequacy check for the Compose path

### 6.1 Word segmentation: Kotlin/JVM/Android vs. `Intl.Segmenter`

- **Compose's own boundary finder** is UAX #29-based and explicitly OS/language dependent: `getWordBoundary` "Word boundaries are defined more precisely in Unicode Standard Annex #29"; `getRangeForRect` notes "the word/character breaking is both operating system and language dependent" ([TextLayoutResult.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextLayoutResult.kt), [MultiParagraph.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/MultiParagraph.kt)).
- **`java.text.BreakIterator`** (JDK, available on desktop and Android) provides `getWordInstance`, UAX #29 word boundaries, and extended-grapheme character breaks; the word iterator's boundaries include whitespace/punctuation, so you filter with your own is-word heuristic ([Java 21 API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/BreakIterator.html)).
- **ICU4J `com.ibm.icu.text.BreakIterator`** is ICU's replacement for the JDK class, adds dictionary-based segmentation with word-type tags (`WORD_IDEO`, `WORD_KANA`, `WORD_LETTER`, `WORD_NUMBER`), and follows UAX #29/#14 ([ICU4J 78 API](https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/text/BreakIterator.html)).
- **Android ships ICU directly** as `android.icu.text.BreakIterator` (API 24+), the same ICU API surface ([android.icu.text package](https://developer.android.com/reference/android/icu/text/package-summary), [BreakIterator](https://developer.android.com/reference/android/icu/text/BreakIterator)).
- **`Intl.Segmenter`** is the JS equivalent — locale-sensitive grapheme/word/sentence segmentation, and MDN marks it **Baseline 2024 ("newly available")**, with the canonical use case being CJK/Thai/Lao/Khmer/Myanmar that have no spaces ([MDN `Intl.Segmenter`](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Segmenter)).

**Finding:** Kotlin/JVM/Android has **equivalent-or-better** segmentation to the DOM: JDK `BreakIterator` is built in; ICU4J/Android ICU adds dictionary-based CJK exactly as `Intl.Segmenter` does under the hood (V8/JSC implement `Segmenter` with ICU). The DOM has **no exclusivity** here, and `Intl.Segmenter` is a *recent* baseline, not ancient maturity. The design choice is which engine to call: `android.icu.text.BreakIterator` on Android, `com.ibm.icu.text.BreakIterator` (ICU4J) on desktop, for dictionary-based CJK parity.

### 6.2 `AnnotatedString` with thousands of spans

- **Structure:** `MultiParagraph` splits text into `Paragraph` objects at `ParagraphStyle` boundaries ("supports multiple `ParagraphStyle`s"); `SpanStyle` runs stay inside one paragraph's `AnnotatedString` ([MultiParagraph.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/MultiParagraph.kt)). So thousands of `SpanStyle`s do **not** create thousands of paragraphs; the cost is per-paragraph layout.
- **Caching:** `TextMeasurer` keeps an LRU cache with `cacheSize = 8` by default and ignores draw-only changes (colour/shadow/decoration) when reusing a layout ([TextMeasurer.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextMeasurer.kt)).
- **No first-party documented limit was found.** A community library (PatternAnnotatedString) warns "Many annotations, long text or complex patterns may impact performance" ([repo](https://github.com/xavier-tobin/PatternAnnotatedString)) **[community / not a primary source]**. No first-party benchmark, hard limit, or known-issue citation for "thousands of `SpanStyle`s" was located in this session **[unverified]**.

**Finding:** the safe engineering answer is the same as every reader: **paginate/chunk**, so a layout is computed for one page (or one paragraph) at a time, not for a whole book. Within a page, a few hundred spans is the relevant number, not the book's word count. The risk is real but bounded, and it is a *performance-tuning* risk, not a missing-capability risk.

### 6.3 Pagination primitives and documented limits

- `TextMeasurer.measure(text, …, constraints, maxLines, overflow)` returns a `TextLayoutResult` with `size`, `lineCount`, `didOverflowHeight`, `didExceedMaxLines`, line metrics (`getLineTop/Bottom/Left/Right`, `getLineBaseline`), and `getOffsetForPosition`/`getLineForVerticalPosition`/`getLineForOffset` ([TextMeasurer.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextMeasurer.kt), [TextLayoutResult.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextLayoutResult.kt)). A page-splitter is a binary search over text length with `Constraints(maxHeight = viewport)` until `didOverflowHeight` is false, then re-measure to find the cut, mapping the cut to the next page's start via line metrics.
- **Documented constraint:** `MultiParagraph` requires `constraints.minWidth == 0 && constraints.minHeight == 0` ("Setting `Constraints.minWidth` and `Constraints.minHeight` is not supported") ([MultiParagraph.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/MultiParagraph.kt)). Not a blocker, but a real rule.
- **No first-party paginator/section-measure container exists.** There is no documented memory/CPU ceiling for large books **[unverified — no first-party numbers]**, but because Compose lays out the amount you ask it to, the memory profile is governed by how much you hand one layout (page/chunk), not by the book size.

---

## 7. Existing native Compose readers

**Verdict: Yes — at least two production, open-source, Compose readers render long richly-styled text natively; none of the ones surveyed does per-word colouring + tap.** This falsifies "only the DOM is mature" as a blanket statement, while confirming that the specific per-word feature is not demonstrated off the shelf.

**Book's Story** — Android, Material You, Jetpack Compose. GPL-3.0, ~1.4k stars, on F-Droid/IzzyOnDroid; supports `.epub`, `.fb2`, `.pdf`, `.txt`, `.html`, `.md`; supports SAF ([README](https://github.com/Acclorite/book-story), [F-Droid](https://f-droid.org/packages/ua.acclorite.book_story/)). Its app `build.gradle.kts` has **no `androidx.webkit` and no WebView**: it parses EPUB with `org.jsoup:jsoup`, PDF with `com.tom-roush:pdfbox-android`, Markdown with `org.commonmark:commonmark`, and uses Compose (`foundation`, `material3`) ([build.gradle.kts](https://raw.githubusercontent.com/Acclorite/book-story/master/app/build.gradle.kts)). The reader is a Compose `LazyColumn` keyed by `LazyListState`; each text item is rendered by `ReaderLayoutTextParagraph` → a `StyledText` composable with a Compose `TextStyle` (`LineBreak.Paragraph`, `TextIndent`, `letterSpacing`, `lineHeight`), and it offers double-click translation on a paragraph ([ReaderScreen.kt](https://raw.githubusercontent.com/Acclorite/book-story/master/app/src/main/java/ua/acclorite/book_story/presentation/reader/ReaderScreen.kt), [ReaderLayoutTextParagraph.kt](https://raw.githubusercontent.com/Acclorite/book-story/master/app/src/main/java/ua/acclorite/book_story/ui/reader/ReaderLayoutTextParagraph.kt), [ReaderContent.kt](https://raw.githubusercontent.com/Acclorite/book-story/master/app/src/main/java/ua/acclorite/book_story/ui/reader/ReaderContent.kt)). **This is proof that a production Compose EPUB reader renders 300+ page books without a WebView.** It does *not* do per-word colouring.

**IReader** — **Kotlin Multiplatform, Android + Desktop**, Apache-2.0, ~955 stars; "Free and open source novel reader for Android and Desktop" ([README](https://github.com/IReaderorg/IReader)). The whole `presentation` module is a KMP Compose module targeting `androidLibrary` + `jvm("desktop")` with Compose in `commonMain`, an explicit `composeCompiler { … enableStrongSkippingMode = true }`, and no Composable-in-WebView architecture ([presentation/build.gradle.kts](https://raw.githubusercontent.com/IReaderorg/IReader/master/presentation/build.gradle.kts)). Its reader lives in `commonMain` and includes `PagedReaderMode.kt` (~45 KB), `InfiniteScrollReaderMode.kt`, `ContinuousReaderMode.kt`, and `ReaderText.kt` (~33 KB) ([reader package listing](https://github.com/IReaderorg/IReader/tree/master/presentation/src/commonMain/kotlin/ireader/presentation/ui/reader)). This is a KMP Compose reader with both paged and infinite-scroll modes shared across Android and desktop. Caveat: its **`androidMain` declares `androidx.webkit`** (alongside media/emoji/work), which is consistent with its web-novel *source*/browser features; I did **not** inspect the reader code line-by-line, so I cannot certify the text reader is entirely WebView-free **[partly unverified]**.

**Counter-example within the same ecosystem:** Readium's own 3.4.0 "Compose" navigators are a Compose host **around a WebView** (Claim 4), i.e., the mature Kotlin EPUB toolkit chose the DOM under a Compose shell.

**Also relevant (not Compose, for contrast):** Readest is React+Tauri (Claim 5); Thorium is EDRLab's Electron/React reader ([EDRLab](https://www.edrlab.org/software/thorium-reader/)); Anx Reader is Flutter ([SourceForge listing](https://sourceforge.net/directory/ebook-readers/)). None of these changes the Compose finding.

**No production Compose app was found that renders every word as a coloured, tappable span.** The primitives exist (Claim 2); the *feature* is not demonstrated off the shelf in the surveyed apps.

---

## What Compose would cost us

Concrete work to build on Android + Compose/JVM, all in `commonMain` where possible:

1. **`WordToken` layer (the heart).** Build an `AnnotatedString` with one `SpanStyle` (colour + underline-style variant for colour-blindness) and one `LinkAnnotation.Clickable` (or a token registry + a single tap handler) per token. Keep a bidirectional map token ↔ char range. Tap maps `Offset → getOffsetForPosition → getWordBoundary → token`. Selected word highlighted via `getPathForRange`/`fillBoundingBoxes`. *This is the part that is genuinely Lekto-specific and would be custom on any substrate.*
2. **Pagination.** Write a page-splitter over `TextMeasurer` with bounded height: binary-search the character count that fits, map the cut with line metrics, re-measure the page, no cross-page reflow of the whole book. Handle images, headings, RTL/CJK, hyphenation (`Hyphens.Auto`, `LineBreak.Paragraph`). *No first-party paginator exists.*
3. **Virtualisation/chunking.** Lay out per page (or chunk of paragraphs), not per book. If a `LazyColumn` of paragraphs is used for smooth scrolling, selection is constrained by `SelectionContainer`'s documented lazy-layout behaviour and needs a custom selection model to span non-composed items.
4. **Selection.** `SelectionContainer` gives cross-child selection on Android/desktop; the newer context-menu API is "initial" on desktop; a custom word-level selection (and multi-word phrase selection for the AI translation flow) is likely needed anyway.
5. **Accessibility.** Android: real TalkBack via semantics, but per-word focusability needs custom semantics (one composable per word would destroy performance; so use a coarse text node plus a custom accessibility action, or a "word mode" affordance). Desktop: macOS real, Windows needs Java Access Bridge enabled + `jdk.accessibility` shipped, **Linux none**.
6. **TTS.** `android.speech.tts.TextToSpeech` is first-party on Android; mapping TTS utterance ranges back to `WordToken`s is own work. Desktop TTS is a JVM binding (already flagged in [android-first-stack.md](./android-first-stack.md) §2).
7. **EPUB fidelity.** Because Lekto parses EPUB itself (ADR-0007), it controls and limits the HTML/CSS it honours — good for tokenisation, worse for fidelity than a full browser engine.

**Cost shape:** one reader engine, shared Android↔desktop, no third-party shell, no WebView. The work is real but bounded and is exactly the work Lekto already committed to (own EPUB→tokens pipeline, own reader).

---

## What DOM would cost us

To actually use the mature DOM substrate from a native-Compose stack, Lekto would have to change the shell, not just the renderer.

1. **Two shells for two platforms.** Android would need a WebView-based shell (Capacitor) and desktop a WebView shell (Tauri or Electron) — or Tauri v2's mobile target for both. There is no single native-Compose shell that hosts the DOM. This re-introduces exactly the third-party shell + framework lock-in ADR-0007 rejected ("We rejected Flutter … and Tauri/Capacitor (third-party shells, no first-party SAF)").
2. **Plugin ecosystems.** The earlier research already documented the tax: Capacitor's `@capacitor/filesystem` cannot write `content://` and has no tree picker → a native SAF plugin; Tauri's official `dialog` has no folder picker on Android and its community `tauri-plugin-android-fs` was ~39 stars/one maintainer; TTS and secure storage are community, single-maintainer plugins ([android-first-stack.md](./android-first-stack.md) §§4–5). ADR-0010 removed SAF from the design, which blunts the *folder* argument, but the app-private vault still has to be reached and synced through the shell.
3. **Tauri vs Electron vs Capacitor.** Tauri v2 is a Rust + NDK toolchain (heaviest contributor bar) but a small binary; Electron ships Chromium (large, but consistent DOM and no per-platform webview variance); Capacitor is the only one that also yields a real browser target (which ADR-0008 closes). For a reading app, Electron/Tauri give you a *consistent* engine across platforms; Capacitor does not (system WebViews differ).
4. **You do not get the per-word layer for free even in the DOM.** `foliate-js`'s annotation layer is a custom SVG overlay with its own hit-testing; Readest wraps it with a large custom reader (`app/reader`: ~80 components, ~30 hooks). The DOM gives you CSS layout, Range hit-testing and `SelectionContainer`-equivalent browser selection — not a `WordToken`.

**Cost shape:** two shells, an extra contributor toolchain, community plugins for TTS/keys, and a new reader UI in React/TS — while the Kotlin domain, vault and provider adapters remain. It is *more* total surface, not less, unless the whole product moves to one web codebase.

---

## Verdict table

| # | Claim | Verdict | Decisive evidence |
|---|---|---|---|
| 1 | Per-word/tap/colour mature only in DOM; `foliate-js` uses `Intl.Segmenter` → DOM Ranges; spans + event delegation; virtualisation solved | **Partly true** | Segmenter→Range **confirmed** ([tts.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/tts.js), [text-walker.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/text-walker.js)); colouring is an **SVG overlay with hit-testing**, not spans ([overlayer.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/overlayer.js)); pagination is **whole-section CSS multicol**, README says "slow", not virtualised ([paginator.js](https://raw.githubusercontent.com/johnfactotum/foliate-js/main/paginator.js)) |
| 2 | Compose has no packaged/verified per-word API | **False** | `getOffsetForPosition`, `getWordBoundary` (UAX #29), `getBoundingBox`, `getPathForRange`, `AnnotatedString`/`SpanStyle`, `LinkAnnotation.Clickable`, `SelectionContainer`, `TextMeasurer`, `MultiParagraph` are all first-party ([TextLayoutResult.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextLayoutResult.kt), [LinkAnnotation.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/LinkAnnotation.kt), [SelectionContainer.kt](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/SelectionContainer.kt)) |
| 3 | Compose Web is canvas/Wasm, beta, partial a11y | **Confirmed for web; Partly true as a critique** | Web = Beta + canvas + partial a11y ([KMP stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html), [CMP 1.9](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)); Android/desktop = Stable, TalkBack/macOS a11y real; desktop Linux a11y **not supported**, Windows JAB opt-in ([desktop a11y](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-accessibility.html)) |
| 4 | Readium 3.4.0 renders in an Android WebView; `androidx.webkit` in the Compose navigators' POM | **Confirmed** | `readium-navigator-web-reflowable:3.4.0` POM declares `androidx.webkit:webkit:1.17.0` + jsoup; `internals/build.gradle.kts` `implementation(libs.androidx.webkit)` ([POM](https://repo1.maven.org/maven2/org/readium/kotlin-toolkit/readium-navigator-web-reflowable/3.4.0/readium-navigator-web-reflowable-3.4.0.pom), [build.gradle.kts](https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/readium/navigators/web/internals/build.gradle.kts)) |
| 5 | Paginated long text proven in DOM (Readest) | **Confirmed, with correction** | Readest = Next.js/React + Tauri v2 + forked `foliate-js`, AGPL-3.0 ([README](https://github.com/readest/readest), [architecture.md](https://raw.githubusercontent.com/readest/readest/main/apps/readest-app/docs/architecture.md)); foliate-js paginates per section (not virtualised) |
| 6 | Compose adequacy (segmentation, span perf, pagination limits) | **Adequate with own work** | ICU parity via JDK/ICU4J/`android.icu` ([Java](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/BreakIterator.html), [ICU4J](https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/text/BreakIterator.html), [Android ICU](https://developer.android.com/reference/android/icu/text/package-summary)); `TextMeasurer` cache size 8; no first-party paginator; no first-party span-count limit found |
| 7 | Existing native Compose readers | **Yes (no per-word one found)** | Book's Story (Compose, no WebView, EPUB via jsoup) ([build.gradle.kts](https://raw.githubusercontent.com/Acclorite/book-story/master/app/build.gradle.kts), [ReaderScreen.kt](https://raw.githubusercontent.com/Acclorite/book-story/master/app/src/main/java/ua/acclorite/book_story/presentation/reader/ReaderScreen.kt)); IReader (KMP Compose Android+Desktop, paged/infinite modes) ([reader listing](https://github.com/IReaderorg/IReader/tree/master/presentation/src/commonMain/kotlin/ireader/presentation/ui/reader)) |

---

## Does this change the stack decision?

**No — the decision stands, but it should absorb two corrections.**

**Strongest argument *for* the DOM (the critic's best case).** The two components most central to Lekto's reading loop — segmentation into words and paginated reflow — are *proven and in production* in the DOM: `Intl.Segmenter`→Ranges, CSS multi-column pagination, browser-native selection, and `foliate-js`/Readest as a battle-tested (if self-described "not stable") engine. And the strongest Kotlin EPUB toolkit, **Readium 3.4.0, itself renders in a WebView — even its brand-new "Compose" navigators do**. That is direct evidence that the ecosystem's mature rendering path is the DOM, and that a "native Compose EPUB reader" has to re-invent the browser. If Lekto's priority were "least original engineering in the reader", the DOM (via a web shell) is the rational choice.

**Strongest argument *for* Compose (why the decision survives).**

1. **The critic's load-bearing factual claim is false.** Compose ships first-party `getOffsetForPosition`/`getWordBoundary`/`getBoundingBox`/`getPathForRange`, `AnnotatedString` + `SpanStyle` + `LinkAnnotation.Clickable`, `SelectionContainer`, `TextMeasurer` and `MultiParagraph` (Claim 2). The gap is a *paginator and a `WordToken` component*, both compositional.
2. **The browser target is closed ([ADR-0008](../adr/0008-web-out-of-scope.md)).** Adopting the DOM means adopting **two third-party shells** (Android + desktop) to host it — the exact framework lock-in ADR-0007 rejected — and you *still* write the reader UI. You do not escape custom work; you relocate it into React and lose the Kotlin domain sharing.
3. **The per-word layer is not free in the DOM.** `foliate-js` highlights via a custom SVG overlay with custom hit-testing, and Readest's reader is a large custom React subsystem. Lekto's `WordToken`, mastery colouring, `${(language, lemma)}$ identity and reading-position format ([ADR-0006](../adr/0006-word-identity-language-lemma.md)) are custom in any substrate.
4. **Compose Android/desktop are Stable, a11y is real on the targets, and production Compose readers exist** (Claim 3, Claim 7). The only genuine a11y hole is **Linux desktop**, and the ADRs already treat desktop as second and its TTS/keys as build-your-own.
5. **Segmentation parity is not a DOM advantage.** JDK `BreakIterator`, ICU4J and `android.icu.text.BreakIterator` give the same UAX #29 + dictionary-based CJK capability as `Intl.Segmenter`, which is only **Baseline 2024** (Claim 6.1).

**What to change as a result of this report (not the stack, the plan):**

- **Treat the paginator + `WordToken` as a named MVP workstream with a spike**, alongside the EPUB→tokens spike ADR-0007 already mandates. The riskiest unknown is page-splitting quality (images, headings, CJK/RTL, hyphenation) and word-level a11y/TTS mapping — not a missing API.
- **Record the desktop a11y limitation** (macOS supported; Windows via Java Access Bridge, off by default; **Linux unsupported**) and the max-selection-across-lazy-items caveat in `SelectionContainer`.
- **Use ICU everywhere for segmentation** (`android.icu.text.BreakIterator` on Android, ICU4J on desktop) so CJK avoids the JDK's weaker default, matching what `Intl.Segmenter` gives the DOM.
- **Keep the Readium option honestly closed on its merits** — not because Compose can't render words, but because Readium's reflow path is a WebView and its per-word seam is unverified; Lekto's own pipeline is the decision it already made.

**Bottom line:** the critic proved that the *mature, packaged* word-and-page machinery ships on the DOM, and that even Readium's "Compose" navigator is a WebView. They did **not** prove Compose lacks the primitives; they proved the opposite is true and that the primitives are unassembled. That is a `WordToken`/paginator workstream, not a reason to abandon a stack whose Android/desktop targets are Stable, first-party and shared.

---

## What I could not verify

1. **A first-party documented limit or benchmark for `AnnotatedString` with thousands of `SpanStyle`s.** None found; only a community library's warning. **[unverified]**
2. **Memory/CPU numbers for Compose text layout on 300+ page books.** No first-party figures; the recommendation is chunk/paginate, but no measurement was possible from this environment. **[unverified]**
3. **IReader's reader text path vs. its `androidx.webkit` dependency.** Android `androidMain` pulls `androidx.webkit`; I confirmed the reader modes are Compose `commonMain` but did not trace every call to certify no WebView is used for text. **[partly unverified]**
4. **Exactly which WebView building blocks Readium's `internals` module uses** (the POM/build file prove `androidx.webkit` + jsoup + Compose; I did not open the Kotlin source that constructs the `WebView`). **[strongly inferred, source not read]**
5. **Readest's per-section chunking/virtualisation details.** The architecture doc says it forks `foliate-js`; I did not audit its reader code for any additional chunking. **[unchecked]**
6. **`Intl.Segmenter` implementation details per browser engine** (whether each uses ICU dictionary data). MDN confirms the API/behaviour; the engine-internal mapping is inferred from ICU being the standard implementation. **[inferred]**
7. **The Narra KMP bilingual e-reader** surfaced in search (a Reddit post) but no public repository was found, so it is not cited as evidence. **[unverified]**

---

## Sources

**Lekto internal**
- [ADR-0007 — Android and desktop clients are Kotlin Multiplatform](../adr/0007-android-desktop-are-kotlin-multiplatform.md)
- [ADR-0008 — Web is out of scope](../adr/0008-web-out-of-scope.md)
- [ADR-0010 — The vault is app-private](../adr/0010-vault-is-app-private.md)
- [ADR-0006 — Word identity is `(language, lemma)`](../adr/0006-word-identity-language-lemma.md)
- [docs/research/android-first-stack.md](./android-first-stack.md), [docs/research/web-client-viability.md](./web-client-viability.md)
- [docs/ux-design-specification.md](../ux-design-specification.md)

**foliate-js / Readest**
- foliate-js repository and README — https://github.com/johnfactotum/foliate-js
- foliate-js README (raw) — https://raw.githubusercontent.com/johnfactotum/foliate-js/main/README.md
- foliate-js `text-walker.js` — https://raw.githubusercontent.com/johnfactotum/foliate-js/main/text-walker.js
- foliate-js `search.js` — https://raw.githubusercontent.com/johnfactotum/foliate-js/main/search.js
- foliate-js `tts.js` — https://raw.githubusercontent.com/johnfactotum/foliate-js/main/tts.js
- foliate-js `paginator.js` — https://raw.githubusercontent.com/johnfactotum/foliate-js/main/paginator.js
- foliate-js `overlayer.js` — https://raw.githubusercontent.com/johnfactotum/foliate-js/main/overlayer.js
- Readest repository and README — https://github.com/readest/readest
- Readest `architecture.md` — https://raw.githubusercontent.com/readest/readest/main/apps/readest-app/docs/architecture.md
- Readest `code-layout.md` — https://raw.githubusercontent.com/readest/readest/main/apps/readest-app/docs/code-layout.md

**Compose / Kotlin / Android**
- `TextLayoutResult.kt` (AOSP) — https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextLayoutResult.kt
- `MultiParagraph.kt` (AOSP) — https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/MultiParagraph.kt
- `TextMeasurer.kt` (AOSP) — https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/TextMeasurer.kt
- `LinkAnnotation.kt` (AOSP) — https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-text/src/commonMain/kotlin/androidx/compose/ui/text/LinkAnnotation.kt
- `ClickableText.kt` (AOSP) — https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/ClickableText.kt
- `SelectionContainer.kt` (AOSP) — https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/text/selection/SelectionContainer.kt
- Android API reference, `TextLayoutResult` — https://developer.android.com/reference/kotlin/androidx/compose/ui/text/TextLayoutResult
- Kotlin Multiplatform stability — https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- What's new in Compose Multiplatform 1.9 (web Beta, a11y) — https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html
- Compose Multiplatform accessibility — https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-accessibility.html
- Compose Multiplatform desktop accessibility (macOS/Windows/Linux) — https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-desktop-accessibility.html
- Jetpack Compose accessibility — https://developer.android.com/develop/ui/compose/accessibility
- androidx.webkit releases — https://developer.android.com/jetpack/androidx/releases/webkit

**Readium Kotlin toolkit**
- Repository and README — https://github.com/readium/kotlin-toolkit ; README raw — https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/README.md
- Web Navigators guide — https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/docs/guides/navigator/web-navigators.md
- CHANGELOG (2.4.0 `WebSettings.textZoom`) — https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/CHANGELOG.md
- `internals/build.gradle.kts` — https://raw.githubusercontent.com/readium/kotlin-toolkit/develop/readium/navigators/web/internals/build.gradle.kts
- POM, `readium-navigator-web-reflowable:3.4.0` — https://repo1.maven.org/maven2/org/readium/kotlin-toolkit/readium-navigator-web-reflowable/3.4.0/readium-navigator-web-reflowable-3.4.0.pom
- POM, `readium-navigator:3.4.0` — https://repo1.maven.org/maven2/org/readium/kotlin-toolkit/readium-navigator/3.4.0/readium-navigator-3.4.0.pom
- POM directory index — https://repo1.maven.org/maven2/org/readium/kotlin-toolkit/

**Compose readers**
- Book's Story — https://github.com/Acclorite/book-story ; F-Droid — https://f-droid.org/packages/ua.acclorite.book_story/
- Book's Story `build.gradle.kts` — https://raw.githubusercontent.com/Acclorite/book-story/master/app/build.gradle.kts
- Book's Story `ReaderScreen.kt` — https://raw.githubusercontent.com/Acclorite/book-story/master/app/src/main/java/ua/acclorite/book_story/presentation/reader/ReaderScreen.kt
- Book's Story `ReaderLayoutTextParagraph.kt` — https://raw.githubusercontent.com/Acclorite/book-story/master/app/src/main/java/ua/acclorite/book_story/ui/reader/ReaderLayoutTextParagraph.kt
- Book's Story `ReaderContent.kt` — https://raw.githubusercontent.com/Acclorite/book-story/master/app/src/main/java/ua/acclorite/book_story/ui/reader/ReaderContent.kt
- IReader — https://github.com/IReaderorg/IReader ; presentation build — https://raw.githubusercontent.com/IReaderorg/IReader/master/presentation/build.gradle.kts ; reader package — https://github.com/IReaderorg/IReader/tree/master/presentation/src/commonMain/kotlin/ireader/presentation/ui/reader

**Segmentation**
- Java 21 `java.text.BreakIterator` — https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/BreakIterator.html
- ICU4J 78 `com.ibm.icu.text.BreakIterator` — https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/text/BreakIterator.html
- Android `android.icu.text` package — https://developer.android.com/reference/android/icu/text/package-summary ; `BreakIterator` — https://developer.android.com/reference/android/icu/text/BreakIterator
- MDN `Intl.Segmenter` (Baseline 2024) — https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Intl/Segmenter
- PatternAnnotatedString (community perf warning) — https://github.com/xavier-tobin/PatternAnnotatedString

**Other readers (contrast)**
- Thorium Reader (EDRLab) — https://www.edrlab.org/software/thorium-reader/
- SourceForge open-source e-book readers (Anx Reader = Flutter) — https://sourceforge.net/directory/ebook-readers/
