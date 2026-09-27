# Android and desktop clients are Kotlin Multiplatform, with our own EPUB pipeline

Lekto's clients are **Kotlin Multiplatform with a Compose Multiplatform UI**: `commonMain`
holds the domain (vault, tokenisation, `(language, lemma)` identity, sync engine, provider
adapters); Android is a first-class native target; desktop is a Compose/JVM target added
second.

We rejected Flutter (framework lock-in, a stale EPUB ecosystem, community TTS/keystore
plugins, and a canvas text model) and Tauri/Capacitor (third-party shells, no first-party
SAF). Kotlin meets every Android requirement **first-party** — SAF, `TextToSpeech`, Android
Keystore — with no plugins.

We also rejected **Readium** as the EPUB engine. Readium's Kotlin toolkit is Android-only
and its KMP conversion is future work, so adopting it would give a different reader on each
platform. Lekto's reader is already custom by necessity (tokenisation, mastery colouring,
our own reading-position format), so we parse EPUB (ZIP + OPF + XHTML) into structured text
in `commonMain` and render it ourselves — one reader for both platforms. A short spike
validates the pipeline before the rest of the work proceeds. See
`docs/research/android-first-stack.md`.
