# Cloud sync backends: can the vault sync directly to the user's own cloud storage?

**Date:** 2026-09-27
**Scope:** For Lekto (Android-first, Kotlin Multiplatform + Compose Multiplatform; desktop second; web out of scope) and the no-backend constraint of ADR-0002, determine whether a **client-only** app can sync the per-record, immutable-JSON vault (ADR-0003) with **per-record last-writer-wins** (ADR-0004) directly to the user's own cloud storage — Google Drive, Dropbox, Microsoft OneDrive/Graph, kDrive, WebDAV, or S3-compatible object stores.
**Method:** Primary sources only — each provider's own API/OAuth/quota documentation, official SDK repositories, RFCs, and the projects' own docs (Joplin, Obsidian, Logseq, Standard Notes, Syncthing). Every claim carries a URL. Claims I could not confirm from a primary source are explicitly marked **[unverified]** or **[absence of documentation]**.

> **Headline:** A client-only, no-backend sync engine is technically possible, and the deciding primitives are far more mundane than "which cloud": **a conditional write (compare-and-swap) to detect a lost update, and a change cursor to avoid re-listing the world.** On those primitives the ranking is: **Dropbox** and **S3-compatible** and **WebDAV** are the honest fits (CAS via `rev` / `If-Match` / `If-None-Match`); **OneDrive/Graph** is capable but its SDK and Entra registration are heavy for Android; **Google Drive** is the weakest fit for a no-backend open-source app because it has **no documented conditional-write header**, its Android flow **cannot mint a refresh token without a server**, and its data is **bound to the OAuth client ID**, which structurally breaks forks. The **folder model is still the right MVP call**; the smallest real improvement is not four cloud APIs but **a sync seam plus one WebDAV driver** (and Dropbox as the second).

> **Stack note:** The repository's product brief still describes a React/Vite/Capacitor stack, while this brief frames the product as Kotlin Multiplatform. The provider analysis is stack-independent; the SDK column is written for **JVM/Kotlin**, which is the Android-first assumption.

---

## 0. What the engine actually needs (the yardstick)

ADR-0003 stores user data as one immutable JSON file per record (`vocabulary/<id>.json`, `progress/<bookId>.json`) plus a `manifest.json`; ADR-0004 resolves conflicts per record by `updatedAt` with `deviceId` as tie-break. To sync that against a dumb blob store, only four primitives matter:

| # | Primitive | Why it matters here |
|---|-----------|---------------------|
| 1 | **Enumerate** a collection with a per-item revision (rev / eTag / hash / `updatedAt`) | Initial full sync, and diff-based sync where no cursor exists |
| 2 | **Conditional write** — create-if-absent and update-if-revision-matches | Turns "read → decide → write" from a race into an atomic check; without it, two devices can both think they won |
| 3 | **Change cursor** (delta) | Cheap incremental sync; without it every sync re-lists every record |
| 4 | **Token model + app registration** that a fork can reproduce | ADR-0002 forbids a project-operated server, so auth must be client-only and the *maintainer must not hold a secret that forks cannot* |

A provider that lacks (2) forces "optimistic write + re-read + repair", which is exactly a per-record LWW overwrite (already accepted in ADR-0004, but silent). A provider that lacks (3) makes sync O(number of records) per run — tolerable for a small vault, annoying at scale.

