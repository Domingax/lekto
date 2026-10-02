# The test harness

The fast, deterministic loop every later ticket is written against. The domain is
pure Kotlin, so the whole suite runs on the JVM in seconds — no emulator, no
Docker. Non-determinism enters only through injected seams.

## The one command

```sh
./gradlew check
```

It builds every module, runs the domain suite (`core`), the UI-semantics
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
| UI semantics         | `app/desktopTest`              | `:app:desktopTest`              | a JVM        |
| UI screenshot goldens| `app/desktopTest`              | `:app:verifyRoborazziDesktop`   | a JVM        |
| Architecture         | `architecture/src/test`        | `:architecture:test`            | a JVM        |
| WebDAV integration   | `integrations/webdav/src/jvmTest` | `:integrations:webdav:jvmTest` | Docker; skips without |
| Dependency licences  | build logic                    | `:checkDependencyLicences`      | resolved metadata |
| Coverage             | build logic (merged)           | `:koverXmlReport`               | a JVM        |

The UI-semantics suite lives in `desktopTest`, not `commonTest`, because the
Compose Multiplatform common test API cannot run under Android's local (host)
test configuration. The slower lanes — screenshot goldens, the containerised
WebDAV driver and instrumented end-to-end runs — run on pull requests or nightly;
the CI fast lane excludes the WebDAV test so the two lanes do not overlap.
`docs/build.md#ci-lanes` lists the workflow jobs that run each one.

The reader's UI tests drive the word layer with `WhitespaceTextSegmenter` in
`testkit` — a deterministic letter/digit splitter — so they do not depend on the
machine's ICU dictionaries. Production segmentation stays behind the
`TextSegmenter` seam (`IcuTextSegmenter` on the JVM, `android.icu` on Android).

The EPUB parser and ICU segmenter (ticket #10) are JVM-backed, so their tests
run in `core/src/jvmTest` and their inputs are committed fixtures under
`core/src/jvmTest/resources` — a generated awkward EPUB and a real Project
Gutenberg book — read through `TestResources` in `testkit`. The extraction
goldens in `resources/golden/` pin the parser's output; a change that alters the
text fails until the golden is deliberately updated. See
`docs/research/epub-to-tokens-spike.md`.

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
driver contract (ticket #27) will run against. The image is pinned by digest —
the server publishes no version tags — so a green build does not move under it.
The test class is annotated `@Testcontainers(disabledWithoutDocker = true)`, so a
machine or runner without Docker skips it instead of failing: the lane is green
everywhere and simply proves more where Docker is present.

The nightly instrumented lane is wired but still has no instrumented tests to run;
they arrive with the platform work (tickets #16, #21, #24). The lane exists and
stays off the critical path so those tickets only have to add tests, not CI.

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

Dispatchers are injected the same way — as a `CoroutineDispatcher` parameter on
whatever asynchronous code needs one — but they are not part of `Seams` yet,
because the domain holds no asynchronous code. `kotlinx-coroutines-test`
supplies the virtual time (`runTest`, `TestScope`, `advanceUntilIdle`) for them
when the sync engine lands; a hard-coded `Dispatchers.IO` would not see it.

## Test levels

A ticket states the **behaviour** it wants; the level that proves it comes from
where the change lands:

| Change surface                                                       | Level                                                   | Where it lives              |
| -------------------------------------------------------------------- | ------------------------------------------------------- | --------------------------- |
| A domain invariant — merge, identity, tokenisation, serialise/parse   | property test over generated data                       | `core/commonTest`           |
| A seam — `SyncTarget`, `VaultStore`, a provider adapter               | contract suite + in-memory fake                         | `testkit/commonMain`        |
| A driver under `integrations/`                                        | that contract suite, plus a containerised integration run | `integrations/webdav/jvmTest` |
| A parser — EPUB, TXT, PDF                                             | golden over a generated corpus, plus one awkward real fixture | `jvmTest`             |
| UI behaviour in `app`                                                 | Compose UI-semantics test                               | `app/desktopTest`           |
| A visual or layout change                                             | + screenshot golden                                     | `app`, on the PR lane       |
| Android platform glue                                                 | Robolectric host test                                   | `androidHostTest`           |
| An architectural boundary or naming convention                       | architecture rule test, plus a synthetic violation it must catch | `architecture/src/test` |
| A bug                                                                 | regression test at the seam the bug occurs              | wherever that seam lives    |

Every change lands with a test at its level: `/deliver` holds each surface to its
row and each demanded level to a red → green before the commit.

`VaultStore` is the first seam to use the pattern: `testkit` holds
`VaultStoreContract` and the `InMemoryVaultStore` fake, `core/commonTest` runs the
contract against the fake, and `core/jvmTest` runs the same contract against the
real directory-backed store — so the two cannot drift (ticket #12, ADR-0014).

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

## Reproducing a failure

A property failure prints the seed it used and writes it to
`~/.kotest/seeds/<spec>/<testname>`. The next run replays that seed
automatically until the test passes; a seed passed to a `PropTestConfig` takes
precedence. `HarnessSelfTest` pins both guarantees: a broken assertion fails, and
a failing property reports a replayable seed.

A KMP build can silently compile zero tests, or run them under a runner that
never discovers them, and still report success. `HarnessSelfTest` exercises each
capability above so the wiring cannot regress unnoticed.
