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

It builds every module and runs the JVM test suites. Today that is the domain
suite in `core`; the full fast harness and its UI-semantics tests arrive with
ticket #5. CI enforces the command on every push (ticket #7).

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
