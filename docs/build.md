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
| Coroutines (test)    | 1.11.0    | `gradle/libs.versions.toml` (`kotlinx-coroutines`) |
| Roborazzi (test)     | 1.75.0    | `gradle/libs.versions.toml` (`roborazzi`)       |
| Testcontainers (test)| 2.0.5     | `gradle/libs.versions.toml` (`testcontainers`)  |
| JUnit Jupiter (test) | 5.13.4    | `gradle/libs.versions.toml` (`junit-jupiter`)   |
| ktlint               | 1.8.0     | `gradle/libs.versions.toml` (`ktlint`)          |
| ktlint Gradle plugin | 14.2.0    | `gradle/libs.versions.toml` (`ktlint-gradle`)   |
| detekt               | 2.0.0-alpha.6 | `gradle/libs.versions.toml` (`detekt`)       |

Compose Material 3 versions independently of Compose Multiplatform, which is
why it carries its own pinned version. The JDK is pinned by *language version*:
any conforming 21 distribution is accepted, and the Foojay toolchain resolver
provisions one when none is present. The bytecode target is 17 for every module.

The Gradle wrapper is committed, so a clean checkout does not need Gradle
installed. The Foojay toolchain resolver provisions the pinned JDK if it is
missing, so a clean checkout does not need JDK 21 installed either — only a JVM
able to run the wrapper.

## The one command

```sh
./gradlew check
```

It builds every module, runs the JVM test suites — the domain suite in
`core` and the UI-semantics suite in `app` — and runs the quality gates
(ktlint and detekt, below). See `docs/testing.md` for the harness, the seams
and how to reproduce a failure. CI enforces the command on every push
(ticket #7).

## Quality gates

`./gradlew check` fails the build on any formatting or static-analysis
violation; neither tool is allowed to degrade to a warning. The gates are wired
in the root `build.gradle.kts` and apply to every project, including the root
`*.gradle.kts` scripts.

- **ktlint** (MIT) formats and lints every Kotlin file.
  `./gradlew ktlintFormat` rewrites the tree in place;
  `./gradlew ktlintCheck` only reports. The engine is pinned in the version
  catalog so the formatter does not drift under the Gradle plugin.
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

## CI lanes

The workflows live in `.github/workflows/`. Every push and pull request runs the
fast lane and the licence gate; pull requests also run the WebDAV and golden
lanes; the emulator lane is nightly and never on the critical path (ticket #7).

| Lane | Trigger | Command |
| ---- | ------- | ------- |
| `fast` | push, pull request | `./gradlew check -x :integrations:webdav:jvmTest` |
| `licences` | push, pull request | `./gradlew checkDependencyLicences --no-configuration-cache` |
| `webdav` | pull request | `./gradlew :integrations:webdav:jvmTest` |
| `golden` | pull request | `./gradlew :app:verifyRoborazziDesktop` |
| `instrumented` (`nightly.yml`) | schedule, manual | `./gradlew :app:connectedCheck` on an emulator |

`fast` and `licences` gate merging: branch protection on `main` must require them.
The `webdav` lane needs Docker — present on GitHub's runners — and skips cleanly
where it is absent; the `fast` lane excludes its test so the two do not overlap.
The `instrumented` lane is the only one that needs an emulator; it is scheduled,
so it never slows a change, and its instrumented tests arrive with the platform
work (tickets #16, #21, #24). Vulnerability alerts are a repository setting,
enabled once with
`gh api --method PUT repos/<owner>/<repo>/vulnerability-alerts`.

`tools/dictionaries` builds by its own workflow (ticket #17) and never sits on
the application CI path.

## Prerequisites

- **A JVM to run the wrapper.** Everything else (Gradle, the pinned JDK) is
  provisioned by the build.
- **The Android SDK — optional.** It is only needed for the Android client.
  The build detects an SDK in `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
  `local.properties` (`sdk.dir`) or `~/Android/Sdk`. When one is present the
  Android target activates; when none is present the project still builds and
  tests as a desktop/JVM project. Force either behaviour with
  `-Plekto.android=true` or `-Plekto.android=false`.

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

`tools/dictionaries` is a JVM module built by its own CI workflow (ticket #17).
It never sits on the application CI path.

```sh
./gradlew :tools:dictionaries:run
```

## Modules

| Module                  | What it is                                                          |
| ----------------------- | ------------------------------------------------------------------- |
| `core`                  | The domain: vault, records, merge, tokenisation, word identity, sync engine, parsers. |
| `testkit`               | Contract suites and in-memory fakes shared by the other modules' tests. Published as a library so a KMP `commonTest` set can be shared. |
| `integrations/webdav`   | The first sync driver, isolated from the domain.                    |
| `app`                   | The Compose Multiplatform application (Android + desktop).           |
| `tools/dictionaries`    | The offline dictionary-pack pipeline, built by its own CI workflow.  |

The module boundaries are a decision, not an accident: the domain never depends
on the application, and integrations never leak into the domain. Ticket #9 turns
that into tests.

## Known build noise

AGP 8.13.2's bundled lint analyses with an embedded Kotlin 2.2.0 compiler and
prints `Module was compiled with an incompatible version of Kotlin` while
analysing code compiled by Kotlin 2.4.20. Lint still produces its report and
does not fail the build. This disappears when the toolchain moves to an AGP that
bundles a matching lint.
