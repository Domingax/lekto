# Lekto

An open-source, local-first immersive reading app for language learning. Android
first, desktop second. All user data lives on the device in a vault; there is no
server behind the product.

This repository is at the engineering-foundation stage. The Kotlin
Multiplatform skeleton is in place; the test harness (#5) and the CI that runs
it (#7) come next, and feature work is written against that loop. See issue #1
for the plan and `docs/` for the product and architecture decisions.

## Build and test

```sh
./gradlew check
```

This builds every module and runs the JVM test suites (the domain suite today;
the full harness lands with ticket #5). The Gradle wrapper is committed and the
JDK is provisioned by the build, so a clean checkout needs only a JVM to run the
wrapper. Android additionally needs the
Android SDK; without one the project builds as a desktop/JVM project. See
[`docs/build.md`](docs/build.md) for the pinned toolchain and the platform
commands.

Run the desktop client with `./gradlew :app:run`, and build the Android debug
APK with `./gradlew :app:assembleDebug`.

## Where things live

| Path                    | What it is                                                       |
| ----------------------- | ---------------------------------------------------------------- |
| `core`                  | The domain — vault, records, merge, parsers, tokenisation, word identity, sync engine, dictionary reader. |
| `testkit`               | Contract suites and in-memory fakes for the other modules' tests.|
| `integrations/webdav`   | The first sync driver.                                            |
| `app`                   | The Compose Multiplatform application (Android + desktop).        |
| `tools/dictionaries`    | The offline dictionary-pack pipeline.                             |
| `architecture`          | The architecture tests: module boundaries and naming conventions.|
| `docs/adr/`             | Architecture decisions (authoritative).                           |
| `docs/research/`        | Research reports behind the decisions.                            |
| `CONTEXT.md`            | The domain glossary.                                             |

## Language-model providers (BYOK)

Phrase translation is bring-your-own-key: Lekto calls the provider directly from the
device with your own API key, and there is no Lekto server in between (ADR-0002,
ADR-0022). Connect one provider in settings and choose a model. Any service that speaks
the OpenAI chat-completions API works through the **Custom** preset.

| Provider          | Notes                                          |
| ----------------- | ---------------------------------------------- |
| OpenAI            | API key                                        |
| Anthropic (Claude) | API key                                       |
| Google Gemini     | API key                                        |
| OpenCode Zen      | API key                                        |
| OpenCode Go       | subscription API key                           |
| Ollama            | local; desktop only                            |
| Custom            | any OpenAI-compatible base URL                 |

Keys are stored in the platform keystore or keychain, never in the vault and never in
an export (ADR-0021).

## Licence

AGPL-3.0. The dictionary pack is a separate CC BY-SA 4.0 artifact and is never
committed here (ADR-0011).
