# The test harness

The fast, deterministic loop every later ticket is written against. The domain is
pure Kotlin, so the whole suite runs on the JVM in seconds — no emulator, no
Docker. Non-determinism enters only through injected seams.

## The one command

```sh
./gradlew check
```

It builds every module, runs the domain suite (`core`), the Android host suite
(`core`'s `androidHostTest`, when an Android SDK is present), the UI-semantics
suite (`app`) and the architecture suite (`architecture`), and runs the
formatting and static-analysis gates
(`docs/build.md#quality-gates`). The narrow version for the inner loop is:

```sh
./gradlew :core:jvmTest :app:desktopTest :architecture:test
```

Force a re-run when Gradle marks the task up-to-date:

```sh
./gradlew :core:jvmTest --rerun
```

## What runs where

| Suite                | Source set                     | Task                            | Needs        |
| -------------------- | ------------------------------ | ------------------------------- | ------------ |
| Domain + properties  | `core/commonTest`              | `:core:jvmTest`                 | a JVM        |
| Android host tests   | `core/androidHostTest`         | `:core:testAndroidHostTest`     | a JVM + an Android SDK (Robolectric downloads its runtime once) |
| UI semantics         | `app/desktopTest`              | `:app:desktopTest`              | a JVM        |
| UI on Android        | `app/androidUnitTest`          | `:app:testDebugUnitTest`        | a JVM + an Android SDK (Robolectric downloads its runtime once) |
| UI screenshot goldens| `app/desktopTest`              | `:app:verifyRoborazziDesktop`   | a JVM        |
| Architecture         | `architecture/src/test`        | `:architecture:test`            | a JVM        |
| Dictionary pack      | `tools/dictionaries/src/test`  | `:tools:dictionaries:test`      | a JVM        |
| WebDAV integration   | `integrations/webdav/src/jvmTest` | `:integrations:webdav:jvmTest` | a JVM; the container cases add Docker and skip without |
| WebDAV on Android    | `integrations/webdav/src/androidHostTest` | `:integrations:webdav:testAndroidHostTest` | a JVM + an Android SDK (Robolectric downloads its runtime once) |
| Android instrumented | `app/androidInstrumentedTest`  | `:app:connectedCheck`           | an emulator (nightly) |
| Dependency licences  | build logic                    | `:checkDependencyLicences`      | resolved metadata |
| Coverage             | build logic (merged)           | `:koverXmlReport`               | a JVM        |

The UI-semantics suite lives in `desktopTest`, not `commonTest`, because the
Compose Multiplatform common test API cannot run under Android's local (host)
test configuration. The Android-specific `ui-test-junit4` API runs the same
screens under Robolectric in `app/androidUnitTest` (`:app:testDebugUnitTest`,
wired into `check`), so an Android-only constraint is not left to the desktop
lane — issue #69's `LazyColumn` key crashed on Android while every JVM test was
green. The slower lanes — screenshot goldens, the containerised WebDAV driver and
instrumented end-to-end runs — run on pull requests or nightly; the CI fast lane
excludes the WebDAV test so the two lanes do not overlap.
`docs/build.md#ci-lanes` lists the workflow jobs that run each one.

The reader's UI tests drive the word layer with `WhitespaceTextSegmenter` in
`testkit` — a deterministic letter/digit splitter — so they do not depend on the
machine's ICU dictionaries. Production segmentation stays behind the
`TextSegmenter` seam (`IcuTextSegmenter`, the ICU4J implementation). ADR-0007
plans an `android.icu` actual for Android; until it lands the Android target
runs the same ICU4J code, so the two clients agree but the APK still carries
ICU4J (a later ticket; ADR-0007's platform update records it as still open).

The parser seam has two implementations now: the JVM-backed `EpubParser` (ticket
#10), whose tests run in `core/src/jvmTest` with their inputs committed fixtures
under `core/src/commonTest/resources` — a generated awkward EPUB and a real
Project Gutenberg book — read through `TestResources` in `testkit`, and the
pure-Kotlin `TxtParser` (ticket #15), whose tests run in `core/commonTest`. The
extraction goldens in `resources/golden/` pin the EPUB parser's output; a change
that alters the text fails until the golden is deliberately updated. See
`docs/research/epub-to-tokens-spike.md`.

The platform-backed implementation lives in `core/src/jvmSharedMain`, a source
set shared by the JVM target and the Android target, so `EpubParser`,
`IcuTextSegmenter` and the directory-backed vault run on both. `core`'s Android
target is applied only when an Android SDK is discoverable
(`docs/build.md#prerequisites`); the `jvm` target alone is enough for the JVM
suite.

`XmlHardeningTest` (`core/jvmTest`) guards the one place the parser was **not
portable**: `DocumentBuilderFactory` feature names outside the JAXP standard are
Xerces-specific, and Android's parser throws on them, so a real EPUB imported on
desktop and failed on Android (ticket #15). The test runs the **whole OPF parse**
through a factory that rejects every non-JAXP feature — the exact Android
condition — and asserts it succeeds, so a future change that sets an optional
feature without tolerating rejection fails here even though the desktop JDK would
accept it. The structural guard is the Robolectric lane below (ticket #49), which
runs the pipeline through Android's own parser; `XmlHardeningTest` stays as the
fast, SDK-free twin.

The library that imports a book and opens a reading session (ticket #15) is a
domain service in `core/book`: `VaultBookLibrary` composes a `VaultStore`, a
`DerivedAssetStore` and the parser map, so its fast-loop tests run against the
in-memory fakes in `core/commonTest`, and a real-EPUB run over a temporary
directory lives in `core/jvmTest`. The vault's binary **attachments** (ADR-0016)
are proven in the shared `VaultStoreContract`, so the in-memory and on-disk
stores cannot drift, and a property in `VaultCodecPropertyTest` round-trips
arbitrary bytes through export and import. The library UI's semantics are proved
in `app/desktopTest` and its same-named Android twin in `app/androidUnitTest`
(issue #74), and the `LibraryController`'s async import is driven with
`kotlinx-coroutines-test`'s `runTest` and an injected dispatcher.

The reader on a real book and its **resume** (issue #16) are proven at the same
levels. The reading position — a character offset, so it survives a reflow — is a
domain behaviour over the in-memory vault in `core/commonTest` and the directory
vault in `core/jvmTest`, so a book cannot lose its place on export, import or a
re-parse; its serialise/parse is a property in
`core/commonTest/.../ReadingPositionRecordPropertyTest`. The reader's UI
semantics — opening at a saved offset, paging with the buttons and the
left/right tap zones, and a tap on the middle receding the chrome — live in
`app/desktopTest/.../ReaderScreenSemanticsTest`, injected with the Compose test
API's pointer injection (`performTouchInput`), and the whole loop (open, turn,
leave, reopen at the same page) in `AppSemanticsTest` against an in-memory
library. Pagination is lazy and tokenisation is per page: `paginateChapter`
yields one page at a time and `buildPageTokens` colours only the visible page, so
opening a book lays out and tokenises its first page and not its every page;
`LongChapterPerformanceTest` pins the shape on a book sized to 300 pages, and
`ReadingProgressWriterTest` pins that rapid page turns coalesce to one vault
write rather than racing it.


## Golden images

The goldens are Roborazzi images recorded from `app/desktopTest` and committed
under `app/src/desktopTest/goldens/`. The desktop target renders with the host's
Skia, so the goldens are deliberately text-free: a golden that renders text would
depend on the fonts installed where it was recorded and would not verify on
another machine. The theme golden records the colour scheme as swatches; the
reader adds `mastery-palette.png`, the mastery palette and the reader page's
word layer as coloured blocks — the same layout a page of text produces, without
the glyphs. A text-bearing reader golden waits on pinned reading typography (a
bundled font); until then, typography is pinned by `ReaderStyles` and the word
layer by `ReaderTextTest`. Record or update with

```sh
./gradlew :app:recordRoborazziDesktop
```

and verify with `./gradlew :app:verifyRoborazziDesktop`, the task the `golden` CI
lane runs. `app/build.gradle.kts` turns on `separateOutputDirs` so the record and
verify tasks cannot race over one directory.

## WebDAV integration lane

`integrations/webdav/src/jvmTest` starts an Apache `mod_dav` server through
Testcontainers and drives it over the network; it is the lane the `SyncTarget`
driver contract (ticket #26) runs against for the WebDAV driver (ticket #27). The
image runs a current Apache and is pinned by digest, so a green build does not
move under it. The driver conditions updates through WebDAV's `If` header rather
than HTTP `If-Match`: Apache marks a freshly written file's ETag weak for a
second, and `If-Match` requires a strong comparison, so it would reject an
immediate update (ADR-0025). The
contract class is annotated `@Testcontainers(disabledWithoutDocker = true)`,
so a machine or runner without Docker skips it instead of failing: the lane is
green everywhere and simply proves more where Docker is present.

The same source set also runs the whole contract against `testkit`'s
`FakeWebDavServer`, an in-process server that speaks the same subset —
`MKCOL`, `PROPFIND`, `GET`, `HEAD`, `PUT`, `DELETE`, Basic auth, ETags and the
conditional headers (`If-None-Match`, `If-Match` and the DAV `If`) — so the
driver's remote layout and conditional writes
are proved without Docker. The fake mirrors the wrinkles that matter to the
driver: a `PUT` that returns no ETag, which forces the driver's `HEAD` fallback,
and a configurable status for a `MKCOL` on a collection that already exists,
which pins that the driver reads a collection's existence instead of trusting
`405` (issue #117). A separate test drives the container lane, a rejected
credential and a `500` (both surfaced as `SyncTargetException`), and a driver that
claims a capability it lacks, which the contract must reject (ADR-0025).

The driver's **Android** half (issue #116; ADR-0026) runs the same contract in
`integrations/webdav/src/androidHostTest` under Robolectric, through the
transport seam's Android implementation — OkHttp — against the same in-process
`FakeWebDavServer`. The fake's `com.sun.net.httpserver` is a host-JVM API that
Android never ships, but Robolectric runs the whole lane on the host JVM, so the
fake stays one implementation in `testkit`'s `jvmMain` and the transport is
proved without Docker and without a network. Because the JVM and Android
transports satisfy one `WebDavTransport` interface and pass one
`SyncTargetContract`, the two cannot drift. The driver also **migrates** a legacy
`<id>.bin` attachment to `<id>.data` when it lists the attachments collection,
with WebDAV's `MOVE` (ADR-0025), so an original a pre-`.data` client uploaded is
recovered rather than lost; `WebDavSyncTargetTest` and the Android host lane pin
it, and the `FakeWebDavServer` speaks `MOVE`.


The nightly instrumented lane runs a launch smoke test on an emulator
(`app/androidInstrumentedTest`, issue #71); the platform integrations that need a
real device — SAF, TTS, Keystore — add their tests there. The lane stays off the
critical path.

## Android host lane

Three host lanes run platform code on a **simulated Android runtime** through
Robolectric, on the host JVM and with no emulator: `core/androidHostTest` (ticket
#49), `app/androidUnitTest` (issue #71) and `integrations/webdav`'s
`androidHostTest` (issue #116). `:core:testAndroidHostTest`,
`:app:testDebugUnitTest` and `:integrations:webdav:testAndroidHostTest` are part
of `check`, so all three run in the `fast` CI job
(they need an Android SDK, which the GitHub runners carry; without one the modules
build as JVM-only and the lanes are skipped).

`core`'s lane exists because the KMP Android library plugin is told to create the
source set (`withHostTest { … }` in `core/build.gradle.kts`); `app` uses the
application plugin, so its lane is the classic `androidUnitTest` source set on
the debug variant (the release variant's unit tests are disabled — the Compose
test rule's host Activity is debug-only). `check` does not reach the Android unit
tests on its own in this KMP setup, so `app/build.gradle.kts` wires
`:app:testDebugUnitTest` into `check` explicitly.

The `core` suite drives the whole `EpubParser` pipeline through
`org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl` — Android's own XML
parser, taken from the `android-all` runtime Robolectric loads — and imports a
real book (`pg1952.epub`). Robolectric never shadows `javax.*`, so the host JDK's
Xerces would hide the difference; the test instantiates Android's factory
explicitly. Replacing the tolerant `XmlHardening.setFeatureIfSupported` with a
plain `setFeature` fails this suite, which is the regression guard for ticket
#15's Android import failure.

The `app` suite renders the Compose UI with `ui-test-junit4` on the same runtime
Android uses, which the desktop lane cannot stand in for: the vocabulary list's
`LazyColumn` keyed its rows by a `WordKey` (a data class), which Android rejects
because a lazy item's key must be Bundle-saveable, and only the Android lane sees
that (issue #69). The desktop lane keeps a Bundle-strict saveable-state registry
in `VocabularyScreenSemanticsTest` as its own guard.

The reader's page — a `SelectionContainer` over an `AnnotatedString` whose
words are clickable `LinkAnnotation`s, paginated by the platform text stack — is
also proved on this lane (issue #75): `ReaderScreenSemanticsTest`
opens at a saved offset, turns pages by button and by tap zone, and toggles the
chrome on the runtime Android uses. Robolectric lays glyphs out far more tightly
than a device font, so the twin uses a longer chapter than the desktop test's
sample; the assertions are about behaviour, not a pinned page count.

The whole application's end-to-end flows are proved on this lane too (issue #79):
`AppSemanticsTest` and `AppVaultSemanticsTest` are the same-named twins of the
desktop files, so import → library → reader → resume, word lookup and save, the
vocabulary list with its search and filter, settings and attribution, and the
vault export/import loop all run under Robolectric through the same `App()`
composable. The book's text is built from one-line paragraphs, because
Robolectric lays glyphs out far more tightly than a device font, and a page
boundary then always falls between paragraphs. A word link is activated through
its semantics action rather than a synthetic tap, because a tap on Robolectric's
tiny measured glyphs lands between them and the reader turns the page instead;
the raw tap zones and paging are proved on this lane by the reader's own twin
(issue #75).

The two lanes are inseparable by rule, not by habit (issue #73): the architecture
suite asserts that every `*SemanticsTest.kt` under `app/src/desktopTest` has a
same-named file under `app/src/androidUnitTest`, so a screen cannot gain a desktop
test and no Android twin without failing `check`. The rule names the missing twin;
screens whose twin has not landed yet sit on `LektoArchitecture.uiTestParityAllowlist`,
which shrinks to empty as each twin lands — and is empty now that issue #79
landed the last two.

Robolectric (MIT) and JUnit 4 (EPL-1.0) are test-scope only and never linked into
the shipped application (`config/dependency-licences.txt`). The first run
downloads Robolectric's `android-all` runtime from Maven Central, so the initial
host run is slower than later ones; the task itself is a few seconds. The common
suite (`commonTest`) rides along on the same compilation, so its JUnit-4 tests run
here too; its Kotest/Kotlin-specs are covered by the JVM lane.

## Dependency licences

`./gradlew checkDependencyLicences` enforces ADR-0011 across every runtime
classpath, test-scope included. The policy and the hand-reviewed overrides live in
`config/dependency-licences.txt`; the task's verdict for every dependency is
written to `build/reports/dependency-licences.txt`. It is a task of its own, not
part of `check`, and CI runs it without the configuration cache. See
`docs/build.md#dependency-licences`.

## Coverage

`./gradlew koverXmlReport` runs the JVM suites and writes one merged,
JaCoCo-compatible coverage report to `build/reports/kover/report.xml` (Kover;
`docs/build.md#coverage-and-the-quality-gate`). The root project is the merging
module, so the report covers `core`, `integrations/webdav` and `app`; `testkit`,
`tools/dictionaries` and `architecture` are not aggregated. The report is a
measurement, not a
gate on its own — the `sonar` CI lane feeds it, with the ktlint and detekt
findings, to SonarCloud, whose quality gate on new code (coverage, duplication,
smells) blocks the pull request. Reproduce a gate failure with the drill in
`docs/build.md#the-quality-gate-drill`.

## The framework

- **`kotlin.test`** — assertions everywhere, as the baseline. On the JVM it maps
  to JUnit 5.
- **Kotest** — the spec style (`FunSpec`), matchers (`shouldBe`), and property
  testing (`forAll`/`checkAll`). Kotest runs on the JUnit Platform; the `jvm`
  target configures `useJUnitPlatform()` and adds `kotest-runner-junit5`.
- **Turbine** — flow assertions (`flow.test { … }`), which fail instead of
  hanging and assert the flow is complete.
- **`kotlinx-coroutines-test`** — `runTest`, `TestScope` and virtual time for the
  async code that lands later.

All four are test-scope. Kotest, Turbine and `kotlinx-coroutines-test` are
Apache-2.0, which is AGPL-compatible (ADR-0011). Kotest's JUnit 5 runner and
`kotlin.test` on the JVM pull in the **JUnit Platform / Jupiter**, which are
EPL-2.0 and *not* GPL-compatible. They are safe here only because they are
test-scope: they are never linked into the shipped app and never conveyed with
it. Keep every JUnit type out of production code — Kotest and `kotlin.test`
cover the test classpath. See `docs/research/testing-harness.md` §2.

## Deterministic seams

Time, identifiers and randomness are the only ways non-determinism enters the
domain. Each is an injected parameter, never an ambient global:

| Seam           | Production        | Test (in `testkit`)       |
| -------------- | ----------------- | ------------------------- |
| Time           | `Clock.System`    | `TestClock`               |
| Identifiers    | `UuidIdGenerator` | `SequentialIdGenerator`   |
| Randomness     | `Random.Default`  | `Random(seed)`            |

`app.lekto.core.Seams` bundles the three so they can be threaded as one value;
`Seams.system()` is the production wiring and `deterministicSeams()` (testkit) is
the test wiring. The domain never reads a wall clock or a global RNG directly.

Dispatchers are the same kind of seam, but the sync engine takes none of its
own: `sync` is a `suspend` function that runs on the caller's context, so
`kotlinx-coroutines-test` supplies the virtual time (`runTest`, `TestScope`,
`advanceUntilIdle`) and a hard-coded `Dispatchers.IO` would not see it. Code
that must move work off the caller's thread takes a `CoroutineDispatcher`
parameter, the way `Seams` threads its clock, ids and randomness.

## Test levels

A ticket states the **behaviour** it wants; the level that proves it comes from
where the change lands:

| Change surface                                                       | Level                                                   | Where it lives              |
| -------------------------------------------------------------------- | ------------------------------------------------------- | --------------------------- |
| A domain invariant — merge, identity, tokenisation, serialise/parse   | property test over generated data                       | `core/commonTest`           |
| A seam — `SyncTarget`, `VaultStore`, a provider adapter               | contract suite + in-memory fake                         | `testkit/commonMain`        |
| A driver under `integrations/`                                        | that contract suite, plus a containerised integration run and the Android host lane | `integrations/webdav/jvmTest`, `integrations/webdav/androidHostTest` |
| A parser — EPUB, TXT, PDF                                             | golden over a generated corpus, plus one awkward real fixture | `jvmTest`             |
| UI behaviour in `app`                                                 | Compose UI-semantics test (desktop, and the same-named Android host lane) | `app/desktopTest`, `app/androidUnitTest` |
| A visual or layout change                                             | + screenshot golden                                     | `app`, on the PR lane       |
| Android platform glue                                                 | Robolectric host test                                   | `androidHostTest` (core), `androidUnitTest` (app) |
| An architectural boundary or naming convention                       | architecture rule test, plus a synthetic violation it must catch | `architecture/src/test` |
| A bug                                                                 | regression test at the seam the bug occurs              | wherever that seam lives    |
| The dictionary-pack transform (`tools/dictionaries`)                  | canonical golden over a committed fixture, plus a built-twice byte hash | `tools/dictionaries/src/test` |

Every change lands with a test at its level: `/deliver` holds each surface to its
row and each demanded level to a red → green before the commit.

`VaultStore` is the first seam to use the pattern: `testkit` holds
`VaultStoreContract` and the `InMemoryVaultStore` fake, `core/commonTest` runs the
contract against the fake, and `core/jvmTest` runs the same contract against the
real directory-backed store — so the two cannot drift (ticket #12, ADR-0014). The
contract covers record storage and, since ticket #15, the binary **attachments**
ADR-0016 adds, so a store cannot quietly lose a book's original on export or on
removal.

Word identity and tokenisation (ticket #13, ADR-0006) are a domain invariant, so
their properties live in `core/commonTest`: the same surface form in the same
language always keys the same, the language separates them, a known lemma
collapses inflections to one key, a missing lemma falls back to the normalised
surface form, and concatenating a text's tokens with their separators
reconstructs it. Normalisation's idempotence and Unicode canonical equivalence
need a platform normaliser, so those two run in `core/jvmTest` beside the JVM
`normaliseSurface` actual. Both suites generate arbitrary code points, so
combining marks and other awkward Unicode are exercised; `testkit` supplies
`InMemoryLemmaLookup` and the deterministic `WhitespaceTextSegmenter`.

Merging is the third domain invariant (ticket #14, ADR-0004), so its properties
also live in `core/commonTest`. `RecordMerge` is the pure comparison and fold: a
version with a later `updatedAt` wins, a tie breaks on the greater `deviceId`,
and the fold of one record's versions is order-independent. `VaultMerge` lifts
that to the whole vault, naming what each side must write or delete. The
properties are idempotence, commutativity for distinct device ids, order
independence, tombstone dominance and convergence after the outcome is applied;
a tombstone with a later timestamp out-votes a live record, and a live record
with a later timestamp out-votes an older tombstone. `testkit` supplies
`testTombstone`, and `testVaultRecord` gained a `device` parameter so a case can
tell two writers apart.

The sync **engine** (ticket #26; ADR-0009, ADR-0015) is the domain service that
moves those merged bytes, so its behaviours live in `core/commonTest`: a full
sync reconciles two vaults that share no history, incremental sync takes a change
cursor where the target offers one and a listing where it does not, a `delete`
propagates and does not resurrect, a conflicting edit resolves by the merge
rules, a conditional write that loses its race is re-read and re-applied, and an
imported book's original follows its record to the other device while a deletion
takes it away on both (ADR-0016). The `SyncTarget` seam is a contract in
`testkit` — `SyncTargetContract` with the `InMemorySyncTarget` fake — run against
the fake in every capability combination, so the cursor and compare-and-swap
branches and the no-capability degraded path are all exercised, and it pins the
attachment channel including that writing a tombstone drops the id's original;
the WebDAV driver (ticket #27) runs the same contract against a container, and
against `testkit`'s in-process `FakeWebDavServer` without Docker, so its remote
layout — one JSON file per item named by the Base64-URL id, one binary file per
book original — and its ETag compare-and-swap are pinned without Docker
(ADR-0025). The
engine's own store of **tombstones** is a second seam, `TombstoneStore`:
`TombstoneStoreContract` runs against `InMemoryTombstoneStore` in
`core/commonTest` and against the JSON store (`JsonTombstoneStore`) over an
in-memory filesystem there and a real directory in `core/jvmTest`. The engine
pins no dispatcher: `sync` is `suspend` and runs on the caller's context, so the
whole suite runs under `runTest`, and one test drives the fake's latency through
the test scheduler to prove it.

The sync **settings and status** (issue #28) are proved at their own levels. The
non-secret endpoint and the enable flag are a seam, `SyncSettingsStore`:
`SyncSettingsStoreContract` runs against `InMemorySyncSettingsStore` in
`core/commonTest` and against the JSON document store (`JsonSyncSettingsStore`)
over an in-memory filesystem there and a real directory in `core/jvmTest`, and
`JsonSyncSettingsStoreTest` pins that a corrupt or future-shaped document reads
as "not configured"; the **application password** never lives there, only in the
`SecretStore` (ADR-0021). The state holder — load the stored endpoint, save it
while the password goes to the secret store, test the connection off the UI
thread, enable sync as a deliberate act, run a manual sync and report its outcome
in plain language, and disconnect without touching the local vault — is
`SyncControllerTest` in `app/desktopTest`, driven by `kotlinx-coroutines-test`'s
virtual time with `testkit`'s `InMemorySyncTarget` and a target that cannot be
reached. It also pins that the blocking probe and sync move onto the injected
dispatcher rather than the caller's scope — so Android never runs a network call
on the main thread — and that a completed sync refreshes the library rather than
leaving it stale (issue #116). `SyncReportSummaryTest` pins the plain-language
summary, the section's
controls, status line and folder-sync warning are `SettingsScreenSemanticsTest`
on both lanes, and the loop — reach settings, save the endpoint and find it
persisted — is driven through `AppSemanticsTest` on both lanes. The
composition roots now both wire a real driver (issue #116; ADR-0026):
`desktopSync` and `androidSync` build the same `WebDavSyncTarget` over their
platform's transport, rooted app-privately under the client's own directory. The
Android wiring is proved on the host lane by `AndroidSyncHostTest` — the settings
document, the tombstone store and the device id under `Context.filesDir`, and the
engine reconciling the Android vault — so **Settings → Sync** is usable on the
first-class client, not only desktop. Without a driver the section says sync is
unavailable and never gates the reader.

The dictionary-pack transform lives in the standalone `tools/dictionaries` module,
so its level is its own: `PackBuilderTest` pins the transform's rules,
`SqlitePackWriterTest` reads the written pack back and proves two builds are
byte-for-byte identical, and `DictionaryPackGoldenTest` pins the canonical output
of a committed JSONL fixture — the reproducibility evidence for ticket #17. The
sanity gate and the command line are unit-tested the same way. The module's tests
run in `check` like any other module's, but the module sits outside the
application's coverage, licence and publication graph, so nothing it builds ships
with the app.

The pack's **consumption in the app** (issue #18) is proved at the same levels.
The `DictionaryPack` contract in `testkit` runs against the in-memory fake in
`core/commonTest`, against the **real SQLite pack** the pipeline built — committed
as `dictionary/en-fr-sample.sqlite` under `core/src/commonTest/resources` — in
`core/jvmTest`, and against the Android framework's SQLite in the Robolectric
`androidHostTest` lane, so the two clients cannot answer a query differently. The
installer's lifecycle — download, the format-handshake refusal, corruption, and
the pack's exclusion from the vault's export — is a `core/commonTest` behaviour
over the fakes in `testkit`, and the JVM downloader's stream-and-decompress path
runs in `core/jvmTest` against a local HTTP server. The settings, attribution and
lookup-panel UI are `app/desktopTest` semantics.

The word lookup panel (issue #19) completes that interaction. The reference
shortcuts are a pure transform in `core`: `DictionarySource.canonicalUrl` builds
each site's canonical page for a word — WordReference, Reverso, Linguee and
Google Translate — and `dictionaryShortcuts` drops a source that cannot address
the language pair rather than hand it a wrong page, so those tests live in
`core/commonTest`. The panel itself, its offline result and its graceful
degradation to the shortcuts and an honest message when no pack is installed are
`app/desktopTest` semantics, and its same-named `app/androidUnitTest` twin (issue
#76) proves the found, offline, pronunciation, mastery and save behaviours under
Robolectric, so the parity rule (issue #73) no longer allowlists the screen. The
reader's selection wash, which keeps the tapped word visible behind the panel, is
pinned on the word layer in `ReaderTextTest`,
and the whole loop — turn a page, tap a word, open Reverso through the injected
browser opener, close, and find the reader still on the same page — is driven
through `AppSemanticsTest`. Opening a shortcut is a platform action: the desktop
opener takes its browse call as a parameter so it is unit-tested, and the Android
glue sits behind the coverage exclusion like the rest of the platform entry
points.

Phrase selection and the zero-configuration **Translation shortcut** (issue #87)
are proved at the same levels. `translationShortcut` is a pure transform in
`core` — Google Translate's page for a phrase, built from the book's language and
the pack's target language — so `TranslationShortcutTest` pins its URL, its
space and non-ASCII encoding, the auto-detect fallback and the pair it cannot
address, in `core/commonTest`. The reader's long-press drag is split from the
page: `PhraseSelectionTest` pins the pure range-to-phrase math, `ReaderTextTest`
pins that a selected phrase washes every word it covers, and the gesture itself
is driven through `ReaderScreenSemanticsTest` on both lanes. The panel is
`PhraseLookupPanelSemanticsTest` on both lanes, and the whole loop — select a
phrase on page two, open the shortcut through the injected browser, close and
land on the same page — runs through `AppSemanticsTest` on both lanes. Robolectric's
glyph metrics are sub-pixel, so its text hit-testing does not extend the drag
reliably; the Android lane proves the gesture selects a phrase and opens the
panel, while the multi-word range is pinned by `PhraseSelectionTest` and the
desktop lane. The shortcut needs no **LLM provider** and no key — it is a plain
outbound URL the platform browser opens, never an embedded or scraped service.

The pronunciation control (issue #21) is proved at its own levels. The desktop
binding's outcome mapping — speak a word, report a language with no installed
voice as `NoVoice`, and an engine that fails or will not start as `Unavailable` —
and the per-OS voice-listing parsers are `core/jvmTest` (`JvmPronouncerTest`,
`SpeechHostTest`). The Android engine is a Robolectric host test in
`core/androidHostTest` (`AndroidPronouncerHostTest`): it drives the platform
`TextToSpeech` on a simulated runtime, where an installed language speaks and a
language with no voice is an honest message, so the two clients cannot disagree
about the outcome. The panel's Listen control and the messages it renders are
`app/desktopTest`, and `AppSemanticsTest` speaks a tapped word through
`testkit`'s `FakePronouncer`, covering the no-voice and no-engine messages.

The secret store (issue #24; ADR-0021) is proved at its own levels. The
`SecretStore` seam is a contract in `testkit` — `SecretStoreContract` with the
`InMemorySecretStore` fake — run in `core/commonTest`, against the desktop store
over an injected `SecretKeychain` in `core/jvmTest`, and against the Android
store over a reversible `SecretCipher` in `core/androidHostTest`, so the three
cannot differ. The Android Keystore cannot run on the host JVM, so `SecretCipher`
is the Robolectric seam and `KeystoreCipher` is the first-party production
cipher; `AndroidSecretStoreHostTest` also proves the store reports an honest
message where the Keystore is unreachable. `SecretStoreIsolationTest` pins that a
stored secret is absent from the vault and its export and that an unavailable
outcome carries no secret, the desktop test adds the same vault-export proof,
and `JvmSecretStoreTest` pins the fail-closed gate — a store with no OS keychain
reports unavailable rather than falling back to a key in the clear. The masked
input, `SecretField`, is proved by `SecretFieldSemanticsTest` on the desktop lane
and its same-named Android twin, and `AppEnvironment` wires the platform store
through the platform entry points.

Connecting an **LLM provider** (issue #88; ADR-0022, ADR-0023) is proved at its own
levels. The provider catalogue and configuration are pure data, so `LlmProviderTest`
in `core/commonTest` pins that every remote preset is HTTPS, that a custom base URL is
trimmed and resolves, and that a configuration is complete only with a base URL and a
model. The status-to-outcome
mapping is the pure `connectionResultFor`, pinned by `LlmConnectionResultTest`, so both
clients report a provider's answer the same way and no message can quote the key. The
`LlmClient` provider-adapter seam is a contract in `testkit` — `LlmClientContract` with
the `FakeLlmClient` fake — run in `core/commonTest`, and the `LlmSettingsStore` seam is
a contract there too — `LlmSettingsStoreContract` with the `InMemoryLlmSettingsStore`
fake — run in `core/commonTest` against the fake and against the JSON document store
over an in-memory filesystem, and in `core/jvmTest` against the same store over a real
directory (`JsonLlmSettingsStoreDirectoryTest`), so the two implementations cannot
drift; `JsonLlmSettingsStoreTest` pins that a corrupt document or an unknown preset
reads as "not configured". The OpenAI-compatible transport is
`OpenAiCompatibleLlmClientTest` in `core/jvmTest`, a request to a local `HttpServer`
that pins the bearer key, the model, the `{baseUrl}/chat/completions` path, the status
mapping and that a dead transport is an inline failure rather than a thrown exception,
and the header policy it proves by value (`llmRequestHeaders`): a named `User-Agent`,
no `Authorization` for a keyless provider, and the `x-opencode-session` that OpenCode
Go requires — without which it answers `MissingSessionID` — sent to the OpenCode
gateways alone. A provider's error body is parsed only for its machine identifier
(`errorIdentifier`) and classified against a fixed allowlist (`connectionResult`), so
no provider-authored text — and therefore no echoed key — ever reaches a message;
`LlmConnectionResultTest` pins the allowlist, the status fallback, and that an unknown
identifier cannot be rendered. No provider and no real key is involved, as the pack
downloader's lane is. The state
holder — load, choose one provider, save app-privately while the key goes to the
`SecretStore`, test off the UI thread, report inline — is `ProviderControllerTest` in
`app/desktopTest`, driven by `kotlinx-coroutines-test`'s virtual time. The section's
controls are `SettingsScreenSemanticsTest` on both lanes, and the whole loop — reach
settings, choose a provider, type a key, test the connection and see it answered — runs
through `AppSemanticsTest` on both lanes. Android excludes the local Ollama preset,
which only a desktop can reach (ADR-0022), through the provider list the platform
wires.

The streamed phrase translation (issue #89; ADR-0022) is proved at its own
levels. The prompt is built in `core`, so `LlmTranslationPromptTest` in
`core/commonTest` pins that the selection and its containing sentence reach the
model, that the disclosed context is exactly the user message sent, and that it
is capped. The streaming half of the `LlmClient` seam joins the same contract:
`LlmClientContract.translationCases()` — the deltas in order, a failure as an
event rather than a throw, and the request reaching the adapter unchanged — runs
against `FakeLlmClient` in `core/commonTest`. The OpenAI-compatible transport's
server-sent-event parsing is pinned in `OpenAiCompatibleLlmClientTest`
(`core/jvmTest`): a streamed answer, the `stream: true` request, a `4xx` and a
mid-stream error classified from the machine identifier alone, a dead transport
as an inline event, and `streamEvent`'s frame shapes. The state holder — read the
active provider and its key from the `SecretStore`, stream the deltas, disclose
the context, report a failure inline, cancel on close, and fall back to the
**Translation shortcut** without a key — is `PhraseTranslationControllerTest` in
`app/desktopTest`, driven by virtual time. The panel is
`PhraseLookupPanelSemanticsTest` on both lanes — the shortcut with no provider,
the non-blocking prompt beside it, the streamed text with its disclosure
collapsed until asked for, the inline failure and the empty answer — and the
whole loop — select a phrase,
watch the translation stream in with the provider reachable directly, close and
find the reader on the same page — runs through `AppSemanticsTest` on both
lanes. Closing the panel cancels the collecting coroutine, and the transport
closes the socket as the flow is cancelled, so the stream stops.

The settings screen's vault export/import (issue #20) is proved at the same
levels. The state holder that runs the transfer — the export's bytes handed to
the save action, an import restoring the vault, a cancelled file dialog as a
no-op, and a bad or unreadable file becoming an honest message rather than a
crash — is a behaviour over the in-memory vault in
`app/desktopTest/.../settings/VaultTransferControllerTest`, driven by
`kotlinx-coroutines-test`'s virtual time. The screen's sections and its
export/import controls are UI semantics in `SettingsScreenSemanticsTest`, and the
same screen is re-proved on a simulated Android runtime in
`app/androidUnitTest/.../settings/SettingsScreenSemanticsTest` (issue #77) — the
dictionary status and its download, the way to attribution, and the vault
section's export/import controls — so the screen is proved on both lanes, as the
parity rule requires (issue #73). The
whole loop — export to the injected save action, import from the injected picker,
and the library reloading the restored records rather than showing its stale list
(`LibraryController.refresh`) — runs through `AppVaultSemanticsTest` against the
real vault-backed `VaultBookLibrary`, so the test proves what the user then sees;
its same-named `app/androidUnitTest` twin (issue #79) re-proves the loop on the
runtime Android uses.
The desktop wiring that roots the transfer at the same directory as the library
is `DesktopVaultTest`; the modal Swing dialogs and the Android `CreateDocument`
and `OpenDocument` glue sit behind the coverage exclusion, like the rest of the
platform entry points.

Saving a word and setting its mastery (issue #22) is proved at the same levels.
The vocabulary entry is a domain fact, so `VaultVocabularyTest` in
`core/commonTest` proves it over the in-memory vault — a save keyed by the word's
identity, an inflection updating its lemma's entry rather than adding a second,
a save replacing an earlier one, and an entry surviving a "restart" over the same
vault — and `VocabularyRecordPropertyTest` pins the record's serialise/parse
round-trip and its deterministic, filename-safe id for arbitrary text. The
context-sentence cut is a property in `core/commonTest/.../ContextSentenceTest`.
`MasteryLookup` is now keyed by `WordKey`, so the reader's own layers are pinned
by `ReaderTextTest`: a lemma lookup collapses an inflection onto the lemma's key,
and a tap carries the sentence the word was found in. The reader's live state —
load once, an unsaved word is unknown, a save bumps the revision that recolours,
and a previous session's save is present at startup — is a `VocabularyControllerTest`
in `app/desktopTest`, and the panel's Save button and level chips are
`WordLookupPanelSemanticsTest`. The whole loop — tap a word, Save it with its
context and level, change the level afterwards, and find an earlier session's
entry already saved — runs through `AppSemanticsTest` against the in-memory
vocabulary, including that saving an inflected form does not create a second
entry; its same-named `app/androidUnitTest` twin (issue #79) re-proves the save
loop on the runtime Android uses. The translation a save keeps is a pure transform,
`VocabularySaveTest`; the desktop wiring that saves to a real directory and
reloads it is `DesktopLibraryTest`, which also proves the whole-vault export
carries the saved word — the vocabulary is a vault record, so it is portable
with no separate export. The selector's chips carry their level's colour (the
filled current one, the others ringed) so the reader's text colours read
straight off the panel; the mapping reuses the pinned mastery palette and
`contentColorOn` is unit-tested in `MasteryPaletteTest`.

Browsing, searching and deleting the saved words (issue #23) is proved at the
same levels. The delete is a domain fact — the entry's vault record is removed,
so the word is gone on the next read, including after a restart — so
`VaultVocabularyTest` in `core/commonTest` proves a deleted word has no entry,
its record is gone from the vault, and deleting an unsaved word is not an error.
The reader's live state gains `all()` and `delete()`, so `VocabularyControllerTest`
proves a delete clears the word's mastery and bumps the revision the reader
recolours on, and that the deletion survives a "restart" over the vault. The
search is a pure transform, `VocabularySearchTest`: a blank query keeps every
entry, and a word is found by its spelling, its lemma, its translation or its
context sentence, case-insensitively. The list's surface is
`VocabularyScreenSemanticsTest` in `app/desktopTest` — the instructional empty
state, an entry's word, translation, context sentence and mastery, the search
field raising the query, the no-match message, the delete action, and going back.
The whole loop runs through `AppSemanticsTest` against the in-memory vocabulary:
a word saved from the reader appears in the list with its details, the list can
be searched, and deleting the word returns it to unsaved in the reader — so the
list and the text cannot disagree. The desktop wiring that deletes over a real
directory is `DesktopLibraryTest`. The list is reached from the library's header,
not the navigation pattern the UX spec draws; that shell is separate work. The
list's `LazyColumn` key is a `String` projected from the word's identity, and
`VocabularyScreenSemanticsTest` renders the list under a saveable-state registry
as strict as Android's, so a key the platform cannot put in a `Bundle` fails on
the desktop lane too (issue #69); `VocabularyRowKeyTest` pins the projection's
`String` type and its uniqueness. The clean guard is the Android host lane's
`VocabularyScreenSemanticsTest`, which renders the same list on a simulated Android
runtime (issue #71).

The mastery filter (issue #66) is proved the same way. `filterByMastery` is a
pure transform, so `VocabularyFilterTest` proves a level keeps only its entries,
`null` keeps them all, and the filter composes with the search. The chips
themselves are the shared `MasteryChip` (in `app`'s reader package), so the
list's filter and the lookup panel's level selector cannot drift;
`VocabularyScreenSemanticsTest` proves a chip raises its level, the all-levels
chip clears it, and a filter that matches nothing says so, and `AppSemanticsTest`
filters the list to a level and finds only that level's word.

## Naming and placement

A few conventions the compiler cannot check are asserted by the `architecture`
suite (ticket #9), so a drift fails `check` rather than the next review:

- A Kotlin file lives at `<module>/src/<sourceSet>/kotlin/<package path>.kt`.
- A package matches the directory it sits in, and sits under its module's package
  root (`core` under `app.lekto.core`, an integration under
  `app.lekto.integrations.<name>`, the application under `app.lekto`).
- A test class lives in a test source set and its file is named `<Class>Test.kt`;
  a production file is never named `*Test.kt`. Shared fakes and fixtures belong in
  `testkit`, not in a test source set, so a test source set holds tests only.
- A desktop `*SemanticsTest` has a same-named twin under `app/src/androidUnitTest`
  — same package, different lane — so a screen proved on the desktop is proved on
  Android too; `check` fails, naming the missing twin, unless the screen is on
  `LektoArchitecture.uiTestParityAllowlist`, the burn-down list of screens whose
  Android twin has not landed yet (issue #73). The list is empty now; the last two
  twins — the whole-app `AppSemanticsTest` and `AppVaultSemanticsTest` — landed
  with issue #79.

## Reproducing a failure

A property failure prints the seed it used and writes it to
`~/.kotest/seeds/<spec>/<testname>`. The next run replays that seed
automatically until the test passes; a seed passed to a `PropTestConfig` takes
precedence. `HarnessSelfTest` pins both guarantees: a broken assertion fails, and
a failing property reports a replayable seed.

A KMP build can silently compile zero tests, or run them under a runner that
never discovers them, and still report success. `HarnessSelfTest` exercises each
capability above so the wiring cannot regress unnoticed.
