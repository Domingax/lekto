# Build and test

The repository is a Kotlin Multiplatform project. This document records the
pinned toolchain, the one command that builds and tests everything, and the
platform-specific extras.

## Pinned toolchain

Every version below is pinned so that an agent and a human get the same build.
The wrapper and the version catalog are the source of truth; do not change one
without the other.

| Component            | Version   | Declared in                                     |
| -------------------- | --------- | ----------------------------------------------- |
| JDK (toolchain)      | 21        | `gradle/libs.versions.toml` (`jdk`)             |
| Gradle               | 8.14.4    | `gradle/wrapper/gradle-wrapper.properties`      |
| Kotlin               | 2.4.20    | `gradle/libs.versions.toml` (`kotlin`)          |
| Android Gradle Plugin| 8.13.2    | `gradle/libs.versions.toml` (`agp`)             |
| Compose Multiplatform| 1.11.1    | `gradle/libs.versions.toml` (`compose-multiplatform`) |
| Compose Material 3   | 1.11.0-alpha07 | `gradle/libs.versions.toml` (`compose-material3`) |
| Android compileSdk   | 36        | `gradle/libs.versions.toml` (`android-compileSdk`) |
| Kotest (test)        | 6.2.5     | `gradle/libs.versions.toml` (`kotest`)          |
| Turbine (test)       | 1.2.1     | `gradle/libs.versions.toml` (`turbine`)         |
| Coroutines           | 1.11.0    | `gradle/libs.versions.toml` (`kotlinx-coroutines`) |
| Roborazzi (test)     | 1.75.0    | `gradle/libs.versions.toml` (`roborazzi`)       |
| Testcontainers (test)| 2.0.5     | `gradle/libs.versions.toml` (`testcontainers`)  |
| JUnit Jupiter (test) | 5.13.4    | `gradle/libs.versions.toml` (`junit-jupiter`)   |
| Robolectric (test)   | 4.16.1    | `gradle/libs.versions.toml` (`robolectric`)      |
| JUnit 4 (test)       | 4.13.2    | `gradle/libs.versions.toml` (`junit4`)          |
| ktlint               | 1.8.0     | `gradle/libs.versions.toml` (`ktlint`)          |
| ktlint Gradle plugin | 14.2.0    | `gradle/libs.versions.toml` (`ktlint-gradle`)   |
| detekt               | 2.0.0-alpha.6 | `gradle/libs.versions.toml` (`detekt`)       |
| Kover (coverage)     | 0.9.9     | `gradle/libs.versions.toml` (`kover`)           |
| ICU4J (segmentation) | 78.3      | `gradle/libs.versions.toml` (`icu4j`)           |
| jsoup (XHTML)        | 1.23.2    | `gradle/libs.versions.toml` (`jsoup`)           |
| kotlinx.serialization (vault) | 1.9.0 | `gradle/libs.versions.toml` (`kotlinx-serialization`) |
| sqlite-jdbc (dictionary pack) | 3.53.4.0 | `gradle/libs.versions.toml` (`sqlite-jdbc`) |
| KSafe (desktop secret storage) | 3.3.0 | `gradle/libs.versions.toml` (`ksafe`) |

Compose Material 3 versions independently of Compose Multiplatform, which is
why it carries its own pinned version. The JDK is pinned by *language version*:
any conforming 21 distribution is accepted, and the Foojay toolchain resolver
provisions one when none is present. The bytecode target is 17 for every module.

The Gradle wrapper is committed, so a clean checkout does not need Gradle
installed. The Foojay toolchain resolver provisions the pinned JDK if it is
missing, so a clean checkout does not need JDK 21 installed either — only a JVM
able to run the wrapper.

ICU4J and jsoup are the reader pipeline's production dependencies (ticket #10):
ICU4J segments text into words with dictionary-based CJK support, and jsoup
parses EPUB content documents leniently. ICU4J is Unicode-3.0 and jsoup MIT;
both are AGPL-compatible and recorded in `config/dependency-licences.txt`
(ADR-0011). See `docs/research/epub-to-tokens-spike.md`.