Joplin is the existence proof that (1)+(2)-lite across heterogeneous providers is shippable in a real app: its synchroniser talks to providers through a **filesystem-like driver interface** ("read, write, delete and list items") ([Joplin sync overview](https://joplinapp.org/help/apps/sync/)). It supports "Joplin Cloud, Nextcloud, S3, WebDAV, Dropbox, OneDrive or the local filesystem" on the same engine ([Joplin sync](https://joplinapp.org/help/apps/sync/)).

---

## 1. Google Drive

### 1.1 Which scope for syncing Lekto's own files?

Google classifies Drive scopes into non-sensitive, sensitive and restricted ([Choose Drive API scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)):

| Scope | Class | Meaning |
|-------|-------|---------|
| `drive.file` | **non-sensitive** | "Create new Drive files, or modify existing files, that you open with an app or that the user shares with an app while using the Google Picker API or the app's file picker." ([api-specific-auth](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)); scope list wording: "See, edit, create, and delete only the specific Google Drive files you use with this app" ([OAuth scopes](https://developers.google.com/identity/protocols/oauth2/scopes)) |
| `drive.appdata` / `drive.appfolder` | **non-sensitive** | "View and manage the app's own configuration data in your Google Drive" — a hidden `appDataFolder` ([api-specific-auth](https://developers.google.com/workspace/drive/api/guides/api-specific-auth); [Store application-specific data](https://developers.google.com/workspace/drive/api/guides/appdata)) |
| `drive`, `drive.readonly`, `drive.metadata*`, `drive.activity*` | **restricted** | Full/near-full access; require restricted-scope verification and, if data is stored or transmitted on servers, an **annual security assessment** ([api-specific-auth](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)) |

**`drive.file` is the right scope.** It is non-sensitive, works with the REST resources, and gives the streamlined non-sensitive verification path ([api-specific-auth](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)). It lets the app see **only the files it created/opened**, which is exactly the vault — and unlike `appdata` the files can live in the user's My Drive and be user-visible/repairable.

**`appdata` is a poor vault home.** The app data folder is a hidden per-app folder whose contents "are hidden from the user and from other Google Drive apps", cannot be shared, moved, or trashed, and is **deleted when the user uninstalls the app from their My Drive** ([appdata](https://developers.google.com/workspace/drive/api/guides/appdata)). For a "own your data, repairable by hand" vault (ADR-0003), a hidden, non-trashable, auto-deletable folder is the opposite of the promise.

### 1.2 Does the changes feed work under `drive.file`?

**Yes.** `changes.list` lists `drive.appdata` and `drive.file` among its required scopes, and `spaces` accepts `drive` and `appDataFolder` ([changes.list reference](https://developers.google.com/workspace/drive/api/reference/rest/v3/changes/list)). `changes.getStartPageToken` + `changes.list` give a page-token cursor ([Retrieve changes](https://developers.google.com/workspace/drive/api/guides/manage-changes)). `includeRemoved` controls deletion entries; `restrictToMyDrive` "omits changes to files such as those in the Application Data folder" ([changes.list reference](https://developers.google.com/workspace/drive/api/reference/rest/v3/changes/list)).

**Caveat [partly unverified]:** the reference proves the *scope is permitted*, but not the per-file visibility rule. Because `drive.file` is defined as access to files the app created/opened, the feed is in practice bounded to app-accessible files; third-party reports agree ([Google issue tracker 330555392](https://issuetracker.google.com/issues/330555392), [Stack Overflow](https://stackoverflow.com/questions/69927416/google-drive-api-edit-shared-files-using-a-less-scary-scope)). Treat "feed contains only app files under `drive.file`" as highly likely but **inferred**, not documented.

`changes.watch` (push) requires a reachable HTTPS callback ([Retrieve changes → Receive notifications](https://developers.google.com/workspace/drive/api/guides/manage-changes)), i.e. a server. Under ADR-0002 that is unusable, so Drive sync is **poll-only**.

### 1.3 Authorization on Android

Android uses the Google Identity **`AuthorizationClient`** (`com.google.android.gms:play-services-auth`) with `Identity.getAuthorizationClient(activity).authorize(...)`, requesting `DriveScopes.DRIVE_FILE` ([Authorize access to Google user data](https://developer.android.com/identity/authorization)). Key consequences for a no-backend app:

- Later `authorize()` calls return an access token "without any user interaction" as long as the grant is not removed — so **foreground sync works without a stored refresh token** ([Authorize access](https://developer.android.com/identity/authorization)).
- But **offline/refresh access requires a server**: `requestOfflineAccess(serverClientId)` returns a `serverAuthCode` that must be exchanged on a backend, and the docs state "it is **strongly discouraged to store refresh tokens on the device**" ([Authorize access](https://developer.android.com/identity/authorization)).
- Android cannot use the loopback redirect ("DEPRECATED for **Android**" / "no longer supported due to the risk of app impersonation" for custom schemes) ([OAuth for iOS & Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app)).

So on Android the app can sync **only while it is in the foreground and Play Services returns a token**, and cannot do unattended background sync. On de-Googled devices `AuthorizationClient` is unavailable. This is the single biggest Google-specific friction.

### 1.4 Authorization on desktop/JVM

Desktop uses the installed-app flow: **loopback IP redirect** (`http://127.0.0.1:port`) on macOS/Linux/Windows with **PKCE (S256)**, `client_secret` **optional**, and refresh tokens issued to installed apps ([OAuth for iOS & Desktop Apps](https://developers.google.com/identity/protocols/oauth2/native-app)). Manual copy/paste (OOB) is deprecated; custom URI schemes are unsupported ([same](https://developers.google.com/identity/protocols/oauth2/native-app)). Google's own snippet in the changes guide uses `DriveScopes.DRIVE_FILE` ([Retrieve changes](https://developers.google.com/workspace/drive/api/guides/manage-changes)). So **desktop Drive is a clean PKCE/loopback story** — the Android asymmetry is the problem.

### 1.5 Shipping a Drive OAuth client as open source

- Every platform needs its own OAuth client, and the Android client is bound to **package name + SHA-1** — for Play-distributed apps the SHA-1 must come from Play App Signing ([Authorize access](https://developer.android.com/identity/authorization)).
- The OAuth policies are blunt: "You must **never commit client credentials** into publicly available code repositories", and "Treat your OAuth client credentials with extreme care ... especially your client secret, just as you would a password" ([OAuth 2.0 Policies](https://developers.google.com/identity/protocols/oauth2/policies)).
- `drive.file`/`appdata` are non-sensitive, so **verification is not mandatory** — but if you want your **app name and logo** on the consent screen you must pass **brand verification**, which requires a homepage on a **verified domain you own**, a privacy policy, and domain ownership verification ([OAuth App Verification](https://support.google.com/cloud/answer/13463073); [Verification requirements](https://support.google.com/cloud/answer/13464321)).
- Restricted scopes would additionally require a demo video, justification, and an **annual security assessment** ([Verification requirements](https://support.google.com/cloud/answer/13464321)) — disqualifying for a hobby client-only app.
- While an external app is in **Testing** status it is subject to a **100-user cap**, and the "unverified app" screen appears for sensitive/restricted scopes ([Changes to approved app](https://support.google.com/cloud/answer/13464018); [OAuth App Verification](https://support.google.com/cloud/answer/13463073)).

**Fork problem (structural):** because data access is keyed to the OAuth client ID, a fork with its **own** client ID cannot see files created under the maintainer's client ID — and the maintainer must not commit theirs. Fork users must create their own Google Cloud project and re-authorize. `appdata` makes this worse: it is explicitly per-app data ([appdata](https://developers.google.com/workspace/drive/api/guides/appdata)).

### 1.6 Quota, rate limits, resumable uploads

- Per project: **1,000,000 quota units/minute**; per user per project: **325,000/minute**; per day per project: **1 TB egress**; daily billing threshold **400,000,000 units/day** ([Usage limits](https://developers.google.com/workspace/drive/api/guides/limits)).
- Per-method units: read 5, list **100**, download 200, edit 50 ([Usage limits](https://developers.google.com/workspace/drive/api/guides/limits)) — a naive per-record `files.get` loop burns quota; listing is cheaper than many gets.
- Uploads are capped at **750 GB/day** (My Drive + shared drives) and **5 TB** max file ([Usage limits](https://developers.google.com/workspace/drive/api/guides/limits)).
- `uploadType=resumable` is supported; the **resumable session URI expires after one week** ([Upload file data](https://developers.google.com/workspace/drive/api/guides/manage-uploads)).
- Back-off on `403: User rate limit exceeded` / `429` ([Usage limits](https://developers.google.com/workspace/drive/api/guides/limits)).

### 1.7 Conditional writes — the gap

I found **no `If-Match`/ETag conditional-write header** in the Drive v3 `files.update` reference, and no precondition/412 semantics documented for it ([files.update](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/update)). Drive exposes `version` (monotonically increasing) and `headRevisionId` on `files` ([files resource](https://developers.google.com/workspace/drive/api/reference/rest/v3/files)), which allow **detection** but not an atomic compare-and-swap. **[Absence of documentation]** — I did not find a documented CAS primitive. Consequence: LWW on Drive has a genuine lost-update race window; you would write, then verify `version`, then repair.

**Drive verdict:** workable scope (`drive.file`), usable cursor (`changes`), but weak on conditional writes, crippled on Android offline auth, and structurally hostile to forks. **Effort: high for the value.**

---

## 2. Dropbox

### 2.1 PKCE without a client secret — explicitly for open source

Dropbox's OAuth guide defines the PKCE flow, validates `S256` or `plain` (S256 recommended), and lists the app types that need it: "Desktop and mobile apps without a server ... Hosted software deployed on untrusted or client infrastructure ... **Open source applications**" ([Dropbox OAuth guide](https://www.dropbox.com/developers/reference/oauthguide)). The guide's own flow matrix recommends:

> "A client-side **Desktop app or mobile app that requires background access** → Use the OAuth code flow with **PKCE, with refresh tokens**." ([Dropbox OAuth guide](https://www.dropbox.com/developers/reference/oauthguide))

Refresh tokens require `token_access_type=offline` on the authorization URL, then `grant_type=refresh_token` ([Dropbox OAuth guide](https://www.dropbox.com/developers/reference/oauthguide)). The only oddity is the guide's phrasing that you pass `code_verifier` "instead of the `client_id`" at the token endpoint ([Dropbox OAuth guide](https://www.dropbox.com/developers/reference/oauthguide)); the companion post shows `client_id` on the authorize URL and describes PKCE as "substituting the static client secret" ([PKCE: What and Why?](https://dropbox.tech/developers/pkce--what-and-why-)), and Dropbox's own docs are the authority ([docs.dropboxapi.com](https://docs.dropboxapi.com/dropbox-api/api-reference)). **[Flagged]** — send `client_id` + `code_verifier`, omit `client_secret`; that is standard RFC 7636 and what the SDK does.

The practical result: a fork needs its **own app key** (public) and redirect URI, but **no secret to leak**, so the "maintainer's secret" problem disappears.

### 2.2 Java/Kotlin SDK on Android

The official **Dropbox Java SDK** ships an Android artifact `com.dropbox.core:dropbox-android-sdk`, supports **Android 8+ (API 26+)**, and its Android auth code is written in **Kotlin**, exposing `Auth.startOAuth2PKCE(...)`; it uses the installed Dropbox app when present, otherwise the browser flow ([dropbox-sdk-java README](https://github.com/dropbox/dropbox-sdk-java)). Java 21+ is required from v8.0.0; the 7.x line remains for Java 8–20 ([README](https://github.com/dropbox/dropbox-sdk-java)). There is an official Kotlin Android example ([README](https://github.com/dropbox/dropbox-sdk-java)). Refresh-token handling is built into the SDK's helper methods ([Dropbox OAuth guide](https://www.dropbox.com/developers/reference/oauthguide)).

### 2.3 Change detection, revisions, conflict writes

- **Cursor delta:** `files/list_folder` returns entries + a `cursor`; `files/list_folder/continue` returns everything changed since that cursor (v2 replaced `/delta` with `list_folder?recursive=true`) ([v1→v2 migration guide](https://www.dropbox.com/developers/reference/migration-guide); [DBX file access guide](https://developers.dropbox.com/dbx-file-access-guide); [docs.dropboxapi.com](https://docs.dropboxapi.com/dropbox-api/api-reference)). `files/list_folder/longpoll` exists for change notification ([docs.dropboxapi.com](https://docs.dropboxapi.com/dropbox-api/api-reference)).
- **Revisions:** each file carries a `rev` "used to detect changes and avoid conflicts" ([files/upload reference](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload)); `files/list_revisions` enumerates revisions.
- **Conditional write (CAS):** `files.WriteMode.update` "Overwrite if the given `rev` matches the existing file's `rev`", with `strict_conflict` forcing a conflict even when the file was deleted or contents are identical ([files/upload reference](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload)). This is **exactly the primitive** the LWW engine needs. (The same reference documents the other write modes `add` and `overwrite`.)

### 2.4 Limits and app lifecycle

- Max file size **2 TB (2,199,019,061,248 bytes)** ([Dropbox upload limitations](https://help.dropbox.com/sync/upload-limitations)).
- Rate limiting returns **429 or 503**, sometimes with `Retry-After`; retry with `Retry-After` or exponential back-off ([Dropbox community, echoed by API docs](https://community.dropbox.com/en/discussion/183714/dropbox-api-rate-limits)). "Data transport" monthly caps apply only to certain **Business** teams, not personal accounts ([Data transport limit](https://www.dropbox.com/developers/reference/data-transport-limit)).
- Scoped apps: while in **development** an app can link **50 users**, then you have **two weeks** to obtain **production approval** ([DBX developer guide](https://docs.dropboxapi.com/dropbox-api/docs/developer-resources/developer-guide); [Dropbox community](https://community.dropbox.com/en/discussion/857591/do-i-need-to-request-production-status)).
- Joplin's Dropbox driver is the reference implementation: it stores everything under `/Apps/Joplin` and "does not have access to anything outside this directory" ([Joplin Dropbox](https://joplinapp.org/help/apps/sync/dropbox)).

**Dropbox verdict:** the best fit for a no-backend OSS app — PKCE with no secret, documented refresh tokens, a maintained Kotlin Android SDK, a cursor delta, and a real conditional write via `rev`. **Effort: moderate.**

---

## 3. Microsoft OneDrive / Microsoft Graph

### 3.1 Auth: PKCE, public client, no secret

The authorization-code flow "paired with Proof Key for Code Exchange (PKCE)" is the documented choice for **desktop and mobile apps**, with `code_challenge`/`code_challenge_method=S256` and no client secret for public clients ([Microsoft identity platform auth code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)). Native/mobile redirect uses `https://login.microsoftonline.com/common/oauth2/nativeclient` (or loopback) ([same](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)). Each fork must register its own **Entra app** ([Register your app](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app)); publisher verification is optional for personal-account scenarios and becomes relevant for multi-tenant/admin-consent scopes.

### 3.2 App Folder scope (the `appdata` analogue)

OneDrive has a **special App Folder**: with the **`Files.ReadWrite.AppFolder`** delegated scope, "your app gets read and write access to this folder", addressed as `/drive/special/approot` ([What is an App Folder](https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/special-folders-appfolder?view=odsp-graph-online)). It is per-app and is the closest Microsoft analogue to Google's `appdata`. (Joplin uses OneDrive's `/Apps/Joplin` folder instead; [Joplin OneDrive](https://joplinapp.org/help/apps/sync/onedrive).)

### 3.3 Delta + conditional writes

- **Delta:** `driveItem: delta` returns `@odata.deltaLink` / `@odata.nextLink` state tokens that are **opaque** and round-trippable; deleted items come back as deleted entries, so the client can reconcile removals without a full listing ([delta query overview](https://learn.microsoft.com/en-us/graph/delta-query-overview); [driveItem: delta](https://learn.microsoft.com/en-us/graph/api/driveitem-delta?view=graph-rest-1.0)).
- **CAS:** `if-match` on an update "if the eTag (or cTag) provided doesn't match the current eTag ... **412 Precondition Failed**" ([driveItem: update](https://learn.microsoft.com/en-us/graph/api/driveitem-update?view=graph-rest-1.0)). This is a real compare-and-swap.
- **Throttling:** **429 + `Retry-After`**, per-app/per-user; avoid immediate retries ([Graph throttling](https://learn.microsoft.com/en-us/graph/throttling)).

### 3.4 JVM/Kotlin SDK and Android weight

The official **Microsoft Graph Java SDK** (`com.microsoft.graph:microsoft-graph`) is JVM-oriented and its README explicitly advises **enabling ProGuard and multidex on Android** to avoid long builds/64K method limits ([msgraph-sdk-java README](https://github.com/microsoftgraph/msgraph-sdk-java)). It is usable from Kotlin but is not a Kotlin Multiplatform library and brings a large dependency surface; the SDK leans on `azure-identity` for token acquisition ([README](https://github.com/microsoftgraph/msgraph-sdk-java)).

**OneDrive/Graph verdict:** technically the most complete primitive set after Dropbox (delta + AppFolder + true `If-Match` CAS), but the Android SDK weight and Entra app-registration/consent friction raise the cost. **Effort: moderate–high.**

---

## 4. kDrive (Infomaniak)

### 4.1 Is there a documented API?

Yes — a REST/JSON API with **OAuth 2** authentication, published as an OpenAPI document at `developer.infomaniak.com/openapi.json` ([Infomaniak API reference](https://developer.infomaniak.com/docs/api); [Discover the API](https://www.infomaniak.com/en/support/faq/2581/discover-the-infomaniak-api)). From the OpenAPI spec I confirmed kDrive file endpoints such as `GET /3/drive/{drive_id}/files/{file_id}/files` (list directory), `POST /3/drive/{drive_id}/upload` and chunked upload sessions (`/upload/session/start`, `/upload/session/{token}/chunk`, `/upload/session/{token}/finish`), rename/move/delete-to-trash, and per-file `hash` ([developer.infomaniak.com/openapi.json](https://developer.infomaniak.com/openapi.json)).

**Maturity signals are mixed:** the API is described as **in beta** by third parties citing Infomaniak's docs ([rclone #8456](https://github.com/rclone/rclone/issues/8456)), and the hard limit is **60 requests/minute, which "cannot be increased"** ([Discover the API](https://www.infomaniak.com/en/support/faq/2581/discover-the-infomaniak-api)). Storage limits: **300,000 files/kDrive**, **1,000 subfolders**, **50,000 files/folder**, **1,000 GB/file via the API**, plus daily bandwidth limits ([Manage kDrive storage](https://www.infomaniak.com/en/support/faq/2387/manage-kdrive-storage)).

### 4.2 Change detection and clients

**[Absence of documentation]** I found no cursor/delta change feed in the OpenAPI paths for a kDrive subtree; the closest are activity/recents endpoints, and directory listing is cursor-paginated. That implies **full re-listing** per sync. Kotlin tooling exists as a **reference implementation, not an SDK**: Infomaniak's own Android app is open source (GPLv3) and built in **Kotlin with Ktor**, and includes a sync layer ([Infomaniak/android-kDrive](https://github.com/Infomaniak/android-kDrive)); the desktop client is also open source ([Infomaniak/desktop-kDrive](https://github.com/Infomaniak/desktop-kDrive)). kDrive also offers **WebDAV**, and Joplin lists "Infomaniak kDrive" among WebDAV-compatible services ([Joplin WebDAV](https://joplinapp.org/help/apps/sync/webdav/)); some plans lack WebDAV access ([rclone forum](https://forum.rclone.org/t/help-needed-infomaniak-kdrive-backend/52142)).

**kDrive verdict:** a credible European option, but **hard 60 req/min**, a **beta** native API, **no delta feed**, and no first-party Kotlin SDK make it a poor *first* target. If kDrive users are a goal, cover them via **WebDAV**, not the native API.

---

## 5. WebDAV (Nextcloud, ownCloud, Synology, kDrive, …)

### 5.1 Auth: Basic with app passwords (and Login Flow v2)

Nextcloud's WebDAV base is `/remote.php/dav/files/{user}/...`; it accepts **HTTP Basic auth** or session cookies, and "you may need to create an **app password**" when the account uses external auth or **2FA** ([Nextcloud WebDAV basics](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html)). For third-party clients, Nextcloud's **Login Flow v2** mints a per-device **app password** so "a client never stores the password of the user" and 2FA still works ([Nextcloud Login Flow](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html)). This is **not OAuth**, but it means **no OAuth app registration and no maintainer secret** — ideal for forks. The same pattern applies across Nextcloud/ownCloud-derived servers; Joplin's WebDAV target documents the Nextcloud URL shape and username/password ([Joplin Nextcloud](https://joplinapp.org/help/apps/sync/nextcloud)).

### 5.2 Listing and conditional writes

- **Listing:** `PROPFIND` with `Depth: 1` returns children and per-resource properties including **`getcontentlength`, `getcontenttype`, `getlastmodified`, `getetag`** ([Nextcloud WebDAV basics](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html)).
- **CAS:** WebDAV's RFC 4918 adds conditional headers. `If-Match` matches the current entity tag; strong ETags are "required for authoring", and **weak ETags cannot be used in `If-Match`**; a failed precondition returns **412 Precondition Failed** ([RFC 4918 §§8.6, 10.4.4, 12.1](https://www.rfc-editor.org/rfc/rfc4918)). This is a real, provider-agnostic compare-and-swap — provided the server issues strong ETags.
- **Incremental cursor:** RFC 6578 defines the **`DAV:sync-collection` REPORT**: the server returns an **opaque `sync-token`** and, on later calls with that token, "the changes from the previous state to the current state" (additions, changes, removals). Depth is expressed with `sync-level` (0/1/infinite). Servers **may invalidate** tokens, forcing a full re-sync, and **`sync-token` values may be used in an `If` header** to condition a write on an unchanged collection ([RFC 6578](https://www.rfc-editor.org/rfc/rfc6578)). SabreDAV (the engine under Nextcloud/ownCloud) documents WebDAV Sync support ([sabre/dav WebDAV Sync](https://sabre.io/dav/sync/)), and Nextcloud has tracked sync-collection behaviour ([nextcloud/server #9339](https://github.com/nextcloud/server/issues/9339)). **Flag:** support is server-dependent; treat RFC 6578 as an optimisation with a PROPFIND fallback.

### 5.3 Clients

- **JVM:** Apache Jackrabbit WebDAV (`org.apache.jackrabbit:jackrabbit-webdav`) is a maintained JVM library; Joplin's own TypeScript WebDAV driver is proof the semantics work ([Joplin WebDAV](https://joplinapp.org/help/apps/sync/webdav/)).
- **Android/Kotlin:** OkHttp handles the HTTP verbs directly; a widely used Android DAV library is `dav4jvm`. **[unverified]** I could not fetch its repository/coordinates from this host (404), so verify before depending on it. Because WebDAV is just HTTP + XML, a small OkHttp + XML client is a reasonable fallback.

**Known-good servers** (Joplin's list): Apache/Nginx WebDAV modules, Nextcloud, ownCloud, Seafile, Synology, Fastmail, mailbox.org, InfiniCLOUD, Infomaniak kDrive, Zimbra ([Joplin WebDAV](https://joplinapp.org/help/apps/sync/webdav/)). **Box is out:** Box ended WebDAV support on **2019-10-25** ([Box EOL notice](https://support.box.com/hc/en-us/articles/360052806073-Announcing-end-of-life-for-Box-WebDAV-support)).

**WebDAV verdict:** the **best provider-agnostic primitive set** for a per-record vault: Basic/app-password auth (no app registration, no secret), PROPFIND listing with ETags, `If-Match` CAS, and optional RFC 6578 incremental sync. Weaknesses: XML ergonomics, heterogeneous server behaviour, and the need for a per-server URL/credential setup. **Effort: low–moderate.**

---

## 6. S3-compatible object storage

### 6.1 Suitability with user-supplied keys

S3-compatible storage is a natural per-record store: each record is an object keyed by `vault/vocabulary/<id>.json`. Auth is **SigV4 with a user's access key/secret** — no OAuth, no app registration, no maintainer secret, so forks are trivially unaffected. Joplin ships an S3 target (labelled "(Beta)") requiring bucket, endpoint URL, access/secret, region and path-style settings ([Joplin S3](https://joplinapp.org/help/apps/sync/s3/)). The cost is a per-record `LIST` to detect changes — there is **no change cursor** in the S3 API.

### 6.2 Conditional PUT / ETag semantics

AWS documents conditional writes via `If-None-Match` and `If-Match`:

- `If-None-Match: *` → **create-only**; fails if an object with that key exists.
- `If-Match: <etag>` → **update-only if the ETag matches**; otherwise the write fails.
- Failure returns **412 Precondition Failed**.
- Applies to `PutObject`, `CompleteMultipartUpload`, `CopyObject`.
- **"To use conditional writes, you must use AWS Signature Version 4 to sign the request."** ([Conditional writes](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html))

This is a genuine compare-and-swap and maps perfectly to per-record LWW: write only if your known ETag still matches; on 412, re-read and re-apply LWW.

### 6.3 Which providers support it

| Provider | Conditional PUT | Source |
|---|---|---|
| AWS S3 | ✅ `If-Match` / `If-None-Match` | [AWS](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html) |
| Cloudflare R2 | ✅ `PutObject`: `If-Match`, `If-None-Match` | [R2 S3 API compatibility](https://developers.cloudflare.com/r2/api/s3/api/) |
| MinIO | ✅ `If-Match`; `If-None-Match` semantics debated | [MinIO discussion #20318](https://github.com/minio/minio/discussions/20318) **[discussion, flag]** |
| Scaleway Object Storage | ✅ documented | [Scaleway conditional writes](https://www.scaleway.com/en/docs/object-storage/api-cli/using-conditional-writes/) |
| OVHcloud Object Storage | ✅ documented | [OVHcloud conditional writes](https://docs.ovhcloud.com/en/guides/storage-and-backup/object-storage/s3-conditional-writes) |
| Backblaze B2 | ⚠️ **not listed** in supported features; community reports `If-None-Match` unsupported | [B2 S3-compatible API](https://www.backblaze.com/docs/cloud-storage-s3-compatible-api) **[unverified]** |

Other provider caveats: conditional writes need TLS **or** SigV4, and an AWS support thread reports `If-Match` misbehaving with presigned non-SigV4 URLs ([re:Post](https://repost.aws/questions/QU0NMXJve9QMS-p7eQQcnBNg/s3-conditional-writes-putobject-presigned-url-with-if-match-etag-does-not-work-for-non-sigv4-requests)) — so **sign client-side with SigV4** rather than presigning.

### 6.4 Kotlin/Android SDK

The **AWS SDK for Kotlin** is GA and targets **JVM and Android (API level 24+)** ([AWS SDK for Kotlin](https://aws.amazon.com/sdk-for-kotlin/); [developer guide](https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/home.html)). Alternatives: MinIO's Java client, or a hand-rolled SigV4 signer over OkHttp (the algorithm is fully specified in [AWS SigV4 docs](https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html)). Note the older **AWS SDK for Android reached end-of-support 2026-08-01** ([re:Post](https://repost.aws/questions/QUvAlFSCdjTZW9n-eEofxpTg/aws-sdk-for-android-end-of-support-definition)).

**S3 verdict:** excellent primitives (CAS, no registration, mature SDK) but **no delta**, **BYO keys** (friction for novices), and per-record listing costs. Best reserved for the "data sovereign / developer" persona. **Effort: moderate.**

---

## 7. The OAuth-for-open-source problem

This is the crux. A no-backend open-source app cannot rely on a secret held by the maintainer; each provider behaves differently.

| Provider | Secret needed? | What a fork must do | Maintainer leak risk | Verification burden |
|---|---|---|---|---|
| **Google Drive** | No client secret for installed/Android clients, **but** per-platform clients and Android-SHA-1 binding | Create its **own** GCP project + OAuth clients (Android package/SHA-1 + desktop); re-authorize. Data is **client-ID-bound**, so it cannot see the upstream app's files | Policies forbid committing credentials; still, client-ID lock-in is worse than a leak | Non-sensitive (`drive.file`): verification optional; **brand verification** for name/logo; **Testing 100-user cap**; restricted → **annual security assessment** |
| **Dropbox** | **No** — PKCE is explicitly recommended for open source | Create its **own app key** + redirect URI (no secret) | Low | Development 50-user cap → **production review** |
| **OneDrive/Graph** | No secret for **public client + PKCE** | Register its own **Entra app** | Low | Publisher verification optional (more for multi-tenant/admin-consent) |
| **WebDAV** | **No** — Basic/app password | Nothing; user points at their server | None | None |
| **S3-compatible** | **No** — access key/secret | Nothing; user supplies keys | None | None |

Sources: Google ([OAuth policies](https://developers.google.com/identity/protocols/oauth2/policies), [Android authorization](https://developer.android.com/identity/authorization), [verification](https://support.google.com/cloud/answer/13463073)); Dropbox ([OAuth guide](https://www.dropbox.com/developers/reference/oauthguide), [developer guide](https://docs.dropboxapi.com/dropbox-api/docs/developer-resources/developer-guide)); Microsoft ([auth code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)); WebDAV/S3 as above.

**Play Store obligations.** Any Android app using Google Sign-In must meet Google's API Services User Data Policy, and the Android OAuth client must use the Play App Signing SHA-1 (not the upload key) ([Authorize access](https://developer.android.com/identity/authorization)). Independently, Play requires a **Data safety** disclosure and a privacy policy; brand verification additionally requires a privacy policy and terms on a domain you own ([Verification requirements](https://support.google.com/cloud/answer/13464321)).

**Bottom line:** Dropbox, OneDrive and WebDAV/S3 are fork-friendly; **Google is the outlier** — not because of a leaked secret, but because per-app client IDs and SHA-1 binding make a fork's vault access non-portable.

---

## 8. Sync engine design for per-record JSON + LWW

### 8.1 Remote layout

Keep the vault shape, adding a tombstone area:

```
<remote-root>/
  vocabulary/<id>.json            # immutable records (ADR-0003)
  progress/<bookId>.json
  tombstones/<id>.json            # { id, deletedAt, deviceId } — LWW-able deletions
  manifest.json                   # optional, derived index (see 8.4)
```

### 8.2 Minimal algorithm

1. **Initial full sync** — list the remote collection (primitive 1), compare each id's `updatedAt`/`deviceId` against local, then download remote-newer and upload local-newer records.
2. **Incremental sync** — if the provider has a cursor (change detection), pull deltas; otherwise list and diff by id + revision. Reconcile adds/updates/deletes.
3. **Deletes via tombstones** — never rely on a file's absence as a delete (offline devices, partial listings, and "online-only" file offloads cause false deletes; see §9). Write `tombstones/<id>.json` with `deletedAt`; LWW compares tombstone vs record by `updatedAt`, `deviceId` tie-break. Retain tombstones at least as long as the maximum plausible offline window; they are tiny.
4. **Conflict / lost-update handling** — per record: fetch remote revision; if it differs from your base revision, apply LWW. If local wins, **conditional write** (CAS) against the remote revision; if the CAS fails (412 / rev mismatch), re-read and re-apply LWW. If the provider has no CAS (Drive), fall back to write-and-verify (`version`/`headRevisionId`) and repair.
5. **Write batching (optional)** — many tiny objects hammer rate limits (e.g. Google list = 100 units, kDrive = 60 req/min). The ADR-0004 optional `log/<device>.jsonl` can anchor an append-only per-device channel so sync uploads one log object rather than hundreds of records, with compaction.
6. **Sync lock** — without a server, a lock is just a conditional create of `<root>/lock.json` (`If-None-Match: *`) with a heartbeat and timeout. Joplin specifies exactly this class of `SYNC`/`EXCLUSIVE` lock with refresh/timeout semantics ([Joplin sync locks](https://joplinapp.org/help/dev/spec/sync_lock)).

### 8.3 Provider fit against the four primitives

| Provider | Enumerate + revision | Conditional write (CAS) | Change cursor | Token model fork-safe |
|---|---|---|---|---|
| **Dropbox** | ✅ entries + `rev` | ✅ `WriteMode.update(rev)` | ✅ `list_folder` cursor | ✅ PKCE, own app key |
| **OneDrive/Graph** | ✅ eTag/cTag | ✅ `If-Match` → 412 | ✅ `driveItem: delta` | ✅ public client + PKCE |
| **WebDAV** | ✅ PROPFIND + `getetag` | ✅ `If-Match` (strong ETags) | ⚠️ RFC 6578 `sync-collection` (server-dependent) | ✅ Basic/app password |
| **S3-compatible** | ✅ list + ETag/LastModified | ✅ `If-Match`/`If-None-Match` (SigV4) | ❌ none | ✅ user keys |
| **Google Drive** | ✅ `version`/`headRevisionId` | ❌ no documented CAS | ✅ `changes` feed | ⚠️ per-client-ID; Android needs a server for refresh |
| **kDrive** | ✅ files list + cursor | ❓ **[unverified]** | ❌ no documented delta | ⚠️ OAuth2, beta API, 60 req/min |

### 8.4 On the `manifest.json`

A single remote `manifest.json` is tempting as the id→revision index, but it becomes a **hot spot**: every device writes it, and providers without CAS (Drive) cannot update it safely. Prefer: **per-record files are the truth**; derive any index locally; if a remote manifest is kept, write it only as an optimisation and tolerate staleness, or replace it with an append-only per-device log (point 5). Joplin's model is instructive: it keeps **per-item sync state locally** (`sync_items`, scoped per sync target) and a small shared `SyncTargetInfo` file, rather than a central authoritative manifest ([Joplin sync spec](https://joplinapp.org/help/dev/spec/sync/)).

---

## 9. How comparable apps handle this

### Joplin — the closest analogue
- Abstract **filesystem-like drivers** (read/write/delete/list); targets include Nextcloud, S3, WebDAV, Dropbox, OneDrive, local filesystem ([Joplin sync](https://joplinapp.org/help/apps/sync/)).
- Uploads "within a few seconds"; **polls every few minutes**; conflicts produce a **Conflict notebook** copy ([Joplin conflict](https://joplinapp.org/help/apps/conflict/)).
- A **delta-sync API** (cursor + create/update/delete events, event compression) exists **only for Joplin Server**, not for generic targets ([Joplin Server delta sync](https://joplinapp.org/help/dev/spec/server_delta_sync)) — confirming the generic providers lack a cursor.
- **Critical caveat:** "After completing the setup, open the Nextcloud desktop client and **disable syncing for the Joplin data directory**, since synchronisation should be handled exclusively by Joplin" ([Joplin Nextcloud](https://joplinapp.org/help/apps/sync/nextcloud)). i.e. **do not double-sync** an app-managed directory.

### Obsidian
- First-party **Obsidian Sync** is a server service (an off-site remote vault), not the user's own cloud ([Introduction to Obsidian Sync](https://help.obsidian.md/sync)).
- For DIY folder sync, Obsidian documents the Android/desktop reality: **iCloud Drive on Windows "may lead to file duplication or corruption"**; **OneDrive is "limited functionality on Android"** and not officially supported on iOS; **Google Drive is "not officially supported"**; **Syncthing's official Android app "is no longer maintained"** (use Syncthing-Fork) ([Sync your notes across devices](https://help.obsidian.md/sync-notes)).
- **Killer caveat for any folder/cloud model:** cloud "Files On-Demand / online-only" offloading makes files locally absent, which Obsidian Sync "will interpret ... as deleted, leading to their removal from your remote vault"; it also warns against mixing sync services ([FAQ](https://help.obsidian.md/sync/faq)).
- Obsidian Sync itself only syncs while the app runs ("**No**, files are only synced when Obsidian is running"), with per-file limits of 5 MB (Standard) / 200 MB (Plus) ([FAQ](https://help.obsidian.md/sync/faq)).

### Logseq
- Plain-Markdown files; built-in **Logseq Sync** is a **paid beta** using the vendor's own servers (encrypted, stored on AWS) and explicitly says: "**Do not** use this feature with any other third party sync service like iCloud, Syncthing, or Dropbox" ([Logseq Sync](https://github.com/logseq/docs/blob/master/pages/Logseq%20Sync.md)).
- DIY file sync has a hard operational rule: "you can only have one device use the data at a time ... make sure the data was synced from your mobile device [before] open[ing] the Logseq app on your desktop", and you may need to re-index ([How to sync your Logseq graph](https://github.com/logseq/docs/blob/master/pages/How%20to%20sync%20your%20Logseq%20graph%20across%20devices.md)). Community reports of iCloud corruption and conflict files are common but are not primary ([discuss.logseq.com](https://discuss.logseq.com/t/lost-2-hours-of-work-due-to-icloud-sync-issue-is-it-common/7451)) **[community]**.

### Standard Notes
- Not a BYO-cloud model at all: "The server is responsible for authentication and syncing", the client encrypts first, and the server is **self-hostable** ([Can I self-host Standard Notes?](https://standardnotes.com/help/47/can-i-self-host-standard-notes)). This is the "run your own sync server" branch — excluded by ADR-0002.

### Syncthing
- The official Android app was **retired** by its maintainer in Oct 2024 and the repo is archived; the stated cause is Android's storage/background restrictions ([Syncthing forum](https://forum.syncthing.net/t/discontinuing-syncthing-android/23002); [syncthing-android (read-only)](https://github.com/syncthing/syncthing-android)). The community continuation is **Syncthing-Fork** ([Obsidian](https://help.obsidian.md/sync-notes); [Catfriend1/syncthing-android](https://github.com/Catfriend1/syncthing-android)). Neither is something Lekto should build a product promise on.

**Cross-cutting lessons:** (a) the folder model is genuinely fragile on Android and under "online-only" cloud folders; (b) every serious app ends up with a **driver abstraction over a small set of primitives** (Joplin) or a **first-party server** (Obsidian, Logseq, Standard Notes); (c) mixing a folder-sync tool with an app-managed sync engine on the same directory causes corruption — which is precisely why ADR-0001's "progressive enhancement" framing is sound.

---

## 10. Comparison table

| Provider | Auth model | PKCE / no secret? | OSS-fork friendly? | Change detection | Conditional write | Kotlin/Android SDK | Maturity | Effort |
|---|---|---|---|---|---|---|---|---|
| **Dropbox** | OAuth 2 code + PKCE; refresh tokens | ✅ **PKCE, no secret; open source explicitly named** | ✅ own app key, no secret | ✅ `list_folder` cursor + `longpoll` | ✅ `WriteMode.update(rev)` | ✅ official Android SDK (Kotlin auth, API 26+) | High | **Moderate** |
| **OneDrive / Graph** | OAuth 2 code + PKCE, public client | ✅ no secret | ✅ own Entra app | ✅ `driveItem: delta` (`@odata.deltaLink`) | ✅ `If-Match` → 412 | ⚠️ Java SDK (ProGuard/multidex), not KMP | High | **Moderate–High** |
| **WebDAV** | HTTP Basic (app password); Nextcloud Login Flow v2 | ✅ N/A (no OAuth app) | ✅ nothing to register | ⚠️ RFC 6578 `sync-collection` (server-dependent) | ✅ `If-Match`, strong ETags → 412 | ⚠️ OkHttp + XML; `dav4jvm` **[unverified]** | High (protocol), variable servers | **Low–Moderate** |
| **S3-compatible** | SigV4 (user keys) | ✅ N/A | ✅ nothing to register | ❌ no cursor | ✅ `If-Match`/`If-None-Match` (SigV4) | ✅ AWS SDK for Kotlin (JVM + Android 24+) | High | **Moderate** |
| **Google Drive** | Android `AuthorizationClient`; desktop loopback + PKCE | ⚠️ no secret, but Android refresh needs a server | ❌ per-app client ID + SHA-1; data client-ID-bound | ✅ `changes` feed (poll only) | ❌ no documented CAS | ✅ play-services-auth (Android), Drive Java client (JVM) | High | **High** |
| **kDrive** | OAuth 2 Bearer | ✅ no secret (OAuth client) | ⚠️ own app; beta API | ❌ no documented delta | ❓ **[unverified]** | ⚠️ no SDK; open-source Kotlin app as reference | **Beta** | **High** |

---

## 11. Recommendation

### The smallest honest sync design

**Do not build four cloud integrations.** Build the **seam plus one driver**, in this order of value-per-effort:

1. **WebDAV first.** It is the only option that needs **no app registration, no maintainer secret, and no fork story** — Basic/app-password auth, PROPFIND listing with ETags, `If-Match` compare-and-swap ([RFC 4918](https://www.rfc-editor.org/rfc/rfc4918)), and optional RFC 6578 incremental sync ([RFC 6578](https://www.rfc-editor.org/rfc/rfc6578)). It covers Nextcloud/ownCloud/Synology/kDrive — exactly the "data sovereign" persona the brief already targets. Add OkHttp + a thin XML layer; keep `dav4jvm` as an option only after verifying it.
2. **Dropbox second.** Best consumer UX and the most OSS-friendly OAuth: PKCE with no secret, documented refresh tokens, an official Kotlin Android SDK, a cursor delta, and `WriteMode.update(rev)` CAS ([OAuth guide](https://www.dropbox.com/developers/reference/oauthguide); [SDK README](https://github.com/dropbox/dropbox-sdk-java); [files/upload](https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload)).
3. **OneDrive/Graph third** (delta + `If-Match` + AppFolder are all there; pay the SDK/Entra cost only if demand exists).
4. **S3-compatible** for the developer persona (CAS, zero registration; no cursor).
5. **Google Drive last** — the Android refresh-token dependency on a backend, the client-ID fork lock-in, and the missing CAS make it the least honest fit despite being the most requested.

Structure the engine per §8: per-record files as truth, tombstones for deletes, per-record LWW, conditional writes where available, a driver-level capability query (does this target have a cursor? does it have CAS?), and a capability-degraded path (write-and-verify + repair) only where necessary.

### The strongest counter-argument

**Every driver is permanent surface area, and the value is unproven.** Each target drags in OAuth app registration/review, token refresh, provider API drift, per-provider conflict semantics, rate-limit handling, and Play-policy/Data-safety duties — for a feature the product brief currently lists as **out of scope**. Meanwhile the folder model already delivers sync through tools users run anyway, and ADR-0001 already frames real folders as a **progressive enhancement**. A half-finished sync engine that silently loses a record is *worse* than a documented export/import plus "point the vault at a folder", because it trades a visible limitation for an invisible data-loss risk. And because ADR-0002 rules out a server, Android's Google Drive path is structurally second-class, so "built-in cloud sync" would ship as a **fragmented** feature (works for Dropbox/WebDAV users, degraded for Drive users) — the opposite of a clean promise.

### Is built-in cloud sync worth it for the MVP?

**No — the folder model remains the right MVP call.** The honest MVP improvement is not four cloud APIs but three smaller things:

1. Make the vault **trivially relocatable and exportable** everywhere (ADR-0001's progressive enhancement), with a clear "how to sync on Android" guide that names the real tools (SAF, Syncthing-Fork, FolderSync, a Nextcloud WebDAV folder).
2. Add the **sync seam** (`SyncTarget` capability interface) so a driver can be added later without touching the vault or storage layer.
3. Ship **at most one driver, WebDAV**, because it carries no OAuth/secret/verification burden and is the only one that maps cleanly onto the self-hoster persona.

Revisit ADR-0002/0003 only after the reading + vocabulary loop is proven and users are actually asking for built-in sync; then the order above (WebDAV → Dropbox → OneDrive/Graph → S3 → Drive) is the low-regret path. **Built-in Google Drive sync is the one to defer longest.**

---

## Sources

**Google Drive**
- Choose Drive API scopes — https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Store application-specific data (appDataFolder) — https://developers.google.com/workspace/drive/api/guides/appdata
- Retrieve changes / notifications — https://developers.google.com/workspace/drive/api/guides/manage-changes
- changes.list reference — https://developers.google.com/workspace/drive/api/reference/rest/v3/changes/list
- files resource (`version`, `headRevisionId`) — https://developers.google.com/workspace/drive/api/reference/rest/v3/files
- files.update reference — https://developers.google.com/workspace/drive/api/reference/rest/v3/files/update
- Upload file data (resumable) — https://developers.google.com/workspace/drive/api/guides/manage-uploads
- Usage limits — https://developers.google.com/workspace/drive/api/guides/limits
- OAuth 2.0 for iOS & Desktop Apps (PKCE/loopback) — https://developers.google.com/identity/protocols/oauth2/native-app
- Authorize access to Google user data (Android) — https://developer.android.com/identity/authorization
- OAuth 2.0 Policies — https://developers.google.com/identity/protocols/oauth2/policies
- OAuth scopes list — https://developers.google.com/identity/protocols/oauth2/scopes
- OAuth App Verification — https://support.google.com/cloud/answer/13463073
- Verification requirements — https://support.google.com/cloud/answer/13464321
- OAuth consent screen setup — https://support.google.com/cloud/answer/10311615
- Changes to approved app (100-user cap) — https://support.google.com/cloud/answer/13464018
- Issue tracker 330555392 (drive.file visibility) — https://issuetracker.google.com/issues/330555392 **[supporting]**
- Stack Overflow (drive.file scope meaning) — https://stackoverflow.com/questions/69927416/google-drive-api-edit-shared-files-using-a-less-scary-scope **[supporting]**

**Dropbox**
- OAuth guide (PKCE, open source, refresh tokens) — https://www.dropbox.com/developers/reference/oauthguide
- PKCE: What and Why? — https://dropbox.tech/developers/pkce--what-and-why-
- dropbox-sdk-java README (Android, Kotlin, PKCE) — https://github.com/dropbox/dropbox-sdk-java
- files/upload reference (WriteMode.update, rev) — https://docs.dropboxapi.com/dropbox-api/api-reference/user-endpoints/files/upload
- API reference index — https://docs.dropboxapi.com/dropbox-api/api-reference
- v1→v2 migration guide (delta → list_folder) — https://www.dropbox.com/developers/reference/migration-guide
- DBX file access guide (cursor) — https://developers.dropbox.com/dbx-file-access-guide
- Upload limitations (2 TB) — https://help.dropbox.com/sync/upload-limitations
- Data transport limit — https://www.dropbox.com/developers/reference/data-transport-limit
- Developer guide (50-user / production review) — https://docs.dropboxapi.com/dropbox-api/docs/developer-resources/developer-guide
- Rate limits (community, echoing docs) — https://community.dropbox.com/en/discussion/183714/dropbox-api-rate-limits **[supporting]**

**Microsoft OneDrive / Graph**
- Auth code flow + PKCE — https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow
- Register an app — https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app
- App Folder (Files.ReadWrite.AppFolder, /special/approot) — https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/special-folders-appfolder?view=odsp-graph-online
- delta query overview — https://learn.microsoft.com/en-us/graph/delta-query-overview
- driveItem: delta — https://learn.microsoft.com/en-us/graph/api/driveitem-delta?view=graph-rest-1.0
- driveItem: update (If-Match, 412) — https://learn.microsoft.com/en-us/graph/api/driveitem-update?view=graph-rest-1.0
- Graph throttling (429, Retry-After) — https://learn.microsoft.com/en-us/graph/throttling
- msgraph-sdk-java README (Android ProGuard/multidex) — https://github.com/microsoftgraph/msgraph-sdk-java

**kDrive (Infomaniak)**
- API reference — https://developer.infomaniak.com/docs/api
- OpenAPI document — https://developer.infomaniak.com/openapi.json
- Discover the API (60 req/min) — https://www.infomaniak.com/en/support/faq/2581/discover-the-infomaniak-api
- Manage kDrive storage (limits) — https://www.infomaniak.com/en/support/faq/2387/manage-kdrive-storage
- android-kDrive (GPLv3, Kotlin/Ktor) — https://github.com/Infomaniak/android-kDrive
- desktop-kDrive — https://github.com/Infomaniak/desktop-kDrive
- rclone issue #8456 (API "in beta") — https://github.com/rclone/rclone/issues/8456 **[supporting]**

**WebDAV**
- RFC 4918 (ETag, If-Match, 412) — https://www.rfc-editor.org/rfc/rfc4918
- RFC 6578 (sync-collection, sync-token) — https://www.rfc-editor.org/rfc/rfc6578
- Nextcloud WebDAV basics — https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html
- Nextcloud Login Flow (app passwords) — https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html
- sabre/dav WebDAV Sync — https://sabre.io/dav/sync/
- nextcloud/server #9339 (sync-collection) — https://github.com/nextcloud/server/issues/9339 **[supporting]**
- Box WebDAV end of life — https://support.box.com/hc/en-us/articles/360052806073-Announcing-end-of-life-for-Box-WebDAV-support

**S3-compatible**
- AWS conditional writes (If-Match/If-None-Match, SigV4, 412) — https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html
- AWS SigV4 — https://docs.aws.amazon.com/AmazonS3/latest/API/sig-v4-authenticating-requests.html
- Cloudflare R2 S3 API compatibility (conditional ops) — https://developers.cloudflare.com/r2/api/s3/api/
- MinIO conditional writes discussion — https://github.com/minio/minio/discussions/20318 **[discussion]**
- Backblaze B2 S3-compatible API — https://www.backblaze.com/docs/cloud-storage-s3-compatible-api
- Scaleway conditional writes — https://www.scaleway.com/en/docs/object-storage/api-cli/using-conditional-writes/
- OVHcloud conditional writes — https://docs.ovhcloud.com/en/guides/storage-and-backup/object-storage/s3-conditional-writes
- AWS SDK for Kotlin (JVM + Android 24+) — https://aws.amazon.com/sdk-for-kotlin/ · https://docs.aws.amazon.com/sdk-for-kotlin/latest/developer-guide/home.html
- AWS SDK for Android end-of-support — https://repost.aws/questions/QUvAlFSCdjTZW9n-eEofxpTg/aws-sdk-for-android-end-of-support-definition **[supporting]**

**Comparable apps**
- Joplin sync overview (drivers, targets) — https://joplinapp.org/help/apps/sync/
- Joplin sync spec (items, sync_items, target info) — https://joplinapp.org/help/dev/spec/sync/
- Joplin Server delta sync — https://joplinapp.org/help/dev/spec/server_delta_sync
- Joplin sync locks — https://joplinapp.org/help/dev/spec/sync_lock
- Joplin conflicts — https://joplinapp.org/help/apps/conflict/
- Joplin Dropbox / OneDrive / S3 / WebDAV / Nextcloud — https://joplinapp.org/help/apps/sync/dropbox · /onedrive · /s3 · /webdav · /nextcloud
- Obsidian Sync intro — https://help.obsidian.md/sync
- Obsidian Sync FAQ (third-party caveats, file limits) — https://help.obsidian.md/sync/faq
- Obsidian "Sync your notes across devices" — https://help.obsidian.md/sync-notes
- Logseq Sync — https://github.com/logseq/docs/blob/master/pages/Logseq%20Sync.md
- Logseq "How to sync your graph" — https://github.com/logseq/docs/blob/master/pages/How%20to%20sync%20your%20Logseq%20graph%20across%20devices.md
- Standard Notes self-hosting — https://standardnotes.com/help/47/can-i-self-host-standard-notes
- Syncthing Android discontinued — https://forum.syncthing.net/t/discontinuing-syncthing-android/23002 · https://github.com/syncthing/syncthing-android
- Syncthing-Fork — https://github.com/Catfriend1/syncthing-android

**Project context**
- `docs/adr/0002-no-backend-server.md`, `0003-vault-record-per-file.md`, `0004-last-writer-wins-per-record.md`, `0001-vault-portability-is-capability-driven.md`
- `docs/research/cross-platform-file-access.md`, `docs/research/dictionary-and-ai-integration.md`
