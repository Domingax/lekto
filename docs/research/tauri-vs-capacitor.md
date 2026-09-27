# Tauri v2 vs Capacitor for Lekto: a cross-platform stack decision

**Date:** 2026-09-27
**Scope:** Which native shell should wrap Lekto's single React/TypeScript UI for **Web + Android** (iOS later), given the hard requirement of a **local-first, user-chosen "vault" folder** (ideally a cloud-synced directory), plus **TTS**, **secure API-key storage**, and **offline-first**?
**Method:** Primary sources only — Tauri's official docs/site (v2.tauri.app, tauri.app/blog), the `tauri-apps` GitHub org, Capacitor's official docs (capacitorjs.com), the `ionic-team`/`capacitor-community` repos, the Capawesome plugin docs, the W3C/MDN secure-context definitions, and Chrome release notes. Where sources disagree, both are cited. Every claim carries a URL. Companion to `docs/research/cross-platform-file-access.md`, which established the Android SAF constraints this report builds on.

> **Headline:** On the axis Lekto cares about most — a **user-chosen vault folder on Android** — neither framework's built-in file APIs can do it: Tauri's dialog plugin explicitly "Does not support folder picker" on Android, and its `fs` plugin cannot enumerate/create inside an SAF tree URI; Capacitor's `@capacitor/filesystem` cannot write `content://` tree URIs either. Both therefore need a native SAF layer. The decisive difference is **Web**: Capacitor ships a real browser/PWA app from the same codebase as a first-class target, while Tauri targets **desktop + mobile binaries only** and its APIs stop working once the frontend runs in a normal browser. Recommend **staying on Capacitor**, and treating the Android vault as a native SAF adapter in either case.

---

## 1. Tauri v2 mobile maturity

**Status: stable, active, but with acknowledged mobile gaps.**

