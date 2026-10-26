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
| OkHttp (Android WebDAV) | 5.4.0 | `gradle/libs.versions.toml` (`okhttp`) |

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
`settings.gradle.kts`, each module's `build.gradle.kts`, every Kotlin file and the
dependency manifests the repository ships into a pure model, then asserts: the
module dependency graph (the domain depends on no module, only the application may
depend on an integration, nothing depends on the application), import purity (the
domain's shared sources name neither the application, nor an integration, nor
Android — its `androidMain` platform set may name the Android API it backs; the
testkit never reaches production), naming and placement (a package matches its
directory, a source sits under its module's package root, a test class is `*Test.kt`
in a test source set, and a desktop `*SemanticsTest` has a same-named Android
host-lane twin — issue #73), that every clickable control carries the hand cursor
(issue #217), and that every manifest has a `.github/dependabot.yml`
entry ([Alert triage](#alert-triage); issue #153).

The rules are hand-rolled rather than Konsist or ArchUnit, so the suite adds no
dependency and can read the Gradle module graph, which a bytecode analyser
cannot; ADR-0013 records the decision. Each rule is proven against a deliberately
broken repository in `ArchitectureViolationTest`, and the real tree is asserted
by `LektoArchitectureTest` — a boundary can only be relaxed by editing
`LektoArchitecture.policies`, and a screen's missing Android twin only by removing
its path from `LektoArchitecture.uiTestParityAllowlist`. Two guards sit beside the
policy engine rather than inside it, because each spans two artefacts instead of
describing the module graph: `DictionaryFormatSynchronisationTest` (the pack format
the producer and the consumer declare) and `DependabotWatchRulesTest`, which proves
`DependabotWatchRules` against hand-built values and then asserts the real tree.

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

The gate reads Gradle metadata, so it does not see a copy of third-party work
checked into the tree. The icon set is such a copy: it adds no coordinate, and its
ISC and Feather/MIT notices live in `THIRD-PARTY-NOTICES.md` at the root instead
(see [Icon pipeline](#icon-pipeline)).

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
`verifySonarReportPaths` — part of `check` — fails if a configured report path is
missing or hides behind a wildcard, so a misconfiguration trips the fast lane
rather than the sonar one. `sonar.qualitygate.wait=true` makes the scan wait for the gate and
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
linter reports are produced. The `webdav` lane needs Docker for the driver's
container cases — present on GitHub's runners — while its non-container tests run
anywhere, so the task still passes where Docker is absent; the `fast` lane excludes
that task so the two lanes do not overlap. The `fast` lane also runs the Android host suites —
`core`'s `:core:testAndroidHostTest`, the app's `:app:testDebugUnitTest` and
`integrations/webdav`'s `:integrations:webdav:testAndroidHostTest` —
which need the Android SDK the GitHub runners carry, and no emulator
(`docs/testing.md#android-host-lane`).
The `guide` lane builds the end-user guide and its npm licence gate on every pull
request, and deploys the site to GitHub Pages only on a push to `main` that
touches the guide ([User guide](#user-guide), ADR-0024).
The `instrumented` lane is the only one that needs an emulator; it is scheduled,
so it never slows a change, and it runs the app's launch smoke test
(`app/androidInstrumentedTest`, issue #71).

`tools/dictionaries` builds and publishes by its own workflow (ticket #17), which
never blocks application CI. Its fast unit tests still run in `check`.

## Alert triage

Vulnerability alerts are a repository setting, enabled once with
`gh api --method PUT repos/<owner>/<repo>/vulnerability-alerts`. The setting is what
raises an alert; `.github/dependabot.yml` is what turns a dependency bump into a
pull request, weekly and grouped by ecosystem. The two are independent: dismissing
an alert does not stop its entry from opening an update pull request, and an entry
opening one does not by itself close an alert.

The question asked of an alert is **which classpath is it on**. It is acted on when
the vulnerable code sits on a classpath the application ships. It is dismissed when
it does not — which was the state of the whole backlog this policy was written
against (issue #153). Of the 63 alerts on `main`, none reached a shipped artifact:
the Maven ones were transitives of the **build** classpath (AGP 8.13.2's
`io.grpc:grpc-netty` and `com.android.tools.utp` pull netty, bouncycastle,
protobuf-java, jose4j, jdom2, freemarker and commons-compress; ktlint 1.8.0 pulls
logback) or of a **test** classpath (Robolectric's bouncycastle and guava,
Testcontainers' commons-compress), and the npm ones were the guide's dev toolchain —
Vite's and esbuild's development-server advisories.

Two commands produce that evidence. `./gradlew checkDependencyLicences` resolves the
classpath of every module and writes `build/reports/dependency-licences.txt`, which
marks each component `[production]` or `[test]` and names the configurations it
appears in; `./gradlew buildEnvironment` prints the build classpath the plugins
resolve. A dismissal records the reason `tolerable_risk` and cites this section, so
the answer survives the alert.

Two guards keep the list honest. `DependabotWatchRulesTest` fails `check` when a
manifest the repository ships has no `.github/dependabot.yml` entry, so a second
npm project cannot go unwatched. And a dismissal is not a mute button: a new alert
arrives open, and the question to ask of it is still the one above.

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

The app's `minSdk` is **27** (Android 8.1) — `android-minSdk` in
`gradle/libs.versions.toml`, read by the app, `core` and `integrations/webdav`. It
is the first level where `SQLiteDatabase.openDatabase(File, OpenParams)` exists,
so the Android dictionary-pack reader carries no deprecation workaround; it also
clears the official Dropbox Android SDK's floor (API 26) for the second sync
driver (#123). Android 8.0 and older are out of support (issue #125).

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
extract (≈3 GiB gz, all languages) into a set of **trimmed SQLite dictionary
packs** and per-language **Difficulty tables**, driven by a committed language set
(`tools/dictionaries/languages.json`, issue #161): the (Reading language ×
Translation language) pairs to build and the reading languages that get a table.
It is published by its own workflow (ticket #17); that build never blocks
application CI.

```sh
./gradlew :tools:dictionaries:check          # the unit tests, the golden, ktlint and detekt
./gradlew :tools:dictionaries:run --args="--input raw.jsonl.gz \
  --languages-config languages.json --output-dir out \
  --frequency en=wordfreq-en.tsv.gz --frequency es=wordfreq-es.tsv.gz \
  --source-url <url> --source-sha256 <hex> --extraction-date <YYYY-MM-DD>"
```

The CLI streams the raw `.jsonl[.gz]` once per configured pair, keeps the
`lang_code == "<reading>"` entries that carry a `<translation>` translation, drops
multi-word surfaces, normalises every key with the same rule as `core`'s
`normaliseSurface` (NFC + lower case), and writes a read-only SQLite file whose
`user_version` is the pack format version, beside a `NOTICE` (attribution) and a
`manifest.json` (provenance and counts).

A **single-artefact run** (`--reading-language`/`--translation-language`/
`--difficulty-language`) builds one pack and, optionally, one reading language's
**Difficulty table** (issue #64; ADR-0029) from a wordfreq frequency list, keyed by
the reading language's word identity. `--difficulty-language` defaults to the
reading language, so the table is keyed by that pack's form index:

```sh
./gradlew :tools:dictionaries:run --args="--input raw.jsonl.gz --output pack.sqlite \
  --reading-language en --translation-language es \
  --frequency-input wordfreq-en.tsv.gz --difficulty-output difficulty-en.sqlite \
  --source-url <url> --source-sha256 <hex> --extraction-date <YYYY-MM-DD>"
```

`--frequency-input` is one `form<TAB>weight` line per surface form (for wordfreq,
`10 ** zipf_frequency(form)`); the builder aggregates each form's weight onto the
lemma the reading language's pack form index knows — or the normalised surface
form — ranks the keys and cuts them into CEFR bands A1–C2 at the calibrated sizes,
writing a table behind its own `user_version` handshake and its own wordfreq
`NOTICE`. `DifficultyGoldenTest` and `SqliteDifficultyWriterTest` pin the
transform and its byte-for-byte reproducibility.

Each pack and table is a **Derived asset**: never committed.
`.github/workflows/dictionary-pack.yml` downloads the source, builds the wordfreq
list for each configured difficulty language, runs the CLI over the configured
set and publishes a GitHub Release tagged `dictionary-<YYYYMMDD>` with every
asset, a `SHA256SUMS` and the per-artefact `NOTICE`/manifest sidecars. The asset
names are date-free (`lekto-dictionary-en-fr.sqlite.gz`,
`lekto-difficulty-es.sqlite.gz`), so
the app fetches each from a stable URL built from the name the **language
manifest** published, `releases/latest/download/<name>` (issues #18, #149, #161).
Because `latest` is one release, the whole configured set is published together
(ADR-0031). The release body is rendered from the committed template
`.github/release-notes/dictionary-pack.md`; the full CC BY-SA 4.0 text stays in the
`NOTICE` sidecars and in each pack's `license_text` metadata. The workflow runs on
`workflow_dispatch` and a monthly `schedule`, so it can never block application
CI. The build is deterministic — fixed row ordering, no timestamps, gzip `-n` —
and `DictionaryPackGoldenTest` proves the transform on a committed fixture; a
sanity gate fails a run only on a changed source schema or a coverage collapse
below 80% of the previous build's lemma count, per reading language.

The same run also writes a **language manifest**
(`--language-manifest`, default `language-manifest.json` in the output directory):
the catalogue of what the release offers for reading — each **Reading language**
with the **Difficulty table** published for it and the **Dictionary pack**s
published for its pairs, each pair naming its **Translation language** (issues
#145, #161). It is built from the run's own output, so a run that published
nothing yields an empty manifest and the catalogue can never advertise an artefact
that was not built. The workflow uploads it under its own date-free name beside
the assets, so the app can fetch one constant address,
`releases/latest/download/language-manifest.json`. Its format version is declared
by the producer (`LanguageManifestFormat.VERSION`) and by the consumer (`core`'s
`LanguageManifest.FORMAT_VERSION`), kept in step by an architecture test
(ADR-0013).

The app consumes the pack through `core`'s `DictionaryPack` seam: it downloads
each pair's alias into the device-local **derived store**, validates the format
handshake and opens the SQLite file in place — `sqlite-jdbc` on the JVM, the
Android framework's SQLite on Android (ADR-0018). The CLI and the JVM reader use
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

**OkHttp** (Apache-2.0, AGPL-compatible) is the Android target's WebDAV transport
(issue #116; ADR-0026). Android ships no `java.net.http` and `HttpURLConnection`
rejects the `PROPFIND`/`MKCOL` methods WebDAV needs, so the driver's HTTP call
sits behind a `WebDavTransport` seam: the desktop target keeps the
dependency-free JDK client and the Android target uses OkHttp, which performs any
method. OkHttp is a production dependency of `integrations/webdav`'s **Android**
target only, so it is on the shipped app's classpath and held to the licence gate
like any other. It is pinned at 5.4.0 because 5.5.0's Android artifact requires
`compileSdk` 37 and this repository is pinned to 36.


## Icon pipeline

`tools/icons/fetch.sh` regenerates the icon set committed under
`app/src/commonMain/kotlin/app/lekto/ui/icons/`. It fetches the twelve SVGs at the
pinned Lucide tag, runs the pinned Valkyrie CLI with its zip digest checked, and
writes the `ImageVector` Kotlin. Nothing is generated at build time and the app
declares no icon dependency: the output is committed, and `LektoIconsTest` pins
its geometry in `./gradlew check`. The licences are in `THIRD-PARTY-NOTICES.md`;
the design rule is `docs/design.md` §5, and the reasoning is
`docs/research/icones-compose.md`.

`tools/icons/mark.sh` regenerates the launcher mark (issue #177) from the SVGs in
`tools/icons/mark/`, which are its only source of truth: `mark.svg` is the letter,
with its paper rim and the four Mastery level colours, `letter.svg` the same letter
in one ink for Android 13's monochrome layer. It converts them into the adaptive
icon's two layers under `app/src/androidMain/res/` — the launch screen reuses the
foreground, because the rim carries the mark onto the Dark paper and the system
masks the same third of both — writes `guide/public/favicon.svg`,
and renders the desktop rasters into `app/src/desktopMain/resources/app-icon/` — a PNG for the
Linux package and the window icon, a multi-resolution ICO for Windows and an ICNS
for macOS. The conversion is `tools/icons/mark/Convert.java`, a single-file JDK
program; the renderer is `app.lekto.tools.RenderAppIcon`, run by
`./gradlew :app:renderAppIcon`, which draws with the Skia already on the desktop
classpath. The pipeline adds no dependency, and every output is committed:
`LauncherIconTest` pins the manifest-and-resource wiring, `AppIconTest`'s golden
pins the geometry, and a raster is regenerated by hand rather than compared in CI.

The ICO and ICNS are committed ahead of the packaging: `nativeDistributions` does
not exist yet (issue #29), so nothing consumes them today beyond the window icon.

## Modules

| Module                  | What it is                                                          |
| ----------------------- | ------------------------------------------------------------------- |
| `core`                  | The domain: vault, records, merge, tokenisation, word identity, vocabulary, import and library, sync engine, parsers, the dictionary-pack reader. |
| `testkit`               | Contract suites and in-memory fakes shared by the other modules' tests. Published as a library so a KMP `commonTest` set can be shared. |
| `integrations/webdav`   | The first sync driver, isolated from the domain; a JVM (desktop) and an Android target behind one `WebDavTransport` seam (ADR-0026). |
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
