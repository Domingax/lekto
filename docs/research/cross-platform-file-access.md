# Cross-platform file access: feasibility of the Lekto "vault"

**Date:** 2026-09-27
**Scope:** Can Lekto (React/Vite/TypeScript; Web + Android via Capacitor) store all user data in a **portable, user-chosen folder** that the user can relocate to a cloud-synced directory (Syncthing, rclone, Dropbox, …) to obtain serverless multi-device sync?
**Method:** Findings below are drawn from primary sources only — MDN, W3C/WICG specs, Chrome release notes, web.dev, caniuse, Capacitor official docs and source, Android developer docs, and the source/docs repos of comparable apps. Where sources disagree, both are cited and the discrepancy is called out. All claims carry a URL.

> **Headline:** A true shared folder is feasible on **Chromium desktop** and (since Chrome 132) **Chromium on Android/WebView**, but it is **not** uniformly available on Firefox/Safari, and on Android the safe, Play-compliant route (Storage Access Framework) is only **partially supported by `@capacitor/filesystem` today**. The vault should therefore be a **capability-driven storage seam**, not a hard "always a folder" promise.

---

## TL;DR verdict matrix

| Platform | User-visible chosen folder | Persist across reload | Third-party sync tool can see it | Verdict |
|---|---|---|---|---|
| **Web desktop — Chromium (Chrome/Edge/Opera)** | ✅ `showDirectoryPicker()` read/write | ⚠️ handle in IndexedDB + re-request permission (persistent from Chrome 122) | ✅ it is a real OS folder | **Feasible** |
| **Web desktop — Firefox** | ❌ API not implemented | — | — | **Not feasible** (fallback needed) |
| **Web desktop — Safari** | ❌ API not implemented | — | — | **Not feasible** (fallback needed) |
| **Web mobile — Chrome for Android (132+)** | ✅ per Chrome release notes + MDN BCD; caniuse lags | ⚠️ same as desktop | ✅ (Android SAF-backed; subject to Android dir restrictions) | **Feasible on 132+** |
| **Web mobile — Firefox/Safari/WebView < 132** | ❌ / limited | — | — | **Not feasible** |
| **Android — Capacitor app-private store** | ❌ private | ✅ | ❌ invisible to Syncthing | **Feasible as primary store, not portable** |
| **Android — public Documents via Capacitor paths** | ⚠️ partial | ✅ for app-created files | ✅ | **Partial** |
| **Android — arbitrary SAF tree folder** | ⚠️ picker not in Capacitor; content:// URIs are **read/delete only** in `@capacitor/filesystem` | ⚠️ requires `takePersistableUriPermission` | ✅ | **Partial — needs a dedicated SAF plugin** |

---

## 1. File System Access API (web)

`showDirectoryPicker()` / `showOpenFilePicker()` / `showSaveFilePicker()` are extensions to the File System API that let a web app read/write real files and directories in the user's OS file system. Handles (`FileSystemFileHandle`, `FileSystemDirectoryHandle`) represent entries, and a directory handle can be enumerated and resolved to relative paths. ([MDN File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API), [MDN `showDirectoryPicker()`](https://developer.mozilla.org/en-US/docs/Web/API/Window/showDirectoryPicker))

### 1.1 Browser support matrix

