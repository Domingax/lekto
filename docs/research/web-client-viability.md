# Can web be a first-class third client now that the vault is app-private?

**Date:** 2026-09-27
**Scope:** Lekto is Android-first, Kotlin Multiplatform + Compose Multiplatform, desktop second, web previously **out of scope**. The web target was dropped largely because the vault was conceived as a **real user-chosen folder** and browsers cannot reliably reach folders ([ADR-0001](../adr/0001-vault-portability-is-capability-driven.md)). The design has since moved toward an **app-private store** (OPFS/IndexedDB on web; app-private or SAF on Android) and an **app-level sync engine** (`SyncTarget` seam with WebDAV/Dropbox/OneDrive-Graph/S3 drivers) instead of an external folder-sync tool ([cloud-sync-backends](./cloud-sync-backends.md)).
**Question:** Does decoupling the vault from the folder remove the web target's structural handicap, and is web viable again as a third client?
**Method:** Primary sources only — provider API/OAuth/CORS documentation, MDN and the WHATWG/WebKit storage specifications, IETF RFCs and BCPs, JetBrains/Kotlin documentation and release notes. Every claim carries a URL. Claims I could not confirm from a primary source are marked **[unverified]**; where the only evidence is a community thread or a vendor forum I mark it **[community]**.

> **Headline:** Removing the folder **does** remove the specific blocker that killed web (no File System Access API on Firefox/Safari). It does **not** remove a whole *class* of web handicaps, because the web client now has to reach three things it did not before: **browser storage that Safari may evict**, **self-hosted WebDAV servers that usually do not send CORS headers**, and **OAuth token endpoints whose browser rules are stricter than the native ones**. Dropbox is genuinely clean in-browser (CORS + PKCE + refresh tokens). Microsoft Graph is workable but caps SPA refresh tokens at **24 hours** and needs a top-level-frame re-auth. Google Drive is effectively **token-only** in a pure browser (no refresh token without a backend). WebDAV — the report's own recommended *first* driver — is the **worst** browser fit: Nextcloud ships no DAV CORS and Synology's is off by default. On top of that, Compose Multiplatform for web is **Beta** with real accessibility/text-input gaps. **Verdict: web becomes technically possible as a third, deliberately degraded client, but the hypothesis ("decoupling the vault makes web viable") is only half right — the constraint moved from "folder access" to "storage durability + provider reachability", and the sync driver that was recommended first is the one that breaks.**

---

## 0. What actually changed, and what did not

The old blocker (documented in ADR-0001) was concrete and absolute: the File System Access API exists on Chromium but not Firefox or any Safari, and stock Capacitor cannot write to a user-chosen Android directory ([ADR-0001](../adr/0001-vault-portability-is-capability-driven.md)). If the vault is now an **app-private store**, that blocker disappears — a browser can always read/write its own origin-private storage.

But "the vault is app-private" changes the question in three ways:

1. **The durability question replaces the access question.** A local-first app that can read/write OPFS still has to answer "will the browser let this data stay?" On Safari it may not (see §5).
2. **The sync engine must run in the browser**, so every `SyncTarget` driver now has to survive CORS, preflight, and OAuth-in-the-browser — constraints that never applied when sync was an external desktop agent or an Android foreground service.
3. **The reader/UI has to run in the browser**, so the question becomes whether Kotlin/Wasm + Compose is a shippable web UI (§6) and whether the EPUB engine (Readium, Android-only per [android-first-stack](./android-first-stack.md)) exists at all on web.

The rest of this report answers each in turn.

---

## 1. WebDAV from a browser

**Short answer: yes in principle, but not out of the box on the servers Lekto's persona actually uses. Nextcloud ships no WebDAV CORS; Synology's is disabled by default; ownCloud alone documents a CORS allowlist.** A browser-based WebDAV client works only against a server an admin has deliberately configured for it.

### 1.1 What the browser requires

