# Development

How Lekto is built, tested and gated. Platform packaging is in
[`platforms.md`](platforms.md); the dictionary pipeline is in
[`dictionary-pipeline.md`](dictionary-pipeline.md).

## Toolchain

The JDK, Gradle, Kotlin, AGP, Compose and dependency versions are pinned in
`gradle/libs.versions.toml` and the wrapper. The wrapper provisions Gradle and
the JDK; a clean checkout needs only a JVM. Change a version in the catalog.

## Commands

```sh
./gradlew check                          # landing gate: build, tests, quality gates
./gradlew :core:jvmTest --tests "app.lekto.core.epub.XhtmlBlocksTest"
./gradlew :app:desktopTest --tests "app.lekto.reader.ReaderTextTest"
./gradlew :core:jvmTest :app:desktopTest :architecture:test   # module pass
./gradlew :core:jvmTest --rerun          # force a re-run
./gradlew check -Plekto.android=false    # JVM/desktop only, no Android SDK
./gradlew checkDependencyLicences        # licence gate, separate task
./gradlew koverXmlReport                 # merged coverage report
./gradlew ktlintFormat                   # format the tree in place
```

`check` builds every module, runs every JVM suite and enforces ktlint and detekt.
The Android host lanes and `:app:lint` join when an Android SDK is present. Run
`check` once on the final diff; use the targeted commands above while iterating,
and rerun only the failing task after a failure.

Two Gradle builds on one machine start two daemons, each with its own heap. Run
one build at a time across worktrees and do not enable `--parallel`.

## Quality gates

`./gradlew check` fails the build on any formatting or static-analysis violation;
neither tool is allowed to degrade to a warning. The gates are wired in the root
`build.gradle.kts` and apply to every project, including the root
`*.gradle.kts` scripts.

- **ktlint** (MIT) formats and lints every Kotlin file. `./gradlew ktlintFormat`
  rewrites the tree in place; `./gradlew ktlintCheck` only reports. The engine is
  pinned in the version catalog so the formatter does not drift under the Gradle
  plugin. It writes one Checkstyle XML report per source set under
  `build/reports/ktlint/`; the build merges them for SonarCloud.
- **detekt** (Apache-2.0) enforces `config/detekt/detekt.yml`, layered on top of
  detekt's defaults. The tuned rules bound cyclomatic and cognitive complexity,
  function and class size, ban dead code and reject orphaned
  `TODO:`/`FIXME:`/`STOPSHIP:` markers. The Analysis-API rules need a classpath,
  so they run once per Kotlin compilation.

The code style is declared once in the committed **`.editorconfig`**
(`ktlint_code_style = intellij_idea`); editors that understand EditorConfig pick
it up with no further setup, and ktlint reads it as its source of truth. Both
tools are AGPL-compatible (ADR-0011).

### Architecture tests