- Tauri **2.0** was released as a **stable** release on **2 Oct 2024**, covering desktop (macOS, Linux, Windows) **and mobile (iOS, Android)** in the same stable major. ([Tauri 2.0 Stable Release](https://tauri.app/blog/tauri-20/))
- The current line is **2.12** (26 Sep 2026), described as "the biggest update so far in the 2.x releases." The MSRV policy is now `stable - 3` and the MSRV was raised to **Rust 1.90**; Windows 7 support was dropped. ([Announcing Tauri 2.12](https://tauri.app/blog/tauri-2.12/))
- Android build tooling in 2.12: the `tauri android init` template moved to **Gradle v9** and **Kotlin v2**, raised `targetSdk` **36 → 37 (Android 17)**, and raised the minimum Gradle to **8.13**. ([Announcing Tauri 2.12](https://tauri.app/blog/tauri-2.12/))
- Official acknowledgement of mobile limitations (from the 2.0 announcement):
  - "On mobile not all of the official plugins are supported. Some are by design not a good fit for mobile and some are just not implemented to support mobile yet." ([Tauri 2.0 Stable Release](https://tauri.app/blog/tauri-20/))
  - "We are not completely happy about the developer experience at the moment but are actively improving to bring it up to par with the desktop experience." ([Tauri 2.0 Stable Release](https://tauri.app/blog/tauri-20/))
- The plugin support table confirms the gaps: mobile-unsupported official plugins include `global-shortcut`, `positioner`, `single-instance`, `sql`, `store`, `updater`, `window-state`, `autostart`, `process` and `os`; `dialog` and `fs` are mobile-supported **with footnotes**. ([Tauri Plugins & Support Table](https://v2.tauri.app/plugin/))
- The `dialog` plugin's own platform table annotates **android: "Does not support folder picker"** and **ios: "Does not support folder picker."** ([Dialog plugin](https://v2.tauri.app/plugin/dialog/))
- Building mobile requires native toolchains, not just Node: Android needs Android Studio + SDK + **NDK** + Rust targets; iOS needs **Xcode and is macOS-only**. ([Prerequisites](https://v2.tauri.app/start/prerequisites/))
- Mobile dev is driven by `tauri android dev` / `tauri ios dev`; Android debugging is via `chrome://inspect`. ([Develop](https://v2.tauri.app/develop/))

**Verdict:** Tauri v2 Android/iOS is genuinely **stable** (not beta), and shipping at a healthy cadence, but it is a younger mobile story than Capacitor's, with real per-plugin gaps and a heavier native toolchain.

---

## 2. File access on Android (Tauri v2)

### 2.1 What the built-in plugins do

- `tauri-plugin-fs` platform notes: **android: "Access is restricted to Application folder by default."** ([File System plugin](https://v2.tauri.app/plugin/file-system/))
- To reach the shared/public directories (`audio`, `cache`, `documents`, `downloads`, `picture`, `public`, `video`), the docs say you must add `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` to the Android manifest — i.e. the legacy storage model, not SAF. ([File System plugin](https://v2.tauri.app/plugin/file-system/))
- Path safety: the `fs` module "prevents path traversal… Paths accessed with this API must be either relative to one of the base directories or created with the path API." ([File System plugin](https://v2.tauri.app/plugin/file-system/))
- The `dialog` plugin **can** return a picked *file* on Android: "On Android, content URIs are returned. The filesystem plugin works with any path format out of the box." But the same plugin's table says it **does not support a folder picker** on Android/iOS. ([Dialog plugin](https://v2.tauri.app/plugin/dialog/))
- Consequently: the built-in stack can read/write a **single file** addressed by a `content://` URI, but it cannot **pick a directory tree** and cannot **enumerate or create files inside** one. This is confirmed by the maintainers' own issue tracker (below).

### 2.2 The scopes / permissions model

Tauri v2 replaced the v1 allowlist with **permissions + scopes + capabilities**:

- **Permissions** are on/off toggles for commands; **scopes** are parameter validation; **capabilities** attach permissions and scopes to windows/webviews. ([Tauri 2.0 Stable Release](https://tauri.app/blog/tauri-20/), [Capabilities](https://v2.tauri.app/security/capabilities/))
- For `fs`, "Enabling a permission such as `fs:allow-exists` by itself does **not** allow access to any path. Most `fs` commands also require a **scope** that explicitly lists which paths the command may access. Without an `allow` scope, calls will fail at runtime with a `forbidden path` error." ([File System plugin § Permissions](https://v2.tauri.app/plugin/file-system/))
- Scopes are expressed with allow/deny path variables such as `$HOME`, `$APPDATA`, `$DOCUMENT`, `$DOWNLOAD`, plus globs like `$HOME/**/*`. Example capability:

  ```json
  { "permissions": [
      "fs:default",
      { "identifier": "fs:allow-exists", "allow": [{ "path": "$HOME" }, { "path": "$HOME/**/*" }] }
  ]}
  ```
  ([File System plugin § Permissions](https://v2.tauri.app/plugin/file-system/))
- By default the `fs` plugin only grants read access to app-specific directories (`$APPCONFIG`, `$APPDATA`, `$APPLOCALDATA`, `$APPCACHE`, `$APPLOG`) and denies access to critical webview-data paths. ([File System plugin § Default Permission](https://v2.tauri.app/plugin/file-system/))

**Scope model vs the Android vault:** Tauri's ACL scopes are a *static allow-list of path globs resolved at build time*. That model does not naturally express "whatever `content://` tree URI the user just picked at runtime," which is exactly the Android vault case.

### 2.3 Known issues: directory-tree pick and write on Android

- **Open feature request #933 — "Dialog Plugin pick_folder not implemented"** (opened Feb 2024, still open, labelled `platform: android`, `platform: ios`, `plugin: dialog`): `pick_folder` compiles and works on desktop, but "the compiler is unable to find the `pick_folder` method once i try to compile to android," while `pick_file` works. ([plugins-workspace#933](https://github.com/tauri-apps/plugins-workspace/issues/933))
- **Issue #14587 — "[feat] To implement a folder picker on Android"** (opened 30 Nov 2025, closed as a duplicate of #933) explains the root cause:
  1. Android's only directory picker, `Intent.ACTION_OPEN_DOCUMENT_TREE`, returns a **URI, not a path**.
  2. "Currently, the Tauri file system API, `tauri_plugin_fs` … can only read and write files, for using a URI. It does not allow **listing the contents of a directory or creating new files within a directory from a URI**."
  3. Android defines **two distinct URIs per directory** (the directory's own handle, and the child-list handle); "One URI cannot be derived from the other," but Tauri's `FsPath` "can only hold a single path or URI, creating a conflict."
  ([tauri#14587](https://github.com/tauri-apps/tauri/issues/14587))
- **Issue #1163** is the same request from a different author and is closed. ([plugins-workspace#1163](https://github.com/tauri-apps/plugins-workspace/issues/1163))

So: **Tauri has the same fundamental SAF problem Capacitor has** for the vault — its first-party plugins cannot pick an arbitrary directory tree and write into it on Android.

### 2.4 The community workaround: `tauri-plugin-android-fs`

The missing capability is covered by an **unofficial** community plugin, [`aiueo13/tauri-plugin-android-fs`](https://github.com/aiueo13/tauri-plugin-android-fs) (v29.0.0, published 22 Jul 2026). It is not in Tauri's official plugin list ([Tauri Plugins](https://v2.tauri.app/plugin/)), but it implements precisely the SAF directory story:

- A `Picker` API providing "file selection, **directory selection**, save dialogs, and permission management for selected entries." ([README](https://github.com/aiueo13/tauri-plugin-android-fs))
- An `AndroidFs` core API with directory-tree aware operations on `FsUri`s: `create_new_file`, `create_dir_all`, `read_dir` (+ ranged/optioned variants), `resolve_dir_uri`, `resolve_file_uri`, `write` ("entirely replace its contents"), `open_file_writable` ("This truncates the existing contents"), `remove_file`, `remove_dir`, `remove_dir_all`, `copy`, `rename`. ([`AndroidFs` API docs](https://docs.rs/tauri-plugin-android-fs/latest/tauri_plugin_android_fs/api/api_async/struct.AndroidFs.html))
- Explicit Play-policy stance: "By default (with default features enabled), this plugin does not add permissions to the application or enable any options that require additional review by Google Play." ([README](https://github.com/aiueo13/tauri-plugin-android-fs))

**Maturity caveat:** ~39 stars, 4 forks, effectively one maintainer (`aiueo13`), and the README itself notes it is written with a translation tool. It proves the capability is reachable in Tauri, but it is a **bus-factor risk** to depend on for the product's core storage.

**Verdict (Q2):** Tauri's first-party `fs`/`dialog` **cannot** deliver an arbitrary user-chosen read/write vault directory on Android. A native SAF plugin (or the community `tauri-plugin-android-fs`) is required — the same conclusion the prior file-access study reached for Capacitor.

---

## 3. File access on web/desktop (Tauri): can it ship a browser web app?

**No — Tauri does not target the browser.** Tauri is "a framework for building tiny, fast binaries for all major **desktop and mobile** platforms." Its targets are desktop and mobile binaries; there is no web target. ([What is Tauri?](https://v2.tauri.app/start/), [Tauri 2.0 Stable Release](https://tauri.app/blog/tauri-20/))

The developer docs make the practical consequence explicit:

> "Tauri's APIs only work in your app window, so once you start using them you won't be able to open your frontend in your system's browser anymore. If you prefer using your browser's developer tooling, you must configure **tauri-invoke-http** to bridge Tauri API calls through a HTTP server." ([Develop](https://v2.tauri.app/develop/))

That is a **dev-time shim**, not a production web deployment. The community `tauri-remote-ui` plugin is likewise scoped "to make your web app bundle available as web page **for test and development**." ([Community plugins, Tauri Plugins](https://v2.tauri.app/plugin/))

On desktop, Tauri gives you a native window over an OS webview — a desktop binary, not a browser-accessible site:

- Windows: **WebView2** (Edge/Chromium-based; updates itself; preinstalled on Win 11). ([Webview Versions](https://v2.tauri.app/reference/webview-versions/))
- macOS: **WKWebView**; Linux: **webkit2gtk** (WebKit; version varies wildly by distro). ([Webview Versions](https://v2.tauri.app/reference/webview-versions/))

**By contrast, Capacitor treats web/PWA as a first-class target:**

> "Capacitor **fully supports traditional web and Progressive Web Apps**. In fact, using Capacitor makes it easy to ship a PWA version of your iOS and Android app store apps with minimal work." … "When you're ready to publish your Progressive Web App … just upload the contents of your web assets directory. That will contain everything you need to run your app!" ([Using Capacitor in a Web Project](https://capacitorjs.com/docs/web))

Plugins with web support "perform feature detection and throw exceptions if a browser does not support a particular Web API." ([Web Project](https://capacitorjs.com/docs/web))

**Verdict (Q3):** Tauri **cannot** deliver a true in-browser web app from the same codebase. Capacitor **can**. Since Lekto's product brief names **Web as a primary target** ("The web app is the primary development target" — `docs/ux-design-specification.md` §58; "Platforms: Web (browser) + Android" — `docs/product-brief-2026-03-02.md` §184), this is the single most consequential difference in the comparison.

---

## 4. WebView differences

### 4.1 Which engine each framework uses

| Platform | Tauri v2 | Capacitor |
|---|---|---|
| **Android** | The **system Android WebView** (Chromium-based); "Tauri does not bundle a WebView with your app, so the runtime version depends on the device's currently selected WebView provider." ([Webview Versions](https://v2.tauri.app/reference/webview-versions/)) | The **system Android WebView**; config exposes `minWebViewVersion` (default 60) and `minHuaweiWebViewVersion` (default 10), and errors if the device is below it. ([Capacitor Config](https://capacitorjs.com/docs/config)) |
| **iOS** | **WKWebView**. ([Webview Versions](https://v2.tauri.app/reference/webview-versions/)) | **WKWebView** (`limitsNavigationsToAppBoundDomains`, `preferredContentMode` options). ([Capacitor Config](https://capacitorjs.com/docs/config)) |
| **Windows** | **WebView2** (Edge/Chromium). ([Webview Versions](https://v2.tauri.app/reference/webview-versions/)) | *No desktop target* (web target = the user's own browser). |
| **macOS / Linux** | **WKWebView / webkit2gtk** (WebKit). ([Webview Versions](https://v2.tauri.app/reference/webview-versions/)) | *No desktop target.* |

**Key implication for Lekto's Android Web API availability (e.g. the File System Access API):** because **both** frameworks run the *same* Android System WebView, feature availability on Android is a property of the **device's WebView version, not the framework**. The prior study established that `showDirectoryPicker()`/`showSaveFilePicker()` shipped in **Chrome/WebView 132** ([Chrome release notes 132](https://developer.chrome.com/release-notes/132), [MDN File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API)); that applies identically to a Tauri Android build and a Capacitor Android build. Neither can upgrade the engine below the OS component.

*(Tauri has an experimental path to bundling a different engine — a Servo-based `tauri-runtime-verso` — but it is explicitly experimental and desktop-oriented, not a shipped Android option. [Experimental Tauri Verso Integration](https://tauri.app/blog/tauri-verso-integration/))*

### 4.2 Origin / secure-context

Both frameworks serve the app from a local origin that satisfies the secure-context requirement:

- **Capacitor:** `server.androidScheme` defaults to **`https`** and `server.hostname` defaults to **`localhost`**, so the Android app is served at `https://localhost`. The config explicitly notes this "allows the use of Web APIs that would otherwise require a secure context." ([Capacitor Config](https://capacitorjs.com/docs/config))
- **Tauri:** the production origin is a custom scheme; on **Windows and Android** the default is **`http://<scheme>.localhost`**, and `useHttpsScheme` (default `false`) switches it to `https://<scheme>.localhost`; macOS/Linux use `<scheme>://localhost`. ([webview JS API — `useHttpsScheme`](https://v2.tauri.app/reference/javascript/api/namespacewebview/); [Upgrade from Tauri 1.0 — New origin URL on Windows](https://v2.tauri.app/start/migrate/from-tauri-1/))
- **Both are secure contexts.** Per MDN/W3C, an origin is potentially trustworthy if its host is `localhost` **or ends in `.localhost`** — so `http://tauri.localhost` and `https://localhost` are both secure contexts. ([MDN: Secure contexts](https://developer.mozilla.org/en-US/docs/Web/Security/Defenses/Secure_Contexts))
- One Tauri-specific gotcha to record: `useHttpsScheme` "will change the IndexedDB, cookies and localstorage location and your app will not be able to access them" — a migration hazard if flipped between releases. ([webview JS API](https://v2.tauri.app/reference/javascript/api/namespacewebview/))

**Verdict (Q4):** On Android the two are effectively identical (same engine, both secure contexts). The meaningful difference is elsewhere: on desktop, Tauri runs OS webviews — **WebKitGTK on Linux and WKWebView on macOS have no File System Access API** — whereas Capacitor's "desktop" experience is whatever browser the user opens, which is Chromium on most desktops (FSA available) but Firefox/Safari otherwise (no FSA, per the prior study).

---

## 5. TTS

| | Tauri v2 | Capacitor |
|---|---|---|
| **First-party** | **None.** TTS is not in Tauri's official feature list or support table. ([Tauri Plugins](https://v2.tauri.app/plugin/)) | **None first-party** for TTS (it is not in the official plugin list). ([Capacitor Plugin APIs](https://capacitorjs.com/docs/apis)) |
| **Community plugin** | [`brenogonzaga/tauri-plugin-tts`](https://github.com/brenogonzaga/tauri-plugin-tts) — delegates to the OS synthesiser: **WinRT** (Windows), **AVSpeechSynthesizer** (macOS/iOS), **speech-dispatcher** (Linux), **`TextToSpeech` (Android)**. API: `speak`, `stop`, `getVoices`, `previewVoice`, `isSpeaking`, `isInitialized`, `pauseSpeaking`/`resumeSpeaking` (iOS only), `setBackgroundBehavior`, `onSpeechEvent`. ([README](https://github.com/brenogonzaga/tauri-plugin-tts)) | [`@capacitor-community/text-to-speech`](https://github.com/capacitor-community/text-to-speech) — wraps Android `TextToSpeech` and iOS `AVSpeechSynthesizer`. API: `speak`, `stop`, `getSupportedLanguages`, `getSupportedVoices`, `isLanguageSupported`, **`openInstall()` (Android-only: install missing resource files)**, `onRangeStart` listener. "No configuration required." ([README](https://github.com/capacitor-community/text-to-speech)) |
| **Maturity signal** | ~21 stars, 7 forks. MIT. Android caveats documented (e.g. "Android has no pause in its TTS API, so it stops"; voices needing an uninstalled language pack are filtered out; mobile engines initialise asynchronously and `getVoices()` returns `[]` until ready). ([README](https://github.com/brenogonzaga/tauri-plugin-tts)) | ~130 stars, 40 forks, maintained in the official **`capacitor-community`** org by robingenz; has an Android `openInstall()` path for missing language packs. ([README](https://github.com/capacitor-community/text-to-speech)) |
| **Web fallback** | Web Speech API `speechSynthesis` (browser) — broad support (~95.4%, per prior study). ([MDN SpeechSynthesis](https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis), [caniuse](https://caniuse.com/speech-synthesis)) | Same: `@capacitor-community/text-to-speech` also exposes a web implementation, and the browser `speechSynthesis` is available. ([README](https://github.com/capacitor-community/text-to-speech)) |

**Verdict (Q5):** TTS is not a blocker for either, and neither has a first-party plugin. Capacitor's community plugin is **more established** (larger, org-owned, includes the Android `openInstall()` affordance that Lekto's "unavailable language pack" UX needs) than Tauri's 21-star plugin.

---

## 6. Secure storage (API keys)

### 6.1 Tauri

- **Official:** [`tauri-plugin-stronghold`](https://v2.tauri.app/plugin/stronghold/) — "Store secrets and keys using the **IOTA Stronghold** secret management engine." Supported on **windows, linux, macos, android, ios**. It is an **encrypted database that must be initialized with a password hash function** (argon2 by default) and unlocked with a vault password. ([Stronghold plugin](https://v2.tauri.app/plugin/stronghold/))
- **No first-party OS-keychain plugin.** Tauri ships no official "secure storage" plugin that wraps Android Keystore / iOS Keychain / desktop credential store; the community fills this gap (`tauri-plugin-keyring`, `tauri-plugin-keyring-store`, `tauri-plugin-secure-keystore`, `@impierce/tauri-plugin-keystore`). ([crates.io: tauri-plugin-keyring-store](https://crates.io/crates/tauri-plugin-keyring-store/0.1.5), [HuakunShen/tauri-plugin-keyring](https://github.com/HuakunShen/tauri-plugin-keyring), [JSR: tauri-plugin-secure-keystore](https://jsr.io/@abdullah/tauri-plugin-secure-keystore), [npm: @impierce/tauri-plugin-keystore](https://www.npmjs.com/package/@impierce/tauri-plugin-keystore))

### 6.2 Capacitor

- **Official `@capacitor/preferences` is NOT secure** — it "will use `UserDefaults` on iOS and `SharedPreferences` on Android," i.e. plaintext. Do not use it for API keys. ([Preferences plugin](https://capacitorjs.com/docs/apis/preferences))
- **Community, Keystore-backed:** `@aparajita/capacitor-secure-storage` (Capacitor 8): "On Android, data is encrypted using **AES in GCM mode with a secret key generated by the Android KeyStore**, then stored in SharedPreferences… On iOS, data is stored in the **encrypted system keychain**." Caveat: "On the web, data is stored **unencrypted in `localStorage`**… for debugging purposes only; you should not use this plugin on the web in production." (~169 stars, 30 forks.) ([README](https://github.com/aparajita/capacitor-secure-storage))
- Alternatives: [Capawesome Secure Preferences](https://capawesome.io/docs/sdks/capacitor/secure-preferences/) and [`@evva/capacitor-secure-storage-plugin`](https://npmjs.com/package/@evva/capacitor-secure-storage-plugin) (AndroidKeyStore / EncryptedSharedPreferences).

**Verdict (Q6):** Both have a viable Android-Keystore-backed answer, but via **community** plugins. Tauri's *official* option is Stronghold (a password-gated encrypted **DB**, not an OS-keychain wrapper) — heavier, and it asks the user for a vault password (or you must store the key somewhere). Capacitor's `@aparajita/capacitor-secure-storage` is a closer fit to "store one API key using the OS Keystore," is more established, and explicitly documents the **web-unencrypted** caveat that Lekto's brief also anticipates ("encrypted local storage on web"). ([README](https://github.com/aparajita/capacitor-secure-storage))

---

## 7. Trade-offs

### 7.1 Bundle size

- Tauri's headline advantage is that it "uses the **system's native webview**" and "doesn't need to bundle a browser engine," so "a minimal Tauri app can be **less than 600KB**." ([What is Tauri?](https://v2.tauri.app/start/))
- Capacitor likewise does not bundle a Chromium engine on Android (it uses the system WebView, §4.1), but it does add a native runtime/bridge layer; it publishes no comparable minimal-size claim. ([Capacitor Config](https://capacitorjs.com/docs/config), [Web Project](https://capacitorjs.com/docs/web))

*Verdict: Tauri wins on install footprint by a documented margin; for an offline reading app with user content, this is a real but secondary benefit.*

### 7.2 Rust requirement for contributors

- Tauri: "You **don't need to write Code in Rust**, Swift or Kotlin in most cases. Tauri already offers an extensive JavaScript API." However, the backend/plugin layer is Rust, mobile native glue is Swift/Kotlin, and the toolchain requires Rust + NDK + Android Studio. ([Tauri 2.0 Stable Release](https://tauri.app/blog/tauri-20/), [Prerequisites](https://v2.tauri.app/start/prerequisites/))
- Capacitor: the native layer is Java/Kotlin + Swift; no Rust. A contributor can build the whole web app with Node alone, and only touches native code for custom plugins. ([Capacitor Config](https://capacitorjs.com/docs/config), [Android docs](https://capacitorjs.com/docs/android), [iOS docs](https://capacitorjs.com/docs/ios))

*Verdict: Capacitor is the lower-friction contributor story — which matters for Lekto's explicit "forkable, community-contributable from day one" goal (product brief §162).*

### 7.3 Build tooling

- Tauri mobile: `tauri android dev` / `tauri ios dev`, Android Studio + NDK + `rustup` targets, iOS via Xcode (macOS-only), Gradle v9 / Kotlin v2 / targetSdk 37 as of 2.12. ([Develop](https://v2.tauri.app/develop/), [Prerequisites](https://v2.tauri.app/start/prerequisites/), [Announcing Tauri 2.12](https://tauri.app/blog/tauri-2.12/))
- Capacitor: `npx cap sync` / `npx cap run`, Android Studio, Xcode; `minWebViewVersion` gate. ([Capacitor Android](https://capacitorjs.com/docs/android), [Capacitor iOS](https://capacitorjs.com/docs/ios), [Config](https://capacitorjs.com/docs/config))

*Verdict: Capacitor's toolchain is Node-centric and lighter; Tauri's is heavier but fully supported.*

### 7.4 Ecosystem size

- Tauri official plugins are extensive but the **mobile support table has holes** (no mobile `sql`, `store`, `window-state`, `updater`, etc.), and the two capabilities Lekto needs beyond the core — **SAF directory access** and **TTS** — are **community** plugins (39-star and 21-star respectively). ([Tauri Plugins](https://v2.tauri.app/plugin/), [tauri-plugin-android-fs](https://github.com/aiueo13/tauri-plugin-android-fs), [tauri-plugin-tts](https://github.com/brenogonzaga/tauri-plugin-tts))
- Capacitor has official plugins plus a large **`capacitor-community`** org and third-party maintainers (Capawesome, Capgo). Its TTS plugin is org-owned and its secure-storage and file-picker plugins are actively maintained by established maintainers. ([Capacitor Plugin APIs](https://capacitorjs.com/docs/apis), [Community plugins](https://capacitorjs.com/docs/plugins/community), [Capacitor Community Directory](https://capacitorjs.com/directory), [Capawesome File Picker](https://capawesome.io/docs/sdks/capacitor/file-picker/))

*Verdict: Capacitor's ecosystem is larger and its critical plugins are better maintained; Tauri's is smaller with more first-party mobile gaps.*

### 7.5 Can either ship a true in-browser web app from one codebase?

- **Capacitor: yes.** Web/PWA is a first-class target; the same web assets run in a browser and plugins feature-detect. ([Web Project](https://capacitorjs.com/docs/web))
- **Tauri: no.** Targets are desktop + mobile binaries; once you use Tauri APIs, the frontend no longer runs in a normal browser. ([What is Tauri?](https://v2.tauri.app/start/), [Develop](https://v2.tauri.app/develop/))

*Verdict: This alone is decisive for a Web-first product.*

---

## Comparison table

| Criterion | Tauri v2 | Capacitor | Verdict for Lekto |
|---|---|---|---|
| **Official status (Android)** | Stable since 2.0 (Oct 2024); 2.12 (Sep 2026); not all plugins support mobile; DX "not completely happy" ([blog](https://tauri.app/blog/tauri-20/), [2.12](https://tauri.app/blog/tauri-2.12/)) | Mature; v8 current, long release history, first-class Android ([config](https://capacitorjs.com/docs/config)) | **Capacitor** (maturity/less risk) |
| **Android arbitrary folder pick** | ❌ Built-in dialog "Does not support folder picker" ([dialog](https://v2.tauri.app/plugin/dialog/), [#933](https://github.com/tauri-apps/plugins-workspace/issues/933)) | ❌ `@capacitor/filesystem` has no tree picker; Capawesome `pickDirectory()` exists but only *makes the grant persistable*, does not take it ([prior study](cross-platform-file-access.md), [Capawesome](https://capawesome.io/docs/sdks/capacitor/file-picker/), [discussion #8484](https://github.com/ionic-team/capacitor/discussions/8484)) | **Tie** — both need native SAF glue |
| **Android write to chosen tree** | ❌ Built-in `fs` cannot list/create in a tree URI ([#14587](https://github.com/tauri-apps/tauri/issues/14587)); ✅ community `tauri-plugin-android-fs` does write/create/read_dir ([docs.rs](https://docs.rs/tauri-plugin-android-fs/latest/tauri_plugin_android_fs/api/api_async/struct.AndroidFs.html)) | ❌ `@capacitor/filesystem` cannot create/overwrite `content://` ([IONFILEController.kt](https://github.com/ionic-team/ion-android-filesystem/blob/main/src/main/kotlin/io/ionic/libs/ionfilesystemlib/IONFILEController.kt)) | **Slight Tauri** (an off-the-shelf plugin exists) |
| **Android vault readiness (off-the-shelf)** | Community plugin, ~39★, 1 maintainer ([repo](https://github.com/aiueo13/tauri-plugin-android-fs)) | Capawesome picker (well-maintained) but still needs native persistence + document-tree I/O ([#8484](https://github.com/ionic-team/capacitor/discussions/8484)) | **Tie with caveats** — both require owning native code |
| **fs permissions model** | Capability + scope ACL; no scope ⇒ `forbidden path` ([fs](https://v2.tauri.app/plugin/file-system/)) | Android permissions for legacy dirs; SAF needs no permission but no built-in tree support ([fs](https://capacitorjs.com/docs/apis/filesystem), [SAF](https://developer.android.com/training/data-storage/shared/documents-files)) | **Capacitor** (simpler mental model) |
| **True web app from same codebase** | ❌ Desktop/mobile binaries only; APIs die in a browser ([start](https://v2.tauri.app/start/), [develop](https://v2.tauri.app/develop/)) | ✅ Web/PWA first-class ([web](https://capacitorjs.com/docs/web)) | **Capacitor — decisive** |
| **Android WebView / Web API availability** | System Android WebView (Chromium), version = device ([webview-versions](https://v2.tauri.app/reference/webview-versions/)) | System Android WebView (Chromium), `minWebViewVersion` ([config](https://capacitorjs.com/docs/config)) | **Tie** — governed by WebView ≥132, not the framework |
| **Secure context** | `http://<scheme>.localhost` default; `https` optional ([webview API](https://v2.tauri.app/reference/javascript/api/namespacewebview/)); `.localhost` is trustworthy ([MDN](https://developer.mozilla.org/en-US/docs/Web/Security/Defenses/Secure_Contexts)) | `https://localhost` default ([config](https://capacitorjs.com/docs/config)) | **Tie** — both secure contexts |
| **TTS** | Community, ~21★, Android `TextToSpeech` ([repo](https://github.com/brenogonzaga/tauri-plugin-tts)) | Community-org, ~130★, Android `TextToSpeech` + `openInstall()` ([repo](https://github.com/capacitor-community/text-to-speech)) | **Capacitor** |
| **Secure API-key storage** | Official Stronghold (password-gated encrypted DB) ([stronghold](https://v2.tauri.app/plugin/stronghold/)); no first-party keychain | Community Keystore-backed `@aparajita/capacitor-secure-storage` ([repo](https://github.com/aparajita/capacitor-secure-storage)) | **Capacitor** (closer fit); Stronghold the fallback |
| **Bundle size** | Minimal app <600KB, OS webview ([start](https://v2.tauri.app/start/)) | No comparable claim; native bridge adds weight | **Tauri** |
| **Contributor friction** | Rust backend + NDK + Android Studio ([prereqs](https://v2.tauri.app/start/prerequisites/)) | Node + Android Studio/Xcode; no Rust ([android](https://capacitorjs.com/docs/android)) | **Capacitor** |
| **Ecosystem** | Official plugins with mobile holes; key gaps are community ([plugins](https://v2.tauri.app/plugin/)) | Official + large community org + third-party maintainers ([community](https://capacitorjs.com/docs/plugins/community)) | **Capacitor** |

---

## Recommendation

**Keep Capacitor as Lekto's shell; do not switch to Tauri.**

Reasoning, in order of weight:

1. **The browser target is non-negotiable for Lekto, and only Capacitor can ship it.** The product brief lists **Web (browser)** as a target and the UX spec calls the web app "the primary development target" (product brief §184; UX spec §58). Capacitor "fully supports traditional web and Progressive Web Apps" from the same assets ([Web Project](https://capacitorjs.com/docs/web)); Tauri produces desktop/mobile binaries and its APIs are inert in a browser ([What is Tauri?](https://v2.tauri.app/start/), [Develop](https://v2.tauri.app/develop/)). No amount of Android advantage can recover a target Tauri does not have.
2. **On the file-access requirement, the two are close to a wash — and both need native SAF work.** Tauri's built-ins **also** cannot pick an Android folder or write to a chosen tree ([dialog](https://v2.tauri.app/plugin/dialog/), [#14587](https://github.com/tauri-apps/tauri/issues/14587)), so switching frameworks does **not** make the vault "just work." Capacitor's gap is a **native SAF adapter** (tree pick → `takePersistableUriPermission` → `DocumentsContract.createDocument`/`openOutputStream`), which the prior study already specified and which is small, well-understood Android code ([Android SAF](https://developer.android.com/training/data-storage/shared/documents-files)). Capawesome's `pickDirectory()` now sets `FLAG_GRANT_PERSISTABLE_URI_PERMISSION`, moving Capacitor closer to parity ([discussion #8484](https://github.com/ionic-team/capacitor/discussions/8484)).
3. **Capacitor's TTS and secure-storage plugins are more mature and better fits** (§5, §6), and its contributor story is simpler — relevant to the brief's forkability goal ([product brief §162](./product-brief-2026-03-02.md)).
4. **The WebView engine is a tie on Android** (§4): both use the same updatable system WebView, so Keyboard/FSA API availability is identical and governed by WebView version, not framework. Tauri's Linux/macOS desktop WebKit is actually *worse* for the Chromium-only File System Access API than a user's Chromium browser.

**Concrete path:** keep the storage seam from the prior study, and implement the Android vault as **one** native SAF adapter behind the `VaultStore` interface (`picker: 'saf'`), with app-private storage + export/import as the always-available default. Do the same file-access capability work you would have had to do in Tauri anyway; the difference is that in Capacitor it also ships to the browser.

### Strongest counter-argument to this recommendation

**Tauri already has an off-the-shelf plugin that does the SAF directory-tree vault, and Capacitor still does not.** [`tauri-plugin-android-fs`](https://github.com/aiueo13/tauri-plugin-android-fs) provides exactly the missing capability — `pick_dir`, `create_new_file`/`create_dir_all`/`read_dir`/`write`/`remove` on SAF `FsUri`s, with a Play-friendly default and no special permissions ([docs.rs `AndroidFs`](https://docs.rs/tauri-plugin-android-fs/latest/tauri_plugin_android_fs/api/api_async/struct.AndroidFs.html)) — packaged as a drop-in rather than native code you own. Capacitor's Capawesome picker only *makes* the grant persistable and leaves `takePersistableUriPermission()` and document-tree enumeration/writing to you ([discussion #8484](https://github.com/ionic-team/capacitor/discussions/8484)). Add Tauri's `<600KB` install ([start](https://v2.tauri.app/start/)), official Stronghold for keys ([stronghold](https://v2.tauri.app/plugin/stronghold/)), and the fact that Rust is optional for most day-to-day work ([Tauri 2.0](https://tauri.app/blog/tauri-20/)), and a defensible case exists that **Tauri is the better architecture if the Android vault is the make-or-break requirement and the browser target is negotiable** — especially since the web vault only works on Chromium anyway (Firefox and Safari lack the File System Access API, per the prior study).

The counter-counter: that trade gives up the **true web target entirely** — not merely the vault on Firefox/Safari — and bets the core storage layer on a **39-star, single-maintainer** community plugin. If Lekto must keep "the same app in a browser," Capacitor remains the right call.

---

## References

**Tauri (official)**
- What is Tauri? — https://v2.tauri.app/start/
- Tauri 2.0 Stable Release — https://tauri.app/blog/tauri-20/
- Announcing Tauri 2.12 — https://tauri.app/blog/tauri-2.12/
- Prerequisites — https://v2.tauri.app/start/prerequisites/
- Develop — https://v2.tauri.app/develop/
- Tauri Architecture — https://v2.tauri.app/concept/architecture/
- Plugins & Support Table — https://v2.tauri.app/plugin/
- Dialog plugin — https://v2.tauri.app/plugin/dialog/
- File System plugin — https://v2.tauri.app/plugin/file-system/
- Stronghold plugin — https://v2.tauri.app/plugin/stronghold/
- Localhost plugin — https://v2.tauri.app/plugin/localhost/
- Capabilities — https://v2.tauri.app/security/capabilities/
- Application Lifecycle Threats — https://v2.tauri.app/security/lifecycle/
- Webview Versions — https://v2.tauri.app/reference/webview-versions/
- webview JavaScript API (`useHttpsScheme`) — https://v2.tauri.app/reference/javascript/api/namespacewebview/
- Upgrade from Tauri 1.0 (origin URL on Windows) — https://v2.tauri.app/start/migrate/from-tauri-1/
- Experimental Tauri Verso Integration — https://tauri.app/blog/tauri-verso-integration/

**Tauri (GitHub / plugin repos)**
- plugins-workspace #933 — Dialog pick_folder not implemented — https://github.com/tauri-apps/plugins-workspace/issues/933
- plugins-workspace #1163 — Mobile selection of folders or files — https://github.com/tauri-apps/plugins-workspace/issues/1163
- tauri #14587 — [feat] To implement a folder picker on Android — https://github.com/tauri-apps/tauri/issues/14587
- tauri-plugin-android-fs (community) — https://github.com/aiueo13/tauri-plugin-android-fs
- tauri-plugin-android-fs `AndroidFs` API — https://docs.rs/tauri-plugin-android-fs/latest/tauri_plugin_android_fs/api/api_async/struct.AndroidFs.html
- tauri-plugin-tts (community) — https://github.com/brenogonzaga/tauri-plugin-tts
- tauri-plugin-keyring — https://github.com/HuakunShen/tauri-plugin-keyring
- tauri-plugin-keyring-store — https://crates.io/crates/tauri-plugin-keyring-store/0.1.5
- tauri-plugin-secure-keystore — https://jsr.io/@abdullah/tauri-plugin-secure-keystore
- @impierce/tauri-plugin-keystore — https://www.npmjs.com/package/@impierce/tauri-plugin-keystore

**Capacitor (official)**
- Using Capacitor in a Web Project — https://capacitorjs.com/docs/web
- Capacitor Config (`androidScheme`, `hostname`, `minWebViewVersion`, `useHttpsScheme` note) — https://capacitorjs.com/docs/config
- @capacitor/filesystem — https://capacitorjs.com/docs/apis/filesystem
- @capacitor/preferences — https://capacitorjs.com/docs/apis/preferences
- Plugin APIs — https://capacitorjs.com/docs/apis
- Community Plugins — https://capacitorjs.com/docs/plugins/community
- Capacitor Plugin Directory — https://capacitorjs.com/directory
- Android — https://capacitorjs.com/docs/android
- iOS — https://capacitorjs.com/docs/ios

**Capacitor (community / plugin repos)**
- @capacitor-community/text-to-speech — https://github.com/capacitor-community/text-to-speech
- @aparajita/capacitor-secure-storage — https://github.com/aparajita/capacitor-secure-storage
- Capawesome Secure Preferences — https://capawesome.io/docs/sdks/capacitor/secure-preferences/
- @capawesome/capacitor-file-picker (`pickDirectory`) — https://capawesome.io/docs/sdks/capacitor/file-picker/
- Capacitor discussion #8484 — SAF directory persistence — https://github.com/ionic-team/capacitor/discussions/8484
- ion-android-filesystem (`content://` create/save limitation) — https://github.com/ionic-team/ion-android-filesystem/blob/main/src/main/kotlin/io/ionic/libs/ionfilesystemlib/IONFILEController.kt

**Web platform / Android**
- MDN: Secure contexts (potentially trustworthy origins incl. `*.localhost`) — https://developer.mozilla.org/en-US/docs/Web/Security/Defenses/Secure_Contexts
- MDN: File System API — https://developer.mozilla.org/en-US/docs/Web/API/File_System_API
- MDN: SpeechSynthesis — https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis
- caniuse: Speech Synthesis API — https://caniuse.com/speech-synthesis
- Chrome for Developers: Release notes 132 (File System Access on Android/WebView) — https://developer.chrome.com/release-notes/132
- Android: Access documents and other files from shared storage (SAF) — https://developer.android.com/training/data-storage/shared/documents-files
- Android: Storage updates in Android 11 — https://developer.android.com/about/versions/11/privacy/storage
- Google Play: Use of All files access permission — https://support.google.com/googleplay/android-developer/answer/10467955

**Lekto internal**
- docs/research/cross-platform-file-access.md (companion study)
- docs/product-brief-2026-03-02.md
- docs/ux-design-specification.md