| Browser | Support | Version | Source |
|---|---|---|---|
| Chrome (desktop) | ✅ | 86+ (full ~105+) | [MDN BCD](https://github.com/mdn/browser-compat-data/blob/main/api/Window.json), [caniuse](https://caniuse.com/native-filesystem-api) |
| Edge (desktop) | ✅ | mirrors Chromium (86+/105+) | caniuse |
| Opera (desktop) | ✅ | 72 partial → 91+ | caniuse |
| **Chrome for Android** | ✅ | **132+** | [Chrome release notes 132](https://developer.chrome.com/release-notes/132), MDN BCD (`chrome_android: 132`) |
| **Android WebView** | ✅ | **132+** | [Chrome release notes 132](https://developer.chrome.com/release-notes/132) |
| Firefox (desktop & Android) | ❌ | not implemented; Mozilla filed a "harmful" standards position | [caniuse](https://caniuse.com/native-filesystem-api) |
| Safari (macOS & iOS) | ❌ | not implemented (through current TP) | caniuse, MDN BCD |
| Brave | ⚠️ | behind a flag | [Chrome FSA docs](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access) |

**Discrepancy to note:** caniuse's table (data as of Aug 2026) still lists *Chrome for Android* as **not supported**, while Chrome's own release notes state: *"File System access shipped on Desktop in Chrome 86, with Chrome 132 it's available on Android and WebView"* ([release notes 132](https://developer.chrome.com/release-notes/132)), and MDN BCD records `chrome_android: 132`. Treat **Chromium Android/WebView 132+ as supported**, but verify on-device before depending on it, because caniuse has not caught up.

**Relevance to Capacitor:** Capacitor's Android WebView loads the app over `https://localhost` by default (`server.androidScheme` defaults to `https` since v1.2.0 — [Capacitor config docs](https://capacitorjs.com/docs/config)), so the web layer satisfies the secure-context requirement. The API's availability then depends on the device's WebView version being ≥132; Android System WebView is updatable via Play on modern Android but can be older on unpatched devices.

### 1.2 Persistence and permission re-prompt semantics

- **Handles survive reloads via IndexedDB.** `FileSystemHandle` objects are structured-cloneable and can be stored in IndexedDB (or transferred via `postMessage`). ([MDN File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API), [Chrome FSA docs](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access))
- **Permissions do not automatically survive.** "Permissions are not always persisted between sessions." Apps must call `queryPermission()` and, if not `granted`, `requestPermission({mode:'readwrite'})`. ([Chrome FSA docs](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access), [MDN `requestPermission()`](https://developer.mozilla.org/en-US/docs/Web/API/FileSystemHandle/requestPermission))
- **Transient user activation is required** for `requestPermission()`; a handle re-acquired on load cannot silently re-request in a worker or without a user gesture. ([MDN `requestPermission()`](https://developer.mozilla.org/en-US/docs/Web/API/FileSystemHandle/requestPermission))
- **Chrome 122+ persistent permissions.** A new three-way prompt offers *"Allow this time"* (session), *"Allow on every visit"* (indefinite until revoked), or *"Don't allow"*. Preconditions: the origin previously stored the handle in IndexedDB and calls `requestPermission()` on the next visit; or the permission was auto-revoked after the tab was backgrounded (one-time-permission behaviour). **Installed PWAs are granted persistent access automatically once the user allows access** — no three-way prompt. ([Chrome persistent-permissions blog](https://developer.chrome.com/blog/persistent-permissions-for-the-file-system-access-api))
- **Writes are non-in-place and slower** for the user-visible FS (temp file + security checks, e.g. Safe Browsing), unlike OPFS. ([MDN OPFS](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system))

### 1.3 Secure-context / origin constraints

- Secure context (HTTPS) required. ([MDN File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API))
- `showDirectoryPicker()` throws `SecurityError` if blocked by same-origin policy or not invoked from user interaction; `AbortError` if the user cancels or the chosen directory is deemed too sensitive. ([MDN `showDirectoryPicker()`](https://developer.mozilla.org/en-US/docs/Web/API/Window/showDirectoryPicker))
- `requestPermission()` throws `SecurityError` in a cross-origin iframe or without transient activation. ([MDN `requestPermission()`](https://developer.mozilla.org/en-US/docs/Web/API/FileSystemHandle/requestPermission))

**Verdict (Q1):** On Chromium desktop and Chromium Android/WebView 132+, a persisted directory handle + IndexedDB + `requestPermission()` (or an installed PWA) yields a real, user-visible folder that survives reloads. On Firefox and all Safari, the API does not exist.

---

## 2. Web alternatives to a real folder

| Mechanism | What it is | User-visible / externally syncable? | Notes |
|---|---|---|---|
| **OPFS (Origin Private File System)** | Origin-private storage endpoint of the File System API; in-place byte-level read/write; synchronous handles in workers. | ❌ "private to the origin of the page and not visible to the user"; "you cannot expect to find the created files matched one-to-one" on disk. | Subject to storage quota; clearing site data deletes it; no permission prompts. ([MDN OPFS](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system)) |
| **IndexedDB** | Origin-scoped transactional object store; can persist `FileSystemHandle` objects. | ❌ not user-visible. | Quota-bound; evictable under storage pressure. ([MDN IndexedDB](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API), [MDN storage quotas](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)) |
| **Download / upload** | `<a download>`, `<input type="file">`, File API blobs. | ⚠️ user-mediated, one-shot transfers only. | No live folder, no incremental sync; requires explicit user action each time. ([MDN File API](https://developer.mozilla.org/en-US/docs/Web/API/File_API)) |
| **Web Share API** | Invokes the OS share sheet with text/URL/files (`navigator.share({files})`, gated by `navigator.canShare({files})`). | ⚠️ export-to-another-app only. | Requires transient activation and `web-share` permission policy; not a storage layer; file sharing unsupported in some engines. ([MDN `Navigator.share()`](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/share), [caniuse web-share](https://caniuse.com/web-share)) |

**Verdict (Q2):** None of these substitutes provide a folder the user can also see and sync externally. OPFS/IndexedDB are excellent **primary local stores** (and OPFS is the recommended backing for SQLite WASM — see §7), but portability/sync requires either the File System Access API or explicit export/import (download/upload, Web Share).

---

## 3. Capacitor `@capacitor/filesystem` (Android)

### 3.1 Accessible directories

`Directory` enum (v8 docs): `Documents`, `Data`, `Library`, `Cache`, `External`, `ExternalStorage`, `ExternalCache`, `LibraryNoCloud`, `Temporary`. ([Capacitor Filesystem](https://capacitorjs.com/docs/apis/filesystem))

Android semantics from the official docs:

- **`Data` / `Library`** — "the directory holding application files. Files will be deleted when the application is uninstalled." (app-private)
- **`External`** — app-owned dir on the primary shared/external volume; "internal to the applications, and not typically visible to the user as media"; deleted on uninstall.
- **`Cache` / `ExternalCache`** — cache, may be deleted by the system.
- **`Documents`** — "the Public Documents folder, so it's **accessible from other apps**. It's not accessible on Android 10 unless the app enables legacy External Storage … **On Android 11 or newer the app can only access the files/folders the app created.**"
- **`ExternalStorage`** — primary shared storage; "only available on Android 9 or older"; not accessible on Android 11+.

Permissions: using `Documents` or `ExternalStorage` on Android 10 and older requires `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` in the manifest, and `checkPermissions()`/`requestPermissions()` at runtime. ([Capacitor Filesystem](https://capacitorjs.com/docs/apis/filesystem))

### 3.2 Can it read/write an arbitrary user-chosen directory?

**No, not through the plugin's own API.** The plugin supports "full `file://` paths, or reading `content://` files on Android" by omitting the `directory` param — but the underlying `ion-android-filesystem` library explicitly states:

- `createFile(...)`: *"This method will fail if a `content://` type URI is passed"*
- `saveFile(...)`: *"This method will fail if a `content://` type URI is passed"*
- `copyFile(...)`: *"Copying files from a `content://` uri to another is not supported, as we cannot create new files with `content://` scheme"*

([IONFILEController.kt](https://github.com/ionic-team/ion-android-filesystem/blob/main/src/main/kotlin/io/ionic/libs/ionfilesystemlib/IONFILEController.kt), [IONFILEContentHelper.kt](https://github.com/ionic-team/ion-android-filesystem/blob/main/src/main/kotlin/io/ionic/libs/ionfilesystemlib/helper/IONFILEContentHelper.kt), used by [FilesystemPlugin.kt](https://github.com/ionic-team/capacitor-filesystem/blob/main/android/src/main/kotlin/com/capacitorjs/plugins/filesystem/FilesystemPlugin.kt))

So today `@capacitor/filesystem` can **read**, **read in chunks**, **get metadata**, and **delete** existing `content://` URIs, but it **cannot create or overwrite files** addressed by a `content://` URI, and it ships **no `ACTION_OPEN_DOCUMENT_TREE` picker** at all. The plugin's `Directory.Documents` route only reaches files *the app itself created* on Android 11+.

### 3.3 How SAF surfaces picks; Capacitor support today

Android's Storage Access Framework gives the user a system picker and returns a URI, not a real path:

- `ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` → per-document URIs.
- `ACTION_OPEN_DOCUMENT_TREE` (API 21+) → a **tree URI** granting access to a directory and all its children.
- Access is persisted across reboots only if the app calls `ContentResolver.takePersistableUriPermission()`; it is lost if the document is moved/deleted.
- No manifest permission is required for SAF; files remain after the app is uninstalled.
- On Android 11+, you **cannot** pick: the root of internal storage, the root of reliable SD-card volumes, or the `Download` directory; and cannot pick `Android/data/` or `Android/obb/`. ([Android: documents & files](https://developer.android.com/training/data-storage/shared/documents-files), [Android 11 storage](https://developer.android.com/about/versions/11/privacy/storage))

Capacitor's plugin neither launches the tree picker nor supports tree-URI writes (above), so a **SAF-backed vault requires a dedicated plugin** (or a small native module) that performs `ACTION_OPEN_DOCUMENT_TREE`, `takePersistableUriPermission`, and `DocumentsContract.createDocument`-based writes.

**Verdict (Q3):** Capacitor can write to app-private storage and to its own files under public `Documents`, and can read SAF `content://` URIs. It **cannot** today treat an arbitrary user-chosen directory as a read/write vault. That capability must be added natively (SAF plugin) or the vault must live app-private with export.

---

## 4. Android scoped storage (10+) and a shared public folder

### 4.1 What changed

- Android 10 introduced scoped storage; `requestLegacyExternalStorage` temporarily opted out.
- **When targeting Android 11+ the system ignores `requestLegacyExternalStorage`, and `WRITE_EXTERNAL_STORAGE`/`WRITE_MEDIA_STORAGE` "no longer provide any additional access."** ([Android 11 storage](https://developer.android.com/about/versions/11/privacy/storage))
- Apps can contribute to well-defined media collections via `MediaStore` without storage permissions, but **non-media files in shared storage are handled via SAF**.
- **All-files access** (`MANAGE_EXTERNAL_STORAGE` + `ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION`, checked with `Environment.isExternalStorageManager()`) grants read/write to all of shared storage plus `MediaStore.Files`, but *not* other apps' `Android/data/` dirs. ([Android manage all files](https://developer.android.com/training/data-storage/manage-all-files))
- Google Play **restricts** `MANAGE_EXTERNAL_STORAGE`: permitted uses are file managers, backup/restore, anti-virus, **document management apps**, on-device search, etc.; the app must justify why SAF/MediaStore are insufficient and get approval. ([Play policy: All files access](https://support.google.com/googleplay/android-developer/answer/10467955))

### 4.2 Can `/storage/emulated/0/Documents` be a shared vault Syncthing also syncs?

Physically yes — it is shared external storage, and a general sync tool with all-files access can read/write it. The canonical example is Obsidian on Android, which offers exactly this and states:

> "With the **device storage** option, your data is stored in a shared location on your device. This allows your Obsidian vault to be accessed by other apps and services, such as third-party sync tools. … Due to limitations with Android, Obsidian will request **'All files' access** to function reliably." ([Obsidian for Android](https://help.obsidian.md/Obsidian/Obsidian+for+Android))

The catch is on the Lekto side: **without** all-files access or a SAF grant, an app on Android 11+ can only reach the files/folders *it created* in `Documents` (Capacitor docs, §3.1). A file that Syncthing drops in from another device was not created by Lekto, so Lekto may not be able to read it via plain paths. A shared vault therefore requires **either**:
1. `MANAGE_EXTERNAL_STORAGE` (Play approval; a language-learning reader would have to argue it is a document-management app — likely a hard sell under the policy above), **or**
2. a **SAF tree grant** on the chosen folder (Play-friendly, no permission), with a native plugin that can create/write through `DocumentsContract`.

### 4.3 Manifest permissions involved

| Permission | Use | Notes |
|---|---|---|
| `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` | legacy public-storage access | Only effective ≤ Android 10; declare with `maxSdkVersion`; `WRITE_…` is a no-op for apps targeting 11+. ([Capacitor Filesystem](https://capacitorjs.com/docs/apis/filesystem), [Android 11 storage](https://developer.android.com/about/versions/11/privacy/storage)) |
| `MANAGE_EXTERNAL_STORAGE` | all-files access | Special app access; Play-restricted; requires declaration form. ([manage all files](https://developer.android.com/training/data-storage/manage-all-files)) |
| *(none)* | SAF tree/document picks | SAF explicitly requires no system permission. ([documents & files](https://developer.android.com/training/data-storage/shared/documents-files)) |

**Verdict (Q4):** A public shared folder is possible, but on Android 11+ it is **not** achievable with plain file paths and standard permissions. The Play-compliant route is SAF; the convenient route (all-files access, as Obsidian uses) is a policy risk for a reading app.

---

## 5. TTS

### 5.1 Web Speech API (`speechSynthesis`)

- `window.speechSynthesis` + `SpeechSynthesisUtterance` + `SpeechSynthesisVoice`; voices come from the OS/browser (`getVoices()`, `voiceschanged`). ([MDN Web Speech API guide](https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API/Using_the_Web_Speech_API), [MDN `SpeechSynthesis`](https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis))
- **Support is broad:** Chrome 33+, Edge 14+, Firefox 49+, Safari 7+, Chrome for Android, Firefox for Android, Samsung Internet, iOS Safari. Global ~95.4%. Not supported: IE, Opera Mini, older Android/UC. ([caniuse speech-synthesis](https://caniuse.com/speech-synthesis))
- **Caveats:** voice availability is OS-dependent; a target language may have no voice installed; voices may load asynchronously (`voiceschanged`); quality/offline behaviour varies. The Web Speech API's *recognition* half may use a server, but *synthesis* uses the platform engine. ([MDN guide](https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API/Using_the_Web_Speech_API))

### 5.2 Capacitor TTS (Android)

The official-ish community plugin is `@capacitor-community/text-to-speech`, wrapping Android's native `TextToSpeech`: `speak`, `stop`, `getSupportedLanguages`, `getSupportedVoices`, `isLanguageSupported`, `openInstall()` (Android-only: install missing resource files), and an `onRangeStart` listener. No configuration required. ([capacitor-community/text-to-speech](https://github.com/capacitor-community/text-to-speech))

### 5.3 Fallbacks when absent

- On web with no target-language voice: show TTS as unavailable in the panel (the UX spec already calls for graceful "unavailable" states); optionally fall back to a different installed voice/locale, or to a remote TTS endpoint (breaks the offline guarantee).
- On Android: `isLanguageSupported({lang})` → `openInstall()` to prompt for the language pack; otherwise fall back to a remote TTS service or hide the control.
- **Verdict (Q5):** `speechSynthesis` covers web desktop and mobile broadly; Capacitor TTS covers Android natively. TTS is not a blocker, but per-language voice availability must be feature-detected at runtime, not assumed.

---

## 6. How comparable local-first apps actually do this

| App | Storage model | Portable folder? | Known caveats (from primary docs) |
|---|---|---|---|
| **Obsidian** | A **vault = a real folder** of Markdown files + `.obsidian/` config. ([Manage vaults](https://help.obsidian.md/Files+and+folders/Manage+vaults)) | ✅ on desktop; ✅ **Android "device storage"** (shared folder, all-files access); ❌ **iOS** third-party folder sync | "Obsidian requires access to the entire vault … This makes it difficult for some services to function reliably" (cloud drives that offload files, e.g. Files On-Demand). Android Syncthing **official app is deprecated** → community [Syncthing-Fork](https://github.com/Catfriend1/syncthing-android). iOS officially supports only Obsidian Sync + iCloud. ([Sync your notes](https://help.obsidian.md/Getting+started/Sync+your+notes+across+devices), [Obsidian for Android](https://help.obsidian.md/Obsidian/Obsidian+for+Android)) |
| **Logseq** | File graphs = folders of Markdown/Org (desktop/mobile); new **DB graphs = SQLite**, synced via **RTC** (paid/self-host). Assets live under `assets/`. | ⚠️ files for file graphs; DB graphs are a SQLite DB, exported via `Export SQLite DB` / `.zip` / EDN. | DB version is beta and issues data-loss warnings; RTC is invite-only alpha. Mobile is a separate app. ([Logseq README](https://github.com/logseq/logseq), [db-version.md](https://github.com/logseq/docs/blob/master/db-version.md)) |
| **Joplin** | Local **SQLite database** per profile; sync is abstracted to a filesystem-like driver (Joplin Cloud, Nextcloud, S3, WebDAV, Dropbox, OneDrive, **local filesystem**), with E2EE. | ❌ no shared vault folder; sync target is optional. | Conflicts are resolved by copying the local note into a *Conflict* notebook and letting the remote version win; docs advise syncing before/after editing. ([Joplin sync](https://joplinapp.org/help/apps/sync/), [Joplin conflicts](https://joplinapp.org/help/apps/conflict/)) |
| **Standard Notes** | Encrypted **local database** (device DB encrypted with account or passcode keys); works fully offline; sync via account/server (self-hostable); backups via export/import. | ❌ no folder model. | No account + no passcode = unencrypted local DB; web has no OS keychain, so keys are stored in the app DB unless a passcode is set. ([encrypt on device](https://standardnotes.com/help/79/how-does-standard-notes-encrypt-data-on-my-device), [totally offline](https://standardnotes.com/help/59/can-i-use-standard-notes-totally-offline)) |

**Pattern:** the only app that truly does "vault = real shared folder" everywhere it can is **Obsidian**, and it pays for it with (a) all-files access on Android, (b) a whole-vault requirement that breaks cloud-drive file offloading, and (c) no folder-based third-party sync on iOS. Joplin/Standard Notes abandon the folder entirely in favour of a local DB + sync engine; Logseq is migrating from files to a SQLite DB + RTC.

---

## 7. Sync-strategy implications if a real shared folder is not universal

Established alternatives, with primary references:

1. **Per-record files + a sync tool (last-writer-wins at the file level).** Obsidian's model: many small Markdown files in a folder synced by Syncthing/iCloud/OneDrive/Git. Works when each file has a single writer; conflicts appear as duplicate/conflicted files. Obsidian's own docs warn that whole-vault access and "keep downloaded" are prerequisites, and that mixing sync services causes conflicts. ([Obsidian sync](https://help.obsidian.md/Getting+started/Sync+your+notes+across+devices)) **Implication for Lekto:** one JSON file per vocabulary entry / per book progress, with a stable id and `updatedAt`, is far safer than a single monolithic DB in a synced folder.

2. **Export/import bundles (manual or scheduled).** Joplin (export/import), Logseq (`Export SQLite DB` / `.zip` / EDN), Standard Notes (backups). Portable and simple, but not automatic. ([Logseq db-version.md](https://github.com/logseq/docs/blob/master/db-version.md), [Standard Notes backups](https://standardnotes.com/help/14/how-do-i-create-and-import-backups-of-my-standard-notes-data))

3. **A sync engine / plugin.** Joplin's driver abstraction over WebDAV/S3/Dropbox/etc. with E2EE and conflict copies; Logseq RTC; Obsidian Sync. This reintroduces a server or a third-party account — outside Lekto's "no server" constraint unless the user supplies storage. ([Joplin sync](https://joplinapp.org/help/apps/sync/), [Logseq README](https://github.com/logseq/logseq))

4. **CRDT / convergent merge.** Merge independent edits automatically instead of LWW:
   - **Yjs** — CRDT with shared types (`Y.Map`, `Y.Array`) that merge without conflicts; explicitly network-agnostic and usable for local-first software with pluggable providers. ([docs.yjs.dev](https://docs.yjs.dev/))
   - **cr-sqlite** — a SQLite/libSQL extension adding multi-master replication via CRDTs (LWW columns, counters, fractional indices) and a `crsql_changes` changeset table; "write to your SQLite database while offline … merge our databases together, without conflict." ([vlcn-io/cr-sqlite](https://github.com/vlcn-io/cr-sqlite))
   - **SQLite WASM over OPFS** as the durable local engine — SQLite's own docs describe `opfs` / `opfs-sahpool` VFSes, quirks (COOP/COEP for `opfs`, single-connection for `opfs-sahpool`, exclusive locking), and that it is not a user-visible FS. ([SQLite WASM persistence](https://sqlite.org/wasm/doc/trunk/persistence.md))
   - Design vocabulary: Kleppmann et al., *Local-first software: You own your data, in spite of the cloud* ([inkandswitch.com/local-first](https://www.inkandswitch.com/local-first/)).
   **Implication for Lekto:** a CRDT-backed store scales to concurrent offline edits and makes cloud-drive sync of a *single* file viable (each device writes a merged doc), but it is significantly more machinery than the MVP needs.

5. **Structure the on-disk format to be merge-friendly regardless of transport.** Append-only per-device change logs + immutable record files (or one CRDT document per aggregate) let any byte-level file sync (Syncthing/rclone/Dropbox) converge without a server, while still allowing a human-readable export.

**Do not** put a single mutable SQLite/JSON database in a synced folder: Joplin's conflict model and Obsidian's whole-vault/offloading caveats both illustrate why opaque single-file stores fight file-level sync. ([Joplin conflicts](https://joplinapp.org/help/apps/conflict/), [Obsidian sync](https://help.obsidian.md/Getting+started/Sync+your+notes+across+devices))

---

## Feasibility verdict per platform

### Web desktop
- **Chromium (Chrome/Edge/Opera): FEASIBLE.** `showDirectoryPicker({mode:'readwrite'})` + store the handle in IndexedDB + `requestPermission()` on each session (or install the PWA for persistent access). Real user-visible folder, external sync works.
- **Firefox & Safari: NOT FEASIBLE.** No picker API. Use an origin-private store (OPFS/IndexedDB) plus export/import (download/upload, Web Share).

### Web mobile
- **Chrome for Android / Android WebView 132+: FEASIBLE (verify on device).** Shipped in Chrome 132 per Chrome release notes and MDN BCD; caniuse still says unsupported, so this is the least-certain cell. Android's own directory restrictions still apply to what the picker can select. ([release notes 132](https://developer.chrome.com/release-notes/132), [Android 11 storage](https://developer.android.com/about/versions/11/privacy/storage))
- **Firefox for Android, iOS Safari, WebView <132: NOT FEASIBLE.** OPFS/IndexedDB + export/import only.

### Android (Capacitor native)
- **App-private store (`Directory.Data`/`Library`): FEASIBLE but NOT PORTABLE** — deleted on uninstall and invisible to Syncthing. ([Capacitor Filesystem](https://capacitorjs.com/docs/apis/filesystem))
- **Public `Documents` via plain paths: PARTIAL** — on Android 11+ only files the app created; cannot read files another app (Syncthing) drops in. ([Capacitor Filesystem](https://capacitorjs.com/docs/apis/filesystem))
- **Arbitrary user-chosen SAF tree: PARTIAL** — Android/SAF supports it well, but `@capacitor/filesystem` has no tree picker and cannot write `content://` URIs (read/delete only), so a **native SAF plugin is required**. ([IONFILEController.kt](https://github.com/ionic-team/ion-android-filesystem/blob/main/src/main/kotlin/io/ionic/libs/ionfilesystemlib/IONFILEController.kt), [Android SAF](https://developer.android.com/training/data-storage/shared/documents-files))
- **All-files access: RISKY** — Play permits `MANAGE_EXTERNAL_STORAGE` mainly for file managers/backup/document-management apps, with a declaration and approval. ([Play policy](https://support.google.com/googleplay/android-developer/answer/10467955))

**Overall:** The founder's suspicion is correct in the strong form ("always a real user-chosen folder on every platform") and incorrect in the weak form ("on Chromium desktop/Android we can do it well"). The promise must be **conditional and capability-driven**.

---

## Recommended storage seam

Model the vault as a **deep module** with a capability descriptor, several adapters, a merge-friendly on-disk format, and capability-driven UX. Key API surface:

```ts
type VaultCapabilities = {
  userVisible: boolean;      // a folder the user can open in a file manager
  relocatable: boolean;      // user can pick a different folder
  externalSync: boolean;     // a third-party tool can read/write it
  liveFolder: boolean;       // changes can be observed, not just exported
  picker: 'fsa' | 'saf' | 'none';
};

interface VaultStore {
  capabilities(): VaultCapabilities;
  read(relPath: string): Promise<Uint8Array | null>;
  write(relPath: string, data: Uint8Array): Promise<void>;   // atomic where possible
  list(prefix: string): Promise<string[]>;
  delete(relPath: string): Promise<void>;
  watch?(cb: (changes: string[]) => void): () => void;        // external-change detection
  exportBundle(): Promise<Blob>;                              // always available
  importBundle(b: Blob): Promise<void>;                       // always available
}
```

**Adapters (selected at startup):**

1. **`FsaVaultStore` (web/Chromium desktop; Chromium Android/WebView 132+).** `showDirectoryPicker({mode:'readwrite'})`, persist the `FileSystemDirectoryHandle` in IndexedDB, `queryPermission()`/`requestPermission()` on each session (or rely on PWA-persistent permission). Use `FileSystemObserver` where available, otherwise poll; write atomically (temp + rename) so a concurrent Syncthing write cannot observe a half-written file. ([MDN File System API](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API), [Chrome persistent permissions](https://developer.chrome.com/blog/persistent-permissions-for-the-file-system-access-api), [MDN FileSystemObserver](https://developer.mozilla.org/en-US/docs/Web/API/FileSystemObserver))
2. **`OpfsVaultStore` (web fallback: Firefox/Safari, WebView <132).** OPFS or IndexedDB as primary store; `exportBundle()` / `importBundle()` via download/upload; optionally Web Share for handing a bundle to another app. `capabilities()` returns `userVisible:false, externalSync:false`. ([MDN OPFS](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system), [MDN `Navigator.share()`](https://developer.mozilla.org/en-US/docs/Web/API/Navigator/share))
3. **`CapacitorPrivateVaultStore` (Android default).** `Directory.Data`/`Library` via `@capacitor/filesystem`; export/import to a user-chosen location; no Syncthing visibility. ([Capacitor Filesystem](https://capacitorjs.com/docs/apis/filesystem))
4. **`SafVaultStore` (Android, opt-in, requires a small native plugin).** `ACTION_OPEN_DOCUMENT_TREE` → `takePersistableUriPermission` → read/write/delete via `ContentResolver.openInputStream` / `DocumentsContract.createDocument` / `openOutputStream`. This is the Play-compliant path to a real shared vault. ([Android SAF](https://developer.android.com/training/data-storage/shared/documents-files))
5. *(Optional, power users)* `AllFilesVaultStore` behind a Play policy decision — do **not** ship unless the app qualifies.

**On-disk format for portability:**
- One **immutable JSON file per record** (`vocabulary/<id>.json`, `progress/<bookId>.json`) with `id`, `schemaVersion`, `updatedAt`, and device/actor id — never one mutable monolithic DB in a synced folder (see Joplin/Obsidian caveats, §7).
- A small `manifest.json` describing the vault; a `.lekto/` config folder analogous to Obsidian's `.obsidian/`.
- **Atomic writes** (write temp, fsync, rename) to avoid Syncthing reading partial files.
- Optional **append-only change log** (`log/<device>.jsonl`) so any file-level sync converges; if/when needed, upgrade to a CRDT document per aggregate (Yjs) or cr-sqlite. ([Yjs](https://docs.yjs.dev/), [cr-sqlite](https://github.com/vlcn-io/cr-sqlite))

**UX degradation (fits the existing Journey 4):**
- If `capabilities().relocatable`: show "Change vault location" / "Open existing vault" via the platform picker.
- If not: show "Export backup" / "Import backup" and, on web, a Web Share button; never promise live sync.
- Keep API keys out of the vault (device-local only), per the product brief's security constraints.

**Bottom line:** Ship the app on an **always-available origin-private/app-private store**, add **export/import** everywhere, and surface a **real shared-folder vault as a progressive-enhancement capability** where the platform supports it (Chromium desktop; Chromium Android/WebView 132+; Android SAF via a native plugin). This preserves Lekto's portability promise without betting the MVP on an API that Firefox, Safari, and stock Capacitor Android do not yet provide.

---

## References

**Web file access / storage**
- MDN, File System API — https://developer.mozilla.org/en-US/docs/Web/API/File_System_API
- MDN, `Window.showDirectoryPicker()` — https://developer.mozilla.org/en-US/docs/Web/API/Window/showDirectoryPicker
- MDN, `FileSystemHandle.requestPermission()` — https://developer.mozilla.org/en-US/docs/Web/API/FileSystemHandle/requestPermission
- MDN, Origin private file system (OPFS) — https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system
- MDN, IndexedDB — https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API
- MDN, Storage quotas and eviction criteria — https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria
- MDN, File API — https://developer.mozilla.org/en-US/docs/Web/API/File_API
- MDN, `Navigator.share()` — https://developer.mozilla.org/en-US/docs/Web/API/Navigator/share
- MDN browser-compat-data (`Window.json`) — https://github.com/mdn/browser-compat-data/blob/main/api/Window.json
- caniuse, File System Access API — https://caniuse.com/native-filesystem-api
- caniuse, Web Share API — https://caniuse.com/web-share
- Chrome for Developers, The File System Access API — https://developer.chrome.com/docs/capabilities/web-apis/file-system-access
- Chrome for Developers, Persistent permissions for the File System Access API — https://developer.chrome.com/blog/persistent-permissions-for-the-file-system-access-api
- Chrome for Developers, Release notes 132 — https://developer.chrome.com/release-notes/132

**Capacitor**
- `@capacitor/filesystem` — https://capacitorjs.com/docs/apis/filesystem
- Capacitor config (`server.androidScheme`) — https://capacitorjs.com/docs/config
- capacitor-filesystem Android source — https://github.com/ionic-team/capacitor-filesystem/blob/main/android/src/main/kotlin/com/capacitorjs/plugins/filesystem/FilesystemPlugin.kt
- ion-android-filesystem (`content://` create/save limitation) — https://github.com/ionic-team/ion-android-filesystem/blob/main/src/main/kotlin/io/ionic/libs/ionfilesystemlib/IONFILEController.kt

**Android**
- Access documents and other files from shared storage (SAF) — https://developer.android.com/training/data-storage/shared/documents-files
- Manage all files on a storage device — https://developer.android.com/training/data-storage/manage-all-files
- Storage updates in Android 11 — https://developer.android.com/about/versions/11/privacy/storage
- Google Play: Use of All files access permission — https://support.google.com/googleplay/android-developer/answer/10467955

**TTS**
- MDN, Using the Web Speech API — https://developer.mozilla.org/en-US/docs/Web/API/Web_Speech_API/Using_the_Web_Speech_API
- MDN, `SpeechSynthesis` — https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesis
- caniuse, Speech Synthesis API — https://caniuse.com/speech-synthesis
- `@capacitor-community/text-to-speech` — https://github.com/capacitor-community/text-to-speech

**Comparable apps**
- Obsidian, Obsidian for Android — https://help.obsidian.md/Obsidian/Obsidian+for+Android
- Obsidian, Sync your notes across devices — https://help.obsidian.md/Getting+started/Sync+your+notes+across+devices
- Obsidian, Manage vaults — https://help.obsidian.md/Files+and+folders/Manage+vaults
- Syncthing-Fork (community Android fork) — https://github.com/Catfriend1/syncthing-android
- Logseq repository — https://github.com/logseq/logseq
- Logseq DB version docs — https://github.com/logseq/docs/blob/master/db-version.md
- Joplin, Synchronisation — https://joplinapp.org/help/apps/sync/
- Joplin, What is a conflict? — https://joplinapp.org/help/apps/conflict/
- Standard Notes, How does Standard Notes encrypt data on my device? — https://standardnotes.com/help/79/how-does-standard-notes-encrypt-data-on-my-device
- Standard Notes, Can I use Standard Notes totally offline? — https://standardnotes.com/help/59/can-i-use-standard-notes-totally-offline

**Sync strategies**
- Yjs CRDT documentation — https://docs.yjs.dev/
- cr-sqlite: Convergent, Replicated, SQLite — https://github.com/vlcn-io/cr-sqlite
- SQLite WASM: Persistent Storage Options — https://sqlite.org/wasm/doc/trunk/persistence.md
- Ink & Switch, Local-first software — https://www.inkandswitch.com/local-first/
