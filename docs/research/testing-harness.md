# A test harness for Lekto: what to test, at which layer, with which tool

**Method:** Primary sources only — Kotlin/JetBrains documentation, library repositories and
release notes, Android Developers documentation, Ktor/Testcontainers/Robolectric docs, the
W3C EPUB specification, and GitHub Actions documentation. Version numbers were read on
**2026-09-27**. Every claim carries a URL; claims I could not verify from a primary source
are gathered in [§Unverified and flagged](#unverified-and-flagged).

> **Headline:** Lekto's domain is pure, synchronous, deterministic Kotlin and should be
> pushed almost entirely into the **JVM** (`jvmTest` running `commonTest`), where the whole
> suite runs in seconds with no emulator. Use **`kotlin.test` + Kotest** for the domain,
> **Kotest property testing** for the merge and tokenisation invariants, **Turbine** for
> flows, one **shared `SyncTarget` contract suite** plus an in-memory fake, **Testcontainers
> + Apache `mod_dav`/sabre/dav** for the real WebDAV driver, **`runComposeUiTest`** for UI
> semantics and **Roborazzi** for headless JVM goldens. Google's own Compose screenshot
> tool is **Android-only and explicitly does not support KMP non-Android targets**
> ([Android docs](https://developer.android.com/studio/preview/compose-screenshot-testing#known-issues)),
> so it is not the right centre of gravity for a Compose Multiplatform app. Keep the
> emulator (instrumented) job **nightly**, never on the critical path.

---

## Version snapshot (2026-09-27)

| Component | Version | Source |
|---|---|---|
| Kotlin | **2.4.20** (2.4.0 released 2026-06-03; 2.5.0 planned Dec 2026) | [Kotlin releases](https://kotlinlang.org/docs/releases.html) |
| Compose Multiplatform | **1.12.1** (`org.jetbrains.compose.ui:ui-test:1.12.1`) | [Testing CMP UI](https://kotlinlang.org/docs/multiplatform/compose-test.html) (page dated 15 May 2026) |
| Android Gradle Plugin | 9.x (Android-KMP plugin; AGP 8.5+ for screenshot plugin; 10.0 due H2 2026) | [Android-KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin) |
| Kotest | 6.x (6.2 stable, 6.3 unreleased) | [Kotest release 6.0](https://kotest.io/docs/next/release6/) |
| Turbine | 1.2.1 | [cashapp/turbine](https://github.com/cashapp/turbine) |
| Testcontainers for Java | 2.0.5 | [java.testcontainers.org](https://java.testcontainers.org/) |
| Ktor | 3.6.0 | [Ktor client testing](https://ktor.io/docs/client-testing.html) |
| Robolectric | 4.x | [robolectric.org](https://robolectric.org/) |

Kotlin 2.4.20 is the current stable release; note that `kotlinx-datetime`'s current README
targets a Kotlin standard library "not lower than `2.3.21`"
([kotlinx-datetime README](https://github.com/Kotlin/kotlinx-datetime)), and Kotest 6.0
requires "a minimum of JDK 11 and Kotlin 2.2"
([Kotest 6.0](https://kotest.io/docs/next/release6/)) — both are satisfied by 2.4.20.

---

## 1. KMP test source sets and execution

### The model

Every source set created by default has a `Main` and `Test` counterpart, and tests can use
the `Main` API without extra configuration. `commonTest` "compiles to all of the declared
targets, allowing you to write common tests", while platform test source sets (for example
`jvmTest`) are for platform-specific tests; each target has a `<targetName>Test` Gradle task
([Understand KMP project structure](https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html#integration-with-tests)).

For a Compose Multiplatform project the wizard generates `*Test` source sets; the docs name
the relevant ones as `commonTest`, `jvmTest` (desktop), `androidDeviceTest` and — after the
Android-KMP plugin migration — `androidHostTest`, renamed from `androidUnitTest`
([Testing CMP UI](https://kotlinlang.org/docs/multiplatform/compose-test.html),
[Android-KMP plugin § Configure host and device tests](https://developer.android.com/kotlin/multiplatform/plugin)).
Kotlin's own tutorial still shows `androidHostTest` for Android-specific tests that run on a
local JVM ([Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)).

> **Naming caution:** the Android-KMP plugin says `androidUnitTest` → `androidHostTest` and
> `androidInstrumentedTest` → `androidDeviceTest`, and "the source sets and compilations can
> be configured in the build script". Test source sets are **disabled by default** under the
> new plugin and must be opted in with `withHostTest {}` / `withDeviceTest {}`
> ([Android-KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin)). This is a
> real trap: a fresh KMP module silently runs zero host tests until you opt in.

### Which tests run on the JVM without an emulator

- **`commonTest` on the `jvm` target** (task `jvmTest`) — the entire pure-domain suite, no
  Android, no emulator.
- **`jvmTest` / `desktopTest`** — JVM-only tests, including Compose Desktop UI tests
  (Compose Desktop's testing API is JUnit-based and runs with `./gradlew desktopTest`,
  [Testing CMP UI with JUnit](https://kotlinlang.org/docs/multiplatform/compose-desktop-ui-testing.html)).
- **`androidHostTest`** — Android unit tests on the local JVM. These are covered by
  Robolectric when you need Android framework classes. Robolectric's own framing: emulator
  tests are slow — "Building, deploying, and running the tests often take minutes" — while
  Robolectric tests "run inside the JVM in seconds" and "[reduce] test cycles from minutes to
  seconds" by skipping dexing, packaging and installing
  ([Robolectric](https://robolectric.org/)).
- **`androidDeviceTest`** — instrumented; requires a device or emulator. The Kotlin tutorial
  is explicit that Android tests in `androidHostTest` "run as local unit tests on the current
  machine", unlike instrumented tests that "run on a device or an emulator"
  ([Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)).
- **iOS later**: `iosSimulatorArm64Test` runs on a macOS simulator, not on Linux CI
  ([Testing CMP UI](https://kotlinlang.org/docs/multiplatform/compose-test.html)).

### Fastest full-suite command

`allTests` is the aggregate KMP task: "if you run the `allTests` Gradle task, every test in
your project will be run with the corresponding test runner". The docs warn that Android
test reports are *not* merged into the `allTests` report — `testDebugUnitTest` /
`testReleaseUnitTest` are separate
([Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)).

For an AI agent's inner loop, do **not** start with `allTests`; run the narrowest task that
covers the code under edit:

```bash
# Fastest: pure domain + parser tests, JVM only, no Android, no emulator
./gradlew :core:jvmTest

# Whole JVM suite (domain + Android host tests + Compose Desktop UI tests)
./gradlew :core:jvmTest :core:testDebugUnitTest :app:desktopTest

# Everything the CI host can run (still excludes emulator/instrumented)
./gradlew allTests

# Defeat Gradle's up-to-date check when re-running unchanged tests
./gradlew :core:jvmTest --rerun
```

(`--rerun` is documented by Kotest as the way to force a re-run
([Kotest setup § Re-running tests](https://kotest.io/docs/framework/project-setup.html)).)

### Speed vs fidelity

| | `androidHostTest` (JVM) | `androidDeviceTest` (instrumented) |
|---|---|---|
| Runtime | seconds (JVM, no dex/install) | minutes (build, dex, install, boot, run) |
| Fidelity | Robolectric-simulated Android; real Android APIs are shadowed | real device/emulator: real SAF, real `TextToSpeech`, real Keystore, real keystore-backed IO |
| Failure mode | deterministic | "frequent failures due to the device environment, leading to false negatives … hard to reproduce" ([Roborazzi](https://github.com/takahirom/roborazzi)) |
| Use for | domain logic with Android deps, file store, resource loading | platform integrations and end-to-end journeys only |

Robolectric explicitly positions itself between "mock frameworks such as Mockito" and the
emulator, offering "closer to black box testing" because it "handles inflation of `View`s,
resource loading, and lots of other stuff that's implemented in native C code on Android"
([Robolectric](https://robolectric.org/)).

---

## 2. Testing the pure domain

Domain under test (per ADR-0003/0004/0006/0009): tokenisation, `(language, lemma)` identity,
record serialisation (`id`, `schemaVersion`, `updatedAt`, `deviceId`), last-writer-wins merge
with `deviceId` tie-break, tombstones, atomic write semantics.

### What supports `commonTest` today

- **`kotlin.test`** — the default multiplatform test API. "Annotations, such as `Test`, map
  to those provided by the selected framework", and assertions run through an `Asserter`
  implementation ([Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)).
  On Android, `kotlin.test` maps to **JUnit 4**; on JVM you can choose JUnit 4/5/TestNG.
- **Kotest 6** — "supported on all targets, including JVM, JavaScript, Native and Wasm"; KMP
  support "no longer requires a compiler plugin" and the setup is simplified
  ([Kotest setup](https://kotest.io/docs/framework/project-setup.html),
  [Kotest 6.0](https://kotest.io/docs/next/release6/)). Note the setup page also says the
  Kotest Gradle plugin's old compiler plugin "has been replaced with a more robust method
  using KSP" — i.e. an optional Gradle plugin/KSP step exists for enhanced IDE support, but
  the framework itself no longer needs a compiler plugin.
- **Turbine 1.2.1** — a Flows testing library. Its Maven module metadata publishes a full
  multiplatform variant set (jvm, android, ios, js, wasm, native), so it is usable from
  `commonTest` ([Turbine README](https://github.com/cashapp/turbine); variants verified from
  [`turbine-1.2.1.module`](https://repo1.maven.org/maven2/app/cash/turbine/turbine/1.2.1/turbine-1.2.1.module)).
- **`kotlinx-coroutines-test`** — Apache-2.0, multiplatform; `runTest`, `TestScope`,
  `TestDispatcher`, `advanceTimeBy`, `advanceUntilIdle`
  ([kotlinx-coroutines-test API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)).
- **JUnit 5** — JVM-only. Kotlin supports it, but it is not a KMP `commonTest` framework;
  use it (if at all) only in JVM-specific source sets. Kotest on the JVM "builds atop of the
  JUnit Platform" ([Kotest setup](https://kotest.io/docs/framework/project-setup.html)).

### Licence check against AGPL-3.0 (ADR-0011)

ADR-0011 requires dependencies to be "AGPL-compatible for the way we link it", with
Apache-2.0 and MIT called out as fine. The FSF's licence list is the authority:
**Apache-2.0** is "compatible with version 3 of the GNU GPL", and **Expat/MIT** is
GPL-compatible ([GNU licence list](https://www.gnu.org/licenses/license-list.html#GPLCompatibleLicenses)).

| Library | Licence | Verdict |
|---|---|---|
| `kotlin.test`, `kotlinx-*` | Apache-2.0 | ✅ compatible |
| Kotest | Apache-2.0 | ✅ compatible |
| Turbine | Apache-2.0 | ✅ compatible |
| Robolectric | MIT | ✅ compatible |
| Roborazzi | Apache-2.0 | ✅ compatible |
| Paparazzi | Apache-2.0 | ✅ compatible |
| Testcontainers | MIT | ✅ compatible |
| Ktor | Apache-2.0 | ✅ compatible |
| Apache PDFBox | Apache-2.0 | ✅ compatible |
| **JUnit 5 / Jupiter** | **EPL-2.0** | ⚠️ see below |
| **JUnit 4** | **EPL-1.0** | ⚠️ see below |

The FSF is blunt about Eclipse licences: **EPL-2.0 "without this designation remains
incompatible with the GPL"**, and EPL-1.0 is listed under "GPL-Incompatible Free Software
Licenses" ([GNU licence list](https://www.gnu.org/licenses/license-list.html#EPL2),
[EPL-1.0 entry](https://www.gnu.org/licenses/license-list.html#EPL)). JUnit 5's
`LICENSE.md` is plain EPL-2.0 ([junit5 LICENSE.md](https://raw.githubusercontent.com/junit-team/junit5/main/LICENSE.md)).

**Practical reading:** JUnit is a **test-scope** dependency; it is not linked into the
shipped AGPL app and so is not "conveyed" with the app. AGPL §6 and the ADR's linking rule
govern the distributed binary, not the build-time test classpath. But since `kotlin.test`
on Android resolves to JUnit 4 anyway, the safest posture is: **keep JUnit strictly test-only,
prefer Kotest/`kotlin.test` assertions, and record this reasoning in the harness docs** so a
future contributor does not accidentally promote a JUnit type into `commonMain`. If the
project wants a fully permissive test classpath, Kotest + `kotlin.test` (both Apache-2.0)
cover everything JUnit gives us.

### Recommended mix

- `kotlin.test` assertions everywhere as the baseline (works in `commonTest`, maps per
  platform).
- **Kotest `StringSpec`/`FunSpec`** where the spec should read as prose (aid for agents, §9).
- **Kotest matchers** (`shouldBe`) for stronger failure messages and its Kotlin 2.2+
  **Power Assert** integration, which "displays values of each part of an expression when an
  assertion fails" ([Kotest 6.0](https://kotest.io/docs/next/release6/)).
- **Turbine** for any `Flow` surfaced by the vault/sync engine (state streams, progress
  streams). Turbine fails instead of hanging, and asserts that all events were consumed
  ([Turbine README](https://github.com/cashapp/turbine)).

---

## 3. Property-based testing

Kotest's property module is independent — "You do **not** need to be using Kotest as your
test framework … to benefit from the property test support" — and "supported on all
targets", with `io.kotest:kotest-property` as the dependency
([Kotest property testing](https://kotest.io/docs/proptest/property-based-testing.html)).
It provides generators, shrinking, per-test `PropTestConfig`, and — crucially for an AI
harness — **seed handling**:

- failing tests print the seed used;
- seeds are written to `~/.kotest/seeds/<spec>/<testname>` and are automatically replayed
  until the test passes ("Rerunning failed seeds");
- `PropertyTesting.defaultSeed` can fix the global seed; `PropertyTesting.failOnSeed` /
  `KOTEST_PROPTEST_SEED_FAIL_IF_SET` can forbid checked-in seeds
  ([Kotest property seeds](https://kotest.io/docs/proptest/property-test-seeds.html)).

### Properties to encode for Lekto

**Merge (ADR-0004) — the highest-value targets**, because last-writer-wins is easy to get
subtly wrong:

1. **Idempotence** — `merge(a, a) == a`.
2. **Commutativity at the record level** — `merge(a, b) == merge(b, a)` when `updatedAt`
   differs; when `updatedAt` ties, the `deviceId` tie-break makes the result well-defined,
   so test `merge(a, b) == merge(b, a)` for *distinct* `deviceId`s and assert the winner is
   the lexicographically/NUL-ordered greater `deviceId`.
3. **Associativity / convergence** — folding the same set of records in any order yields the
   same result (a for-all over permutations; Kotest has a permutations primitive).
4. **Monotonicity of `updatedAt`** — the winning record's `updatedAt` is the maximum.
5. **Tombstone dominance** — a tombstone with a later `updatedAt` beats a live record;
   a live record with a later `updatedAt` resurrects (or is rejected — decide the rule and
   encode it).
6. **Serialise/parse round-trip** — `parse(serialise(r)) == r` for generated records,
   including awkward strings (empty, Unicode combining marks, embedded quotes/newlines,
   very long values) and all `schemaVersion` values in range.

**Tokenisation / `(language, lemma)` identity (ADR-0006):**

7. **Round-trip** — concatenating token surface forms with the original separators
   reconstructs the input (design the token model so this holds; it is the property that
   catches most tokeniser bugs).
8. **Idempotence of normalisation** — `normalise(normalise(s)) == normalise(s)`.
9. **Identity stability** — the same surface form in the same language always maps to the
   same key; a known lemma mapping collapses inflections to one key; absence of a lemma
   falls back to the normalised surface form.
10. **Unicode safety** — token counts and boundaries are invariant under NFC/NFD for
    languages where that matters (generate both forms).

### Alternatives

Kotest's `kotest-property` is the pragmatic KMP choice (Apache-2.0, all targets, seeds).
There is no second KMP-native property framework of comparable maturity that I verified;
Java-only options (jqwik) and non-KMP QuickCheck ports would not run in `commonTest`.
**Use Kotest property testing.** Flag: I did not find a maintained, KMP-native competitor to
Kotest's property module in 2026.

---

## 4. Contract tests for `SyncTarget`

ADR-0009 makes `SyncTarget` a **seam** with thin per-backend drivers and a capability query.
That is exactly the shape a shared contract suite is for: the contract suite *is* the
specification of the seam.

### One shared suite, every driver

1. Define the contract as an **abstract test class (or a `fun syncTargetContract(factory: () -> SyncTarget)` helper) in a dedicated source set** that every driver module can depend on.
2. Because KMP `commonTest` is not published, cross-module sharing needs a real module.
   Gradle's `java-test-fixtures` plugin is JVM-only and does not cover KMP targets, so use a
   **`:testkit` KMP library module** whose `commonMain` (not `commonTest`) exposes:
   - `abstract class SyncTargetContract` (or `fun contract(factory…)`) — the shared suite;
   - `class InMemorySyncTarget` — an in-memory fake used by both the contract suite and the
     engine's own tests.
   Driver modules then add `testImplementation(project(":testkit"))` and subclass/apply it.
3. **Capability-gated assertions.** Because ADR-0009 lets a backend lack a primitive, the
   contract should assert the base operations, then have optional blocks guarded by the
   driver's capability query (e.g. `supportsConditionalWrites`, `supportsCursor`). The
   contract then documents in code exactly what "WebDAV-capable" and "Dropbox-capable" mean.
4. Drive the **engine** against `InMemorySyncTarget` for the bulk of LWW/tombstone/conflict
   tests, and drive each real driver through the same contract suite.

### In-memory fake

`InMemorySyncTarget` should:
- store records in a `MutableMap` keyed by record path;
- model `updatedAt`/`deviceId` metadata and (optionally) ETags/`If-Match` so conditional
  write paths can be exercised without a server;
- simulate a stale ETag (a `failNextConditionalWrite()` hook) to test conflict handling;
- be the default in `commonTest` for the engine.

This is a fake, not a mock: it implements the seam, so tests exercise real engine code
instead of a stubbed call sequence (Robolectric makes the same argument for test doubles
over mock frameworks, [Robolectric](https://robolectric.org/)).

### Integration-testing a real WebDAV driver in CI

WebDAV is the first driver (ADR-0009), so at least one real server must run in CI.

**Choosing a server.** ADR-0009 requires ETags and `If-Match` compare-and-swap, so the test
server must implement conditional requests, not just PUT/GET/PROPFIND:

| Server | Form | Notes |
|---|---|---|
| **Apache `mod_dav`** via `bytemark/webdav` | Docker (`AUTH_TYPE`, `USERNAME`, `PASSWORD`, volume at `/var/lib/dav`) | Widely used (10M+ pulls) and Apache-based, but the image is old ("Updated over 7 years ago") ([Docker Hub](https://hub.docker.com/r/bytemark/webdav/)) |
| `morrisjobke/webdav` | Docker | Apache-based, ~889k pulls, more recent ([Docker Hub](https://hub.docker.com/v2/repositories/morrisjobke/webdav/)) |
| **sabre/dav** | PHP app / Nextcloud's engine | The reference open-source DAV server; supports ETag + `If-Match` ([sabre.io](https://sabre.io/)) |
| **`rclone serve webdav`** | Docker (`rclone/rclone`, 131M+ pulls) or binary | One-flag server: `rclone serve webdav remote:path --addr 127.0.0.1:8080 --user … --pass …`; has `--etag-hash` and no auth by default ([rclone serve webdav](https://rclone.org/commands/rclone_serve_webdav/)) |

**Recommendation:** use **Apache `mod_dav`** (`morrisjobke/webdav` or a small purpose-built
Apache image) or **sabre/dav** as the *conformance* server, because conditional
request semantics are what the WebDAV driver is built on. Keep `rclone serve webdav` as a
second, cheap target if you want breadth — but verify its ETag/`If-Match` behaviour before
trusting it for the CAS tests (flagged in §Unverified). Nextcloud itself is sabre/dav under
the hood, which aligns the test server with the self-hoster persona in ADR-0009.

**Driving it from Kotlin JVM with Testcontainers.** Testcontainers is a JVM library; its
JUnit 5 quickstart shows the pattern: annotate with `@Testcontainers`, declare
`@Container GenericContainer(...)`, expose a port, then read the runtime address/port with
`getHost()`/`getFirstMappedPort()` and **never hard-code `localhost`**; `disabledWithoutDocker = true`
makes the suite skip rather than fail where Docker is absent; `parallel = true` enables
parallel container init ([Testcontainers JUnit 5 quickstart](https://java.testcontainers.org/quickstart/junit_5_quickstart/)).
Its Maven coordinates are `org.testcontainers:testcontainers:2.0.5` (MIT)
([Testcontainers home](https://java.testcontainers.org/)).

```kotlin
// jvmTest of the WebDAV driver module
@Testcontainers(disabledWithoutDocker = true)
class WebDavDriverContractTest : SyncTargetContract() {
    @Container
    val dav = GenericContainer("morrisjobke/webdav:latest")
        .withEnv("USERNAME", "lekto").withEnv("PASSWORD", "lekto")
        .withExposedPorts(80)

    override fun target(): SyncTarget =
        WebDavSyncTarget(baseUrl = "http://${dav.host}:${dav.getMappedPort(80)}/",
                         user = "lekto", password = "lekto")
}
```

Put Testcontainers-based tests in **`jvmTest`** (they are JVM-only). Do not try to run them
from `commonTest` or Kotlin/Native.

**Reaching the server from Android instrumented tests.** The container binds to the host;
the emulator reaches the host loopback via the special alias **`10.0.2.2`** — "To access
services running on your development machine loopback interface, use the special address
`10.0.2.2`" ([Android emulator network address space](https://developer.android.com/studio/run/emulator-networking-address)).
For a physical device (or to use `127.0.0.1`), set up port forwarding with `adb forward`
(the same mechanism is available as `adb reverse`) — "Use the `forward` command to set up
arbitrary port forwarding, which forwards requests on a specific host port to a different
port on a device" ([adb § Set up port forwarding](https://developer.android.com/tools/adb#forwardports)).
Prefer a real Nextcloud/WebDAV instance for **nightly** instrumented end-to-end sync, not for
every push.

---

## 5. Parsing fixtures

### EPUB

Lekto parses EPUB itself (ADR-0007): "we parse EPUB (ZIP + OPF + XHTML) into structured text
in `commonMain`". The format is specified by **EPUB 3.3, a W3C Recommendation dated
13 January 2026**, with a normative test suite at `w3c.github.io/epub-tests` and an
implementation report ([EPUB 3.3](https://www.w3.org/TR/epub-33/)). The ZIP container,
`META-INF/container.xml`, the package document, and the spine are all specified there — these
are the things a golden test should pin.

**Fixture strategy (small, licence-clean):**

1. **Generate as much as possible in-test.** Most awkward cases are small and can be built
   programmatically as an in-memory ZIP (mimetype first, `META-INF/container.xml`, an OPF
   with one or more spine items, XHTML with entities, namespaces, RTL text, nested
   elements, `<br>`, CSS-referenced content). Generated fixtures are tiny, diffable, and
   licence-free (§9).
2. **A handful of real EPUBs for regression**, chosen for *awkwardness*, not size:
   malformed-ish XML, multiple OPF rootfiles, NCX fallback, unusual encodings, fixed-layout,
   remote resources. Good sources:
   - **Standard Ebooks** — "dedicates its own work to the public domain … via the CC0 1.0
     Universal Public Domain Dedication" ([Standard Ebooks About](https://standardebooks.org/about));
     these are clean, well-formed EPUBs with known text, ideal as *positive* goldens.
   - **Project Gutenberg** — classic EPUBs with more variation; the *text* is public domain
     in the US, but Gutenberg's trademark/licence terms apply to the files and must be
     checked per item, and the downloaded file must be checked in only if its licence
     permits redistribution.
3. **Golden text extraction.** Store the expected extracted text as small `.txt` files next
   to the `.epub`, compare exactly. Where extraction order is legitimately ambiguous
   (reading order vs. document order), assert a *normalised* form (whitespace-collapsed) and
   keep a second exact test for a canonical fixture.
4. **A licence manifest.** One `docs/research`/`src/commonTest/resources/FIXTURE-LICENCES.md`
   recording, per fixture: source URL, licence, and whether redistribution is permitted.
   Keep the corpus in `commonTest/resources` (or a dedicated `resources` source set) so it
   ships nowhere.

> **Flag:** I did not verify the licence of the W3C `epub-tests` suite or of individual
> Gutenberg EPUBs. Standard Ebooks CC0 is verified. Check before checking files in.

### PDF

PDF is **best-effort text extraction** (ADR-0007; `CONTEXT.md`). Apache PDFBox is
Apache-2.0 ([PDFBox LICENSE](https://raw.githubusercontent.com/apache/pdfbox/trunk/LICENSE.txt));
the Android port (`pdfbox-android`) is likewise Apache-2.0 per its project. Because the
extraction library is not yet chosen (flagged), the harness should:

- isolate extraction behind a `PdfTextExtractor` interface so the parser test doesn't change
  when the library does;
- generate **tiny one-page PDFs with known text** in-test (PDFBox can write as well as read)
  and store a couple of real public-domain PDFs for regression;
- assert on normalised text plus a small set of structural facts (page count, presence of
  expected tokens), not byte-identical output — PDF text order is inherently fuzzy.

---

## 6. Compose UI testing

### The API

Compose Multiplatform implements "the same finders, assertions, actions, and matchers as the
Jetpack Compose testing API", but the common API is **not JUnit-`TestRule`-based**: you call
`runComposeUiTest { … }` and act on the `ComposeUiTest` receiver. The API is **Experimental**
([Testing CMP UI](https://kotlinlang.org/docs/multiplatform/compose-test.html)).

```kotlin
// commonTest — runs on desktop (jvmTest) and androidDeviceTest; not on Android local tests
@OptIn(ExperimentalTestApi::class)
@Test fun readingProgressSurvivesReload() = runComposeUiTest {
    setContent { ReaderScreen(state = …) }
    onNodeWithTag("word-42").performClick()
    onNodeWithTag("mastery-badge").assertTextEquals("Recognized")
}
```

Setup per the official page: add `org.jetbrains.compose.ui:ui-test:1.12.1` to `commonTest`,
`compose.desktop.currentOs` to `jvmTest`; for Android instrumented tests opt in with
`withDeviceTestBuilder { sourceSetTreeName = "test" }`, set
`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`, add
`ui-test-junit4-android` and `ui-test-manifest`, and create an
`androidDeviceTest/AndroidManifest.xml` pointing at `androidx.activity.ComponentActivity`
([Testing CMP UI](https://kotlinlang.org/docs/multiplatform/compose-test.html)).

**Important constraint:** "Currently, you cannot run common Compose Multiplatform tests using
`android (local)` test configurations" — i.e. the CMP common UI tests do **not** run under
Robolectric/`androidHostTest`; they run on `jvmTest` (fast) or `androidDeviceTest`
(emulator) ([Testing CMP UI](https://kotlinlang.org/docs/multiplatform/compose-test.html)).
So desktop/JVM is the fast UI lane.

Compose Desktop also offers a JUnit-based API for `desktopTest`:
`implementation(compose.desktop.uiTestJUnit4)`, `compose.desktop.currentOs`, and
`createComposeRule()` ([Testing CMP UI with JUnit](https://kotlinlang.org/docs/multiplatform/compose-desktop-ui-testing.html)).

### Screenshot / golden testing — what is mature in 2026

| Tool | Platforms | Status | Headless CI? |
|---|---|---|---|
| **Google Compose Preview Screenshot Testing** (`com.android.compose.screenshot` 0.0.1-alpha16) | **Android only** | Experimental; standalone plugin deprecated in favour of AGP test suites; **"engineered exclusively for Android projects. They don't support non-Android targets in KMP projects"** | host-side yes |
| **Roborazzi** | Android/Robolectric, **Compose Desktop**, **Compose iOS** | Apache-2.0, actively developed, ships an agent skill and a deterministic UI-tree JSON | yes (JVM) |
| **Paparazzi** | Android only | Apache-2.0, but 2.0.0-alpha05 and "incompatible with Robolectric" | yes (JVM) |

Sources: [Android Compose screenshot testing](https://developer.android.com/studio/preview/compose-screenshot-testing)
(updated 2026-09-25; note the explicit KMP limitation), [Roborazzi](https://github.com/takahirom/roborazzi),
[Paparazzi](https://github.com/cashapp/paparazzi).

**Recommendation: Roborazzi**, because it is the only one of the three that covers Compose
Desktop and iOS as well as Android, runs on the JVM (Robolectric Native Graphics), and
provides `record`/`verify`/`compare` Gradle tasks plus GitHub Actions recipes
([Roborazzi README](https://github.com/takahirom/roborazzi)). Its docs also note a real KMP
hazard and the fix: when multiple Roborazzi tasks run in one invocation (e.g. `allTests`),
their shared output directory can race and hard-fail on Gradle 9 — enable
`roborazzi { separateOutputDirs.set(true) }`. Turn that on from day one.

Google's tool remains worth knowing, but for a Compose Multiplatform app it can at most cover
the Android target; do not build the golden strategy around it.

**Golden hygiene:** goldens are environment-sensitive (font rendering, anti-aliasing). Use a
fixed JVM/Robolectric device profile (`@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)`),
and Roborazzi's `changeThreshold` / `SimpleImageComparator` for anti-aliasing tolerance
([Roborazzi README](https://github.com/takahirom/roborazzi)). Store goldens in Git LFS or keep
them few and small.

---

## 7. Determinism and seams for testability

The core rule: **make non-determinism an injected parameter, not an ambient global.** Prefer
interfaces + constructor injection over `expect/actual` for anything complex; the Kotlin docs
themselves say that "in more complex code, a better approach is to use interfaces and factory
functions" ([Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)).

| Non-determinism | Seam | Production | Test |
|---|---|---|---|
| Time | `kotlin.time.Clock` (stdlib; integrated from `kotlinx-datetime` 0.7.0) | `Clock.System` | fixed clock; Kotest 6's `TestClock` (mutable, ms precision) |
| Coroutine scheduling / backoff | injected `CoroutineDispatcher` / `TestScope` | `Dispatchers.Default/IO` | `StandardTestDispatcher(testScheduler)`, `runTest`, `advanceTimeBy`, `advanceUntilIdle` |
| Device id | `value class DeviceId` constructor param | platform-derived, persisted | literal `DeviceId("test-device-a")` |
| Id generation | `fun interface IdGenerator { fun newId(): String }` | `Uuid.random().toString()` | sequential `"0001"`, `"0002"` |
| Randomness | injected `kotlin.random.Random` | `Random.Default` | `Random(seed)` |
| Network | `HttpClientEngine` (Ktor) constructor param | `CIO`/`OkHttp` | Ktor `MockEngine` (ktor-client-mock) |
| Filesystem / vault | `VaultStore` interface | platform paths | `InMemoryVaultStore`; on JVM, Okio `FakeFileSystem` |
| Sync target | `SyncTarget` interface | WebDAV/Dropbox driver | `InMemorySyncTarget` |

Details and citations:

- **`kotlin.time.Clock`.** `kotlinx-datetime` 0.7.0 moved `Instant` and `Clock` into the
  Kotlin standard library ("The Kotlin standard library started including its own, identical
  `kotlin.time.Instant` and `kotlin.time.Clock`"), and the recommended pattern is a
  `fun printCurrentTimeInBerlin(clock: Clock, …)` that takes the clock as a parameter
  ([kotlinx-datetime README](https://github.com/Kotlin/kotlinx-datetime)). Documents carry
  `updatedAt` (ADR-0003), so the clock is a first-class seam.
- **Kotest 6 `TestClock`** — "A new `TestClock` implementation … Mutable Clock that supports
  millisecond precision … Allows setting specific instants and manipulating time with plus
  and minus operations" ([Kotest 6.0](https://kotest.io/docs/next/release6/)).
- **Virtual time for sync scheduling.** `kotlinx-coroutines-test` "provides utilities for
  efficiently testing coroutines" and virtual time "will automatically advance to the point
  of its resumption"; `StandardTestDispatcher` + `TestCoroutineScheduler` expose
  `advanceTimeBy`/`advanceUntilIdle`/`runCurrent`
  ([kotlinx-coroutines-test](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/),
  [Android: testing coroutines](https://developer.android.com/kotlin/coroutines/test)).
  **Do not hard-code `Dispatchers.IO`/`Default` in the sync engine** — a hard-coded dispatcher
  "will NOT see virtual time".
- **`Uuid`** is in the Kotlin standard library (`Uuid.random()`)
  ([kotlin.uuid.Uuid API](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.uuid/-uuid/));
  still inject it so tests are deterministic and readable.
- **Ktor `MockEngine`** "simulates HTTP calls without connecting to the endpoint"; the
  documented pattern is to make the client take an `HttpClientEngine` so the test can pass a
  `MockEngine` ([Ktor client testing](https://ktor.io/docs/client-testing.html)). Ktor is
  Apache-2.0. `MockWebServer` (OkHttp) is a JVM-only alternative if a real socket is needed.
- **Turbine caveat:** its timeout is "a wall clock time timeout that ignores `runTest`'s
  virtual clock time" ([Turbine README](https://github.com/cashapp/turbine)). Keep Turbine
  timeouts short and do not expect `advanceTimeBy` to unblock a Turbine `awaitItem`.
- **`expect/actual` test doubles.** Keep `commonTest` tests framework-agnostic and assert only
  on `kotlin.test` APIs; platform test source sets may use platform frameworks (JUnit 4 on
  Android host tests). The Kotlin docs warn that "the compiler and the IDE prevent you from
  using framework-specific functionality" outside the chosen framework, so decide the
  framework per target deliberately
  ([Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html)).

---

## 8. CI on GitHub Actions

### Matrix and runners

Use the matrix strategy to build the two client targets and run the JVM suite; GitHub
"automatically [creates] multiple job runs that are based on the combinations of the
variables" ([Using a matrix](https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs)).
For the emulator job, the `android-emulator-runner` action supports a matrix over
`api-level`/`target`/`arch` and documents the KVM setup step
([android-emulator-runner README](https://github.com/ReactiveCircus/android-emulator-runner)).
**Use `ubuntu-latest` runners for emulators** — the README states Ubuntu is "2-3 times faster
than the macOS ones which are also a lot more expensive" — and enable KVM before the action:

```yaml
- name: Enable KVM group perms
  run: |
    echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
    sudo udevadm control --reload-rules
    sudo udevadm trigger --name-match=kvm
```

### Caching

Use `gradle/actions/setup-gradle@v6`, which "will use the GitHub Actions cache to save and
restore reusable state", caches the Gradle User Home, and validates the wrapper. It offers
`cache-provider: enhanced` (default; free for public repos) or `basic` ("a fully open-source
(MIT) caching implementation … free for all repositories"). It explicitly warns **not** to
combine it with `actions/setup-java`'s `cache: gradle` or a hand-rolled `actions/cache` of
the Gradle User Home ([setup-gradle docs](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)).

### What to run when

```yaml
name: ci
on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  # 1. Fast lane — every push and PR. No emulator, no Docker required.
  jvm:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v6
        # cache-provider: basic   # uncomment for the MIT provider
      - run: ./gradlew :core:jvmTest :core:testDebugUnitTest :app:desktopTest --no-daemon
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: jvm-reports, path: "**/build/reports/**" }

  # 2. Integration lane — PR and nightly. Needs Docker (present on ubuntu-latest).
  webdav:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew :integrations:webdav:jvmTest :integrations:webdav:contractTest --no-daemon

  # 3. Golden lane — PR and nightly. Headless JVM.
  screenshots:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v6
      - run: ./gradlew :app:verifyRoborazziDesktop --no-daemon
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: screenshot-diff, path: "**/build/outputs/roborazzi/**" }

  # 4. Nightly instrumented lane — emulator; never on the critical path.
  instrumented:
    if: github.event_name == 'schedule'
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        api-level: [29, 34]
    steps:
      - uses: actions/checkout@v6
      - uses: actions/setup-java@v5
        with: { distribution: temurin, java-version: 21 }
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - uses: gradle/actions/setup-gradle@v6
      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: ${{ matrix.api-level }}
          arch: x86_64
          profile: pixel_7_pro
          script: ./gradlew connectedCheck --no-daemon
```

Guidance:

- **Every push:** job 1 only. It must be fast (seconds-to-low-minutes) and hermetic.
- **Every PR:** jobs 1–3. Docker and JVM goldens are cheap and catch real regressions.
- **Nightly:** job 4, plus (later) `iosSimulatorArm64Test` on macOS, plus a real
  Nextcloud/sabre-dav container end-to-end. Use `schedule:` — `setup-gradle` documents the
  `cron:` schedule pattern ([setup-gradle docs](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)).
- **Release:** instrumented on at least two API levels, plus manual soak.
- `fail-fast: false` for the API matrix; `continue-on-error` for experimental legs.
- Upload `build/reports/**`, `build/test-results/**`, and Roborazzi diffs as artifacts (the
  Roborazzi docs show exactly this recipe in their GitHub Actions section).

---

## Layered test pyramid tailored to this stack

| # | Layer | What it covers | Tool | Speed | When it runs |
|---|---|---|---|---|---|
| 1 | **Pure domain (properties)** | tokenisation round-trips, `(language, lemma)` identity, LWW convergence/idempotence/commutativity, tombstones, serialise/parse round-trip | `kotlin.test` + Kotest property (`commonTest` → `jvmTest`) | ms | every save / push |
| 2 | **Flows / async logic** | state streams, progress, backoff, cancellation | Turbine + `kotlinx-coroutines-test` (`commonTest` → `jvmTest`) | ms | every save / push |
| 3 | **Seam contracts** | `SyncTarget` (fake + every driver), `VaultStore`, dictionary/provider adapters | shared contract suite in `:testkit` + `InMemory*` fakes (`commonTest`) | ms–s | push |
| 4 | **Parser goldens** | EPUB (ZIP/OPF/XHTML → tokens), TXT, PDF best-effort | `jvmTest` + generated + small real fixtures | s | push |
| 5 | **Compose UI semantics** | reader interactions, mastery colouring, navigation, a11y | `runComposeUiTest` (`commonTest` → `jvmTest`/`desktopTest`) | s | push |
| 6 | **UI screenshot goldens** | layout, theming, word-colour rendering | Roborazzi (`jvmTest`, Robolectric + Compose Desktop) | s–min | PR / nightly |
| 7 | **Android host** | SAF/file glue, `VaultStore` on Android, resource loading | Robolectric (`androidHostTest`) | s | push |
| 8 | **Real WebDAV integration** | DAV PUT/GET/PROPFIND, ETag/`If-Match` CAS, driver contract | Testcontainers + Apache `mod_dav`/sabre-dav (`jvmTest`) | tens of s | PR / nightly |
| 9 | **Instrumented end-to-end** | real device: SAF, TTS, Keystore, full sync against a real Nextcloud | emulator + `androidDeviceTest` | min | nightly / release |

The pyramid is **wide at 1–4** (thousands of generated cases, no I/O, no device), **narrow at
9**. UI (5–7) is deliberately split so the cheap semantic assertions run everywhere and only
pixels and platform glue need the heavier lanes.

---

## Recommended harness

### Gradle modules

```
lekto/
├── core/                      # KMP library — the product's domain (ADR-0007 commonMain)
│   ├── src/commonMain/         # vault, tokens, identity, merge, sync engine, SyncTarget, parsers
│   ├── src/commonTest/         # domain + property + Turbine + contract tests; test resources (fixtures)
│   ├── src/jvmMain/  src/jvmTest/      # JVM actuals; Testcontainers integration tests
│   ├── src/androidMain/        # Android actuals (VaultStore paths, etc.)
│   ├── src/androidHostTest/    # Robolectric tests (platform glue)
│   └── src/androidDeviceTest/  # instrumented tests (SAF, TTS, Keystore)
├── testkit/                   # KMP library — publishes SyncTargetContract + InMemory* fakes
│   └── src/commonMain/         # contract suite + fakes (consumed via testImplementation)
├── integrations/
│   └── webdav/                # WebDAV SyncTarget driver
│       ├── src/commonMain/
│       └── src/jvmTest/        # Testcontainers contract run
└── app/                       # Compose Multiplatform application (Android + desktop)
    ├── src/commonMain/  src/commonTest/   # screens + runComposeUiTest UI tests
    └── src/desktopTest/                     # Compose Desktop JUnit tests + Roborazzi goldens
```

Notes: `testkit` exists because KMP `commonTest` is not published; put the contract and fakes
in its `commonMain`. `integrations/webdav` gets its own module so a driver can be added or
removed without touching the domain's test compile.

### Source sets and the test task each maps to

| Source set | Targets | Gradle task | Emulator? |
|---|---|---|---|
| `commonTest` | all | run per target | no (on JVM) |
| `jvmTest` | jvm | `:core:jvmTest` | no |
| `androidHostTest` | android | `:core:testDebugUnitTest` (name to verify) | no |
| `androidDeviceTest` | android | `connectedAndroidTest` / `connectedCheck` | **yes** |
| `desktopTest` | jvm (app) | `:app:desktopTest` | no |
| `iosSimulatorArm64Test` (later) | ios | macOS only | simulator |

### Dependencies (pin in `gradle/libs.versions.toml`)

Test-only, all AGPL-compatible (Apache-2.0/MIT):

```toml
[versions]
kotlin = "2.4.20"
kotest = "6.x"          # Apache-2.0
turbine = "1.2.1"       # Apache-2.0
robolectric = "4.x"     # MIT
roborazzi = "x.y.z"     # Apache-2.0
testcontainers = "2.0.5" # MIT
ktor = "3.6.0"          # Apache-2.0
pdfbox = "3.x"          # Apache-2.0

[libraries]
kotlin-test         = { module = "org.jetbrains.kotlin:kotlin-test" }
kotest-framework    = { module = "io.kotest:kotest-framework-engine", version.ref = "kotest" }
kotest-assertions   = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-property     = { module = "io.kotest:kotest-property", version.ref = "kotest" }
turbine             = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
coroutines-test     = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test" }
compose-ui-test     = { module = "org.jetbrains.compose.ui:ui-test", version = "1.12.1" }
robolectric         = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
roborazzi           = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose   = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
testcontainers      = { module = "org.testcontainers:testcontainers", version.ref = "testcontainers" }
testcontainers-junit5 = { module = "org.testcontainers:testcontainers-junit-jupiter", version.ref = "testcontainers" }
ktor-client-mock    = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
okio-fakefilesystem = { module = "com.squareup.okio:okio-fakefilesystem" }
```

Add `implementation(libs.kotest.framework)` + `useJUnitPlatform()` for JVM; add Kotest's
`io.kotest` Gradle plugin only for enhanced IDE support (optional). Enable
`roborazzi { separateOutputDirs.set(true) }` to avoid the KMP `allTests` race.

### CI jobs (final)

1. `jvm` — push + PR: `./gradlew :core:jvmTest :core:testDebugUnitTest :app:desktopTest` (fast, hermetic).
2. `webdav` — PR + nightly: `./gradlew :integrations:webdav:jvmTest` (Testcontainers; `disabledWithoutDocker = true`).
3. `screenshots` — PR + nightly: `./gradlew :app:verifyRoborazziDesktop`.
4. `instrumented` — nightly: emulator matrix `connectedCheck`, KVM enabled.
5. `ios` — nightly (once iOS exists, macOS runner): `iosSimulatorArm64Test`.

### The single command an agent should run

```bash
./gradlew :core:jvmTest :app:desktopTest
```

Everything that must be true of the domain and the UI semantics, on the JVM, in seconds, no
emulator, no Docker, deterministic.

---

## Unverified and flagged

- **Android host/device test task names under the Android-KMP plugin.** The docs rename the
  source sets to `androidHostTest`/`androidDeviceTest` and say "the source sets and
  compilations can be configured", but I did not find the exact new Gradle task names
  (`testDebugUnitTest` vs. something like `testAndroidHostTest`). **[Verify on a real build
  before writing the CI scripts.]** ([Android-KMP plugin](https://developer.android.com/kotlin/multiplatform/plugin))
- **Whether `allTests` includes Android host tests.** The Kotlin docs say `allTests` runs
  every test and that Android reports are *not merged* into `allTests/index.html`; that
  implies Android host tests may or may not be part of the aggregate. Verify.
  ([Test your multiplatform app](https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html))
- **`rclone serve webdav` ETag/`If-Match` fidelity.** rclone advertises `--etag-hash` but
  I did not verify that its WebDAV server implements conditional writes to the standard
  ADR-0009 relies on. Prefer Apache `mod_dav`/sabre-dav for the CAS contract.
  ([rclone serve webdav](https://rclone.org/commands/rclone_serve_webdav/))
- **W3C `epub-tests` suite licence** and **per-item Project Gutenberg licence.** Standard
  Ebooks CC0 is verified; the other two are not. Confirm before checking fixtures in.
- **`kotlin.uuid.Uuid` stability level.** The API page exists in the standard library and
  `Uuid.random()` is documented ([kotlin.uuid.Uuid](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.uuid/-uuid/)),
  but I did not pin the exact Kotlin version at which it became Stable. It is safe to use
  with an explicit `@OptIn` if still experimental on 2.4.20.
- **Kotest 6 KMP requirement (KSP).** The 6.0 release notes say KMP "no longer requires a
  compiler plugin", while the setup page says the compiler plugin "has been replaced … using
  KSP". These are consistent only if KSP is for the optional Gradle/IDE plugin, not the
  framework itself. Verify the minimal Gradle setup. ([Kotest setup](https://kotest.io/docs/framework/project-setup.html),
  [Kotest 6.0](https://kotest.io/docs/next/release6/))
- **PDF extraction library choice.** Not decided in ADR-0007; PDFBox Apache-2.0 verified, but
  `pdfbox-android`'s exact KMP/Android story is unverified. Keep it behind an interface.
- **Compose UI testing API stability.** The CMP test API is marked **Experimental** and "may
  change in the future" ([Testing CMP UI](https://kotlinlang.org/docs/multiplatform/compose-test.html)).
- **Roborazzi/Paparazzi/Kotest/Testcontainers patch versions.** I verified licences and
  capabilities from primary sources, not the latest published patch numbers shown above as
  `x.y.z`; resolve from Maven Central at build time.

---

## References

**Kotlin / KMP / Compose**
- Kotlin releases — https://kotlinlang.org/docs/releases.html
- Understand KMP project structure (source sets, tests) — https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html
- Test your multiplatform app — https://kotlinlang.org/docs/multiplatform/multiplatform-run-tests.html
- Testing Compose Multiplatform UI — https://kotlinlang.org/docs/multiplatform/compose-test.html
- Testing Compose Multiplatform UI with JUnit — https://kotlinlang.org/docs/multiplatform/compose-desktop-ui-testing.html
- `kotlinx-coroutines-test` — https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/
- `kotlin.uuid.Uuid` — https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.uuid/-uuid/
- kotlinx-datetime (stdlib Clock/Instant) — https://github.com/Kotlin/kotlinx-datetime

**Testing libraries**
- Kotest — https://kotest.io/ ; setup — https://kotest.io/docs/framework/project-setup.html
- Kotest 6.0 changes — https://kotest.io/docs/next/release6/
- Kotest property testing — https://kotest.io/docs/proptest/property-based-testing.html
- Kotest property seeds — https://kotest.io/docs/proptest/property-test-seeds.html
- Turbine — https://github.com/cashapp/turbine
- Robolectric — https://robolectric.org/
- Roborazzi — https://github.com/takahirom/roborazzi
- Paparazzi — https://github.com/cashapp/paparazzi
- Testcontainers for Java — https://java.testcontainers.org/ ; JUnit 5 quickstart — https://java.testcontainers.org/quickstart/junit_5_quickstart/
- Ktor client testing (MockEngine) — https://ktor.io/docs/client-testing.html

**Android**
- Android-KMP library plugin — https://developer.android.com/kotlin/multiplatform/plugin
- Compose Preview Screenshot Testing (Android-only; KMP unsupported) — https://developer.android.com/studio/preview/compose-screenshot-testing
- Emulator network address space (`10.0.2.2`) — https://developer.android.com/studio/run/emulator-networking-address
- adb port forwarding — https://developer.android.com/tools/adb#forwardports
- Testing coroutines on Android — https://developer.android.com/kotlin/coroutines/test

**Formats**
- EPUB 3.3 (W3C REC, 2026-01-13) — https://www.w3.org/TR/epub-33/
- Standard Ebooks (CC0) — https://standardebooks.org/about
- Apache PDFBox — https://pdfbox.apache.org/ ; licence — https://raw.githubusercontent.com/apache/pdfbox/trunk/LICENSE.txt

**CI**
- GitHub Actions matrix — https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs
- `gradle/actions/setup-gradle` — https://github.com/gradle/actions/blob/main/docs/setup-gradle.md
- `ReactiveCircus/android-emulator-runner` — https://github.com/ReactiveCircus/android-emulator-runner

**Licences**
- GNU licence list (GPL-compatible / EPL entries) — https://www.gnu.org/licenses/license-list.html
- JUnit 5 licence — https://raw.githubusercontent.com/junit-team/junit5/main/LICENSE.md

**Sync backends**
- sabre/dav — https://sabre.io/
- `rclone serve webdav` — https://rclone.org/commands/rclone_serve_webdav/
- `bytemark/webdav` — https://hub.docker.com/r/bytemark/webdav/
