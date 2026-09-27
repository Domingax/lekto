# Client architecture for an Android-first Lekto

**Date:** 2026-09-27
**Scope:** Choose the client stack for Lekto now that the premise has changed: **Android is the primary target, desktop (a "heavy client") is the likely second target, and the browser target is in question**. The founder accepts **separate codebases per platform** if that is simpler, and is **averse to being locked in by a third-party shell/framework**.
**Requirements assessed (per option):** a real user-chosen vault folder (Android SAF read/write without a third-party plugin; plain filesystem on desktop), TTS, secure API-key storage (Android Keystore / OS keychain), EPUB / TXT import with PDF best-effort, a performant immersive reader, offline-first, and an open-source contributor-friendly repo.
**Method:** Primary sources only — official docs, GitHub repos, issue trackers and release notes (Kotlin/JetBrains, Android Developers, Flutter/Dart, Tauri, Capacitor, Readium, .NET/MAUI, Avalonia, React Native, pub.dev, crates.io). Where a source is secondary or a claim could not be verified, it is flagged. Every claim carries a URL. This report **supersedes the premise** of `docs/research/tauri-vs-capacitor.md` and `docs/research/cross-platform-file-access.md`, which assumed one web codebase wrapped for Android; those studies remain valid as primary-source references for the SAF/TTS/secure-storage details they established.