The `architecture` module turns the module boundaries and naming conventions into
tests, so a violation fails `check` instead of waiting for review. The rules and
allowed arrows are in [`ARCHITECTURE.md`](../ARCHITECTURE.md); the naming and
placement rules are in [naming and placement](#naming-and-placement).
The suite adds no dependency: it reads `settings.gradle.kts`, each module's
`build.gradle.kts`, every Kotlin file and the dependency manifests into a pure
model. ADR-0013 records the decision.

### Dependency licences

`./gradlew checkDependencyLicences` reads every dependency's declared licence and
fails on one that is not allowed. The policy, the test-only exceptions and the
hand-reviewed overrides live in `config/dependency-licences.txt`; the verdict per
dependency is written to `build/reports/dependency-licences.txt`. It is a task of
its own, not part of `check`, because it resolves dependency metadata at execution
time, which the configuration cache cannot store. See
[`dependencies.md`](dependencies.md).

The gate reads Gradle metadata, so it does not see a copy of third-party work
checked into the tree. The icon set is such a copy: it adds no coordinate, and
its ISC and Feather/MIT notices live in `THIRD-PARTY-NOTICES.md`.

### Coverage and the quality gate

Coverage is measured with **Kover** (Apache-2.0), JetBrains' Kotlin coverage
engine and the KMP-native alternative to JaCoCo. The root project is Kover's
*merging* module, so one report covers `core`, `integrations/webdav` and `app`.
The `koverXmlReport` task runs the JVM tests it needs and writes a
JaCoCo-compatible report to
`build/reports/kover/report.xml`. Applying Kover instruments the JVM test runs, so
`./gradlew check` measures coverage as it goes; the report is written only when
the report task is invoked. `testkit`, `tools/dictionaries` and `architecture` are
not aggregated — the first two never ship or are standalone, and the last is test
scaffolding.

**SonarCloud** ingests that coverage report plus the ktlint and detekt findings,
then evaluates its **quality gate on new code** — new-code coverage, duplicated
lines and code smells. The `fast` CI job generates all three reports and uploads
them; the `sonar` job downloads them and runs the scan, so the build and its tests
happen once.

`sonar-project.properties` names the merged reports;
`verifySonarReportPaths` checks them in `check`. `sonar.qualitygate.wait=true`
makes the scan wait for the gate, and the job blocks a pull request once branch
protection on `main` requires it.

The scanner runs as a GitHub action, not a Gradle plugin, so nothing SonarCloud
owns enters the build; Kover is the only new build dependency (AGPL-compatible,
ADR-0011). A fork carries no `SONAR_TOKEN`, so the scan is skipped there and only
the reports are produced. ADR-0012 records the decision.

## What runs where

| Suite | Source set | Task | Needs |
| ----- | ---------- | ---- | ----- |
| Domain + properties | `core/commonTest` | `:core:jvmTest` | a JVM |
| Android host tests | `core/androidHostTest` | `:core:testAndroidHostTest` | a JVM + an Android SDK |
| UI semantics | `app/desktopTest` | `:app:desktopTest` | a JVM |
| UI on Android | `app/androidUnitTest` | `:app:testDebugUnitTest` | a JVM + an Android SDK |
| UI goldens | `app/desktopTest` | `:app:desktopTest` with `-Proborazzi.test.verify=true`, or `:app:verifyRoborazziDesktop` | a JVM |
| Architecture | `architecture/src/test` | `:architecture:test` | a JVM |
| Dictionary pack | `tools/dictionaries/src/test` | `:tools:dictionaries:test` | a JVM |
| WebDAV integration | `integrations/webdav/src/jvmTest` | `:integrations:webdav:jvmTest` | a JVM; the container cases add Docker and skip without |
| WebDAV on Android | `integrations/webdav/src/androidHostTest` | `:integrations:webdav:testAndroidHostTest` | a JVM + an Android SDK |
| Android instrumented | `app/androidInstrumentedTest` | `:app:connectedCheck` | an emulator (nightly) |

The Android host lanes require the SDK. Without one, the modules build as JVM-only
and those lanes are skipped; `check` still runs the rest.

## UI lanes

The UI-semantics suite lives in `desktopTest`, not `commonTest`, because the
Compose Multiplatform common test API cannot run under Android's local host test
configuration. The same screens run under Robolectric in `app/androidUnitTest`
(`:app:testDebugUnitTest`, wired into `check`), so an Android-only constraint is
not left to the desktop lane.

Every `*SemanticsTest.kt` under `app/src/desktopTest` has a same-named twin under
`app/src/androidUnitTest`; the architecture suite enforces the pair. The
`LektoArchitecture.uiTestParityAllowlist` names screens whose twin has not landed
yet; it is empty.

## Android host lane

Three host lanes run platform code on a simulated Android runtime through
Robolectric, on the host JVM and with no emulator:

- `core/androidHostTest` (`:core:testAndroidHostTest`);
- `app/androidUnitTest` (`:app:testDebugUnitTest`);
- `integrations/webdav/androidHostTest`
  (`:integrations:webdav:testAndroidHostTest`).

All three are part of `check` when the Android SDK is present, and the `fast` CI
lane runs them. The app's release unit-test variant is disabled: the Compose test
rule's host Activity is debug-only.

`core`'s lane drives the whole `EpubParser` pipeline through Android's own XML
parser (`org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl`) and imports
a real book (`pg1952.epub`). Robolectric does not shadow `javax.*`, so the test
instantiates Android's factory explicitly; the host JDK's Xerces would hide the
difference. The lane is the platform guard for the parser hardening:
`XmlHardening` must tolerate a feature the platform rejects, and a plain
`setFeature` fails this lane.

The `app` lane renders the Compose UI on the runtime Android uses. It catches
constraints the desktop lane cannot, such as a `LazyColumn` key that must be
Bundle-saveable. The reader page and the whole application's flows
(`AppSemanticsTest`, `AppVaultSemanticsTest`) run there through the same `App()`
composable; taps are taken from the text layout (`GetTextLayoutResult`), not from
guessed pixels.

Robolectric (MIT) and JUnit 4 (EPL-1.0) are test-scope and never linked into the
shipped application. The first host run downloads Robolectric's `android-all`
runtime from Maven Central; later runs reuse it.

## Golden images

The goldens are Roborazzi images recorded from `app/desktopTest` and committed
under `app/src/desktopTest/goldens/`. The desktop target renders with the host's
Skia, so the goldens are deliberately text-free: a golden that renders text would
depend on the fonts installed where it was recorded and would not verify on
another machine. The theme, mastery, reader, library, lookup, vocabulary,
settings, languages, guide and launcher sheets are the committed set; that
directory is the catalogue.

Record or update them with `./gradlew :app:recordRoborazziDesktop`; verify with
`./gradlew :app:verifyRoborazziDesktop`. The CI `fast` lane instead passes
`-Proborazzi.test.verify=true` to `check`, so the goldens are compared in the
`desktopTest` run `check` already triggers. `app/build.gradle.kts` sets
`separateOutputDirs`, so the record and verify tasks cannot race.

A golden pins a colour; `MasteryPaletteContrastTest` pins its readability. The
mastery ramps keep a 4.5:1 floor against the paper and a 1.25:1 floor between any
two coloured levels, on every **App theme**.

## WebDAV integration lane

`integrations/webdav/src/jvmTest` runs the `SyncTarget` contract against:

- an Apache `mod_dav` server through Testcontainers. The image is pinned by
  digest. The class is annotated `@Testcontainers(disabledWithoutDocker = true)`,
  so a machine or runner without Docker skips it instead of failing.
- `testkit`'s `FakeWebDavServer`, an in-process server that speaks `MKCOL`,
  `PROPFIND`, `GET`, `HEAD`, `PUT`, `DELETE`, Basic auth, ETags and the
  conditional headers (`If-None-Match`, `If-Match`, and the DAV `If`). It also
  covers the driver's fallbacks: a `PUT` without an ETag forces `HEAD`; a
  configurable `MKCOL` status pins collection existence; a mode refuses the DAV
  `If` header while honouring `If-Match`; and the `GET` count pins the listing's
  re-read avoidance.

The Android host lane runs the same contract through the OkHttp transport against
the same fake, under Robolectric. The fake's `com.sun.net.httpserver` is a
host-JVM API; because Robolectric runs on the host JVM, the fake stays one
implementation.

The nightly instrumented lane runs `AppInstrumentedTest` on an emulator: the app
launches on the real Android runtime, opens the Library and navigates to
Vocabulary. It stays off the critical path. Device-only integrations (SAF, TTS,
Keystore) are not covered by an instrumented test.

## The framework

- **`kotlin.test`** — assertions everywhere, as the baseline. On the JVM it maps
  to JUnit 5.
- **Kotest** — the spec style (`FunSpec`), matchers (`shouldBe`), and property
  testing (`forAll`/`checkAll`). It runs on the JUnit Platform.
- **Turbine** — flow assertions (`flow.test { … }`) that fail instead of hanging
  and assert the flow is complete.
- **`kotlinx-coroutines-test`** — `runTest`, `TestScope` and virtual time.

All four are test-scope. JUnit Platform / Jupiter are EPL-2.0 and not
GPL-compatible; they are safe only because they are never linked into the shipped
app. Keep every JUnit type out of production code. See
[`dependencies.md`](dependencies.md).

## Deterministic seams

Time, identifiers and randomness are the only ways non-determinism enters the
domain. Each is an injected parameter, never an ambient global:

| Seam | Production | Test (in `testkit`) |
| ---- | ---------- | ------------------- |
| Time | `Clock.System` | `TestClock` |
| Identifiers | `UuidIdGenerator` | `SequentialIdGenerator` |
| Randomness | `Random.Default` | `Random(seed)` |

`app.lekto.core.Seams` bundles the three so they can be threaded as one value;
`Seams.system()` is the production wiring and `deterministicSeams()` is the test
wiring. The domain never reads a wall clock or a global RNG directly.

Dispatchers are the same kind of seam. The sync engine takes none of its own:
`sync` is a `suspend` function that runs on the caller's context, so
`kotlinx-coroutines-test` supplies the virtual time. Code that must move work off
the caller's thread takes a `CoroutineDispatcher` parameter.

A test must also wait for the work it means to observe. A surface that renders a
provisional state lets a test run ahead of it; the suite waits for the settled
state with `AWAIT_DISPATCHED_WORK_MILLIS`, and `testkit`'s fakes can hold work in
flight (`InMemoryLanguageCatalog.whileInFlight`) so the race is proved on an idle
machine.

The window insets are a seam of the same kind: `App` takes a `WindowInsets` that
defaults to `WindowInsets.safeDrawing`. `AppWindowInsetsSemanticsTest` injects a
known strip and asserts every screen's chrome clears it, on both lanes.

## Test levels

Every change lands with a test at the level its surface demands.

| Change surface | Level | Where |
| -------------- | ----- | ----- |
| A domain invariant — merge, identity, tokenisation, serialise/parse | property test over generated data | `core/commonTest` |
| A seam — `SyncTarget`, `VaultStore`, a provider adapter | contract suite + in-memory fake | `testkit/commonMain` |
| A driver under `integrations/` | that contract suite, plus a containerised run and the Android host lane | `integrations/webdav/jvmTest`, `integrations/webdav/androidHostTest` |
| A parser — EPUB, TXT, PDF | golden over a generated corpus, plus one awkward real fixture | `jvmTest` |
| UI behaviour in `app` | Compose UI-semantics test on desktop and the same-named Android host twin | `app/desktopTest`, `app/androidUnitTest` |
| A visual or layout change | + screenshot golden | `app`, on the PR lane |
| Android platform glue | Robolectric host test | `androidHostTest` (core), `androidUnitTest` (app) |
| An architectural boundary or naming convention | architecture rule test, plus a synthetic violation | `architecture/src/test` |
| A bug | regression test at the seam where it occurs | wherever that seam lives |
| The dictionary-pack transform | canonical golden + built-twice byte hash | `tools/dictionaries/src/test` |
| A generated, committed asset (icons) | contract test over the generated output | `app/desktopTest` |

## Naming and placement

The `architecture` suite asserts conventions the compiler cannot check, so drift
fails `check` rather than the next review:

- A Kotlin file lives at `<module>/src/<sourceSet>/kotlin/<package path>.kt`.
- A package matches its directory and sits under its module's package root
  (`core` under `app.lekto.core`, an integration under
  `app.lekto.integrations.<name>`, the application under `app.lekto`).
- A test class lives in a test source set and its file is named `<Class>Test.kt`;
  a production file is never named `*Test.kt`. Shared fakes and fixtures belong in
  `testkit`, not in a test source set.
- A desktop `*SemanticsTest` has a same-named twin under `app/src/androidUnitTest`,
  same package, different lane.
- Every clickable control carries the hand cursor.
- Every manifest the repository ships has a `.github/dependabot.yml` entry.

`tools/dictionaries` is standalone and cannot depend on `testkit`, so its shared
fixture lives beside its tests.

## Test data

The parser fixtures live under `core/src/commonTest/resources` and are read
through `TestResources` in `testkit`: a generated awkward EPUB, a real Project
Gutenberg book (`pg1952.epub`), and an awkward TXT
(`les-trois-mousquetaires.txt`). The extraction goldens under
`resources/golden/` pin the parser's output; a change that alters the text fails
until the golden is deliberately updated.

The reader's UI tests drive the word layer with `WhitespaceTextSegmenter` in
`testkit` — a deterministic letter/digit splitter — so they do not depend on the
machine's ICU dictionaries. Production segmentation stays behind the
`TextSegmenter` seam (`IcuTextSegmenter`, the ICU4J implementation). The
platform-backed implementations live in `core/src/jvmSharedMain`, shared by the
JVM and Android targets.

## Reproducing a failure

A property failure prints the seed it used and writes it to
`~/.kotest/seeds/<spec>/<testname>`. The next run replays that seed automatically
until the test passes; a seed passed to a `PropTestConfig` takes precedence.
`HarnessSelfTest` pins both guarantees.

A KMP build can silently compile zero tests, or run them under a runner that
never discovers them, and still report success. `HarnessSelfTest` exercises each
capability so the wiring cannot regress unnoticed.

## CI lanes

The workflows live in `.github/workflows/`. Every pull request, and every push to
`main`, runs the fast lane and the licence gate; the `sonar` lane reuses the fast
lane's reports instead of building a second time. The emulator lane is nightly and
never on the critical path.

A push to a feature branch starts no workflow of its own: the pull request runs
the same commit, so an unfiltered `push` trigger would double every pipeline. The
same filter keeps the lanes off a release tag, which `release.yml` covers.

| Lane | Trigger | Command |
| ---- | ------- | ------- |
| `fast` | pull request, push to `main` | `./gradlew check koverXmlReport -Proborazzi.test.verify=true`, then `./gradlew :app:assembleRelease -Plekto.android=true` unsigned |
| `licences` | pull request, push to `main` | `./gradlew checkDependencyLicences --no-configuration-cache` |
| `sonar` | pull request, push to `main` | the SonarCloud scan over the reports `fast` uploaded |
| `guide` | pull request; push to `main` when the guide changed | `npm ci`, `npm test`, `npm run check:licences` and `npm run build` in `guide/` |
| `release` (`release.yml`) | version tag, manual | jpackage on Windows and both macOS architectures, `createDistributable` + `appimage.sh` on Linux, the signed Android APK on Linux, then `gh release create` |
| `instrumented` (`nightly.yml`) | schedule, manual | `./gradlew :app:connectedCheck` on an emulator |

`fast`, `licences`, `sonar` and `guide` gate merging: branch protection on `main`
must require them. `fast` builds and tests the change once and uploads the merged
coverage XML and the merged ktlint and detekt reports (`sonar-reports`); `sonar`
downloads them and only runs the scanner, so no lane repeats the build. The
`sonar` lane needs the `SONAR_TOKEN` repository secret; a fork pull request has no
secret, so the scan is skipped there.

`fast` runs every JVM suite, which includes the WebDAV driver's container cases
and the committed UI goldens. The container cases need Docker, present on GitHub's
runners; where it is absent they skip rather than fail, so the lane still passes.
The goldens are compared inside the `desktopTest` run `check` already triggers
(`-Proborazzi.test.verify=true`); the `verifyRoborazziDesktop` task would run that
whole suite a second time. The lane also runs the Android host suites —
`core`'s `:core:testAndroidHostTest`, the app's `:app:testDebugUnitTest` and
`integrations/webdav`'s `:integrations:webdav:testAndroidHostTest` — which need
the Android SDK the GitHub runners carry, and no emulator.

The `guide` lane builds the end-user guide and its npm licence gate on every pull
request, and deploys the site to GitHub Pages only on a push to `main` that
touches the guide. The `instrumented` lane is the only one that needs an
emulator; it is scheduled, so it never slows a change, and it runs the app's
launch smoke test (`app/androidInstrumentedTest`).

`tools/dictionaries` builds and publishes by the reusable dictionary-data
workflow. The tool's fast unit tests run in `check`; the publication workflow does
not gate application CI. The architecture suite pins the workflow's four layers,
its sample validation and its data-repository split. See
[`dictionary-pipeline.md`](dictionary-pipeline.md).

## Prerequisites

- **A JVM to run the wrapper.** Everything else (Gradle, the pinned JDK) is
  provisioned by the build.
- **The Android SDK — optional.** It is needed for the Android client. The build
  detects an SDK in `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `local.properties`
  (`sdk.dir`) or `~/Android/Sdk`. When one is present the Android targets
  activate — the `app` client and `core`'s Android target, which brings the
  `androidHostTest` Robolectric lane into `check` — and when none is present the
  project still builds and tests as a desktop/JVM project. Force either behaviour
  with `-Plekto.android=true` or `-Plekto.android=false`.
- **The packaging tools — only to build an installer.** jpackage ships with the
  pinned JDK, but Windows installers need WiX; macOS images use the tools the
  operating system carries. The Linux AppImage needs
  `tools/packaging/appimage.sh` and `appimagetool`, which the release lane
  downloads at the pinned version and digest. `./gradlew check` needs none of
  them, and jpackage cannot cross-compile. See [`platforms.md`](platforms.md).
