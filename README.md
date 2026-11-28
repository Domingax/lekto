# Lekto

An open-source, local-first immersive reading app for language learning. Android
first, desktop second. All user data lives on the device in a vault; there is no
server behind the product.

The Kotlin Multiplatform skeleton, the test harness and the CI lanes are in
place; feature work is written against that loop. The internal documentation
starts at [`ARCHITECTURE.md`](ARCHITECTURE.md) and
[`docs/development.md`](docs/development.md); decisions are in [`docs/adr/`](docs/adr/).

## Build and test

```sh
./gradlew check
```

This builds every module, runs the JVM suites and enforces ktlint and detekt. The
Gradle wrapper is committed and the JDK is provisioned by the build, so a clean
checkout needs only a JVM to run the wrapper. The Android SDK is optional;
without one the project builds as a desktop/JVM project. See
[`docs/development.md`](docs/development.md) for the pinned toolchain, the test
harness and the quality gates.

Run the desktop client with `./gradlew :app:run`, and build the Android debug APK
with `./gradlew :app:assembleDebug`. Packaging for Windows, macOS and Linux is in
[`docs/platforms.md`](docs/platforms.md); the desktop's screen-reader limitation
is in [the user guide](guide/desktop.md).

## Where things live

Modules and their allowed dependencies: [`ARCHITECTURE.md`](ARCHITECTURE.md).
The end-user guide is the `guide/` VitePress site; its commands and feature→page
map are in [`guide/AGENTS.md`](guide/AGENTS.md). Decisions are in
[`docs/adr/`](docs/adr/), investigations in [`docs/research/`](docs/research/),
and the domain vocabulary in [`CONTEXT.md`](CONTEXT.md).

## Language-model providers (BYOK)

Phrase translation is bring-your-own-key: Lekto calls the provider directly from the
device with your own API key, and there is no Lekto server in between (ADR-0002,
ADR-0022). Connect one provider in settings, load the models it offers and choose one
(or type a name where a provider has no list). Any service that speaks
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
an export (ADR-0021); the chosen provider, model and base URL are app-private settings
outside the vault (ADR-0023). Settings offers a connection test, so a provider can be
checked before you read.

## Licence

AGPL-3.0. The dictionary pack is a separate CC BY-SA 4.0 artifact and is never
committed here (ADR-0011). See [`docs/dependencies.md`](docs/dependencies.md).