> **Headline:** On an Android-first premise, the file-access and secure-storage gaps that dominated the earlier, web-first study mostly disappear — **native Kotlin gets SAF, `TextToSpeech` and the Android Keystore with zero third-party plugins** ([SAF](https://developer.android.com/training/data-storage/shared/documents-files), [TTS](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Keystore](https://developer.android.com/privacy-and-security/keystore)). The decisive new question is *how much Android and desktop share*. The best fit for "Android first, desktop second, no third-party shell, separate codebases acceptable" is **Kotlin Multiplatform with a Compose Multiplatform UI**, structuring the domain as a shared `commonMain` module from day one and shipping the Android app first, then a Compose/JVM desktop client. The real risk is not the framework — it is the **EPUB reader engine**: Readium's Kotlin toolkit is **Android-only today and KMP conversion is still in progress** ([Readium #547](https://github.com/readium/kotlin-toolkit/discussions/547)). If the web target is kept, Kotlin/Wasm is only **Beta** and Safari-class WasmGC support is the constraint ([Kotlin stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html), [Wasm browser versions](https://kotlinlang.org/docs/wasm-configuration.html)); keeping web points back toward Flutter or Capacitor, at the cost of adopting a framework the founder is wary of.

---

## 0. What changed since the earlier research

The earlier studies concluded "stay on Capacitor" because **the browser target was non-negotiable** and only Capacitor ships it (`docs/research/tauri-vs-capacitor.md`, §3, §5; [Capacitor web docs](https://capacitorjs.com/docs/web)). Both of those premises are now reversed:

1. **Android is primary**, not web.
2. **Separate codebases are acceptable** ("s'il est plus simple de développer individuellement les parties android et web/client lourd, alors faisons comme ça").
3. **A third-party shell/framework is a liability**, not a neutral convenience.

Consequences that reframe the whole comparison:

- The two requirements that previously forced a native plugin on Android — a **user-chosen folder** and **Android Keystore storage** — are **first-party** in native Android (and therefore in KMP's Android target, which *is* native Android). The "every framework needs a SAF adapter" finding from the earlier study still holds for Flutter/Tauri/Capacitor/RN, but **not** for native Android/KMP.
- The web target's weight collapses from "decisive" to "one column in the matrix, and a Beta one for Kotlin/Wasm".
- The interesting axis becomes **shared code**: how much of the reader, vault and provider adapters can be written once, and whether the shared core is worth its friction.

Two internal decisions constrain any recommendation and are carried forward: the vault is a **capability-driven seam**, not a guaranteed folder (ADR-0001), and there is **no backend server** — all network calls go client → user's chosen services (ADR-0002). Record-per-file JSON with atomic writes (ADR-0003) and `(language, lemma)` word identity (ADR-0006) are the format-level facts a shared core must implement.

---

## 1. Native Android (Kotlin + Jetpack Compose) + a separate desktop client

**Status:** Jetpack Compose is the Android UI standard: Google announced at I/O 2026 that it is **"Compose-first, meaning that all future UI development will happen only in Compose, while the Views toolkit enters maintenance mode"**, with **>68% of the top 1,000 apps using it in production**; the current line is 1.11 (1.12 imminent) ([5 years of Jetpack Compose](https://android-developers.googleblog.com/2026/07/five-years-of-jetpack-compose.html), [Compose-first](https://developer.android.com/develop/ui/compose/first)).

**File access (Android):** first-party, no plugin. `ACTION_OPEN_DOCUMENT_TREE` (API 21+) grants access to a directory and its children; `ContentResolver.takePersistableUriPermission()` preserves the grant across reboots; the app reads via `openInputStream`/`openFileDescriptor` and creates/writes via `DocumentsContract` / `ContentResolver.openOutputStream`; none of it needs a manifest permission ([SAF doc](https://developer.android.com/training/data-storage/shared/documents-files), [`DocumentsContract`](https://developer.android.com/reference/android/provider/DocumentsContract)). Caveats to design around, both documented: on Android 11+ the picker cannot select the internal-storage root, the root of "reliable" SD volumes, `Download/`, or `Android/data|obb/`; and the grant is lost if the user moves/deletes the folder ([SAF doc](https://developer.android.com/training/data-storage/shared/documents-files)). This is exactly the capability-driven `SafVaultStore` seed from ADR-0001 — but without the "third-party plugin" tax the earlier reports attached to it.

**File access (desktop):** whichever desktop stack is chosen, it has plain filesystem access (native), so the capability set is the full `VaultCapabilities` (`userVisible`, `relocatable`, `externalSync`, `liveFolder`). No SAF equivalent is needed.

**TTS:** first-party `android.speech.tts.TextToSpeech`, including the Android 11+ requirement to declare `TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE` in the manifest `<queries>` and to feature-detect voices/language packs at runtime ([`TextToSpeech`](https://developer.android.com/reference/android/speech/tts/TextToSpeech); the `<queries>` requirement is documented in the [`flutter_tts` Android notes](https://pub.dev/packages/flutter_tts)). Desktop TTS depends on the desktop stack (see §2 for KMP, §6 for Flutter/Tauri).

**Secure storage:** first-party **Android Keystore** — key material never enters the app process and can be hardware-bound (TEE/StrongBox), with optional user-authentication binding ([Android Keystore](https://developer.android.com/privacy-and-security/keystore)). This satisfies the product brief's "API key storage … using OS-level secure storage (Android Keystore on Android)" directly ([product brief](./product-brief-2026-03-02.md), Security Constraints).

**EPUB / PDF / TXT + reader:** the strongest option by far on Android. **Readium Mobile Kotlin toolkit** is a mature EPUB/audiobook/comics toolkit whose current minimum is Android API 24 and which ships EPUB, PDF and image navigators and an EPUB rendering engine ([kotlin-toolkit](https://github.com/readium/kotlin-toolkit), [3.0.0-alpha.1](https://github.com/readium/kotlin-toolkit/discussions/451), [3.2.0 release note](https://blog.readium.org/release-note-kotlin-toolkit-version-3-2-0/)). For a Lekto-style reader (per-word tokens and mastery colouring), Readium offers deco/rendering hooks, but the exact seam for **per-word interaction and colouring** would have to be validated against Readium's navigator API before committing — this report does **not** verify that seam (see §10). PDF best-effort text extraction is separate (pdfbox-android / PDFium bindings) — also unverified here.

**Do we get a browser target?** **No.** Native Android/Compose has no browser target; a web client would be a third, separate codebase.

**Lock-in / replaceability:** **the lowest of any option.** You are writing directly against OS APIs and JetBrains' Compose, which is an open-source library, not a runtime shell or bridge. There is no third-party framework to be "stuck behind": leaving Compose means rewriting UI, but the domain, vault code and platform integrations are plain Kotlin/Android and fully portable.

**Contributor accessibility:** excellent for Android — the largest mobile ecosystem, Kotlin is Google's Android language, Compose-first is official, and the tooling is Android Studio ([5 years of Jetpack Compose](https://android-developers.googleblog.com/2026/07/five-years-of-jetpack-compose.html)). The weakness is the **second codebase**: a separate desktop client means a second language/stack and duplicated reader + vault-spec logic.

**Desktop client — which one?** If Android stays pure-native, the desktop is a fork in the road:
- **Compose/JVM desktop** (JetBrains' Compose for Desktop) shares Kotlin and can share the domain — but at that point you are effectively doing KMP (§2).
- **Web-technology desktop (Tauri/Electron)** reuses HTML/CSS text rendering (excellent for word-level styling and text selection) and a web reader engine (`epub.js`), but introduces a third-party shell the founder is wary of, and duplicates the reader engine versus Android's Compose reader.
- **Flutter desktop** (see §6) is a full second framework.
There is no free lunch: a *truly separate* desktop client duplicates the reader; the only way to avoid duplication is to share a UI framework (Compose Multiplatform or Flutter), which is §2/§6.

**Verdict (1):** The best possible **Android** client, and the only option whose Android requirements (vault, TTS, keys) are met with **zero third-party dependencies**. Its cost is the second codebase and the duplicated reader — which the shared-UI options below exist to remove.

---

## 2. Kotlin Multiplatform (KMP): shared Kotlin domain, Android + Desktop via Compose Multiplatform

**Status:** KMP is stable for the platforms Lekto needs: the official stability page lists **Android: Stable, iOS: Stable, Desktop (JVM): Stable**, and **Compose Multiplatform 1.12.1 supports Android, iOS, macOS 13 arm64, Windows 10, Linux (Ubuntu 20.04) and Web** ([KMP stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html), [CMP compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)). On Android, Compose Multiplatform **uses Google's Jetpack Compose artifacts**, so Android remains fully native ([CMP compatibility § Jetpack Compose artifacts](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)). Recent releases (1.11, May 2026) improved iOS and **web scrolling**, and 1.12 added a desktop window/dialog v2 API and MCP tooling for Compose Hot Reload ([CMP 1.11](https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/), [CMP 1.12](https://blog.jetbrains.com/kotlin/2026/08/compose-multiplatform-1-12-0/)).

**File access (Android):** identical to §1 — KMP's Android target *is* native Android, so SAF read/write with `ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission` + `DocumentsContract` is first-party, no plugin ([SAF doc](https://developer.android.com/training/data-storage/shared/documents-files)).

**File access (desktop):** plain JVM filesystem I/O (`java.io`/`java.nio`), so the full vault capability set including a live, user-visible folder.

**TTS:** Android first-party ([`TextToSpeech`](https://developer.android.com/reference/android/speech/tts/TextToSpeech)). Desktop is the gap. The leading KMP TTS library, **TextToSpeechKt**, supports Android, iOS, macOS and browser (JS/Wasm) but marks **Desktop (Kotlin/JVM) as experimental** ([TextToSpeechKt](https://github.com/Marc-JB/TextToSpeechKt)). On Windows/Linux the realistic paths are a JVM/OS binding you write, or an `expect/actual` around the platform engine — a real (if bounded) desktop cost that Flutter shares on Linux (§6).

**Secure storage:** Android Keystore is first-party ([Keystore](https://developer.android.com/privacy-and-security/keystore)). For a portable API, **KSafe** is a KMP key/value store that is **encrypted (AES-256-GCM) by default**, supports **Android, iOS, macOS, JVM/Desktop, Wasm and JS**, uses hardware-backed keys where available, and whose JVM protection is documented as **Windows DPAPI / macOS Keychain / Linux Secret Service (libsecret)** with a software fallback ([KSafe](https://github.com/ioannisa/KSafe), [JVM protection](https://github.com/ioannisa/KSafe/blob/main/docs/JVM_PROTECTION.md)). That covers the brief's "Android Keystore on Android; encrypted local storage on web" as an OS-keychain story on desktop too. (Note the Compose Desktop packaging requirement: add `jdk.unsupported`/`java.management` modules for OS-backed key custody — [KSafe Setup](https://github.com/ioannisa/KSafe#setup).)

**EPUB / PDF / TXT + reader — the decisive risk.** Readium's Kotlin toolkit is the best EPUB engine in this ecosystem, but it is **Android-only (JVM/Android) today**. In the project's own discussions the maintainers state the KMP conversion is **not done**, that they will "pivot to Android by the end of 2026" and only then "experiment with converting shared models to KMP", and that a Compose Multiplatform navigator "is not a priority" and would need sponsoring or a third-party contribution ([Readium #547](https://github.com/readium/kotlin-toolkit/discussions/547)). So on a Compose Multiplatform **desktop** target you would **not** get Readium's EPUB rendering for free; you would either (a) use Readium only on Android and build a separate desktop parser/renderer (e.g. a JVM EPUB parser + Compose text rendering), or (b) contribute the KMP/conversion work upstream. This is the single most important finding for the KMP option and the strongest argument against it (see §7 counter-argument). PDF remains best-effort on both.

**Do we get a browser target?** **Beta, and conditional.** KMP's core web target is **Kotlin/Wasm: Beta** and **Kotlin/JS: Stable**; Compose Multiplatform for web is **Beta** ([KMP stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)). Kotlin/Wasm "already shows encouraging performance traits … outperforms JavaScript and is approaching that of the JVM", but requires a **browser with WasmGC** support ([Kotlin/Wasm](https://kotlinlang.org/docs/wasm-overview.html), [browser versions](https://kotlinlang.org/docs/wasm-configuration.html)). Practically: a Kotlin/Wasm web app is viable on Chromium/Firefox-class engines but is a Beta deliverable and cannot be assumed on all engines. For a product whose web target is already "in question", this is a *later, optional* client — not a reason to choose or reject KMP by itself.

**Lock-in / replaceability:** low-to-moderate, and **not a "third-party shell"**. KMP/CMP are an open-source language + UI library that compile to native/JVM/Wasm; there is no hosted runtime or bridge that owns your app. The shared UI is the main coupling: leaving Compose means rewriting UI, but the shared domain stays plain Kotlin. This aligns well with the founder's "je ne veux pas me retrouver coincé par une techno tierce" concern — Kotlin/JetBrains is a foundation-governed open ecosystem, not a wrapper.

**Contributor accessibility:** strong and growing. Kotlin is the Android-ecosystem language, so Android contributors are immediately productive; the KMP surface (Gradle, `expect/actual`, Compose Multiplatform) adds a learning step, and Compose Desktop packaging uses `jpackage` (JDK 17+) ([CMP compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)). The community is smaller than Flutter's but the "native Android skill" transfer is direct.

**Shared code:** **the best of any option.** One Kotlin codebase can share the vault seam, the on-disk JSON schema (ADR-0003), tokenisation rules, `(language, lemma)` identity (ADR-0006), dictionary/LLM provider adapters (ADR-0002), TTS abstraction, and the UI (Compose). Android stays native; desktop is JVM.

**Verdict (2):** Aligns most precisely with the new premise — Android-first, no third-party shell, separate codebases *not even required*, shared domain + UI. The two caveats are the **Android-only EPUB engine** and **Beta web**.

---

## 3. Flutter (Android + desktop + web)

**Status:** Flutter 3.47; the desktop targets **Windows 10/11, macOS 12–27, and Debian/Ubuntu Linux** are supported and CI-tested, and web is supported (JS on all engines; **WebAssembly on Chrome/Edge and Firefox**) ([supported platforms](https://docs.flutter.dev/reference/supported-platforms), [desktop](https://docs.flutter.dev/platform-integration/desktop)).

**File access (Android):** **not first-party for a durable vault.** Flutter's official `file_selector` advertises "Choose a directory — pick a directory and get its path" on Android ([`file_selector`](https://pub.dev/packages/file_selector)), but its Android implementation (`getDirectoryPath`) uses `ACTION_OPEN_DOCUMENT_TREE` and then converts the tree's document URI to a **filesystem path** — it **does not call `takePersistableUriPermission()`** and returns a path rather than an SAF handle ([`FileSelectorApiImpl.java`](https://github.com/flutter/packages/blob/main/packages/file_selector/file_selector_android/android/src/main/java/dev/flutter/packages/file_selector_android/FileSelectorApiImpl.java)). The community `file_picker` has the same class of problem, tracked as "getDirectoryPath() does not grant permissions to its contents" ([file_picker #1781](https://github.com/miguelpruivo/flutter_file_picker/issues/1781)). So a durable read/write vault needs a **custom platform channel / native plugin** (Kotlin + SAF), exactly as in the earlier Capacitor/Tauri studies. This is small, well-understood code — but it is code you own.

**File access (desktop):** plain filesystem via `dart:io` — full capability.

**TTS:** community `flutter_tts` — Android, iOS, macOS, web, Windows; **Linux is not listed**; it wraps Android `TextToSpeech` and documents the `<queries>` requirement and the Android pause workaround ([flutter_tts](https://pub.dev/packages/flutter_tts)). So desktop TTS has a gap on Linux and is a community plugin, not first-party.

**Secure storage:** community `flutter_secure_storage` — Android (Keystore-backed RSA-OAEP + AES-GCM, biometrics), iOS/macOS Keychain, Windows, Linux (libsecret), **web "experimental … use at your own risk"** ([flutter_secure_storage](https://pub.dev/packages/flutter_secure_storage), [changelog](https://pub.dev/packages/flutter_secure_storage/changelog)). The Android story is solid; it is not first-party.

**EPUB / PDF / TXT + reader:** community-only and shallower than Readium. The parser most viewers build on, `epubx`, was **last published in 2023** (v4.0.0) and has **74 likes**; `epub_view` (161 likes, last published Aug 2026) is a pure-Dart widget renderer built on it ([epubx](https://pub.dev/packages/epubx), [epub_view](https://pub.dev/packages/epub_view)). PDF is better served by `pdfrx`, a mature, actively maintained (Sep 2026) PDFium-based viewer for Android/iOS/Windows/macOS/Linux/Web ([pdfrx](https://pub.dev/packages/pdfrx)). For Lekto's per-word colouring and selection you would render EPUB content yourself (`flutter_html`-style) rather than use a viewer package — feasible but more DIY than Readium. Text selection/rendering is canvas-based in Flutter, not DOM, which matters for word-tap precision and accessibility.

**Do we get a browser target?** **Yes** — a real web build from the same codebase, including Wasm on Chromium/Firefox ([supported platforms](https://docs.flutter.dev/reference/supported-platforms)). This is Flutter's unique advantage if web stays.

**Lock-in / replaceability:** moderate-to-high. Flutter is Google-maintained, the UI is a bespoke widget tree in Dart, and leaving means rewriting the entire UI; you also adopt a full framework runtime (the opposite of the founder's stated preference). The domain in pure Dart is portable-with-effort, but the codebase is framework-shaped.

**Contributor accessibility:** excellent on paper — one language (Dart), one framework, ~179k GitHub stars on the main repo (seen in the [`flutter/flutter`](https://github.com/flutter/flutter) page chrome), vast docs and community. The catch for a reading app is the canvas-based text model: contributors used to DOM/Compose text selection face a different mental model.

**Shared code:** one codebase for Android + desktop + web — the most sharing of any option. But "shared" here includes the SAF gap that KMP gets for free.

**Verdict (3):** The most *compact* single-codebase story (Android + desktop + web), with mature desktop support and a huge community, at the cost of a **casualty in the founder's framework-aversion**, a **community/secured storage stack instead of first-party OS APIs**, a **weaker EPUB ecosystem** than Readium, and **writing the Android SAF vault yourself**.

---

## 4. Tauri v2 (desktop + Android; no browser)

**Status:** Tauri 2.0 stable (Oct 2024) covers desktop **and mobile**; the current line is 2.12 ([What is Tauri](https://v2.tauri.app/start/), [Tauri 2.0](https://tauri.app/blog/tauri-20/)). Tauri targets **"desktop and mobile platforms"** and its APIs "only work in your app window, so once you start using them you won't be able to open your frontend in your system's browser anymore" ([What is Tauri](https://v2.tauri.app/start/)). **There is no browser target** — confirmed.

**File access (Android):** **not first-party.** The official plugin table shows `dialog` and `fs` flagged with footnotes on Android ([plugins support table](https://v2.tauri.app/plugin/)); the dialog plugin documents **"Does not support folder picker"** on Android/iOS, and `fs` "is restricted to Application folder by default" ([dialog](https://v2.tauri.app/plugin/dialog/), [fs](https://v2.tauri.app/plugin/file-system/)). The maintainers' own tracker explains why: Android's tree picker returns a URI, and `tauri_plugin_fs` "does not allow listing the contents of a directory or creating new files within a directory from a URI" ([plugins-workspace #933](https://github.com/tauri-apps/plugins-workspace/issues/933), [tauri #14587](https://github.com/tauri-apps/tauri/issues/14587)). The missing capability is filled by the **community** `tauri-plugin-android-fs` (SAF directory selection, `read_dir`, `create_new_file`, `write`, etc.), which is listed as a community plugin on Tauri's own plugins page ([plugins page](https://v2.tauri.app/plugin/), [tauri-plugin-android-fs](https://github.com/aiueo13/tauri-plugin-android-fs)). Maturity caveat carried from the earlier study: ~39 stars, effectively one maintainer.

**File access (desktop):** Rust `fs` with full filesystem access — strong.

**TTS:** no first-party plugin; community `tauri-plugin-tts` delegates to the OS synthesizer including Android `TextToSpeech` (~21 stars) ([tauri-plugin-tts](https://github.com/brenogonzaga/tauri-plugin-tts)).

**Secure storage:** no first-party OS-keychain plugin; the official option is **Stronghold**, a password-gated encrypted database, not a Keystore/keychain wrapper ([Stronghold](https://v2.tauri.app/plugin/stronghold/)). Community keystore/keyring plugins exist but are third-party.

**EPUB / PDF / TXT + reader:** the reader would be web-technology (`epub.js` etc.) in the webview — good for word-level styling/selection, but a different engine from any Android-native client, so a Tauri desktop + native-Android split duplicates the reader.

**Do we get a browser target?** **No** ([What is Tauri](https://v2.tauri.app/start/), [plugins table without web](https://v2.tauri.app/plugin/)).

**Lock-in / replaceability:** moderate-to-high — Tauri is the shell, the frontend is standard web and the backend is Rust; most app logic that touches Tauri APIs is Tauri-shaped, and mobile support is younger than desktop with real per-plugin gaps ([Tauri 2.0](https://tauri.app/blog/tauri-20/)).

**Contributor accessibility:** the heaviest toolchain. Android builds need Android Studio + SDK + **NDK** + Rust targets and `rustup target add …` ([Prerequisites](https://v2.tauri.app/start/prerequisites/)). That raises the bar for the "forkable, contributable from day one" goal.

**Verdict (4):** A strong **desktop** framework, but on Android it is the *weakest* fit of the options that claim Android support: no first-party folder picker, a 39-star community plugin as the vault foundation, no first-party keychain, and the heaviest contributor toolchain. On an Android-**first** premise, Tauri drops from "the close runner-up" to a desktop-only candidate.

---

## 5. Capacitor (Android + real browser target)

**Status:** v8 current; Capacitor "fully supports traditional web and Progressive Web Apps" and ships a real browser/PWA target from the same assets ([Capacitor web](https://capacitorjs.com/docs/web)). It is Android + iOS + web; there is **no true desktop binary**.

**File access (Android):** **not first-party** — established in `docs/research/cross-platform-file-access.md`: `@capacitor/filesystem` has no tree picker and cannot create/overwrite `content://` URIs (read/delete only), so a native SAF plugin is required ([Capacitor Filesystem](https://capacitorjs.com/docs/apis/filesystem); the underlying `IONFILEController.kt` caveats are cited in the companion study). This remains true; nothing here changes it.

**File access (desktop):** there is no desktop target; "desktop" means the user's browser, so the vault is the File System Access API on Chromium only (Firefox/Safari unsupported) per the companion study.

**TTS:** community `@capacitor-community/text-to-speech` (~130 stars, org-owned, robingenz), wrapping Android `TextToSpeech` with `openInstall()` for missing language packs and an `onRangeStart` listener ([repo](https://github.com/capacitor-community/text-to-speech)).

**Secure storage:** community `@aparajita/capacitor-secure-storage` (~169 stars): Android AES-GCM with an Android Keystore-generated key, iOS keychain, **web unencrypted `localStorage` for debugging only** ([repo](https://github.com/aparajita/capacitor-secure-storage)). The web caveat directly conflicts with the brief's "encrypted local storage on web".

**EPUB / PDF / TXT + reader:** web ecosystem (`epub.js`, `pdf.js`) — good for DOM-based word-level styling, shared with web, and reusable on desktop *if* desktop ever means "a browser".

**Do we get a browser target?** **Yes, first-class** ([Capacitor web](https://capacitorjs.com/docs/web)). This is its entire reason to stay.

**Lock-in / replaceability:** moderate. Capacitor is a shell bridging to native; the web UI is portable, but the app depends on Capacitor + community plugins for the core vault/TTS/secure-storage capabilities — several of them single-maintainer.

**Contributor accessibility:** low-friction (Node + Android Studio; no Rust), which is why it scored well on a web-first premise.

**Verdict (5):** **Only wins if the web target stays.** On an Android-first premise it is strictly dominated by native Android/KMP for the vault, TTS and keys, and it has no desktop-binary story.

---

## 6. Other credible options

### 6.1 React Native

- **Platforms:** Android + iOS natively; **Windows** via `react-native-windows` and macOS via `react-native-macos` (Microsoft). The latest Windows release, **0.84.0 (30 Jun 2026), "targets React Native 0.84.0"** ([RNW releases](https://github.com/microsoft/react-native-windows/releases)), so the Windows lag is one minor series; macOS is maintained separately and has historically lagged more (the platform.uno comparison is a **secondary** source claiming Windows ~3 / macOS ~6 minor-series lag as of Aug 2026 — treat the macOS figure as unverified).
- **Vault:** community `react-native-saf-x` "Open the Document Picker to select a folder. Read/Write Permission will be granted to the selected folder" ([react-native-saf-x](https://github.com/jd1378/react-native-saf-x)) — i.e. a community SAF plugin, same shape as Tauri/Capacitor, not first-party.
- **TTS:** community `react-native-tts` — the npm listing shows **last published ~2 years ago**, which is a staleness flag ([react-native-tts](https://www.npmjs.com/package/react-native-tts), [repo](https://github.com/ak1394/react-native-tts)).
- **Secure storage:** `react-native-keychain` — established library providing iOS Keychain / Android Keystore ([react-native-keychain](https://github.com/oblador/react-native-keychain)).
- **Reader:** JS EPUB engines (`epub.js`) or native; text selection is native. Three desktop targets with three maturity levels make this a weaker fit than Flutter for a "Android + one desktop" product. **Verdict: does not clearly beat Flutter or KMP; not recommended.**

### 6.2 .NET MAUI

- **Platforms:** officially **Android, iOS, Mac Catalyst, Windows**; **no Linux** ([MAUI supported platforms](https://learn.microsoft.com/en-us/dotnet/maui/supported-platforms)). MAUI Blazor adds browser via Blazor WebAssembly. AvaloniaUI has previewed MAUI-on-Linux/WASM support, but that is a third-party add-on, not Microsoft support ([DevClass](https://www.devclass.com/development/2026/03/24/avaloniaui-enhances-net-maui-with-linux-and-webassembly-support/5209515) — secondary).
- **Secure storage:** first-party `ISecureStorage` using Android `EncryptedSharedPreferences` (AES-256-GCM) / iOS Keychain / Windows `DataProtectionProvider` ([MAUI secure storage](https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/storage/secure-storage)). Note the documented Auto Backup key-decryption failure mode and the recommendation to wrap calls in try/catch ([same](https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/storage/secure-storage)).
- **TTS / file picker:** Essentials provides a file picker but **no folder picker**, so an Android vault still needs platform code for SAF. **Verdict: the no-Linux desktop gap and the folder-picker gap make it a poorer fit than KMP; not recommended.**

### 6.3 Avalonia (with Compose Multiplatform as the Kotlin analogue)

- **Platforms:** Windows (Tier 1), macOS (Tier 1), desktop Linux (Tier 1), iOS/Android (Tier 1/2, following the MAUI support lifecycle), **WebAssembly** (all browsers with full Wasm support, .NET 8+) ([Avalonia supported platforms](https://docs.avaloniaui.net/docs/supported-platforms)). This is a genuinely broad matrix and the closest .NET analogue to KMP+CMP.
- **Reader / TTS / keys:** relies on .NET EPUB/PDF libraries and platform/community services; the ecosystem for an immersive reader is smaller than Readium/Kotlin or Flutter. **Verdict: credible for a desktop-first product, but Android-first favours Kotlin (first-party SAF/TTS/Keystore) over .NET. Not recommended over KMP.**

### 6.4 A shared non-Kotlin core (Rust via uniffi / WASM)

- A Rust core compiled with **uniffi** could back a Kotlin Android app and a non-Kotlin desktop/web client, sharing tokenisation and vault logic across languages. This is only worth its FFI cost if the clients are **polyglot** (e.g. native Android + Rust/Tauri desktop). If both clients are Kotlin/JVM (KMP), a shared Kotlin module is simpler and needs no FFI. **Verdict: relevant only to the "truly separate, different-stack desktop" path; over-engineering otherwise** (see §8).

---

## 7. Decision matrix (criterion × option)

Legend: ✅ first-party / fully met · 🟡 workable, with the noted caveat · ❌ not met / absent.

| Criterion | Native Android + separate desktop | **KMP + Compose Multiplatform** | Flutter | Tauri v2 | Capacitor | React Native | .NET MAUI | Avalonia |
|---|---|---|---|---|---|---|---|---|
| **Android vault: pick tree + read/write + persist, no plugin** | ✅ first-party (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission` + `DocumentsContract`) | ✅ first-party (Android target *is* native) | ❌ `file_selector` returns a path, no persistable grant → custom plugin | ❌ dialog has no folder picker; `fs` can't list/create in a tree → community plugin | ❌ `@capacitor/filesystem` can't write `content://`; no tree picker → native plugin | ❌ community `react-native-saf-x` | ❌ Essentials has no folder picker → platform code | ❌ platform code for SAF |
| **Desktop plain filesystem** | ✅ (whichever desktop stack) | ✅ JVM `java.io`/`nio` | ✅ `dart:io` | ✅ Rust `fs` | ❌ no desktop binary (browser only) | 🟡 RNW/RN-macOS | ✅ Win/macOS; ❌ Linux | ✅ Win/macOS/Linux Tier 1 |
| **TTS on Android** | ✅ first-party `TextToSpeech` | ✅ first-party | 🟡 community `flutter_tts` | 🟡 community `tauri-plugin-tts` (~21★) | 🟡 community `@capacitor-community/text-to-speech` (~130★) | 🟡 community `react-native-tts` (stale ~2y) | ✅ Essentials `TextToSpeech` | 🟡 platform/community |
| **TTS on desktop** | depends on desktop stack | 🟡 Android/iOS/macOS/browser via TextToSpeechKt; **Desktop experimental**; Windows/Linux gap | 🟡 Windows/macOS; **Linux missing** | 🟡 community | n/a (browser) | 🟡 community | ✅ Win; macOS; Linux n/a | 🟡 platform |
| **Secure storage: Android Keystore** | ✅ first-party Keystore | ✅ first-party (+ portability via KSafe) | 🟡 community `flutter_secure_storage` (Keystore-backed) | 🟡 no first-party keychain; official Stronghold is a password DB | 🟡 community `@aparajita/...` | 🟡 `react-native-keychain` | ✅ `ISecureStorage` (EncryptedSharedPreferences) | 🟡 platform/community |
| **Secure storage: desktop keychain** | depends on desktop stack | ✅ KSafe: Win DPAPI / macOS Keychain / Linux Secret Service | 🟡 Keychain/Win/libsecret via plugin | 🟡 community | ❌ (web = unencrypted) | 🟡 `react-native-keychain` (macOS) | ✅ macOS Keychain / Win DPAPI; Linux n/a | 🟡 platform |
| **EPUB parse/render ecosystem** | ✅ **Readium Kotlin (Android)** — mature | 🟡 Readium Android-only today; KMP conversion in progress; desktop needs own engine | 🟡 `epubx` (stale 2023) + `epub_view`; DIY for word-level | 🟡 web `epub.js` | 🟡 web `epub.js` | 🟡 JS/native mix | 🟡 .NET libs (e.g. VersOne.Epub) | 🟡 .NET libs |
| **PDF best-effort** | ✅ native PDF libs | 🟡 Android native; desktop separate | ✅ `pdfrx` (mature, PDFium) | 🟡 web `pdf.js` | 🟡 web `pdf.js` | 🟡 | 🟡 | 🟡 |
| **Browser target** | ❌ | 🟡 **Beta** (Kotlin/Wasm; WasmGC browsers) | ✅ JS all engines; Wasm Chromium/Firefox | ❌ | ✅ **first-class** | 🟡 via react-native-web | 🟡 MAUI Blazor | 🟡 WebAssembly (.NET 8+) |
| **Lock-in / third-party shell** | ✅ lowest (OS APIs + OSS Compose; no shell) | ✅ low (OSS language + UI lib; no shell/bridge) | ❌ framework-shaped (Google, Dart widget tree) | ❌ Tauri is the shell; Rust+NDK | ❌ Capacitor shell + community plugins | ❌ RN ecosystem fragmentation | 🟡 .NET framework | 🟡 .NET framework |
| **Contributor accessibility** | ✅ Android ecosystem, Compose-first | ✅ Kotlin/Android + Gradle learning step | ✅ huge; one language; canvas text model | ❌ Rust + NDK + Android Studio | ✅ Node + Android Studio | 🟡 | 🟡 | 🟡 smaller |
| **Shared code Android↔desktop** | ❌ unless desktop is Compose/KMP | ✅ **domain + UI** | ✅ **domain + UI (+web)** | ❌ (web vs native) | ❌ (web only) | 🟡 | ✅ | ✅ |
| **Desktop target quality** | depends on desktop stack | ✅ Compose Desktop stable | ✅ supported/CI-tested | ✅ Tauri's strength | ❌ none | 🟡 Windows ahead of macOS | ✅ Win/macOS | ✅ Win/macOS/Linux Tier 1 |

---

## 8. Shared-code analysis: what is actually shareable, and what KMP buys

If Android and desktop are split, the honest inventory of what can be shared:

| Artifact | Shareable? | Notes |
|---|---|---|
| **Vault format spec** (per-record JSON, `manifest.json`, atomic writes) | ✅ always, as a **spec** | ADR-0003. A spec is shareable even across unrelated stacks; the implementations are per-platform. |
| **Record schema / serialization** (fields `id`, `schemaVersion`, `updatedAt`, `deviceId`) | ✅ in a shared core | Trivial to duplicate, but duplication invites drift. |
| **Tokenisation rules** (word boundaries, per-language segmentation) | ✅ in a shared core | Small, deterministic, high-value to share — this is the reader's heart. |
| **`(language, lemma)` identity + normalisation** (ADR-0006) | ✅ in a shared core | Pure logic; the dictionary pack supplies lemma↔form mappings. |
| **Dictionary / LLM provider adapters** (HTTP, BYOK, no server) | ✅ in a shared core | ADR-0002. Portable HTTP logic; per-platform secure key access stays native. |
| **Reader rendering (WordToken, colouring, selection, perf)** | 🟡 only if the **UI framework** is shared | This is where "shared UI" (Compose Multiplatform or Flutter) pays off; a polyglot split duplicates it — the single largest duplication cost. |
| **TTS + secure storage** | ❌ by nature | Thin per-platform bindings; share only the interface. |

**Is a shared core worth it?**

- **If both clients are Kotlin** (Android + Compose/JVM desktop) → **yes, and it is free**: KMP is the shared core, `expect/actual` covers TTS/keys/SAF, and you avoid a second reader implementation. This is the strongest argument for KMP.
- **If the clients are polyglot** (e.g. native Android + Rust/Tauri desktop) → a shared core via **uniffi/WASM** is possible but adds an FFI boundary, a second build system and a second language for contributors. For Lekto's modest domain (tokenisation + JSON records + HTTP adapters), the shared logic is small enough that **duplication is a defensible MVP choice**, and the vault spec can be versioned as documentation first. The Rust core only becomes attractive if you *also* want a Rust/WASM web client — a scenario the founder is moving away from.
- **Decision rule:** share a core when the reader engine is also shared; otherwise share the **spec** and keep the cores independent, then converge later if duplication actually hurts.

---

## 9. Recommendation

**Adopt Kotlin Multiplatform with a Compose Multiplatform UI, structured as a shared `commonMain` domain + a native Android (Jetpack Compose) app first, then a Compose/JVM desktop client.**

**Shape:**

1. **One Kotlin codebase, `commonMain` = the domain**: vault seam (capability-driven, ADR-0001), record-per-file JSON + atomic writes (ADR-0003), tokenisation, `(language, lemma)` identity (ADR-0006), dictionary/LLM adapters (ADR-0002), TTS and secure-storage **interfaces**.
2. **Android is a first-class native target**: SAF vault (`ACTION_OPEN_DOCUMENT_TREE` → `takePersistableUriPermission` → `DocumentsContract`/`openOutputStream`), `android.speech.tts.TextToSpeech`, Android Keystore — **all first-party, no plugins** ([SAF](https://developer.android.com/training/data-storage/shared/documents-files), [TTS](https://developer.android.com/reference/android/speech/tts/TextToSpeech), [Keystore](https://developer.android.com/privacy-and-security/keystore)). Readium Kotlin provides the EPUB engine ([kotlin-toolkit](https://github.com/readium/kotlin-toolkit)).
3. **Desktop is Compose/JVM** (stable on Windows/macOS/Linux — [CMP compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)), plain filesystem vault, and a `expect/actual` TTS/keychain layer (TextToSpeechKt for macOS/Windows where experimental, a JVM binding otherwise; **KSafe** for keys across Android/Desktop/Web — [KSafe](https://github.com/ioannisa/KSafe)).
4. **Web is out of scope for now.** If revived, Kotlin/Wasm is a **Beta later-stage client** ([KMP stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)), and the vault degrades per ADR-0001. Do not let it constrain the Android/desktop design.

**Why this over the alternatives:**

- It is the **only option that satisfies every Android requirement with zero third-party plugins** *and* shares code with a desktop client. Native Android alone also satisfies Android but duplicates the reader; Flutter/Tauri/Capacitor/RN all need a custom SAF layer and a community secure-storage/TTS stack.
- It treats Kotlin/JetBrains as what it is — an **open-source language and UI library that compiles to native/JVM/Wasm**, not a third-party shell with a hosted runtime — which respects the founder's anti-lock-in stance far better than Flutter (Google widget framework), Capacitor/Tauri (shells) or RN.
- It preserves the option value: **ship Android now**; the desktop is a second target that reuses the domain rather than a rewrite; a web client can be added as a Beta later or dropped without rework.
- It keeps the reader engine on **Readium**, the strongest EPUB toolkit in any of these ecosystems, on the platform that matters most.

**Strongest counter-argument.**

**The EPUB engine — the product's core — is not actually shared, and that undercuts the whole premise of KMP.** Readium's Kotlin toolkit is **Android/JVM-only today**, its maintainers explicitly say KMP conversion is future work ("pivot to Android by the end of 2026 … experiment with converting shared models to KMP") and that a Compose Multiplatform navigator "is not a priority" ([Readium #547](https://github.com/readium/kotlin-toolkit/discussions/547)). So on a Compose Multiplatform **desktop** target you do **not** get Readium's rendering; you either build a second EPUB parser + renderer for desktop or sponsor/contribute upstream — i.e. you adopt KMP's cross-platform friction *and* still duplicate the hardest component, which was the main reason to share a framework. Add that Kotlin/Wasm web is **Beta** and Compose Desktop's reader ecosystem is thin, and a defensible alternative emerges: **write the Android app natively (best Android reader, zero plugins), and give the desktop a small, independent client in a stack that is ideal for text — Flutter (mature desktop, one language, huge community) or a Tauri/Electron web reader** — accepting a duplicated reader in exchange for avoiding KMP's toolchain cost. A second, softer counter: if the founder's real priority is *one codebase, least total work, and web kept as insurance*, **Flutter** does all three today (Android + desktop + web, CI-tested, ~one language) and only forces him to write the ~few-hundred-line SAF adapter and use community secure-storage/TTS plugins — arguably a smaller bill than maintaining a Compose Desktop EPUB engine.

**Counter-counter.** Both counters trade away something the recommendation keeps: the native+separate path duplicates the reader **permanently** (not just until Readium KMP lands), and Flutter accepts exactly the framework lock-in the founder rejects, plus a canvas text model and a weaker EPUB stack. KMP's EPUB gap is *bounded and trackable* (Readium KMP is on the maintainers' roadmap and open to contributions), and it is the only path where, when the desktop arrives, the reader and domain are already shared. If the founder judges the desktop to be genuinely optional and far off, the native-Android-only path is the rational minimal-risk start — and it is compatible with this recommendation's structure (put the domain in a shareable module from day one), so choosing it now does not burn the KMP option later.

---

## 10. Branch: if the web target is dropped vs kept

### If the web target is dropped (the founder's leaning)

Take the KMP recommendation above. Web disappears from the matrix, which **removes KMP's only soft spot** (Beta Kotlin/Wasm) and removes Capacitor's only reason to exist. The ranking becomes:

1. **KMP + Compose Multiplatform** — Android-first, desktop second, shared domain + UI, no third-party shell.
2. **Native Android + separate desktop client** — maximal native fidelity, lowest lock-in, highest duplication; choose it if the desktop is optional/remote, or if you want to prototype the Android reader with zero cross-platform constraint.
3. Flutter / Avalonia — single-codebase alternatives if a shared UI is wanted without Kotlin.
4. Tauri — desktop-only in practice.
5. Capacitor — no longer competitive.

### If the web target is kept as a first-class requirement

The earlier "stay on Capacitor" conclusion (`docs/research/tauri-vs-capacitor.md`) reasserts itself for the **browser** client specifically, and the calculus changes:

- **Capacitor** remains the only option that delivers Android + a real browser target from one codebase, at the cost of native SAF glue and community TTS/secure-storage plugins ([Capacitor web](https://capacitorjs.com/docs/web); companion study for the SAF/TTS/keys details).
- **Flutter** is the strongest *single-framework* alternative: Android + desktop + a real web build in one language, with mature desktop support ([supported platforms](https://docs.flutter.dev/reference/supported-platforms)), at the cost of framework lock-in and a weaker EPUB stack.
- **KMP** can still keep Android + desktop + a **Beta** web client, but the web client is the weakest link (Kotlin/Wasm Beta, WasmGC browsers) ([KMP stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html), [browser versions](https://kotlinlang.org/docs/wasm-configuration.html)).
- **Tauri** is out for web by definition ([What is Tauri](https://v2.tauri.app/start/)).

**If web must be first-class**, the pragmatic split that matches the founder's "separate codebases acceptable" position is: **native Android (or KMP-Android) as the primary client, and a thin, independent web client** (Reactor/Vite or Capacitor wrapping the same web assets) — sharing the **vault spec** and provider adapters, not a UI framework. That keeps the Android experience uncompromised and treats web as a genuinely separate, lower-fidelity surface, consistent with ADR-0001's capability-driven degradation.

---

## 11. What I could not verify / open questions

1. **Readium's seam for per-word tokening and mastery colouring.** Readium offers decorations/rendering hooks and ships EPUB/PDF/image navigators ([kotlin-toolkit](https://github.com/readium/kotlin-toolkit)), but this report did not verify that Lekto's `WordToken` model (per-word colour + tap + selection, UX spec §Component Strategy) maps cleanly onto Readium's navigator API. **Prototype this before committing** — it is the single highest-risk assumption.
2. **`DocumentsContract.createDocument` / `openOutputStream` specifics.** The SAF guide demonstrates `openInputStream`, `openFileDescriptor`, `openOutputStream`-equivalent writes, `deleteDocument` and `DocumentsContract` usage ([SAF doc](https://developer.android.com/training/data-storage/shared/documents-files)); `createDocument` itself is documented on the [`DocumentsContract` reference](https://developer.android.com/reference/android/provider/DocumentsContract) rather than in the walkthrough. Treat the exact create/write call sequence as standard-but-unverified-in-detail.
3. **Compose Multiplatform desktop packaging and macOS x64.** The compatibility page lists supported desktop platforms and requires JDK 17+ for `jpackage` ([CMP compatibility](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)); whether macOS **x64** (Intel) is supported as of 1.12.1 was not verified (the table shows macOS 13 arm64).
4. **Kotlin/Wasm browser coverage.** Kotlin/Wasm requires a **WasmGC**-capable browser ([Wasm](https://kotlinlang.org/docs/wasm-overview.html), [browser versions](https://kotlinlang.org/docs/wasm-configuration.html)); the exact Safari/Safari-mobile status was not verified. Do not assume universal browser support.
5. **Flutter `file_selector` Android semantics.** Verified from source that `getDirectoryPath` uses `ACTION_OPEN_DOCUMENT_TREE` and converts to a **path without `takePersistableUriPermission`** ([source](https://github.com/flutter/packages/blob/main/packages/file_selector/file_selector_android/android/src/main/java/dev/flutter/packages/file_selector_android/FileSelectorApiImpl.java)); what that path is usable for on Android 11+ was not separately verified, but the community report that the picker "does not grant permissions to its contents" points the same way ([file_picker #1781](https://github.com/miguelpruivo/flutter_file_picker/issues/1781)).
6. **Tauri Stronghold mobile support.** The official plugins support table rendered without its per-platform check glyphs in this fetch ([plugins table](https://v2.tauri.app/plugin/)); the earlier study states Stronghold supports android/ios ([Stronghold](https://v2.tauri.app/plugin/stronghold/)). Re-check the table on a rendered page.
7. **React Native macOS version lag.** The claim that macOS trails upstream by ~6 minor series comes from a secondary source ([platform.uno](https://platform.uno/articles/react-native-windows-macos-versions-vs-dotnet-lts/)); the primary evidence gathered here is only that **RNW 0.84.0 targets RN 0.84.0** ([RNW releases](https://github.com/microsoft/react-native-windows/releases)). Verify `react-native-macos` tags directly if this matters.
8. **Maturity of KMP TTS and KSafe on Linux/Windows desktops.** TextToSpeechKt marks Desktop **experimental** ([repo](https://github.com/Marc-JB/TextToSpeechKt)); KSafe documents its JVM protection tiers but not per-distro quirks ([JVM protection](https://github.com/ioannisa/KSafe/blob/main/docs/JVM_PROTECTION.md)). Prototype desktop TTS and keychain early if desktop is committed.

---

## References

**Kotlin / KMP / Compose Multiplatform**
- Kotlin Multiplatform — stability of supported platforms — https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Compose Multiplatform — compatibility and versions — https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html
- Compose Multiplatform 1.11.0 — https://blog.jetbrains.com/kotlin/2026/05/compose-multiplatform-1-11-0/
- Compose Multiplatform 1.12.0 — https://blog.jetbrains.com/kotlin/2026/08/compose-multiplatform-1-12-0/
- Kotlin/Wasm overview — https://kotlinlang.org/docs/wasm-overview.html
- Kotlin/Wasm configuration (browser versions) — https://kotlinlang.org/docs/wasm-configuration.html
- JetBrains, Compose Multiplatform product page — https://kotlinlang.org/compose-multiplatform/
- TextToSpeechKt (KMP TTS) — https://github.com/Marc-JB/TextToSpeechKt
- KSafe (KMP secure storage) — https://github.com/ioannisa/KSafe
- KSafe JVM key protection — https://github.com/ioannisa/KSafe/blob/main/docs/JVM_PROTECTION.md
- Readium Kotlin toolkit — https://github.com/readium/kotlin-toolkit
- Readium #547, Support for Kotlin Multiplatform — https://github.com/readium/kotlin-toolkit/discussions/547
- Readium Kotlin 3.2.0 release note — https://blog.readium.org/release-note-kotlin-toolkit-version-3-2-0/

**Android (platform primitives)**
- Jetpack Compose — 5 years / Compose-first — https://android-developers.googleblog.com/2026/07/five-years-of-jetpack-compose.html
- Compose-first — https://developer.android.com/develop/ui/compose/first
- Access documents and other files from shared storage (SAF) — https://developer.android.com/training/data-storage/shared/documents-files
- DocumentsContract — https://developer.android.com/reference/android/provider/DocumentsContract
- Android Keystore system — https://developer.android.com/privacy-and-security/keystore
- TextToSpeech — https://developer.android.com/reference/android/speech/tts/TextToSpeech
- Manage all files on a storage device — https://developer.android.com/training/data-storage/manage-all-files
- Storage updates in Android 11 — https://developer.android.com/about/versions/11/privacy/storage
- Google Play, Use of All files access permission — https://support.google.com/googleplay/android-developer/answer/10467955

**Flutter**
- Flutter — supported deployment platforms — https://docs.flutter.dev/reference/supported-platforms
- Flutter — desktop support — https://docs.flutter.dev/platform-integration/desktop
- flutter_tts — https://pub.dev/packages/flutter_tts
- flutter_secure_storage — https://pub.dev/packages/flutter_secure_storage
- flutter_secure_storage changelog — https://pub.dev/packages/flutter_secure_storage/changelog
- file_selector — https://pub.dev/packages/file_selector
- file_selector_android — https://pub.dev/packages/file_selector_android
- file_selector_android source (`FileSelectorApiImpl.java`) — https://github.com/flutter/packages/blob/main/packages/file_selector/file_selector_android/android/src/main/java/dev/flutter/packages/file_selector_android/FileSelectorApiImpl.java
- file_picker #1781 (directory path does not grant permissions) — https://github.com/miguelpruivo/flutter_file_picker/issues/1781
- epubx — https://pub.dev/packages/epubx
- epub_view — https://pub.dev/packages/epub_view
- pdfrx — https://pub.dev/packages/pdfrx

**Tauri**
- What is Tauri? — https://v2.tauri.app/start/
- Prerequisites (Rust + NDK) — https://v2.tauri.app/start/prerequisites/
- Plugins & support table — https://v2.tauri.app/plugin/
- Dialog plugin — https://v2.tauri.app/plugin/dialog/
- File System plugin — https://v2.tauri.app/plugin/file-system/
- Stronghold plugin — https://v2.tauri.app/plugin/stronghold/
- Tauri 2.0 stable release — https://tauri.app/blog/tauri-20/
- plugins-workspace #933 — https://github.com/tauri-apps/plugins-workspace/issues/933
- tauri #14587 — https://github.com/tauri-apps/tauri/issues/14587
- tauri-plugin-android-fs (community) — https://github.com/aiueo13/tauri-plugin-android-fs
- tauri-plugin-tts (community) — https://github.com/brenogonzaga/tauri-plugin-tts

**Capacitor**
- Using Capacitor in a Web Project — https://capacitorjs.com/docs/web
- @capacitor/filesystem — https://capacitorjs.com/docs/apis/filesystem
- @capacitor-community/text-to-speech — https://github.com/capacitor-community/text-to-speech
- @aparajita/capacitor-secure-storage — https://github.com/aparajita/capacitor-secure-storage

**Other frameworks**
- .NET MAUI — supported platforms — https://learn.microsoft.com/en-us/dotnet/maui/supported-platforms
- .NET MAUI — secure storage — https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/storage/secure-storage
- Avalonia — supported platforms — https://docs.avaloniaui.net/docs/supported-platforms
- React Native Windows — home — https://microsoft.github.io/react-native-windows/
- React Native Windows — releases — https://github.com/microsoft/react-native-windows/releases
- react-native-tts — https://github.com/ak1394/react-native-tts (npm: https://www.npmjs.com/package/react-native-tts)
- react-native-keychain — https://github.com/oblador/react-native-keychain
- react-native-saf-x — https://github.com/jd1378/react-native-saf-x
- DevClass — Avalonia adds MAUI Linux/WASM support (secondary) — https://www.devclass.com/development/2026/03/24/avaloniaui-enhances-net-maui-with-linux-and-webassembly-support/5209515
- platform.uno — React Native Windows/macOS versions vs upstream (secondary) — https://platform.uno/articles/react-native-windows-macos-versions-vs-dotnet-lts/

**Lekto internal**
- docs/product-brief-2026-03-02.md
- docs/ux-design-specification.md
- docs/adr/0001-vault-portability-is-capability-driven.md
- docs/adr/0002-no-backend-server.md
- docs/adr/0003-vault-record-per-file.md
- docs/adr/0006-word-identity-language-lemma.md
- docs/research/tauri-vs-capacitor.md (superseded premise; SAF/TTS/keys references)
- docs/research/cross-platform-file-access.md (superseded premise; SAF constraints)
