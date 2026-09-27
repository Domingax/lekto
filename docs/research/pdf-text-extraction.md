# PDF text extraction on the Lekto stack

**Date:** 2026-09-27
**Scope:** Estimate the *real* complexity of **best-effort PDF text extraction** for Lekto — Kotlin Multiplatform, Android first, Compose/JVM desktop second (`docs/adr/0007-android-desktop-are-kotlin-multiplatform.md`), AGPL-3.0 project (`docs/adr/0011-agpl3-with-separate-ccbysa-pack.md`). Lekto's reader tokenises plain text and renders it itself, so we need **plain extracted text**, not a page renderer.
**Method:** Primary sources only — Android developer docs and API references, Apache PDFBox docs/JIRA/source, GitHub repos and releases, Maven Central metadata, PDFium source headers, iText and MuPDF docs. Raw artifact sizes were read from `Content-Length` on Maven Central. Every claim carries a URL. Anything I could not verify, or that is inferred from adjacent evidence, is marked **[unverified]** or **[inferred]**.

> **Headline:** There is **no first-party Android text extractor** — `android.graphics.pdf.PdfRenderer` is render-only ([method list](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)). The mainstream JVM library, **Apache PDFBox 3, does not run on Android at all**: its `PDDocument` static initialiser touches `java.awt` before any parsing, so even loading a file throws `NoClassDefFoundError: java.awt.Point` ([PDFBOX-5716](https://issues.apache.org/jira/browse/PDFBOX-5716), [SO reproduction](https://stackoverflow.com/questions/77448126/pdfbox-3-0-0-library-using-in-android-studio-for-reading-pdf)). That forces a **per-platform parser** if both clients need PDF. The only true single-code-path option today is a young pure-Kotlin library (**KitePDF**, Apache-2.0, pre-1.0). Because Lekto only needs *text* (not rendering), the cheapest credible MVP is a shared `commonMain` interface with an **Android actual built on `pdfbox-android` (Apache-2.0)** and the desktop actual added later on **PDFBox 3** — budget it as **roughly the same order as the EPUB parse step for one platform**, doubled for platform parity, with a **much lower quality ceiling** (scanned/custom-encoded/multi-column PDFs are the recurring failure modes). OCR is a separate, larger feature and should be deferred.

---

## 1. Is there any first-party Android option?

**No.** `android.graphics.pdf.PdfRenderer` can **render pages to bitmaps and nothing else**. Its complete public surface is:

| Method | What it does |
|---|---|
| `PdfRenderer(ParcelFileDescriptor)` | open a seekable FD |
| `getPageCount()` | page count |
| `openPage(int)` → `PdfRenderer.Page` | open a page **for rendering** |
| `shouldScaleForPrinting()` | print hint |
| `getDocumentLinearizationType()` (API 35) | linearisation flag |
| `getPdfFormType()` (API 35) | AcroForm/XFA classification |
| `write(ParcelFileDescriptor, boolean)` (API 35) | save/re-encrypt |
| `close()` | release |

— from the [PdfRenderer reference](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer). There is **no** method returning text, spans, or a text layer. `PdfRenderer.Page` likewise exposes `getWidth()`, `getHeight()` and `render(Bitmap, Rect, Matrix, RenderParams)` — it "Renders a page to a bitmap" ([PdfRenderer.Page reference](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page)). The sibling `android.graphics.pdf.PdfDocument` is for *creating* PDFs, not reading them.

So Android gives you a **PDF viewer primitive**, not a PDF text extractor. Any text extraction is a third-party/native dependency. Android's own runtime is also the reason PDFBox fails (no `java.awt`, see §2.1).

---

## 2. Candidate libraries

### 2.1 Apache PDFBox 3 (JVM) — does it work on Android?

**No.** This is the decisive finding for a "one JVM parser" plan.

- The Apache PDFBox **JIRA request to support Android explicitly states it currently does not**: "even with the most basic thing, of creating a new instance of `PDDocument`, this library fails to be used on Android, **as it requires AWT for many things**, including this simple creation. Android barely has anything related to AWT" — [PDFBOX-5716](https://issues.apache.org/jira/browse/PDFBOX-5716) (affects 3.0.0).
- A concrete PDFBox 3.0.0-on-Android crash is `java.lang.NoClassDefFoundError: Failed resolution of: Ljava/awt/Point; at org.apache.pdfbox.pdmodel.PDDocument.<init>` — i.e. it fails while *loading*, before extraction ([StackOverflow, 2023](https://stackoverflow.com/questions/77448126/pdfbox-3-0-0-library-using-in-android-studio-for-reading-pdf)).
- The failure is not only rendering: `PDDocument`'s static initialiser eagerly warms up a `java.awt.image.Raster`/`ColorModel`, and in an environment without AWT native libs it throws `UnsatisfiedLinkError`/`NoClassDefFoundError`; a maintainer replies that "it might still fail later because pdfbox uses some awt classes when not rendering" — [PDFBox users mailing list](https://www.mail-archive.com/users@pdfbox.apache.org/msg14097.html).
- The project has an *open, old request* to remove AWT ([PDFBOX-1962](https://issues.apache.org/jira/browse/PDFBOX-1962)); it is not done.

**JVM/desktop:** excellent. PDFBox 3 is actively maintained (3.0.8 released 2026-07-11; 2.0.37 released 2026-07-15 — [project home](https://pdfbox.apache.org/), [Maven metadata](https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/maven-metadata.xml)), Apache-2.0, and extracts Unicode text via `PDFTextStripper` ([features](https://pdfbox.apache.org/)).
**Licence:** Apache-2.0 ([project home](https://pdfbox.apache.org/)) — AGPL-compatible.
**Size:** `pdfbox-3.0.8.jar` = 2,070,583 B + `fontbox-3.0.8.jar` = 1,647,000 B + `commons-logging` ≈ **3.6 MiB** raw ([Maven Central](https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/3.0.8/pdfbox-3.0.8.jar), [fontbox](https://repo1.maven.org/maven2/org/apache/pdfbox/fontbox/3.0.8/fontbox-3.0.8.jar), [dependencies](https://pdfbox.apache.org/3.0/dependencies.html)).
**Method count:** a legacy `DexIndexOverflowException` concern exists (the old Android port advised enabling multidex), but **on `minSdk ≥ 21` Android's ART supports multidex natively**, so the 64K method limit is no longer a hard constraint — it reduces to an APK-size concern ([Android multidex](https://developer.android.com/build/multidex)). **[unverified]** — I did not count PDFBox's methods.
**Verdict:** the right desktop extractor; **unusable on Android**.

### 2.2 `pdfbox-android` (Tom Roush) — the Android port

- **What it is:** "A port of Apache's PdfBox library to be usable on Android." Latest **2.0.27.0**, published **2023-01-02**, "Currently based on PDFBox v2.0.27", requires **API 19+** ([README](https://github.com/TomRoush/PdfBox-Android), [release](https://github.com/TomRoush/PdfBox-Android/releases/tag/v2.0.27.0), [Maven metadata](https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/maven-metadata.xml)).
- **Text extraction works on Android.** The bundled sample's `stripText()` loads a document and runs `new PDFTextStripper().getText(document)`; it imports `com.tom_roush.pdfbox.text.PDFTextStripper` and calls `PDFBoxResourceLoader.init(context)` first ([sample `MainActivity.java`](https://github.com/TomRoush/PdfBox-Android/blob/master/sample/src/main/java/com/tom_roush/pdfbox/sample/MainActivity.java)). API is a near-copy of PDFBox 2.x, so the code shape matches the desktop parser.
- **Maintenance is the risk.** Last **release** Jan 2023; the upstream PDFBox JIRA calls it "barely maintained and is stuck in the past" ([PDFBOX-5716](https://issues.apache.org/jira/browse/PDFBOX-5716)). The repo still has **113 open issues** and intermittent activity — e.g. open issue #588 (Aug 2025, render bug) and an open PR #589 "Update Gradle and Android SDK for modern compatibility" (AGP 8.11.1, compile/target SDK 35, Java 1.8) ([releases](https://github.com/TomRoush/PdfBox-Android/releases), [issue #588](https://github.com/TomRoush/PdfBox-Android/issues/588), [PR #589](https://github.com/TomRoush/PdfBox-Android/pull/589)). It is based on PDFBox **2.0.27**, not the current 2.0.37/3.x.
- **Licence:** Apache-2.0 ([README](https://github.com/TomRoush/PdfBox-Android)).
- **Size:** `pdfbox-android-2.0.27.0.aar` = 3,254,019 B ≈ **3.1 MiB** ([Maven Central](https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.aar)); APK contribution after R8 is smaller **[unverified]**.
- **Common code?** No. It is Android-only and uses `com.tom_roush.*` packages, so it must live in `androidMain`, behind `expect/actual` or an interface.
- **Verdict:** the lowest-risk *Android* text extractor for a best-effort MVP — Apache-2.0, text API present, well-understood — accepting a stale, PDFBox-2-era engine.

### 2.3 PdfiumAndroid / PDFium bindings

- **PDFium itself has a full text API.** The C header documents `FPDFText_LoadPage`, `FPDFText_CountChars`, `FPDFText_GetText` ("Extract unicode text string from the page"), `FPDFText_GetCharBox`, `FPDFText_GetBoundedText`, and (experimental) `FPDFText_IsHyphen` / `FPDFText_HasUnicodeMapError` ([`fpdf_text.h`](https://pdfium.googlesource.com/pdfium/+/refs/heads/main/public/fpdf_text.h)). PDFium is the Chromium PDF engine.
- **Licence:** BSD-3-Clause ("Redistribution and use in source and binary forms …", [PDFium `LICENSE`](https://github.com/chromium/pdfium/blob/main/LICENSE)) — AGPL-compatible. Some distributions also describe it as BSD/Apache-2.0 ([ComposePdfReader README](https://github.com/NucleusFramework/ComposePdfReader)).
- **The classic `barteksc/PdfiumAndroid` binding does *not* expose text.** Its public API is rendering + `getPageLinks` + `getTableOfContents` + metadata; there is no text method ([README](https://github.com/barteksc/PdfiumAndroid)). It is also **abandoned: 1.9.0, published 2018-06-28** ([Maven metadata](https://repo1.maven.org/maven2/com/github/barteksc/pdfium-android/maven-metadata.xml)), and the `.aar` is **19,297,946 B ≈ 18.4 MiB** because it bundles native `libpdfium.so` for all ABIs ([Maven Central](https://repo1.maven.org/maven2/com/github/barteksc/pdfium-android/1.9.0/pdfium-android-1.9.0.aar)). **Do not use it for text.**
- **Newer bindings that do expose text** exist but are young: `HyntixHQ/KotlinPdfium` ("396 JNI bindings across all 22 PDFium C API headers", Android arm64-v8a, `PdfTextPage`) ([repo](https://github.com/HyntixHQ/KotlinPdfium)); `NucleusFramework/ComposePdfReader` (KMP, per-page text + per-char boxes) ([repo](https://github.com/NucleusFramework/ComposePdfReader)); `dshatz/pdfmp` (KMP PDFium viewer) ([repo](https://github.com/dshatz/pdfmp)). Prebuilt PDFium binaries come from [`bblanchon/pdfium-binaries`](https://github.com/bblanchon/pdfium-binaries).
- **Licence caveat:** ComposePdfReader's **wrapper** README says "No license file is committed here yet — treat the wrapper code as unlicensed pending a decision", even though GitHub labels it MIT ([README](https://github.com/NucleusFramework/ComposePdfReader)). That is not shippable until resolved. `HyntixHQ/KotlinPdfium`'s licence was not verified.
- **Common code?** Raw PDFium is C; a KMP wrapper can put it in `commonMain` (ComposePdfReader does), but it drags a native library, a JNI/build step, and (for the Compose wrappers) a full Skia/Compose rendering stack we do **not** need for text-only extraction.
- **Verdict:** PDFium is the *best engine* for both quality and speed, but on this stack the mature binding is render-only and the text-capable bindings are young/licence-unclear. Watch, don't depend, yet.

### 2.4 iText 7/9 Community — same licence as us (AGPL-3.0)

- **Licence:** dual **AGPL-3.0** / commercial. "By following the rules of the Affero General Public License (AGPLv3), you may use the iText PDF library and our open-source add-ons at no cost. This license applies to iText 5 and all subsequent versions." Under AGPL you must disclose your full source, disclose modifications, and **retain the iText producer line** ([AGPLv3 page](https://itextpdf.com/how-buy/AGPLv3-license), [iText Community](https://itextpdf.com/products/itext-community)). For an AGPL-3.0 project this is **compatible** — and confirms ADR-0011's note.
- **Android support is real but secondary.** iText ships Android artifacts under `com.itextpdf.android:*` from a **private Artifactory** (`maven { url "https://repo.itextsupport.com/android" }`), e.g. `com.itextpdf.android:itext-core-android:9.2.0`; the reference Android SDK was announced for **API 27+** and "depends on forked versions of PdfiumAndroid and the AndroidPdfViewer project" ([Installing iText on Android](https://kb.itextpdf.com/itext/installing-itext-on-android), [Core 7.2.x on Android](https://kb.itextpdf.com/itext/installing-itext-core-7-2-x-on-android), [Hello World with iText 9 on Android](https://kb.itextpdf.com/itext/creating-a-hello-world-application-with-itext-on-a), [7.2.3 release note](https://itextpdf.com/blog/technical-notes/itext-7-suite-723-released)). Artifacts are **not on Maven Central**, and 7.2.3 warned "there are still some features of iText 7 Core which are not yet fully-functional" on Android — **whether `PdfTextExtractor` is fully functional on Android is [unverified]**.
- **Text extraction:** `PdfTextExtractor.getTextFromPage(page)` uses **`LocationTextExtractionStrategy`** by default, which sorts text by location rather than content-stream order ([`PdfTextExtractor.java`](https://github.com/itext/itext-java/blob/develop/kernel/src/main/java/com/itextpdf/kernel/pdf/canvas/parser/PdfTextExtractor.java), [`LocationTextExtractionStrategy.java`](https://github.com/itext/itext-java/blob/develop/kernel/src/main/java/com/itextpdf/kernel/pdf/canvas/parser/listener/LocationTextExtractionStrategy.java)).
- **Size:** `kernel-9.7.1.jar` = 1,475,201 B + `io-9.7.1.jar` = 850,852 B ≈ **2.2 MiB** for the two modules you need (upstream is at 9.7.1, 2026-07-22) ([Maven metadata](https://repo1.maven.org/maven2/com/itextpdf/itext7-core/maven-metadata.xml), [kernel](https://repo1.maven.org/maven2/com/itextpdf/kernel/9.7.1/kernel-9.7.1.jar), [io](https://repo1.maven.org/maven2/com/itextpdf/io/9.7.1/io-9.7.1.jar)).
- **Verdict:** licence-clean and mature on JVM, but the Android path is a non-standard repository, needs API 27+, bundles PDFium/AndroidPdfViewer forks, and its Android extraction parity is unproven. More moving parts than `pdfbox-android` for the same best-effort goal.

### 2.5 MuPDF (fitz)

- **Licence:** **AGPL-3.0-or-later** — compatible with our project. Artifex's Android guide states the open-source terms, with an important restriction: under AGPL you "may not use any proprietary closed source libraries or components in your app. This includes … **Google Play Services**" ([MuPDF README](https://github.com/ArtifexSoftware/mupdf/blob/master/README), [Using with Android](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html)). That directly conflicts with using **ML Kit** (a Play Services library, §4).
- **Text extraction:** excellent engine; `fz_stext_options` exposes `FZ_STEXT_DEHYPHENATE`, `FZ_STEXT_PRESERVE_LIGATURES`, `FZ_STEXT_PRESERVE_WHITESPACE`, `FZ_STEXT_PRESERVE_SPANS`, `FZ_STEXT_PARAGRAPH_BREAK`, and the Java/Android binding exposes `StructuredText.asText()`, `getBlocks()`, `walk(...)` ([`structured-text.h`](https://github.com/ArtifexSoftware/mupdf/blob/master/include/mupdf/fitz/structured-text.h), [structured text options](https://mupdf.readthedocs.io/en/latest/reference/common/stext-options.html), [`StructuredText.java`](https://github.com/ArtifexSoftware/mupdf/blob/master/platform/java/src/com/artifex/mupdf/fitz/StructuredText.java)).
- **Android:** distributed as a prebuilt viewer/JNI from `maven.ghostscript.com` (`com.artifex.mupdf:viewer`, `com.artifex.mupdf:fitz`), minSdk 16, but the integration path is "copy the viewer `lib` module", i.e. heavier than a Maven dependency ([Using with Android](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html)).
- **JVM:** Java bindings exist for desktop too, built from source (`platform/java`) ([Using with Java](https://mupdf.readthedocs.io/en/latest/guide/using-with-java.html)).
- **Verdict:** technically the strongest text engine with a wide option set, but its AGPL interpretation **bans Play Services** (kills ML Kit), distribution is via a non-standard Maven repo/native module, and it is a C/JNI dependency. Good umbrella, wrong fit for "small best-effort feature".

### 2.6 Kotlin/KMP-native and pure-Kotlin options

- **KitePDF** (`io.github.yuroyami:kitepdf:0.11.0`) is the standout: a **pure-Kotlin KMP document engine with no JNI and no `expect/actual`**, running on Android/JVM/iOS/JS/Wasm, with `doc.pages[0].extractText()` and `page.structuredText.blocks` in `commonMain`. It is **Apache-2.0** and on Maven Central. But it is **pre-1.0** ("the API can still change between minor versions"), a single-maintainer/small project (**43 stars**, ~69 open issues), and its docs admit "Extraction uses the font's `/ToUnicode` CMap **when the font has one**" ([KitePDF](https://github.com/yuroyami/KitePDF)). **[unverified]** — I did not evaluate its extraction quality; the search index shows several near-identical repo copies (`rohan-paudel`, `caygal123`), which I could not distinguish from forks.
- **ComposePdfReader** and **pdfmp** are KMP wrappers over PDFium (see §2.3); they are rendering-first and bring Skia/Compose, which is overkill for text-only, and ComposePdfReader's licence is unresolved.
- I found **no other credible pure-Kotlin/KMP PDF text extractor** with production maturity.

### 2.7 Library comparison table

| Library | Licence | Android | JVM / desktop | Text extraction | Maintenance | Size (raw) | Verdict |
|---|---|---|---|---|---|---|---|
| **Apache PDFBox 3** | Apache-2.0 | ❌ **fails on Android** (AWT in `PDDocument`) | ✅ best-in-class | ✅ `PDFTextStripper` | ✅ active (3.0.8, 2026-07) | ~3.6 MiB (pdfbox+fontbox) | Desktop actual only |
| **pdfbox-android** | Apache-2.0 | ✅ (API 19+) | ❌ Android-only | ✅ `PDFTextStripper` | ⚠️ last release 2023-01; based on PDFBox 2.0.27 | ~3.1 MiB (AAR) | **Best Android MVP pick** |
| **PdfiumAndroid (barteksc)** | BSD-3-Clause | ✅ (but abandoned) | ❌ | ❌ none exposed | ❌ last release 2018 | ~18.4 MiB (AAR, all ABIs) | Avoid for text |
| **PDFium (engine/raw C)** | BSD-3-Clause | ✅ via binding | ✅ via binding | ✅ `FPDFText_*` | ✅ upstream active | ABI-split native lib | Best engine; needs a binding |
| **KMP PDFium wrappers** (ComposePdfReader, pdfmp, KotlinPdfium) | MIT-ish / **unresolved** | ✅ | ✅ | ✅ | ⚠️ young, single-maintainer, licence unclear | pulls Skia/Compose + native | Watch, don't depend |
| **iText Community / Android** | **AGPL-3.0** / commercial | ✅ API 27+, private Artifactory | ✅ | ✅ `PdfTextExtractor` | ✅ active (9.7.1) | ~2.2 MiB (kernel+io) | Licence-clean, heavier Android story |
| **MuPDF (fitz)** | **AGPL-3.0-or-later** | ✅ (native module) | ✅ (from source) | ✅ `StructuredText` + rich options | ✅ active | native lib, size **[unverified]** | Strong engine; **bans Play Services** |
| **KitePDF** | Apache-2.0 | ✅ | ✅ | ✅ `extractText()` | ⚠️ pre-1.0, 43★ | **[unverified]** | Only true common-code option; immature |
| **ML Kit OCR** | proprietary (Play Services) | ✅ API 23+ | ❌ | ✅ (OCR, not extraction) | ✅ Google | +4 MB/script bundled | Separate OCR feature |

---

## 3. Text extraction quality — what the libraries document and admit

PDF is a **graphics format**, not a text format: text is a sequence of positioned glyph draws, so even the libraries' own docs warn that order is not guaranteed. The failure modes Lekto should expect:

- **Reading order / columns.** PDFBox: "By default, text extraction is done in the same sequence as the text in the PDF page content stream. PDF is a graphic format, not a text format … To get text sorted from left to right and top to bottom, use `setSortByPosition(true)`." And round-tripping is not guaranteed: a producer may draw "World" before "Hello" ([PDFBox FAQ — text extraction](https://pdfbox.apache.org/3.0/faq.html)). `PDFTextStripper`'s default is `sortByPosition = false` ([source](https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/text/PDFTextStripper.java)). iText's default `LocationTextExtractionStrategy` **does** sort by location ([source](https://github.com/itext/itext-java/blob/develop/kernel/src/main/java/com/itextpdf/kernel/pdf/canvas/parser/PdfTextExtractor.java)) — marginally friendlier for multi-column prose, but still not true layout analysis. **Multi-column reading order is the single most likely source of garbled context sentences.**
- **Hyphenation across line breaks.** PDFBox's `PDFTextStripper` removes soft hyphens (`\u00ad`) but **does not de-hyphenate hard line-break hyphens by default** ([source](https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/text/PDFTextStripper.java)). PDFium exposes `FPDFText_IsHyphen` for callers to implement it ([`fpdf_text.h`](https://pdfium.googlesource.com/pdfium/+/refs/heads/main/public/fpdf_text.h)). MuPDF has an explicit `FZ_STEXT_DEHYPHENATE` option ([`structured-text.h`](https://github.com/ArtifexSoftware/mupdf/blob/master/include/mupdf/fitz/structured-text.h)). **Expect to write a small de-hyphenation pass yourself.**
- **Ligatures.** PDFBox normalises e.g. the "fi" ligature to "f"+"i" ([source](https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/text/PDFTextStripper.java)), and since 3.0.0 supports Latin ligatures and some complex scripts via GSUB, with the caveat "Text extraction may be incorrect or incomplete" and "we don't support GPOS at all" ([FAQ — complex scripts](https://pdfbox.apache.org/3.0/faq.html)). MuPDF keeps ligatures unless `FZ_STEXT_PRESERVE_LIGATURES` is off ([`structured-text.h`](https://github.com/ArtifexSoftware/mupdf/blob/master/include/mupdf/fitz/structured-text.h)).
- **Subset fonts without a `ToUnicode` CMap.** This is the hard floor. PDFBox: "When you see gibberish … a meaningless internal encoding is being used. The only way to access the text is to use OCR" ([FAQ](https://pdfbox.apache.org/3.0/faq.html)). PDFium gives callers a detector, `FPDFText_HasUnicodeMapError`, and `FPDFText_GetUnicode` returns 0 when it cannot map a glyph ([`fpdf_text.h`](https://pdfium.googlesource.com/pdfium/+/refs/heads/main/public/fpdf_text.h)). KitePDF's own docs say it uses the `/ToUnicode` CMap "when the font has one" ([KitePDF](https://github.com/yuroyami/KitePDF)). **If the CMap is absent, extraction is garbage and no amount of tuning fixes it.**
- **Headers/footers.** No library removes them; the page-footer/page-number text simply appears inline. With `sortByPosition` it tends to land at the page boundaries, so a **line-based heuristic** (strip repeated short first/last lines) is needed.
- **Tables.** None of these libraries offers structured table extraction in the core: PDFBox's FAQ says it "doesn't provide a higher level API to do page layout, paragraph handling, automatic line wrapping or create tables" ([FAQ](https://pdfbox.apache.org/3.0/faq.html)); iText table extraction lives in a commercial add-on (`pdf2Data`); the add-ons the Community page lists as open-source are `pdfHTML`, `pdfSweep` and `pdfOCR`, not `pdf2Data` ([iText Community](https://itextpdf.com/products/itext-community)). MuPDF has experimental table-hunt options ([options list](https://mupdf.readthedocs.io/en/latest/reference/common/table-hunt-options.html)). **For vocabulary context, tables are acceptable noise; don't build a table model.**
- **Encrypted / permission-restricted PDFs.** PDFBox: if the "cannot extract text" permission bit is set you must decrypt with the owner password ([FAQ](https://pdfbox.apache.org/3.0/faq.html)). Surface a clear error rather than failing silently.
- **Scanned PDFs** — see §4.

**What "usable sentences" requires in practice:** join per-page extraction, `setSortByPosition(true)`, de-hyphenate, collapse whitespace, drop repeated header/footer lines. That is a **bounded post-processing pass**, and it is where most of the real engineering lives — not in calling the library.

---

## 4. Scanned PDFs and OCR

**Confirmed: a PDF with no text layer yields nothing.** PDFBox's own FAQ: "It might really be an image instead of text. Some PDF documents are just images that have been scanned in. You can tell by using the selection tool in Acrobat; if you can't select any text then it is probably an image" ([PDFBox FAQ](https://pdfbox.apache.org/3.0/faq.html)). The PDFium text API likewise returns only characters present in the content stream; a pure image page has no text objects.

**OCR is a separate, larger feature**, and it is *not* "install a library and call it":

- It needs page rasterisation first (which is exactly the `PdfRenderer`/PDFBox-render path we otherwise avoid), then OCR, then a way to associate recognised text with reading position.
- **ML Kit Text Recognition v2** is proprietary and delivered through **Google Play Services**; the bundled model adds "about 4 MB size increase per script per architecture", the unbundled model is a runtime download, and it requires **`minSdkVersion 23`** ([ML Kit docs](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)). A Play Services dependency also rules out **MuPDF** (its AGPL terms ban Play Services — [Using with Android](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html)) and complicates F-Droid distribution.
- **Tesseract** via `Tesseract4Android` (Apache-2.0 wrapper; Tesseract Apache-2.0, Leptonica BSD; **API 21+**) avoids Play Services, but requires bundling/downloading multi-MB `*.traineddata` per language into an app-readable path, and it is materially slower ([Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android)).

**Conclusion:** scanned-PDF support is an OCR pipeline (renderer + engine + language data + progress UI + quality tuning), not a PDF-library feature. It is **out of scope for the PDF MVP**; the correct behaviour is to detect "no extractable text" and tell the user, not to silently produce an empty book.

---

## 5. Cost estimate for a KMP app

**What "PDF best-effort" actually entails.** Because Lekto renders its own text, we do **not** need a PDF renderer, a page canvas, zoom, selection, or a PDF viewer widget — only a `String` (or per-page `List<String>`) and a book-ish container. That removes the single most expensive part of any PDF integration.

- **One shared parser or two?** Not one, unless we bet on KitePDF. PDFBox 3 is JVM-only; the Android port is a *different artifact and package* (`com.tom_roush.*`). So the natural shape is:
  - a `commonMain` interface, e.g. `PdfTextExtractor { suspend fun extract(bytes: ByteArray): ExtractedText }`;
  - an **`androidMain` actual** using `pdfbox-android`;
  - a **`jvmMain` actual** using `PDFBox 3` when desktop lands.
  The two implementations are small and share the same call sequence (`PDDocument.load` → `PDFTextStripper` → text), so the duplication is **code shape, not logic**, plus the shared post-processing pass (de-hyphenation, whitespace, headers/footers) which can live in `commonMain` and be unit-tested once.
- **Code surface (rough, [inferred]):** Android actual ≈ 100–250 lines (init, load, per-page strip, error mapping); desktop actual ≈ 100–250 lines; shared cleaning ≈ 100–300 lines; heuristic tests ≈ more than the code. Call the whole thing **a few hundred to ~1,000 lines**, dominated by quality heuristics, not by parsing.
- **Relative complexity vs the EPUB pipeline:** for **one platform**, PDF extraction is **roughly the same order of magnitude as the EPUB *parse* step** (ZIP + OPF + XHTML → structured text) — arguably a little less code, because there is no ZIP/OPF/nav/resource resolution — but the *value* is lower: EPUB yields chapters and clean structure, PDF yields a flat, sometimes-garbled stream. For **two platforms (Android + desktop)**, budget **~1.5–2×** the EPUB parse path, because the PDF parser is not shared while the EPUB parser is. Crucially, PDF extraction is **much cheaper than a PDF reader/viewer** would be, since we skip rendering entirely.
- **APK / binary size (raw artifacts, before R8/ABI splits):**
  - `pdfbox-android` AAR ≈ **3.1 MiB**, plus `commons-logging` and optional BouncyCastle for encrypted PDFs ([AAR](https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.aar), [dependencies implied by the sample](https://github.com/TomRoush/PdfBox-Android/blob/master/sample/src/main/java/com/tom_roush/pdfbox/sample/MainActivity.java)).
  - PDFBox 3 on desktop ≈ **3.6 MiB** ([Maven Central](https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/3.0.8/pdfbox-3.0.8.jar)).
  - PDFium bindings carry native libraries; the old `barteksc` AAR is **18.4 MiB** across ABIs, but an App Bundle ABI split delivers only one ABI (roughly a quarter to a third of that) **[unverified — I did not download/measure the per-ABI `.so`]**.
  - iText `kernel`+`io` ≈ **2.2 MiB**, before its Android bundle of forked Pdfium/AndroidPdfViewer ([Maven metadata](https://repo1.maven.org/maven2/com/itextpdf/itext7-core/maven-metadata.xml)).
  - ML Kit (if OCR is ever added): **+4 MB per script per architecture** bundled ([ML Kit](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)).
- **Performance on a 300-page book.** **No published benchmark was found**, so treat the following as order-of-magnitude guesses **[unverified]**: a pure-Java `PDFTextStripper` pass on mobile is on the order of **tens to a few hundred ms per page** → a 300-page book plausibly takes **tens of seconds to a couple of minutes**; a native PDFium pass is typically **well under that**. Either way it **must run off the UI thread** with progress and cancellation (PDFBox is documented as **not thread-safe** — one thread per document — [FAQ](https://pdfbox.apache.org/3.0/faq.html)). "Best-effort" should therefore mean *background import with a progress indicator*, not instant open.

---

## 6. Recommendation for an MVP-shaped scope

**Choose (b)-leaning-(d): an Android-first, per-platform extractor behind a shared `commonMain` interface; defer scanned/OCR entirely; keep a single common-code extractor on the watch list.**

Concretely:

1. **Define the seam in `commonMain` now.** `PdfTextExtractor { suspend fun extract(bytes: ByteArray): ExtractedText }` returning pages of plain text, plus a shared **cleanup pass** (de-hyphenate, whitespace, repeated-header/footer stripping) that is platform-agnostic and unit-testable. This mirrors how the EPUB/TXT pipeline already lives in `commonMain` and keeps the domain independent of whichever parser wins.
2. **Ship the Android actual with `pdfbox-android` (Apache-2.0).** It is the only mature, licence-clean Android text extractor with a text API, it is ~3 MiB, and its API mirrors PDFBox so the desktop actual will look the same. Guard the stale-engine risk with tests on a real corpus and be ready to swap in PDFium if quality disappoints.
3. **Add the desktop actual later with PDFBox 3**, when the desktop client exists. Do not contort the architecture now to share a parser with a platform we have not built.
4. **Do not adopt iText or MuPDF for this feature.** iText is licence-clean but adds a private Artifactory, API 27+, and Pdfium/AndroidPdfViewer forks ([KB](https://kb.itextpdf.com/itext/installing-itext-on-android)); MuPDF's AGPL terms ban Play Services ([MuPDF Android guide](https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html)) and it is a native-module integration. Neither is worth it for "best-effort text".
5. **Treat scanned PDFs as unsupported**, detected by "no extractable text" and reported honestly. OCR (ML Kit or Tesseract) is a **separate feature**, not a checkbox on PDF import.
6. **Watch two things to collapse the per-platform work later:** a mature **PDFium KMP wrapper** (`HyntixHQ/KotlinPdfium`, `dshatz/pdfmp`, or ComposePdfReader once its licence is fixed) and **KitePDF** if it reaches 1.0 and demonstrates robust `ToUnicode`/ligature handling. If either matures, PDF becomes a genuine `commonMain` implementation and the desktop duplication disappears.

**Relative complexity, one line:** for one platform, PDF best-effort text ≈ **0.5–1× the EPUB parse step**, but with a **lower quality ceiling and a background-import UX**; for Android **and** desktop, ≈ **1.5–2×**, because the parser is not shared. The reason it is not larger is the deliberate decision to need **text only** — no PDF rendering, no page model, no viewer.

---

## Complexity verdict and recommended scope

- **First-party:** none — `PdfRenderer` is render-only ([ref](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer)).
- **JVM:** PDFBox 3 (Apache-2.0, active, ~3.6 MiB) is the desktop answer, and **cannot run on Android** because of `java.awt` ([PDFBOX-5716](https://issues.apache.org/jira/browse/PDFBOX-5716)).
- **Android:** `pdfbox-android` (Apache-2.0, ~3.1 MiB, `PDFTextStripper`) is the pragmatic **MVP** choice; it is stale (2023, PDFBox 2.0.27) but works and is licence-clean.
- **Licence-clean alternatives:** iText Community (AGPL-3.0, heavier Android story) and MuPDF (AGPL-3.0, bans Play Services, native module). PDFium (BSD-3-Clause) is the best engine but its mature binding has no text API and the text-capable bindings are young.
- **Common-code future:** **KitePDF** (Apache-2.0, pure Kotlin, `commonMain`, no `expect/actual`) is the only one-implementation path today — but it is pre-1.0 and unvetted. **[unverified]**
- **Scanned PDFs:** nothing extracted, ever; OCR is a separate, larger feature (ML Kit proprietary/Play-Services, or Tesseract Apache-2.0 with multi-MB language data).
- **Recommended scope:** shared `commonMain` `PdfTextExtractor` interface + shared cleaning pass; **Android-first actual via `pdfbox-android`**; desktop actual via PDFBox 3 later; **no iText/MuPDF**; **no OCR**; revisit common-code PDFium/KitePDF when they mature.

---

## What I could not verify

1. **Extraction *quality* on a real corpus** — I did not run `pdfbox-android`, PDFBox 3, iText, PDFium, or MuPDF against multi-column, ligature-heavy, header/footer-heavy, or `ToUnicode`-less PDFs. The documented failure modes are cited, but their *frequency in the books Lekto users will import* is unknown. **Prototype before committing.**
2. **Performance on a 300-page book** — no public benchmark found; §5's per-page figures are **order-of-magnitude estimates**.
3. **Dex method count / true APK delta** — raw artifact sizes are measured, but post-R8/ABI-split APK contribution is not; the 64K limit is relaxed by native multidex on API 21+ ([Android multidex](https://developer.android.com/build/multidex)).
4. **iText Android extraction parity** — the KB confirms Android support and API 27+, but the 7.2.3 note says some core features are not fully functional on Android; whether `PdfTextExtractor` is among the working set is **unverified**.
5. **KitePDF maturity** — version 0.11.0, 43 stars, single maintainer; extraction quality, `/ToUnicode` fallback behaviour, and the multiple near-identical GitHub copies (`yuroyami`, `rohan-paudel`, `caygal123`) were not investigated. Treat as an experiment.
6. **KMP wrapper licences** — ComposePdfReader's README says the wrapper is "unlicensed pending a decision" despite an MIT label; `HyntixHQ/KotlinPdfium`'s licence was not found. Not usable until clarified.
7. **Per-ABI native sizes** for PDFium/MuPDF — not measured.
8. **`pdfbox-android` 2.0.27 vs upstream PDFBox 2.0.37/3.x extraction differences** — the port lags upstream; the practical impact on text extraction was not assessed.

---

## References

**Android platform**
- `PdfRenderer` — https://developer.android.com/reference/android/graphics/pdf/PdfRenderer
- `PdfRenderer.Page` — https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page
- Multidex — https://developer.android.com/build/multidex
- ML Kit Text Recognition v2 (Android) — https://developers.google.com/ml-kit/vision/text-recognition/v2/android

**Apache PDFBox / pdfbox-android**
- Apache PDFBox home & features — https://pdfbox.apache.org/
- PDFBox 3.0 FAQ (text extraction, fonts, threading) — https://pdfbox.apache.org/3.0/faq.html
- PDFBox 3.0 dependencies — https://pdfbox.apache.org/3.0/dependencies.html
- PDFBox 3.0 migration guide — https://pdfbox.apache.org/3.0/migration.html
- `PDFTextStripper` source — https://github.com/apache/pdfbox/blob/trunk/pdfbox/src/main/java/org/apache/pdfbox/text/PDFTextStripper.java
- PDFBOX-5716 (Android / AWT) — https://issues.apache.org/jira/browse/PDFBOX-5716
- PDFBOX-1962 (remove AWT) — https://issues.apache.org/jira/browse/PDFBOX-1962
- PDFBox users thread on AWT warm-up — https://www.mail-archive.com/users@pdfbox.apache.org/msg14097.html
- Maven metadata (`org.apache.pdfbox:pdfbox`) — https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/maven-metadata.xml
- pdfbox-android repo — https://github.com/TomRoush/PdfBox-Android
- pdfbox-android releases — https://github.com/TomRoush/PdfBox-Android/releases
- pdfbox-android sample (`PDFTextStripper`) — https://github.com/TomRoush/PdfBox-Android/blob/master/sample/src/main/java/com/tom_roush/pdfbox/sample/MainActivity.java
- pdfbox-android issue #77 (AWT runtime errors) — https://github.com/TomRoush/PdfBox-Android/issues/77
- pdfbox-android issue #588 / PR #589 (modernisation) — https://github.com/TomRoush/PdfBox-Android/issues/588 · https://github.com/TomRoush/PdfBox-Android/pull/589
- Maven metadata (`com.tom-roush:pdfbox-android`) — https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/maven-metadata.xml

**PDFium**
- PDFium text C API — https://pdfium.googlesource.com/pdfium/+/refs/heads/main/public/fpdf_text.h
- PDFium `LICENSE` — https://github.com/chromium/pdfium/blob/main/LICENSE
- barteksc/PdfiumAndroid (render-only) — https://github.com/barteksc/PdfiumAndroid
- Maven metadata (`com.github.barteksc:pdfium-android`) — https://repo1.maven.org/maven2/com/github/barteksc/pdfium-android/maven-metadata.xml
- bblanchon PDFium binaries — https://github.com/bblanchon/pdfium-binaries
- HyntixHQ/KotlinPdfium — https://github.com/HyntixHQ/KotlinPdfium
- NucleusFramework/ComposePdfReader — https://github.com/NucleusFramework/ComposePdfReader
- dshatz/pdfmp — https://github.com/dshatz/pdfmp

**iText**
- iText AGPLv3 page — https://itextpdf.com/how-buy/AGPLv3-license
- iText Community — https://itextpdf.com/products/itext-community
- Installing iText on Android — https://kb.itextpdf.com/itext/installing-itext-on-android
- Installing iText Core 7.2.x on Android — https://kb.itextpdf.com/itext/installing-itext-core-7-2-x-on-android
- Hello World with iText 9 on Android — https://kb.itextpdf.com/itext/creating-a-hello-world-application-with-itext-on-a
- iText 7 Suite 7.2.3 release note (Android reference implementation) — https://itextpdf.com/blog/technical-notes/itext-7-suite-723-released
- `PdfTextExtractor.java` — https://github.com/itext/itext-java/blob/develop/kernel/src/main/java/com/itextpdf/kernel/pdf/canvas/parser/PdfTextExtractor.java
- `LocationTextExtractionStrategy.java` — https://github.com/itext/itext-java/blob/develop/kernel/src/main/java/com/itextpdf/kernel/pdf/canvas/parser/listener/LocationTextExtractionStrategy.java
- Maven metadata (`com.itextpdf:itext7-core`) — https://repo1.maven.org/maven2/com/itextpdf/itext7-core/maven-metadata.xml

**MuPDF**
- MuPDF README (AGPL-3.0) — https://github.com/ArtifexSoftware/mupdf/blob/master/README
- Using with Android (AGPL terms, Play Services ban) — https://mupdf.readthedocs.io/en/latest/guide/using-with-android.html
- Using with Java — https://mupdf.readthedocs.io/en/latest/guide/using-with-java.html
- `structured-text.h` (dehyphenate/ligature options) — https://github.com/ArtifexSoftware/mupdf/blob/master/include/mupdf/fitz/structured-text.h
- Structured text options — https://mupdf.readthedocs.io/en/latest/reference/common/stext-options.html
- `StructuredText.java` — https://github.com/ArtifexSoftware/mupdf/blob/master/platform/java/src/com/artifex/mupdf/fitz/StructuredText.java

**KMP / pure Kotlin**
- KitePDF — https://github.com/yuroyami/KitePDF

**OCR**
- ML Kit Text Recognition v2 — https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Tesseract4Android — https://github.com/adaptech-cz/Tesseract4Android
```