Robolectric (MIT) runs `core`'s and the app's platform code on a simulated
Android runtime on the JVM, with no emulator (tickets #49 and #71): it is
test-scope only, and its JUnit 4 runner (EPL-1.0) is likewise never conveyed.
`core`'s `androidHostTest` source set exists only when its Android target is
applied, under the KMP Android library plugin
(`com.android.kotlin.multiplatform.library`); the app, an Android application,
uses the classic `androidUnitTest` source set instead. See
`docs/testing.md#android-host-lane`.

kotlinx.serialization (Apache-2.0) is the vault's dependency (ticket #12): records
are one JSON file each (ADR-0003), so the domain encodes and decodes them with
`kotlinx-serialization-json`. It is AGPL-compatible; ADR-0014 records why the
format is a versioned JSON bundle and where the platform file seam sits. Version 2
of that format adds binary **attachments** — a book's original file — so the
import work (ticket #15) needed no new dependency: ADR-0016 records the decision,
and the parsed-text model serialises with the same library as a derived asset
(ADR-0005).

## The one command

```sh
./gradlew check
```

It builds every module, runs the JVM test suites — the domain suite in
`core`, the Android host suite in `core`'s `androidHostTest` (when an Android SDK
is present, ticket #49), the UI-semantics suite in `app` and the architecture
suite in `architecture` — and runs the quality gates (ktlint and detekt, below).
See `docs/testing.md` for the harness, the seams and how to reproduce a failure.
CI enforces the command on every push (ticket #7).

## Quality gates

`./gradlew check` fails the build on any formatting or static-analysis
violation; neither tool is allowed to degrade to a warning. The gates are wired
in the root `build.gradle.kts` and apply to every project, including the root
`*.gradle.kts` scripts.

- **ktlint** (MIT) formats and lints every Kotlin file.
  `./gradlew ktlintFormat` rewrites the tree in place;
  `./gradlew ktlintCheck` only reports. The engine is pinned in the version
  catalog so the formatter does not drift under the Gradle plugin. It writes one
  Checkstyle XML report per source set under `build/reports/ktlint/`; the build
  merges them for SonarCloud to ingest (below).
- **detekt** (Apache-2.0) enforces `config/detekt/detekt.yml`, layered on top
  of detekt's defaults. The tuned rules bound cyclomatic and cognitive
  complexity, function and class size, ban dead code (unused private functions,
  properties and variables, unreachable code) and reject orphaned
  `TODO:`/`FIXME:`/`STOPSHIP:` markers. The Analysis-API rules need a
  classpath, so they run once per Kotlin compilation — the only detekt tasks
  that carry one.

The code style is declared once in the committed **`.editorconfig`**
(`ktlint_code_style = intellij_idea`); editors that understand EditorConfig
pick it up with no further setup, and ktlint reads it as its source of truth.
Both tools are AGPL-compatible (ADR-0011).

### Architecture tests

The module boundaries and naming conventions are themselves tested, so a
violation fails `./gradlew check` instead of waiting for a human review
(ticket #9). The suite lives in the test-only `architecture` module — the rule
engine in `src/main`, its tests in `src/test`. It reads
`settings.gradle.kts`, each module's `build.gradle.kts` and every Kotlin file
into a pure model, then asserts: the module dependency graph (the domain depends
on no module, only the application may depend on an integration, nothing depends
on the application), import purity (the domain's shared sources name neither the
application, nor an integration, nor Android — its `androidMain` platform set may
name the Android API it backs; the testkit never reaches production), and
naming and placement (a package matches its directory, a source sits under its
module's package root, a test class is `*Test.kt` in a test source set, and a
desktop `*SemanticsTest` has a same-named Android host-lane twin — issue #73).

The rules are hand-rolled rather than Konsist or ArchUnit, so the suite adds no
dependency and can read the Gradle module graph, which a bytecode analyser
cannot; ADR-0013 records the decision. Each rule is proven against a deliberately
broken repository in `ArchitectureViolationTest`, and the real tree is asserted
by `LektoArchitectureTest` — a boundary can only be relaxed by editing
`LektoArchitecture.policies`, and a screen's missing Android twin only by removing
its path from `LektoArchitecture.uiTestParityAllowlist`.

### Dependency licences

`./gradlew checkDependencyLicences` reads every dependency's declared licence and
fails the build on one that is not AGPL-compatible (ADR-0011). The policy lives
in `config/dependency-licences.txt`: the allowed ids, the test-only exceptions
(EPL, for JUnit, which is never conveyed), and hand-reviewed overrides for the
components whose own POM declares no licence. It runs as its own task, not from
`check`, because it resolves dependency metadata at execution time — which the
configuration cache cannot store — so `check` stays cache-friendly and fast. A
licence-less dependency on a **production** classpath fails (it ships, so it must
be reviewed); on a **test** classpath it warns, so the override list records
decisions rather than gating every dependency bump. CI runs the task without the
configuration cache; see below.

### Coverage and the quality gate

Coverage is measured with **Kover** (Apache-2.0), JetBrains' Kotlin coverage
engine and the KMP-native alternative to JaCoCo. The root project is Kover's
*merging* module, so one report covers `core`, `integrations/webdav` and `app`:

```sh
./gradlew koverXmlReport
```

It runs the JVM tests it needs and writes a JaCoCo-compatible report to
`build/reports/kover/report.xml`. Applying Kover instruments the JVM test runs,
so `./gradlew check` measures coverage as it goes; the report is written only
when the report task is invoked. `testkit`, `tools/dictionaries` and
`architecture` are deliberately not aggregated — the first two never ship or are
standalone, and the last is test scaffolding.

**SonarCloud** is the static-analysis service. It ingests that coverage report
plus the ktlint and detekt findings, then evaluates its **quality gate on new
code** — new-code coverage, duplicated lines and code smells. The `sonar` CI job
generates all three reports and then scans (ticket #8); `sonar-project.properties`
tells the scanner where each one lives. SonarCloud's Kotlin importer takes a
comma-separated list of report **files** and does not expand wildcards, while
detekt and ktlint each write one report per Kotlin compilation or source set, so
`mergeDetektReports` and `mergeKtlintReports` fold each tool's reports into a
single Checkstyle XML under `build/reports/sonar/` for the scanner to read.
`verifySonarReportPaths` — part of `check` — fails if a configured path is missing
or hides behind a wildcard, so a misconfiguration trips the fast lane rather than
the sonar one. `sonar.qualitygate.wait=true` makes the scan wait for the gate and
fail the job when it fails. That job blocks a pull request once branch protection
on `main` requires it.

The scanner runs as a GitHub action, not a Gradle plugin, so nothing SonarCloud
owns enters the build; Kover is the only new build dependency (AGPL-compatible,
ADR-0011). A fork carries no `SONAR_TOKEN`, so the scan is skipped there and only
the reports are produced. `docs/adr/0012` records the decision.

#### The quality-gate drill

The gate is only trustworthy if a regression trips it. To reproduce, on a branch:

1. Add a public function with an obvious code smell and no test, e.g. a long,
   deeply nested function that nothing calls.
2. Open the pull request and watch the `sonar` lane: the scan waits for the gate,
   the gate fails on new-code coverage and on the new smell, and the job exits
   non-zero. `git revert` the commit and the lane goes green.

## CI lanes

The workflows live in `.github/workflows/`. Every push and pull request runs the
fast lane and the licence gate; pull requests also run the WebDAV and golden
lanes; the emulator lane is nightly and never on the critical path (ticket #7).

| Lane | Trigger | Command |
| ---- | ------- | ------- |
| `fast` | push, pull request | `./gradlew check -x :integrations:webdav:jvmTest` |
| `licences` | push, pull request | `./gradlew checkDependencyLicences --no-configuration-cache` |
| `sonar` | push, pull request | `./gradlew check koverXmlReport` then the SonarCloud scan |
| `webdav` | pull request | `./gradlew :integrations:webdav:jvmTest` |
| `golden` | pull request | `./gradlew :app:verifyRoborazziDesktop` |
| `guide` | pull request; push to `main` when the guide changed | `npm ci`, `npm test`, `npm run check:licences` and `npm run build` in `guide/` |
| `instrumented` (`nightly.yml`) | schedule, manual | `./gradlew :app:connectedCheck` on an emulator |

`fast`, `licences`, `sonar` and `guide` gate merging: branch protection on `main`
must require them. The `sonar` lane needs the `SONAR_TOKEN` repository secret; a fork
pull request has no secret, so the scan is skipped there and only the coverage and
linter reports are produced. The `webdav` lane needs Docker — present on GitHub's
runners — and skips cleanly where it is absent; the `fast` lane excludes its test
so the two do not overlap. The `fast` lane also runs the Android host suites —
`core`'s `:core:testAndroidHostTest` and the app's `:app:testDebugUnitTest` —
which need the Android SDK the GitHub runners carry, and no emulator
(`docs/testing.md#android-host-lane`).
The `guide` lane builds the end-user guide and its npm licence gate on every pull
request, and deploys the site to GitHub Pages only on a push to `main` that
touches the guide ([User guide](#user-guide), ADR-0024).
The `instrumented` lane is the only one that needs an emulator; it is scheduled,
so it never slows a change, and it runs the app's launch smoke test
(`app/androidInstrumentedTest`, issue #71). Vulnerability alerts are a repository
setting, enabled once with
`gh api --method PUT repos/<owner>/<repo>/vulnerability-alerts`.

`tools/dictionaries` builds and publishes by its own workflow (ticket #17), which
never blocks application CI. Its fast unit tests still run in `check`.

## User guide

The end-user guide is a [VitePress](https://vitepress.dev/) site whose content
lives in the top-level `guide/` directory — deliberately **outside** `docs/`, so
the internal material (an ADR, `CONTEXT.md`, a research report) can never be
published by accident (ADR-0024). The site is built and deployed by
`.github/workflows/docs.yml` (the `guide` CI lane). Run it from `guide/`:

```sh
cd guide
npm ci                    # restore from the committed lockfile
npm run check:licences    # the guide's npm licence gate (ADR-0011)
npm test                  # the licence gate's own tests
npm run build             # write the site to guide/.vitepress/dist
npm run dev               # preview while writing
```

`npm run build` fails on a dead internal link, so a broken page cannot merge. The
Gradle licence task sees only the Gradle graph, so the `guide` lane runs its own
npm licence gate (`guide/scripts/check-licences.mjs`) over the Node toolchain.
The site is configured with English as its root locale and an `fr/` tree that
holds a placeholder page, ready for the French translation to land page by page.
Node itself is pinned to 22 in `guide/package.json`'s `engines` and in the
`guide` lane.

## Prerequisites

- **A JVM to run the wrapper.** Everything else (Gradle, the pinned JDK) is
  provisioned by the build.
- **The Android SDK — optional.** It is only needed for the Android client.
  The build detects an SDK in `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
  `local.properties` (`sdk.dir`) or `~/Android/Sdk`. When one is present the
  Android targets activate — the `app` client and `core`'s Android target, which
  brings the `androidHostTest` Robolectric lane into `check` — and when none is
  present the project still builds and tests as a desktop/JVM project. Force
  either behaviour with `-Plekto.android=true` or `-Plekto.android=false`.

```sh
./gradlew check -Plekto.android=false   # JVM/desktop only, no SDK needed
```

## Android

```sh
./gradlew :app:assembleDebug             # build the debug APK
./gradlew :app:installDebug              # install on a running device/emulator
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Desktop

```sh
./gradlew :app:run                        # run the desktop app
```

Native packaging for Windows, macOS and Linux is ticket #29.

## Dictionary pipeline

`tools/dictionaries` is a standalone JVM CLI that turns the raw Wiktextract
extract (≈3 GiB gz, all languages) into a **trimmed EN→FR SQLite pack**: English
entries that carry a French translation, their senses and French translations,
their IPA/audio, and their inflections reversed into a surface→lemma index. It is
published by its own workflow (ticket #17); that pack build never blocks
application CI.

```sh
./gradlew :tools:dictionaries:check          # the unit tests, the golden, ktlint and detekt
./gradlew :tools:dictionaries:run --args="--input raw.jsonl.gz --output pack.sqlite \
  --source-url <url> --source-sha256 <hex> --extraction-date <YYYY-MM-DD>"
```

The CLI streams the raw `.jsonl[.gz]`, filters `lang_code == "en"` entries that
carry a French translation, drops multi-word surfaces, normalises every key with
the same rule as `core`'s `normaliseSurface` (NFC + lower case), and writes a
read-only SQLite file whose `user_version` is the pack format version. It also
writes a `NOTICE` (attribution) and a `manifest.json` (provenance and counts).

The pack is a **Derived asset**: it is never committed.
`.github/workflows/dictionary-pack.yml` downloads the source, runs the CLI and
publishes a GitHub Release tagged `dictionary-en-fr-<YYYYMMDD>` with the pack, a
`SHA256SUMS`, the `NOTICE` and a `manifest.json`. It also uploads a **date-free
alias** (`lekto-dictionary-en-fr.sqlite.gz`) beside the dated asset, so the app
can fetch the latest pack from one stable URL,
`releases/latest/download/lekto-dictionary-en-fr.sqlite.gz` (issue #18). The
release body is rendered from the committed template
`.github/release-notes/dictionary-pack.md` and the manifest — provenance, counts,
hashes and a verification line — while the full CC BY-SA 4.0 text stays in the
`NOTICE` asset and in the pack's `license_text` metadata. The workflow runs on
`workflow_dispatch` and a monthly `schedule`, so it can never block application
CI. The build is deterministic — fixed row ordering, no timestamps, gzip `-n` —
and `DictionaryPackGoldenTest` proves the transform on a committed fixture; a
sanity gate fails a run only on a changed source schema or a coverage collapse
below 80% of the previous build's lemma count.

The app consumes the pack through `core`'s `DictionaryPack` seam: it downloads
the alias into the device-local **derived store**, validates the format handshake
and opens the SQLite file in place — `sqlite-jdbc` on the JVM, the Android
framework's SQLite on Android (ADR-0018). The CLI and the JVM reader use
**xerial sqlite-jdbc** (Apache-2.0, AGPL-compatible); it is now a production
dependency of `core`'s JVM target (desktop) as well as the standalone tool, so it
is held to the licence gate like any other. See ADR-0017, ADR-0018 and
`docs/research/open-dictionaries.md`.

**KSafe** (Apache-2.0, AGPL-compatible) is the desktop secret store's dependency
(issue #24): it drives the OS keychain — Windows DPAPI, the macOS login Keychain,
the Linux Secret Service — behind `core`'s `SecretStore` seam (ADR-0021). It is a
production dependency of `core`'s JVM target only: the Android client backs the
same seam with its own Keystore code, so KSafe never reaches the Android
classpath. Its transitive dependencies (JNA, androidx.datastore, okio) clear the
licence gate; JNA's POM declares both LGPL-2.1-or-later and Apache-2.0, and the
gate accepts the Apache-2.0 arm. See `docs/testing.md` for how the seam is
proved.

**kotlinx-coroutines** (Apache-2.0, AGPL-compatible) is a production dependency
of `core`'s shared sources (issue #89; ADR-0022): the phrase-translation
provider seam returns a `Flow` of text deltas, so `Flow` is part of the domain's
public model and a consumer (the app, `testkit`) needs it on its compile
classpath. It was already on the test classpath through
`kotlinx-coroutines-test`, which the harness uses for virtual time
(`docs/testing.md`), so the change promotes it to an `api` dependency rather than
adding a new component.

## Modules

| Module                  | What it is                                                          |
| ----------------------- | ------------------------------------------------------------------- |
| `core`                  | The domain: vault, records, merge, tokenisation, word identity, vocabulary, import and library, sync engine, parsers, the dictionary-pack reader. |
| `testkit`               | Contract suites and in-memory fakes shared by the other modules' tests. Published as a library so a KMP `commonTest` set can be shared. |
| `integrations/webdav`   | The first sync driver, isolated from the domain.                    |
| `app`                   | The Compose Multiplatform application (Android + desktop).           |
| `tools/dictionaries`    | The offline dictionary-pack pipeline; published by its own CI workflow. |
| `architecture`          | The architecture tests (ticket #9): the module boundaries and naming conventions as tests. Test-only, never published. |

The module boundaries are a decision, not an accident: the domain never depends
on the application, and integrations never leak into the domain. The
`architecture` suite asserts both, and the naming and placement conventions with
them, so `check` fails where a review would otherwise have to notice (ticket #9).

## Known build noise

AGP 8.13.2's bundled lint analyses with an embedded Kotlin 2.2.0 compiler and
prints `Module was compiled with an incompatible version of Kotlin` while
analysing code compiled by Kotlin 2.4.20. Lint still produces its report and
does not fail the build. This disappears when the toolchain moves to an AGP that
bundles a matching lint.