- **`PROPFIND` and `REPORT` are not CORS-"simple" methods.** A simple request is only `GET`, `HEAD`, or `POST` (with a safelisted `Content-Type`); everything else triggers a **preflight `OPTIONS`**, and the server must answer with `Access-Control-Allow-Methods` including the method ([MDN: CORS → simple requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#simple_requests), [preflighted requests](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#preflighted_requests)). So `PROPFIND`/`REPORT`/`PUT`/`MKCOL`/`MOVE` all require the server to answer a preflight.
- **WebDAV's request headers are CORS-unsafe.** `Depth: 1` and `Content-Type: application/xml` are not safelisted, so they are preflighted too; the response will only expose `Content-Type`/`ETag`-style headers to JS if the server sends `Access-Control-Expose-Headers` ([MDN: CORS → the HTTP response headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#the_http_response_headers)).
- **HTTP Basic credentials can be sent, but only as a manually-attached header or a credentialed request.** By default cross-origin `fetch` sends no credentials; to send them you set `credentials: "include"` **and** the server must reply with an explicit `Access-Control-Allow-Origin` (not `*`) **and** `Access-Control-Allow-Credentials: true` ([MDN: CORS → requests with credentials](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#requests_with_credentials)). Note the preflight itself must never carry credentials, and the credentialed response may not use `*` wildcards for any of the access-control headers ([same](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#preflight_requests_and_credentials), [credentialed requests and wildcards](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#credentialed_requests_and_wildcards)). In practice, browser WebDAV clients attach an `Authorization: Basic …` header directly, which just makes the preflight mandatory.

### 1.2 Nextcloud does **not** send WebDAV CORS by default

- Nextcloud's WebDAV base is `/remote.php/dav`, authenticated by Basic auth or session cookies ([Nextcloud WebDAV basics](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html)).
- Nextcloud's built-in CORS support lives in `CORSMiddleware` and applies **only to AppFramework/OCS controller methods annotated `@CORS`** — not to the DAV endpoint. Its comments describe it as "for webapps that want to access an API and don't run on the same domain", it requires Basic auth, and it explicitly **forbids** `Access-Control-Allow-Credentials: true` (`CORSMiddleware::afterController` throws if it is set) ([nextcloud/server `CORSMiddleware.php`](https://github.com/nextcloud/server/blob/master/lib/private/AppFramework/Middleware/Security/CORSMiddleware.php)).
- The current Nextcloud sample configuration contains **no** `cors.allowed-domains` key at all ([nextcloud/server `config.sample.php`](https://github.com/nextcloud/server/blob/master/config/config.sample.php)). There is no documented native knob to enable DAV CORS.
- The known workaround is the third-party **`webapppassword`** app, whose own README says it exists "to generate a temporary app password and set CORS headers to allow WebDAV/CalDAV, Share API and Preview access from inside a webpage", configurable via `webapppassword.origins` (`config.php`) or a settings page ([digital-blueprint/webapppassword README](https://github.com/digital-blueprint/webapppassword)). Nextcloud's own issue tracker records the same gap: the app "lets use the DAV resources" by patching the Sabre responses against an allowlist, while the sharing API still has "neither the necessary preflight OPTIONS route, nor the `@CORS` annotation" ([nextcloud/server #37716](https://github.com/nextcloud/server/issues/37716)).
- Consequently the most-used browser WebDAV client states it bluntly: *"It is a known issue that Nextcloud servers by default don't return friendly CORS headers, making working with this library within a browser context impossible. You can of course force the addition of CORS headers (Apache or Nginx configs) yourself, but do this at your own risk."* ([perry-mitchell/webdav-client README → CORS](https://github.com/perry-mitchell/webdav-client#cors)).

### 1.3 ownCloud and Synology

- **ownCloud** *does* document a first-class option: `cors.allowed-domains` — "Define the global list of CORS domains. All users can use tools running CORS requests from the listed domains." ([owncloud/core `config.sample.php`](https://github.com/owncloud/core/blob/master/config/config.sample.php)). So ownCloud-derived servers can be made browser-usable by config; Nextcloud (as of master) cannot, except via the app.
- **Synology** WebDAV CORS is off by default and must be added through reverse-proxy/HAProxy or server config. The evidence is **community-only** — a Synology forum thread asking how to enable it, a Keeweb issue, and a blog describing the HAProxy workaround ([Synology Community: WebDAV enable CORS](https://community.synology.com/enu/forum/17/post/104122) **[community]**, [keeweb #703](https://github.com/keeweb/keeweb/issues/703) **[community]**, [Tevin Zhang: Fix CORS for Synology WebDAV](https://tevinzhang.com/en/fix-cors-for-synology-webdav-no-nginx-hacks/) **[community]**). Synology's own help page documents enabling WebDAV but not CORS ([Synology KB: access files with WebDAV](https://kb.synology.com/vi-vn/DSM/tutorial/How_to_access_files_on_Synology_NAS_with_WebDAV)).

### 1.4 What real browser WebDAV clients do

- `webdav` (perry-mitchell) is a TypeScript client that explicitly supports the browser, uses `fetch`, and implements `PROPFIND`/custom methods ([README](https://github.com/perry-mitchell/webdav-client)). Its browser guidance is "handle CORS yourself" (see §1.2). It also notes that browser streams are stubbed, so large file transfers cannot be streamed in the browser ([README → Browser support](https://github.com/perry-mitchell/webdav-client#browser-support)).
- Nextcloud's own WebDAV JavaScript sample is deliberately **same-origin** — it builds the DAV URL with `@nextcloud/router` and uses the `webdav` library from inside the Nextcloud page, which is exactly the case that avoids CORS ([Nextcloud WebDAV basics → Making requests in JavaScript](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html)).
- The workaround that makes it work cross-origin (`webapppassword`) also introduces a **server-side component**: it mints a temporary app password. That is not a Lekto server, but it *is* an extra app the user must install, and its absence on stock Nextcloud/Synology is the default.

**WebDAV verdict:** viable against ownCloud or a CORS-enabled reverse proxy; **blocked by default** on Nextcloud and Synology. It is the least portable of the drivers *from a browser*, which matters because the earlier report ranked WebDAV **first** for *native* clients ([cloud-sync-backends §11](./cloud-sync-backends.md)).

---

## 2. Dropbox API from a browser

**Short answer: yes. Dropbox is the cleanest browser story of any provider here — official browser SDK, documented PKCE-without-secret, documented CORS accommodations, and refresh tokens.**

### 2.1 CORS

Dropbox publishes a dedicated browser/CORS note: browser JavaScript can avoid the preflight round-trip by using the URL parameters `arg` and `authorization` instead of the `Dropbox-API-Arg`/`Authorization` headers, setting `Content-Type: text/plain; charset=dropbox-cors-hack`, and passing `reject_cors_preflight=true` ([Dropbox API v2 → Browser-based JavaScript and CORS pre-flight requests](https://www.dropbox.com/developers/paper-api-alpha)). The very existence of that documented accommodation, plus the official browser build, is primary evidence that the API is browser-usable; the "hack" is an optimisation to keep requests *simple*, not a statement that preflight is refused.

- **Caveat:** whether *every* endpoint survives a preflight is not documented. A community thread reports that `files/list_folder/longpoll` is CORS-blocked ([Dropbox Community: /list_folder/longpoll not working with CORS](https://community.dropbox.com/en/discussion/658737/list-folder-longpoll-not-working-with-cors) **[community]**). Treat "longpoll is CORS-blocked" as **[unverified]** and plan for cursor polling rather than longpoll.

### 2.2 PKCE entirely in-browser

- The official JavaScript SDK is built for "modern web browsers and Web Workers"; browser/Worker environments must provide `Promise`, `fetch`, `TextEncoder`, and **"PKCE authentication also requires the Web Crypto API"** ([dropbox-sdk-js](https://dropbox.github.io/dropbox-sdk-js/)).
- The SDK's own guidance is explicit: *"Client-side applications cannot keep an app secret confidential. Never embed a Dropbox app secret in browser or Worker code. Use the OAuth authorization-code flow with PKCE and your app key instead."* It ships a browser PKCE example ([dropbox-sdk-js → Browser authentication and PKCE](https://dropbox.github.io/dropbox-sdk-js/)).
- Dropbox's API reference names PKCE as "the recommended flow for client-side apps, such as mobile, desktop, or **browser JavaScript apps**", and warns never to display the authorize page in a webview ([Dropbox API v2 → Authorization](https://www.dropbox.com/developers/paper-api-alpha)).
- `token_access_type=offline` returns a long-lived `refresh_token`, and `grant_type=refresh_token` mints new short-lived access tokens "without direct interaction from a user" ([Dropbox API v2 → /oauth2/token](https://www.dropbox.com/developers/paper-api-alpha)). A browser PWA can therefore hold and use a Dropbox refresh token — the community question is literally "how do I do this", with the official SDK providing the primitives ([Dropbox Community: PKCE refresh token in a JavaScript PWA](https://community.dropbox.com/en/discussion/528083/need-help-generating-a-pkce-refresh-token-in-a-javascript-pwa)).

**Dropbox verdict:** fully browser-viable. No secret, no backend, refresh tokens available, CORS documented. The residual questions are longpoll (unverified) and the general browser-token-storage caveats in §4.

---

## 3. Microsoft Graph from a browser

**Short answer: yes, with real caveats — a `spa` redirect type is mandatory for CORS token redemption, `/content` downloads must be re-routed, and SPA refresh tokens are capped at 24 hours, forcing a top-level-frame re-auth roughly daily (and every hour of access-token expiry needs a token refresh).**

### 3.1 CORS

- Microsoft's OneDrive/Graph CORS page states the OneDrive API "supports HTTP access control (CORS) to allow single page JavaScript applications to use the OneDrive API through the common XMLHttpRequest pattern", with a working `graph.microsoft.com/v1.0/me/drive/root/children` example ([OneDrive: CORS support](https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/working-with-cors)).
- **Download caveat:** a JS app "cannot use the `/content` API, since this responds with a `302` redirect. A `302` redirect is explicitly prohibited when a CORS preflight is required, such as when providing the Authorization header." The documented fix is to request the pre-authenticated `@microsoft.graph.downloadUrl` instead ([same](https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/working-with-cors)). This is the same class of rule as MDN's warning about redirects after preflight ([MDN: preflighted requests and redirects](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#preflighted_requests_and_redirects)).

### 3.2 PKCE and the `spa` redirect type

- The auth-code flow "paired with Proof Key for Code Exchange (PKCE)" is the documented choice for SPAs, desktop and mobile apps; `code_challenge_method=S256` is "required by the Microsoft identity platform for single page apps using the authorization code flow" ([Microsoft identity platform and OAuth 2.0 authorization code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)).
- SPA redirect URIs "require special configuration": you must set the redirect URI's `type` to `spa`, "backward-compatible with the implicit flow", and CORS is enabled on the login endpoints only for `spa`-typed redirect URIs. Without it: *"access to XMLHttpRequest at 'https://login.microsoftonline.com/…/token' … has been blocked by CORS policy"* and *"cross-origin token redemption is permitted only for the 'Single-Page Application' client-type"* ([same](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)).
- Microsoft also states it "prevents the use of client credentials in all flows in the presence of an `Origin` header, to ensure that secrets aren't used from within the browser" ([same](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)) — a hard provider-level rule against browser secrets.

### 3.3 Refresh-token lifetime and third-party cookies

- SPA refresh tokens "have a 24-hour lifetime rather than a 90-day lifetime" ([How to handle third-party cookie blocking in browsers](https://learn.microsoft.com/en-us/entra/identity-platform/reference-third-party-cookies-spas)). The token-endpoint reference repeats: "For refresh tokens sent to a redirect URI registered as `spa`, the refresh token expires after 24 hours … apps must be prepared to re-run the authorization code flow using an interactive authentication to get a new refresh token every 24 hours", done "in a top level frame" in browsers without third-party cookies such as **Safari** ([auth code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow)).
- The same page frames the security reason: XSS or a compromised JS package can steal a refresh token, so SPAs are issued only 24-hour refresh tokens ([reference-third-party-cookies-spas](https://learn.microsoft.com/en-us/entra/identity-platform/reference-third-party-cookies-spas)).

**Graph verdict:** workable, but the browser build is a **degraded** Graph client compared to a native one: 24h refresh ceiling, daily top-level-frame interaction, and a non-standard download path. Background/unattended sync is not possible while the tab is closed.

---

## 4. OAuth in a browser for these providers

### 4.1 Popup vs redirect

- **Google Identity Services** supports both: `ux_mode: 'redirect'` (Google redirects to your server endpoint) and `ux_mode: 'popup'` (a JS callback receives the code); Google recommends "Popup mode UX flow with Authorization Code model" ([GIS: Use Code Model](https://developers.google.com/identity/oauth2/web/guides/use-code-model)).
- **Microsoft** documents full-page redirect and popup as the two ways to sign in when third-party cookies are blocked, and warns "Browsers are decreasing support for popups, so they might not be the most reliable option. User interaction with the SPA before creating the popup might be needed" ([third-party cookie blocking](https://learn.microsoft.com/en-us/entra/identity-platform/reference-third-party-cookies-spas)).
- **Dropbox** requires the authorize page be opened in the system browser, not a webview ([Dropbox API v2 → Authorization](https://www.dropbox.com/developers/paper-api-alpha)).

### 4.2 Refresh-token storage in a browser — the security caveats

The authoritative source is now **RFC 10017 (BCP 212), "OAuth 2.0 for Browser-Based Applications"** ([RFC 10017](https://www.rfc-editor.org/rfc/rfc10017)):

- Malicious JavaScript "has the same privileges as the legitimate application code" and can read origin-based storage such as localStorage and IndexedDB; the browser-based OAuth client architecture "is vulnerable to all attack scenarios" and a stolen refresh token enables long-term impersonation ([RFC 10017 §§5, 6.3](https://www.rfc-editor.org/rfc/rfc10017)).
- Refresh tokens should be rotated per use or sender-constrained, with a maximum lifetime/non-use expiry; "Limiting the overall refresh token lifetime to the lifetime of the initial refresh token ensures a stolen refresh token cannot be used indefinitely" ([RFC 10017 §6.3.2.3](https://www.rfc-editor.org/rfc/rfc10017)).
- Storage options: cookies are **NOT RECOMMENDED** for JS token storage; localStorage is readable by any same-origin script and synchronous; sessionStorage is tab-scoped; IndexedDB is preferred over localStorage (asynchronous) but shared across tabs and Service Workers; in-memory storage dies on reload; none "can fully mitigate token exfiltration" ([RFC 10017 §8](https://www.rfc-editor.org/rfc/rfc10017)).
- "In all cases, as of this writing, **there is no guarantee that browser storage is encrypted at rest.**" A non-extractable Web Crypto key can encrypt tokens but cannot protect against filesystem-level exfiltration, because the key-holding guarantees do not extend to OS storage ([RFC 10017 §8.6](https://www.rfc-editor.org/rfc/rfc10017)).
- PKCE itself is defined in [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636); it protects the code exchange but was designed for code-interception, not for a fully XSS-compromised client (see RFC 10017 §5.1.3: "There are no practical security mechanisms for frontend applications that counter this attack scenario").

This is the crux for ADR-0002: a **no-backend** browser client must hold its own refresh token, which every provider treats as a public-client risk. The native clients inherit the same OAuth model but run in an OS sandbox where keystores exist; the browser has no equivalent.

### 4.3 Do any providers forbid browser clients?

| Provider | Browser client | Evidence |
|---|---|---|
| **Dropbox** | Explicitly supported | "recommended flow for client-side apps … browser JavaScript apps"; "Never embed a Dropbox app secret in browser or Worker code" ([Dropbox](https://www.dropbox.com/developers/paper-api-alpha), [SDK](https://dropbox.github.io/dropbox-sdk-js/)) |
| **Microsoft Graph** | Allowed only as an `spa`-type public client with PKCE and no secret; secrets in the browser are actively blocked | [auth code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow) |
| **Google** | The token model is supported in-browser, but it is **implicit-style and issues no refresh token**; the code model that *does* get refresh tokens "hosts an authorization code endpoint on your server" and "securely stores refresh tokens" on the backend | [token model](https://developers.google.com/identity/oauth2/web/guides/use-token-model), [code model](https://developers.google.com/identity/oauth2/web/guides/use-code-model) |
| **Google (webviews)** | Embedded user-agents are refused (`disallowed_useragent`) | [OAuth policies](https://developers.google.com/identity/protocols/oauth2/policies) |
| **WebDAV / S3** | N/A (no OAuth; Basic/SigV4) | [Nextcloud WebDAV](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html) |

**Google is the standout limitation.** For a pure browser with no backend, Google Drive is effectively **access-token-only**: "Access tokens have a short lifetime and a new one must be obtained by calling `requestAccessToken()` from a user-driven event if it expires" ([token model](https://developers.google.com/identity/oauth2/web/guides/use-token-model)). The code model that could yield a refresh token explicitly requires a backend endpoint, and Google's older client-side flow notes that the authorization endpoint and the revoke endpoint "does not support Cross-Origin Resource Sharing (CORS)" ([OAuth 2.0 for Client-side Web Applications](https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow)). That confirms the earlier report's conclusion that a no-backend Google Drive sync is structurally second-class — and it is now *worse* on web than on Android.

---

## 5. OPFS as the web vault

**Short answer: OPFS is a real, byte-oriented, performant file store available in all current major browsers — but it is origin-private, best-effort by default, subject to quota eviction, deleted when the user clears site data, and on Safari it can be proactively evicted after seven days without user interaction. It is a fine cache and a workable *primary* store only if the user installs the PWA and the vault can be re-derived from sync.**

### 5.1 What OPFS is

MDN: OPFS is "a storage endpoint provided as part of the File System API, which is private to the origin of the page and not visible to the user like the regular file system. It provides access to a special kind of file that is highly optimized for performance and offers in-place write access to its content." It is reached via `navigator.storage.getDirectory()`; there is an async API on the main thread and a synchronous `createSyncAccessHandle()` **available only in Web Workers** ([MDN: Origin private file system](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system)).

### 5.2 Durability, quotas, eviction

- OPFS "is subject to browser storage quota restrictions, just like any other origin-partitioned storage mechanism (for example IndexedDB)", `navigator.storage.estimate()` reports usage, **"Clearing storage data for the site deletes the OPFS"**, and the files are not meant to be visible to the user ([MDN: OPFS](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system)).
- Data is **best-effort by default**: it persists "as long as the origin is below its quota, the device has enough storage space, and the user doesn't choose to delete the data". An origin can request persistent mode with `navigator.storage.persist()`; Chrome/Edge and Safari "automatically approve or deny the request based on the user's history of interaction" and Firefox shows a prompt. Under storage pressure browsers evict the **least-recently-used origin**, skipping persisted origins ([MDN: Storage quotas and eviction criteria](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)).
- **Safari proactively evicts:** "If an origin has no user interaction, such as click or tap, in the last seven days of browser use, its data created from script will be deleted. Cookies set by server are exempt from this eviction." ([MDN: Storage quotas → proactive eviction](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)). WebKit's own policy post confirms eviction "when the site has not been interacted with by the user for some time (see Intelligent Tracking Prevention)", and that an origin "might be excluded from eviction if it has active page at the time of eviction, **or its storage is in persistent mode**" ([WebKit: Updates to Storage Policy](https://webkit.org/blog/14403/updates-to-storage-policy/)). WebKit grants persistence "based on heuristics like whether the website is opened as a Home Screen Web App" ([same](https://webkit.org/blog/14403/updates-to-storage-policy/)).
- Quotas (Safari 17+/iOS 17+): browser apps ~60% of total disk per origin and ~80% overall; non-browser WebKit apps ~15%/~20% ([WebKit](https://webkit.org/blog/14403/updates-to-storage-policy/); [MDN](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)). Chromium allows up to 60% per origin and evicts best-effort origins when the browser-wide cap (currently ~80%) is exceeded; Firefox caps best-effort at min(10% of disk, 10 GiB) per group ([MDN](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)).

**Implication for ADR-0003's "one immutable JSON file per record":** OPFS fits the shape well (many small files, atomic write-temp-then-rename), and the synchronous in-worker handle gives the atomicity/perf a file-oriented vault wants ([MDN: OPFS worker section](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system)). The durability story is the problem, not the file model.

### 5.3 OPFS vs IndexedDB

| Axis | OPFS | IndexedDB |
|---|---|---|
| Model | Byte-oriented files/directories, in-place writes | Structured objects, keyed/indexed store |
| Perf model | `createSyncAccessHandle()` (worker-only) for fast synchronous I/O | Async transactions |
| Visibility to user | None (origin-private) | None (origin-private) |
| Quota | Same browser storage manager | Same browser storage manager |
| Eviction | Evicted as part of the origin | Evicted as part of the origin |
| Source | [MDN OPFS](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system) | [MDN Storage quotas](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria) |

For a per-record JSON vault, OPFS is the closer match (it *is* a file system, and "write temp, rename" maps directly). IndexedDB would be the fallback if OPFS primitives are missing, but as a **query cache** IndexedDB is arguably better (ADR-0003 already reserves SQLite for that role). Either way, **both share the same quota and eviction policy per origin** — choosing OPFS does not buy durability ([MDN](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)).

### 5.4 Browser support

Per MDN's browser-compat data:

- `StorageManager.getDirectory()` (the OPFS root): **Chrome 86, Edge mirror, Firefox 111, Safari 15.2** (iOS mirror) ([BCD: StorageManager.getDirectory](https://github.com/mdn/browser-compat-data/blob/main/api/StorageManager.json)).
- `FileSystemFileHandle` and `createSyncAccessHandle()`: **Chrome 86/102, Firefox 111, Safari 15.2** ([BCD: FileSystemFileHandle](https://github.com/mdn/browser-compat-data/blob/main/api/FileSystemFileHandle.json)).
- `FileSystemFileHandle.createWritable()`: Chrome 86, Firefox 111, **Safari 26** ([same](https://github.com/mdn/browser-compat-data/blob/main/api/FileSystemFileHandle.json)).

**No current major browser lacks OPFS**, but the *write path* differs: Safari's mature OPFS API is the worker-only synchronous access handle; the async `createWritable()` path landed only in Safari 26. A Kotlin/Wasm client must therefore prefer the worker + sync-handle route on Safari.

### 5.5 Can an app-level sync engine work on OPFS reliably?

Yes for the mechanics: an engine can enumerate/read/write/rename files, keep a local cursor, and reconcile against the remote. Reliability concerns are **not** the API but (a) the origin being evicted (Safari 7-day rule; uninstall/clear-site-data), (b) a closed tab meaning no sync happens — the same "only syncs while the app runs" limitation Obsidian documents ([Obsidian FAQ](https://help.obsidian.md/sync/faq) via [cloud-sync-backends §9](./cloud-sync-backends.md)), and (c) OPFS being invisible to the user, which weakens the "own your data, repairable by hand" promise (ADR-0003) unless export/import stays first-class.

---

## 6. Kotlin/Wasm + Compose Multiplatform for web

**Short answer: both are Beta, and the web build is the weakest target in the matrix. It runs on Chrome 119+, Firefox 120+, and Safari 18.2+; older browsers must fall back to a separate Kotlin/JS build. Accessibility, text input, drag-and-drop, binary size/startup, and DOM interop all still have documented gaps.**

### 6.1 Stability

- Kotlin Multiplatform stability: **Web based on Kotlin/Wasm = Beta** (Web based on Kotlin/JS = Stable); **Compose Multiplatform web = Beta** ([Kotlin: Stability of supported platforms](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)).
- JetBrains' own milestone post is candid: Compose for web "is now in Beta … ready for real-world use by early adopters" ([Compose Multiplatform 1.9.0: Compose for Web goes Beta](https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta/)).
- The roadmap toward stable explicitly still includes "**Implementing support for drag-and-drop functionality in mobile browsers**", "**Improving accessibility support**", and "Addressing issues related to the `TextField` component" ([What's new in Compose Multiplatform 1.9](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)).

### 6.2 Browser support, including Safari and Safari iOS

Kotlin's own configuration page lists the required browser versions for WasmGC (the feature Kotlin/Wasm depends on):

- Chrome/Chromium-based **119+**: works by default; older versions need flags or an old Kotlin.
- Firefox **120+**: works by default.
- **Safari/WebKit 18.2+**: works by default; "**For older versions: Not supported.**" Safari 18.2 corresponds to iOS 18.2/iPadOS 18.2/macOS 15.2 ([Kotlin/Wasm browser versions](https://kotlinlang.org/docs/wasm-configuration.html)).

JetBrains corroborates: "As of December 11, 2024, all major browsers, including Safari, support WebAssembly Garbage Collection (WasmGC)" ([Present and Future of Kotlin for Web](https://blog.jetbrains.com/kotlin/2025/05/present-and-future-kotlin-for-web/); see also [web.dev: WasmGC baseline](https://web.dev/blog/wasmgc-wasm-tail-call-optimizations-baseline)). The operational consequence on iOS: **any device that cannot run iOS 18.2+ cannot run a Compose/Wasm web client at all.** JetBrains provides a **compatibility mode**: cross-compile to both `js` and `wasmJs`, run Wasm in modern browsers and "for older browsers, the JavaScript version will run automatically, preventing a blank screen" ([web-overview → Compatibility mode for web targets](https://kotlinlang.org/docs/web-overview.html)). That is a real mitigation, but it doubles the web artifacts and lowers fidelity on the fallback path.

### 6.3 Known limitations

- **Accessibility:** "Compose Multiplatform now provides initial accessibility support for the web target", enabling screen readers for labels and buttons, but "the following features are not yet supported: Accessibility for interop and container views with scrolls and sliders; Traversal indexes." ([What's new in CMP 1.9](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)). For a reading app with heavy scroll/selection semantics this is a material gap.
- **DOM interop / HTML semantics:** Compose for web renders into a canvas (`ComposeViewport`), so the DOM contains no semantic text. JetBrains itself recommends HTML-based Kotlin/JS (Kobweb/Kilua/React) when the goal is "better control … SEO and accessibility … find on page and page translation" ([web-overview](https://kotlinlang.org/docs/web-overview.html)). Embedding real HTML is possible only through the newer `WebElementView()` composable, which "overlays the canvas area … It intercepts input events within that area" ([CMP 1.9](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html)).
- **Binary size and startup:** JetBrains lists **per-module compilation** and **multithreading** as future work, and says per-module compilation "enables loading program parts on demand, which can **significantly improve application startup time and reduce network load**" ([Present and Future of Kotlin for Web](https://blog.jetbrains.com/kotlin/2025/05/present-and-future-kotlin-for-web/)) — i.e. today's single-module Wasm payload is a known startup/network cost. **No first-party numeric size/startup figures were found [unverified].**
- **Performance is otherwise good:** JetBrains states Compose on Wasm "outperforms JavaScript and is approaching that of the JVM" in their Chrome benchmarks ([Kotlin/Wasm performance](https://kotlinlang.org/docs/wasm-overview.html)).
- **Browser APIs:** the Kotlin/Wasm stdlib ships declarations "including the DOM API" and JS interop ([wasm-overview](https://kotlinlang.org/docs/wasm-overview.html)). **OPFS is not in that list** as far as this research could confirm, so a web `VaultStore` would need hand-written `external` declarations for `navigator.storage.getDirectory()`, `FileSystemDirectoryHandle`, etc. **[unverified — not checked against the stdlib source]**.

### 6.4 Can `commonMain` + Compose realistically target web today?

- **`commonMain` domain: yes.** Kotlin/Wasm is a KMP target; shared domain logic (vault format, tokenisation, provider adapters) is the intended use ([web-overview](https://kotlinlang.org/docs/web-overview.html)). `expect/actual` gaps are limited to platform services: OPFS, TTS (Web Speech API via interop), and secure storage (KSafe documents Wasm support, per [android-first-stack §2](./android-first-stack.md)).
- **Compose UI: yes, but Beta and lower-fidelity.** The same Compose UI can compile to Wasm, but the web rendering is canvas-based with the documented accessibility/interop/text-input gaps above. For a *reader*, where text selection, find-in-page, translation, and screen-reader access matter, this is the single biggest UX risk — and it is exactly the area where JetBrains points users at HTML-based Kotlin/JS instead ([web-overview](https://kotlinlang.org/docs/web-overview.html)).
- **The EPUB engine is not shared.** Readium's Kotlin toolkit is Android-only with KMP conversion "future work" ([android-first-stack §2](./android-first-stack.md), [Readium #547](https://github.com/readium/kotlin-toolkit/discussions/547)). A Compose/Wasm reader would need a **third** EPUB implementation (JS/Wasm) or an HTML-based reader — the same duplication cost that already applies to desktop.

---

## 7. Verdict

### 7.1 Verdict table

| Question | Browser-viable? | Decisive constraint | Key evidence |
|---|---|---|---|
| **WebDAV from browser** | ⚠️ Only on configured servers | Nextcloud ships **no DAV CORS**; Synology off by default; workaround is a server-side app | [Nextcloud `CORSMiddleware`](https://github.com/nextcloud/server/blob/master/lib/private/AppFramework/Middleware/Security/CORSMiddleware.php), [config.sample](https://github.com/nextcloud/server/blob/master/config/config.sample.php), [webdav-client CORS](https://github.com/perry-mitchell/webdav-client#cors), [webapppassword](https://github.com/digital-blueprint/webapppassword), [ownCloud `cors.allowed-domains`](https://github.com/owncloud/core/blob/master/config/config.sample.php) |
| **Dropbox from browser** | ✅ Yes | None material; longpoll CORS unverified | [dropbox-sdk-js](https://dropbox.github.io/dropbox-sdk-js/), [Dropbox CORS note](https://www.dropbox.com/developers/paper-api-alpha), [longpoll thread](https://community.dropbox.com/en/discussion/658737/list-folder-longpoll-not-working-with-cors) **[community]** |
| **Microsoft Graph from browser** | ⚠️ Yes, degraded | `spa` redirect required; `/content` redirect blocked by preflight; **24h refresh-token cap + top-level-frame re-auth daily** | [OneDrive CORS](https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/working-with-cors), [auth code flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow), [third-party cookies](https://learn.microsoft.com/en-us/entra/identity-platform/reference-third-party-cookies-spas) |
| **Google Drive from browser** | ❌ No (for unattended sync) | Browser gets the **token model with no refresh token**; the code model needs a backend | [token model](https://developers.google.com/identity/oauth2/web/guides/use-token-model), [code model](https://developers.google.com/identity/oauth2/web/guides/use-code-model) |
| **OAuth / refresh tokens in browser** | ⚠️ Works, permanently insecure-by-design | Tokens are readable by any same-origin script; browser storage not encrypted at rest; rotate + cap lifetimes | [RFC 10017](https://www.rfc-editor.org/rfc/rfc10017), [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636) |
| **OPFS as the web vault** | ⚠️ Workable, not durable by default | Origin-private; best-effort; deleted on clear-site-data; **Safari evicts after 7 days without interaction** unless persisted/PWA-installed | [MDN OPFS](https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system), [MDN quotas](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria), [WebKit storage policy](https://webkit.org/blog/14403/updates-to-storage-policy/) |
| **Kotlin/Wasm + Compose web** | ⚠️ Beta | Safari 18.2+ only (JS fallback otherwise); accessibility/text-input/drag-drop gaps; startup/size work pending | [KMP stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html), [Wasm browser versions](https://kotlinlang.org/docs/wasm-configuration.html), [CMP 1.9 notes](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html), [Kotlin for Web](https://blog.jetbrains.com/kotlin/2025/05/present-and-future-kotlin-for-web/) |

Legend: ✅ viable · ⚠️ viable with a material caveat · ❌ blocked.

### 7.2 Can web be a first-class third client, and should it be?

**It can be a real third client, but not a first-class one, and it should not be a near-term goal.**

The hypothesis is **half-correct**. Decoupling the vault from the folder does remove the original structural blocker: with an app-private store, a browser no longer needs the File System Access API, and OPFS/IndexedDB are universally available (§5.4). That is a genuine change, and it is why the answer is no longer a flat "no".

But the decoupling relocates the problem rather than deleting it, because three of the web platform's constraints are now *on the sync path*:

1. **The recommended first driver is the worst browser driver.** The earlier report's value-per-effort order was WebDAV → Dropbox → OneDrive → S3 ([cloud-sync-backends §11](./cloud-sync-backends.md)). WebDAV is exactly the driver whose browser support is worst: Nextcloud's own sample config has no CORS option and its CORS middleware is confined to `@CORS`-annotated AppFramework routes, not DAV; Synology is off by default; only ownCloud offers a native allowlist ([§1](#1-webdav-from-a-browser)). A web client therefore cannot honour "point at any WebDAV server" — it can only talk to servers an admin has deliberately opened. That is a *different* structural handicap, not the absence of one.
2. **Durability is weaker than native.** OPFS is best-effort by default, is wiped by "clear site data", and on Safari is proactively evicted after seven days without interaction unless persistence is granted (heuristically, for installed web apps) ([§5.2](#52-durability-quotas-eviction)). A local-first app whose local store can silently vanish is not the same product it is on Android/desktop, and "cloud is the source of truth" only holds if sync is intact and the user re-downloads on every cold start.
3. **OAuth in the browser is a permanently reduced trust model.** RFC 10017 is unambiguous that any same-origin script can read the tokens and that browser storage is not guaranteed encrypted at rest ([§4.2](#42-refresh-token-storage-in-a-browser--the-security-caveats)). ADR-0002 forbids a server, so the browser must be the token holder. Microsoft caps it at 24 hours; Google refuses refresh tokens to a pure browser client entirely. The browser client is therefore the only client where "sync" is tied to a user gesture once a day (Graph) or once an hour (Drive).

Add the UI risk — Compose/Wasm is Beta, Safari 18.2+ only without a parallel JS build, and has documented accessibility and text-input gaps ([§6](#6-kotlinwasm--compose-multiplatform-for-web)) — and the honest conclusion is:

> **Web is worth keeping as an explicitly lower-fidelity, optional third surface, with a driver chosen for the browser (Dropbox first), not with the native driver order. It should not become first-class, and it should not influence the Android/desktop architecture.**

**Strongest counter-argument.** The strongest case *for* first-class web is that the reframing genuinely changes the target's role: if the vault is app-private and sync is the engine, the browser becomes a **stateless-ish client that only needs to reach one sync provider**, and Dropbox does that cleanly — CORS, PKCE, no secret, refresh tokens, cursor deltas and `WriteMode.update(rev)` CAS all survive in-browser ([§2](#2-dropbox-api-from-a-browser)). OPFS is then just a cache; Safari's 7-day eviction is survivable because the remote is authoritative; and the whole client is `commonMain` domain + a Beta Compose UI, which is exactly the kind of incremental third target KMP was chosen for ([android-first-stack §2, §9](./android-first-stack.md)). On that reading, "web" is not the folder-dependent product of 2023 — it is a **Dropbox-client with a reading UI**, and the only true blocker is Beta UI quality, not platform capability.

The reason that argument does not carry the decision is **who Lekto is for**. The product and the prior research target the **data-sovereign / self-hoster persona** — Nextcloud, ownCloud, Synology, WebDAV — and the no-backend ADR deliberately rules out the OAuth-server scaffolding that makes browser OAuth safe ([cloud-sync-backends §5, §11](./cloud-sync-backends.md), [ADR-0002](../adr/0002-no-backend-server.md)). For exactly that persona, the browser path is the *worst* path: their servers lack CORS, their refresh tokens must live in a browser, and their "own your data" vault is now an invisible origin-private store. A web client that works well only for Dropbox users, while being degraded exactly for the WebDAV/self-hosters the app is built around, is not a first-class client — it is a **second-class client for a different audience**, and shipping it would fragment the product promise the way ADR-0001 already warns against. Keep the `VaultStore` and `SyncTarget` seams web-compatible (pure Kotlin in `commonMain`, no folder assumptions, an OPFS `actual` behind the seam), treat Compose/Wasm as a Beta option to revisit once its stable release lands, and choose the browser's first driver as **Dropbox, not WebDAV**, if and when web is revived.

---

## 8. What I could not verify

1. **Dropbox `list_folder/longpoll` CORS.** Only a community report that it is blocked; no Dropbox documentation either way. Treat as **[unverified]** ([community thread](https://community.dropbox.com/en/discussion/658737/list-folder-longpoll-not-working-with-cors)).
2. **Synology WebDAV CORS behaviour.** Evidence is vendor-forum/community and a blog, not Synology documentation. **[community]** ([Synology forum](https://community.synology.com/enu/forum/17/post/104122)).
3. **S3 from the browser.** SigV4 can be signed with Web Crypto, and buckets can be given CORS rules, but I did not verify a first-party S3-in-browser CORS story for LEKTO's target set here; the earlier report covers S3 `If-Match` on the server side ([cloud-sync-backends §6](./cloud-sync-backends.md)). Bucket CORS must be configured by the user, which is a setup burden. **[not re-verified in this report]**
4. **OPFS-exposed-to-Kotlin/Wasm.** The Wasm stdlib documents DOM/fetch declarations ([wasm-overview](https://kotlinlang.org/docs/wasm-overview.html)) but I did not confirm whether the File System API/OPFS is among them; assume custom `external` declarations are needed. **[unverified]**
5. **Compose/Wasm binary size and cold-start numbers.** JetBrains acknowledges startup/network costs and lists per-module compilation as future work, but publishes no first-party numbers. **[unverified]**
6. **Whether Graph data endpoints beyond `/me/drive` have identical CORS behaviour.** The primary CORS statement I found is OneDrive-specific ([OneDrive CORS](https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/working-with-cors)); Graph as a whole is widely used from browsers, but I did not find a single Graph-wide CORS statement. **[partly unverified]**
7. **Compose Multiplatform web on Safari iOS 18.2+ in production.** The browser-version requirement is documented; real-device performance and accessibility on iOS Safari were not testable from this environment. **[unverified]**

---

## Sources

**WebDAV / CORS**
- MDN, Cross-Origin Resource Sharing (CORS) — https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS
- Nextcloud, WebDAV basics — https://docs.nextcloud.com/server/latest/developer_manual/client_apis/WebDAV/basic.html
- nextcloud/server, `CORSMiddleware.php` — https://github.com/nextcloud/server/blob/master/lib/private/AppFramework/Middleware/Security/CORSMiddleware.php
- nextcloud/server, `config.sample.php` — https://github.com/nextcloud/server/blob/master/config/config.sample.php
- nextcloud/server issue #37716 (CORS origin allowlist) — https://github.com/nextcloud/server/issues/37716
- owncloud/core, `config.sample.php` (`cors.allowed-domains`) — https://github.com/owncloud/core/blob/master/config/config.sample.php
- digital-blueprint/webapppassword — https://github.com/digital-blueprint/webapppassword
- perry-mitchell/webdav-client (browser support, CORS) — https://github.com/perry-mitchell/webdav-client
- Synology Community: WebDAV enable CORS **[community]** — https://community.synology.com/enu/forum/17/post/104122
- keeweb #703 **[community]** — https://github.com/keeweb/keeweb/issues/703
- Tevin Zhang, Fix CORS for Synology WebDAV **[community]** — https://tevinzhang.com/en/fix-cors-for-synology-webdav-no-nginx-hacks/
- Synology KB, access files with WebDAV — https://kb.synology.com/vi-vn/DSM/tutorial/How_to_access_files_on_Synology_NAS_with_WebDAV

**Dropbox**
- Dropbox JavaScript SDK — https://dropbox.github.io/dropbox-sdk-js/
- Dropbox API v2 (CORS pre-flight note; OAuth; `/oauth2/token`) — https://www.dropbox.com/developers/paper-api-alpha
- Dropbox Community: PKCE refresh token in a JS PWA — https://community.dropbox.com/en/discussion/528083/need-help-generating-a-pkce-refresh-token-in-a-javascript-pwa
- Dropbox Community: `/list_folder/longpoll` and CORS **[community]** — https://community.dropbox.com/en/discussion/658737/list-folder-longpoll-not-working-with-cors

**Microsoft Graph**
- OneDrive API, CORS support — https://learn.microsoft.com/en-us/onedrive/developer/rest-api/concepts/working-with-cors
- Microsoft identity platform, OAuth 2.0 authorization code flow (incl. SPA redirect URIs, 24h refresh tokens) — https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-auth-code-flow
- How to handle third-party cookie blocking in browsers (SPAs) — https://learn.microsoft.com/en-us/entra/identity-platform/reference-third-party-cookies-spas

**Google**
- GIS, Use the token model — https://developers.google.com/identity/oauth2/web/guides/use-token-model
- GIS, Use Code Model — https://developers.google.com/identity/oauth2/web/guides/use-code-model
- OAuth 2.0 for Client-side Web Applications (no CORS on auth/revoke endpoints) — https://developers.google.com/identity/protocols/oauth2/javascript-implicit-flow
- OAuth 2.0 policies (embedded user-agents) — https://developers.google.com/identity/protocols/oauth2/policies

**OAuth / browser security**
- RFC 10017, OAuth 2.0 for Browser-Based Applications (BCP 212) — https://www.rfc-editor.org/rfc/rfc10017
- RFC 7636, PKCE — https://www.rfc-editor.org/rfc/rfc7636

**Browser storage**
- MDN, Origin private file system — https://developer.mozilla.org/en-US/docs/Web/API/File_System_API/Origin_private_file_system
- MDN, Storage quotas and eviction criteria — https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria
- WebKit, Updates to Storage Policy — https://webkit.org/blog/14403/updates-to-storage-policy/
- MDN BCD, `StorageManager` — https://github.com/mdn/browser-compat-data/blob/main/api/StorageManager.json
- MDN BCD, `FileSystemFileHandle` — https://github.com/mdn/browser-compat-data/blob/main/api/FileSystemFileHandle.json

**Kotlin / Compose Multiplatform**
- Kotlin, Stability of supported platforms — https://kotlinlang.org/docs/multiplatform/supported-platforms.html
- Kotlin/Wasm, supported versions and configuration (browser versions) — https://kotlinlang.org/docs/wasm-configuration.html
- Kotlin/Wasm overview — https://kotlinlang.org/docs/wasm-overview.html
- Kotlin, Overview of web targets (JS vs Wasm; compatibility mode) — https://kotlinlang.org/docs/web-overview.html
- What's new in Compose Multiplatform 1.9 (web Beta; accessibility gaps) — https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html
- Compose Multiplatform 1.9.0 — Compose for Web goes Beta — https://blog.jetbrains.com/kotlin/2025/09/compose-multiplatform-1-9-0-compose-for-web-beta/
- Present and Future of Kotlin for Web — https://blog.jetbrains.com/kotlin/2025/05/present-and-future-kotlin-for-web/
- web.dev, WasmGC baseline — https://web.dev/blog/wasmgc-wasm-tail-call-optimizations-baseline

**Lekto internal**
- `docs/adr/0001-vault-portability-is-capability-driven.md`
- `docs/adr/0002-no-backend-server.md`
- `docs/adr/0003-vault-record-per-file.md`
- `docs/research/cloud-sync-backends.md`
- `docs/research/android-first-stack.md`
