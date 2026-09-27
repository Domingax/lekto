# Dictionary & AI integration: what is possible with **no backend server**

**Date:** 2026-09-27
**Scope:** For Lekto (React/Vite/TypeScript web + Android via Capacitor; hard constraint: no backend server, no proxy the project operates), determine what is actually feasible client-side for (a) word-level dictionary lookups from WordReference / Reverso / Linguee / Google Translate, and (b) phrase-level BYOK LLM calls (OpenAI / Anthropic / Gemini / Ollama), plus secure key storage and streaming.
**Method:** Primary sources only — the services' own new/ToS/robots pages, official API docs, vendor SDK source, and **raw HTTP header evidence captured with `curl`** (OPTIONS preflight + GET/POST) from this host on 2026-09-27. Where a header could not be observed (bot/rate limits from a datacenter IP), that is stated explicitly rather than guessed. Every claim carries a URL; header captures are reproduced in the Appendix.

> **Headline:** None of WordReference, Reverso, Linguee, or the Google Translate *site* exposes an official client-side API, and all four forbid automated access in their terms. Their only honest no-server integration is **deep-linking out** (new tab / in-app browser) — not embedding, not scraping. Google's **official Cloud Translation v2 API does send permissive CORS headers** and can be called from the browser with a BYOK key, but Google explicitly warns against client-side keys. For BYOK LLM work, **OpenAI, Anthropic and Gemini all send working CORS headers and support SSE streaming**; Anthropic additionally *requires* the `anthropic-dangerous-direct-browser-access: true` header. **Ollama** works from the browser only when the app's origin is added to `OLLAMA_ORIGINS`. The real constraint is therefore **not CORS** — it is **API-key exposure**, which is a per-platform storage problem, not a network one.

---

## 0. How to read the CORS evidence

A cross-origin `fetch()` from Lekto's origin only succeeds if the response (and any preflight) carries a matching `Access-Control-Allow-Origin` (ACAO) and, for preflighted requests, the right `Access-Control-Allow-Headers`/`-Methods`. The captures below used `Origin: https://example.com`. Header **echo** (`access-control-allow-origin: https://example.com`) and **wildcard** (`*`) both permit browser calls; a preflight with **no** ACAO means the browser blocks the request.

Two caveats on the evidence:
- The capture host is a datacenter IP, so Google's undocumented translate endpoint returned **HTTP 429** (throttled) before headers could be read, and Reverso/WordReference served **bot challenges** (Cloudflare `cf-mitigated: challenge`; WordReference `418`). Those cells are marked "unverified from this host".
- Header evidence shows what a *browser may do*, not what is *permitted*. ToS is a separate axis and is treated separately.

---

## 1. Dictionary services

### 1.1 WordReference

**Official public API:** **None.** The historical docs page `https://www.wordreference.com/docs/api.aspx` now returns **404**, and `http://api.wordreference.com/` serves the ordinary dictionary site (verified 2026-09-27). There is no developer portal or published API.

**Terms of service.** The current ToS is explicit ([TermsOfService.aspx](https://www.wordreference.com/english/TermsOfService.aspx)):

> "We grant you a limited license to access and make personal use of this website. You are not allowed to download or modify it, collect users' content or information, or otherwise access WordReference, using automated means (such as harvesting bots, robots, spiders, or scrapers) without our prior permission. You are not allowed to make derivative works or use it for machine learning, AI or as a basis for your machine translation or other dictionary or its services. This may be done only with direct written consent from us."

So **scraping, embedding-as-data, and ML/AI use are all expressly prohibited** without written consent.

**CORS.** No `Access-Control-Allow-Origin` is emitted; unauthenticated automated requests get an anti-bot **HTTP 418** challenge page (`server: nginx`, body sets `nginx_wr_human=1`). With that cookie a normal page returns 200, but still no ACAO. → **not directly callable from the browser.**

**Framing.** With a browser cookie the 200 response carried **both**:
```
x-frame-options: SAMEORIGIN
content-security-policy: frame-ancestors 'self' https://wordreference.com https://*.wordreference.com
  https://lextutor.ca https://*.lextutor.ca https://cssps.gouv.qc.ca ... https://app.formative.com https://*.formative.com https://exam.net https://*.exam.net;
```
It allowlists a handful of named education domains; **Lekto is not among them** → **not embeddable**.

**Deep-link pattern.** Language-pair path, e.g. `https://www.wordreference.com/enfr/<word>` (English→French), `/fren/`, `/enes/`, `/ensv/` … (observed patterns on `https://www.wordreference.com/ensv/forumtitles/Office`, `https://www.wordreference.com/enes/non`). **Deep-link-only.**

### 1.2 Reverso Context

**Official public API:** **No self-serve/public API.** Reverso offers a **B2B "Corporate Translator" / enterprise API** only ([corporate-translation.reverso.com](https://www.corporate-translation.reverso.com/)). There is no documented public endpoint.

**Terms.** [Reverso – Terms and Conditions of Use](https://www.reverso.net/disclaimer.aspx?lang=EN), Article 4 (User Obligations), prohibits:
> "- integrate all or part of Reverso services into another service, whether free of charge or for a fee;
>  - use the services without the official user interface or while concealing copyright notices, logos, or trademarks;"

That directly forbids both **scraping** and **re-integrating the service into another UI** (which is what a translation panel is).

**CORS.** Automated requests hit a **Cloudflare challenge**: `HTTP/2 403`, `cf-mitigated: challenge`, `server: cloudflare`, plus `cross-origin-embedder-policy: require-corp`. No ACAO → **not browser-callable**.

**Framing.** `x-frame-options: SAMEORIGIN` on the challenged response → **not embeddable** (and the bot challenge would break an iframe anyway).

**Deep-link pattern.** `https://context.reverso.net/translation/<from>-<to>/<term>` e.g. `https://context.reverso.net/translation/english-french/hello`; spaces are `+` (e.g. `.../english-french/give+up`). **Deep-link-only.**

### 1.3 Linguee

**Official public API:** **None for Linguee.** Linguee is operated by **DeepL SE**; the commercial API sibling is the **DeepL API**, which is paid, server-oriented, and **blocks browsers** (see §2.4). No `linguee` JSON API is documented.

**Terms / robots.** Linguee's [`robots.txt`](https://www.linguee.com/robots.txt) is unusually blunt:
> "# In ANY CASE, you are NOT ALLOWED to train Machine Translation Systems on data crawled on Linguee. … None … We will take all legal measures against anyone training Machine Translation systems on data crawled from this website."

That is a crawl-policy signal, not a public licence, and it signals active anti-scraping intent.

**CORS.** `GET https://www.linguee.com/english-french/search?source=auto&query=hello` → `200` with only `access-control-expose-headers: Server-Timing, X-Trace-ID` and **no ACAO** → **not browser-callable** (you cannot read the HTML cross-origin).

**Framing.** No `X-Frame-Options` and no `frame-ancestors` CSP were observed on `/` or the search page → **technically frameable**, but (a) same-origin policy means Lekto cannot read or parse the framed document anyway, so it is display-only, (b) ToS/copyright make embedding a liability, and (c) DeepL/Linguee can add headers at any time. Treat as **deep-link-only**.

**Deep-link pattern.** `https://www.linguee.com/english-french/search?source=auto&query=<term>` (and localized hosts `linguee.fr`, `linguee.de`, …; canonical result pages `https://www.linguee.com/english-french/translation/<term>.html`). **Deep-link-only.**

### 1.4 Google Translate (site)

- **Official API:** Cloud Translation API — see §2.
- **Site framing:** `https://translate.google.com/?sl=en&tl=fr&text=hello&op=translate` returned `302` with `x-frame-options: SAMEORIGIN` and a CSP → **not embeddable**.
- **Deep-link pattern:** `https://translate.google.com/?sl=<src>&tl=<tgt>&text=<urlencoded>&op=translate`.

---

## 2. Google Translate specifically

### 2.1 Official Cloud Translation API — server-oriented, but v2 is CORS-open

Cloud Translation **v2** accepts a plain **API key** (`POST https://translation.googleapis.com/language/translate/v2`; the reference notes "A valid API key to handle requests for this API") and its REST reference also documents OAuth scopes for v3 ([Cloud Translation v2 REST reference](https://cloud.google.com/translate/docs/reference/rest/v2/translate)). Cloud Translation **v3** requires OAuth2/service-account credentials — no simple API key.

**Header evidence (primary):**
```
OPTIONS https://translation.googleapis.com/language/translate/v2?key=FAKE
  Origin: https://example.com
HTTP/2 200
access-control-allow-origin: https://example.com
access-control-allow-headers: content-type
access-control-allow-methods: DELETE,GET,HEAD,OPTIONS,PATCH,POST,PUT
access-control-max-age: 3600

GET .../v2?key=FAKE&q=hello&target=fr  (Origin: https://example.com)
HTTP/2 400
access-control-allow-origin: https://example.com
```
So **the official v2 endpoint does work from a browser origin** (a real key would return 200). That is a genuine, licensed, no-server path — with a caveat: Google's own guidance is that keys must not ship client-side (see §2.2 for the same warning applied to Gemini; API keys can additionally be restricted by HTTP referrer via [Google Cloud API-key restrictions](https://cloud.google.com/docs/authentication/api-keys)).

### 2.2 Undocumented `translate.googleapis.com/translate_a/single`

This is the endpoint behind the Google Translate web widget — **no terms, no SLA, no API key, no documented quota**. Header behaviour observed on 2026-09-27:

| URL | Result |
|---|---|
| `translate.googleapis.com/translate_a/single?client=gtx&…` | **HTTP 429** (throttled before headers) — CORS **unverified from this host** |
| `translate.googleapis.com/translate_a/t?client=dict-chrome-ex&…` | **200**, `access-control-allow-origin: *` |
| `clients5.google.com/translate_a/t?client=dict-chrome-ex&…` | **200**, `access-control-allow-origin: *` |
| `clients5.google.com/translate_a/single?client=gtx&…` | **HTTP 429** |

**Reading:** the `client=gtx` single endpoint could not be verified here; the `dict-chrome-ex` variant (used by Chrome's built-in dictionary) *is* CORS-open. Either way this is an **unsupported, undocumented interface with no ToS grant** — using it in shipped software is a liability and can break without notice. **Not recommended.**

### 2.3 Public site
Deep-link only (see §1.4); `X-Frame-Options: SAMEORIGIN` blocks embedding.

### 2.4 The DeepL warning (applies to "Linguee's API sibling")
DeepL's own docs are the clearest statement of policy for browser calls ([CORS requests](https://developers.deepl.com/docs/best-practices/cors-requests)):
> "The DeepL API does not allow calls directly from browser-based applications."

confirmed by header capture (`OPTIONS https://api-free.deepl.com/v2/translate` → `204` with **no ACAO**; `POST` → `403`). DeepL even ships a **local Node proxy** for development ([deepl-api-nodejs-proxy](https://github.com/DeepL/deepl-api-nodejs-proxy)) — i.e. the vendor's own answer is "run a proxy", which conflicts with Lekto's no-server rule unless the *user* runs it.

---

## 3. The viable integration set (no server)

Classification keys: **direct client call (CORS-OK)** · **iframe embeddable** · **deep-link-only** · **not usable**.

| Feature / provider | Feasible without server? | How | Evidence |
|---|---|---|---|
| WordReference word lookup | ✅ deep-link-only | Open `wordreference.com/<pair>/<word>` in new tab/in-app browser | No API, ToS bans automation; `X-Frame-Options: SAMEORIGIN` + CSP allowlist; no ACAO |
| Reverso Context lookup | ✅ deep-link-only | Open `context.reverso.net/translation/<pair>/<term>` | No public API; ToS Art. 4 bans integration; Cloudflare 403; no ACAO |
| Linguee lookup | ✅ deep-link-only | Open `linguee.com/english-french/search?query=` | No API; robots.txt anti-MT; no ACAO; frameable but unreadable (SOP) |
| Google Translate site | ✅ deep-link-only | Open `translate.google.com/?sl=…&tl=…&text=…` | `X-Frame-Options: SAMEORIGIN` |
| **Google Cloud Translation v2 (BYOK)** | ✅ **direct client call** | `POST translation.googleapis.com/language/translate/v2?key=…` | Preflight `ACAO` echo + `content-type` allowed; Google warns against client keys |
| Google `translate_a/single` (unofficial) | ⚠️ technically maybe, **not usable legally** | undocumented; CORS unverified | 429 here; no ToS/SLA |
| **OpenAI (BYOK)** | ✅ **direct client call** | `POST api.openai.com/v1/chat/completions`, `Authorization: Bearer` | Preflight `ACAO` echo, `authorization,content-type` allowed; GET → 401 w/ ACAO `*` |
| **Anthropic (BYOK)** | ✅ **direct client call** | `POST api.anthropic.com/v1/messages` + `anthropic-dangerous-direct-browser-access: true` | Preflight `ACAO: *`, header explicitly allowed |
| **Gemini (BYOK)** | ✅ **direct client call** | `POST generativelanguage.googleapis.com/...` + `x-goog-api-key` | Preflight `ACAO` echo, `x-goog-api-key,content-type` allowed |
| **Ollama (BYOK, local)** | ⚠️ conditional | `http://localhost:11434/api/chat`; add app origin to `OLLAMA_ORIGINS` | FAQ: default CORS = 127.0.0.1/0.0.0.0 only |
| DeepL API (BYOK) | ❌ not usable client-side | Server-only by policy | docs: "does not allow calls directly from browser-based applications" |
| Any dictionary **iframe embed** | ❌ not usable | — | All four set `X-Frame-Options` (WR/Reverso/GT), and SOP prevents reading framed content |
| Any dictionary **scrape** | ❌ not usable | — | ToS/robots prohibit automated access in all four |

**Smallest honest design:** **deep-link launcher for the four dictionaries + BYOK direct calls for word/phrase translation + BYOK LLM for phrases/paragraphs.** Do **not** build an iframe reader or a scraper. If real *inline* dictionary data is required later, use a **licensed API** (Google Cloud Translation v2, DeepL via user proxy, Merriam-Webster/Oxford APIs) — all of which are BYOK, and only Google v2 works without a proxy.

---

## 4. BYOK LLM browser calls

### 4.1 OpenAI
- **Browser calls: yes.** Preflight `OPTIONS api.openai.com/v1/chat/completions` → `200`, `access-control-allow-origin: https://example.com`, `access-control-allow-headers: authorization,content-type`, `access-control-allow-methods: GET, OPTIONS, POST`, `access-control-max-age: 86400`; `GET /v1/models` → `401` with `access-control-allow-origin: *`.
- The official SDK gates this: browsers are "disabled by default … Enable browser support by explicitly setting `dangerouslyAllowBrowser` to `true`" ([openai-node README](https://github.com/openai/openai-node#readme)). OpenAI's help centre is unambiguous that exposing keys client-side is unsafe ([Best Practices for API Key Safety](https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety)).
- **Streaming: yes, SSE** `stream: true` ([Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create); README streaming example).

### 4.2 Anthropic
- **Browser calls: yes, with a required opt-in header.** Preflight `OPTIONS api.anthropic.com/v1/messages` → `200`, `access-control-allow-origin: *`, `access-control-allow-headers: x-api-key,anthropic-version,anthropic-dangerous-direct-browser-access,content-type`, `access-control-allow-methods: DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT`, `access-control-allow-credentials: true`, `access-control-max-age: 600`, `vary: Origin, Access-Control-Request-Headers`.
- The SDK sets `anthropic-dangerous-direct-browser-access: true` only when `dangerouslyAllowBrowser` is true ([anthropic-sdk-typescript `src/client.ts` L641/L1577](https://github.com/anthropics/anthropic-sdk-typescript/blob/main/src/client.ts)); the README states browsers are disabled by default with the same warning ([README](https://github.com/anthropics/anthropic-sdk-typescript#readme)).
- **Streaming: yes, SSE** `stream: true`, `content_block_delta`/`text_delta` events ([Streaming messages](https://platform.claude.com/docs/en/build-with-claude/streaming)).

### 4.3 Google Gemini
- **Browser calls: yes.** Preflight `OPTIONS generativelanguage.googleapis.com/v1beta/models` → `200`, `access-control-allow-origin: https://example.com`, `access-control-allow-headers: x-goog-api-key,content-type`; `GET /v1beta/models?key=FAKE` → `400` with ACAO echo.
- **But Google explicitly discourages it:** the Gemini API-key docs state *"Never expose keys client-side in production: … To secure client-side apps, run a backend proxy server"* ([Using Gemini API keys](https://ai.google.dev/gemini-api/docs/api-key)). Same page notes the 2026 migration from standard keys to **auth keys** bound to a service account and "fast-acting leaked key enforcement".
- **Streaming: yes, SSE.** Current Interactions API streams with `stream: true` and SSE events ([Streaming interactions](https://ai.google.dev/gemini-api/docs/streaming)); the legacy `generateContent` path uses `:streamGenerateContent?alt=sse`.

### 4.4 Ollama
- **Browser calls: conditional.** Local server at `http://localhost:11434`. FAQ: *"Ollama allows cross-origin requests from `127.0.0.1` and `0.0.0.0` by default. Additional origins can be configured with `OLLAMA_ORIGINS`"* ([FAQ](https://docs.ollama.com/faq)). A packaged Android app whose WebView origin is `https://localhost` / `capacitor://localhost`, or a web app on `http://localhost:5173` in dev, is **not** automatically in the default list — the user must set `OLLAMA_ORIGINS` (or the app must document it).
- **Streaming: yes.** `/api/chat` and `/api/generate` stream by default (`"stream": true`) as NDJSON ([API reference](https://docs.ollama.com/api)).

### 4.5 BYOK LLM summary table

| Provider | Direct browser calls | Special requirement | Streaming |
|---|---|---|---|
| OpenAI | ✅ CORS-OK | SDK `dangerouslyAllowBrowser: true`; key is exposed by design | SSE `stream: true` |
| Anthropic | ✅ CORS-OK | **`anthropic-dangerous-direct-browser-access: true`** header; SDK `dangerouslyAllowBrowser: true` | SSE `stream: true` |
| Gemini | ✅ CORS-OK | `x-goog-api-key` header; Google recommends a proxy; auth-key migration | SSE `stream: true` |
| Ollama | ⚠️ only for allowed origins | Add app origin to `OLLAMA_ORIGINS` | NDJSON, stream default on |
| DeepL (for reference) | ❌ | server-only by policy | n/a |

**Browser streaming mechanics:** all four are consumable with `fetch()` + `response.body.getReader()` / `TextDecoderStream` and SSE/NDJSON line parsing; there is no need for a server, and no need for `EventSource` (which cannot POST or set auth headers).

---

## 5. Secret storage per platform

### 5.1 Web — there is no OS keychain
A browser origin has **no equivalent of Android Keystore / iOS Keychain**. Honest options, strongest to weakest:

| Option | Persistence | Threat model | Notes |
|---|---|---|---|
| **In-memory only** (JS variable / `sessionStorage`) | tab session only | safest at rest; gone on close | `sessionStorage` is per-tab and cleared on close; best default for a "re-enter once per session" UX |
| **WebCrypto + IndexedDB** | across reloads | protects against casual disk inspection / accidental vault copies; **does not** stop XSS or a determined local attacker | Generate an **AES-GCM** key with `extractable: false`, store the `CryptoKey` (structured-cloneable) in IndexedDB, store only ciphertext; the raw key bytes are never readable from JS ([MDN SubtleCrypto](https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto), [MDN IndexedDB](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API)) |
| **Plaintext `localStorage` / IndexedDB** | across reloads | any XSS or local read = full key theft | convenient but dishonest to call "secure" |
| **`<input type="password">` only** | not persisted | user re-enters | good for the "no persistence" mode |

Key reality to state in the UI: **encryption-at-rest in the browser cannot defend against XSS**, because the code that decrypts the key is the same origin that an attacker would control. WebCrypto's non-extractable key raises the bar (the raw key cannot be exfiltrated as bytes) but the key can still *be used*. Also note browser storage is evictable ([Storage quotas and eviction](https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria)), so never treat it as durable.

### 5.2 Android via Capacitor — real Keystore/Keychain plugins exist
| Plugin | Backing store | Status |
|---|---|---|
| **`@aparajita/capacitor-secure-storage`** | iOS: **system Keychain** (optional iCloud sync); Android: **AES-GCM with a key generated by the Android KeyStore**, stored in app-private `SharedPreferences` | Actively maintained, **Capacitor 8**; 169★; web implementation is **unencrypted `localStorage` "for debugging purposes only"** ([repo](https://github.com/aparajita/capacitor-secure-storage)) |
| `capacitor-secure-storage-plugin` | iOS SwiftKeychainWrapper; Android | Community; check Capacitor-version compat ([npm](https://www.npmjs.com/package/capacitor-secure-storage-plugin)) |
| `@capawesome/capacitor-secure-preferences` | native secure preferences | Commercial-adjacent Capawesome plugin ([docs](https://capawesome.io/docs/sdks/capacitor/secure-preferences/)) |
| Ionic Secure Storage | SQLite + full encryption | Paid/enterprise ([Capacitor Preferences docs](https://capacitorjs.com/docs/apis/preferences)) |
| `@capacitor/preferences` | iOS `UserDefaults` / Android `SharedPreferences` | ⚠️ **NOT secure** — plaintext; do not use for API keys ([docs](https://capacitorjs.com/docs/apis/preferences)) |

**Recommendation:** use a Keystore/Keychain plugin on Android, and pick a web strategy from §5.1. Critically, **the web fallback of these plugins is itself unencrypted `localStorage`** — so on web the plugin does not solve the problem; implement the web path separately.

### 5.3 How comparable apps do it
- **Obsidian.** Historically plugins stored API keys in plaintext in `data.json` inside the vault. Obsidian added a **SecretStorage** API (v1.11.4) — a central secret store so values are *not* written to `data.json`; the docs describe the old behaviour directly: *"When plugins store secrets directly in `data.json`, … Secrets are stored in plaintext alongside other plugin data."* ([Store secrets](https://docs.obsidian.md/plugins/guides/secret-storage)). Obsidian's SecretStorage is OS-keychain-backed where available. **Takeaway: keep keys out of the synced vault, exactly as Lekto's brief requires.**
- **Joplin.** E2EE protects data **on the sync target and in transit**, not the local profile: *"Joplin supports end-to-end encryption (E2EE)… It prevents potential eavesdroppers … from being able to access the data"* — the local SQLite DB is not encrypted at rest ([E2EE](https://joplinapp.org/help/apps/sync/e2ee/)). Sync credentials have historically lived in the settings table.
- **Standard Notes** (from the companion storage report): local DB encrypted with the account/passcode key; web "no account + no passcode = unencrypted local DB" ([Standard Notes](https://standardnotes.com/help/79/how-does-standard-notes-encrypt-data-on-my-device)).

---

## 6. CORS-safe alternatives when a provider blocks browsers

The good news is **the three major cloud LLM providers do not block browsers** (verified in §4), so Lekto needs no proxy for OpenAI/Anthropic/Gemini. Anti-CORS providers (DeepL, and any future one) offer these no-server patterns:

1. **Prefer a CORS-OK provider.** Given BYOK, route through OpenAI/Anthropic/Gemini (all CORS-OK) rather than a provider that blocks browsers. This is the cleanest answer.
2. **Ollama-local.** Fully local, no network exposure, CORS configurable via `OLLAMA_ORIGINS` ([FAQ](https://docs.ollama.com/faq)). Best for privacy-first users; only constraint is that the app's origin must be added.
3. **User-run local proxy (power-user path).** For DeepL/etc., the vendor's own recommended dev pattern is a local proxy ([DeepL node proxy](https://github.com/DeepL/deepl-api-nodejs-proxy)). Lekto ships no server, but can *document* that a power user may run one on `localhost`; the app would then call `http://localhost:PORT`. This reintroduces a process the user runs — acceptable only as an explicit, opt-in escape hatch, never as a requirement.
4. **Provider browser flags.** `dangerouslyAllowBrowser` (OpenAI/Anthropic SDKs) and Anthropic's `anthropic-dangerous-direct-browser-access: true` header are the *official* opt-ins for browser use. They enable the call; they do **not** mitigate key exposure — that is what §5 is for.
5. **Licensed official APIs that are CORS-OK.** Google Cloud Translation v2 is the only *official, licensed, no-server* translation API verified to work from the browser (§2.1). Use it if inline word translation is ever required.

Do **not** rely on: undocumented Google `translate_a` endpoints (§2.2), scraping (§1), or iframe-scraping (SOP makes it impossible).

---

## Recommended translation-panel integration design

**Principle: capability-driven, BYOK, no iframes, no scraping. The panel works fully with zero configuration (dictionary deep links) and upgrades to inline translation only when the user supplies a key.**

1. **Single word — default (zero config): deep-link launcher.**
   Render one download/open action per service, opening the canonical URL in a **new tab / Capacitor in-app browser** (`@capacitor/browser` or `InAppBrowser`), never an iframe:
   - WordReference: `https://www.wordreference.com/<src><tgt>/<word>`
   - Reverso: `https://context.reverso.net/translation/<src>-<tgt>/<word>`
   - Linguee: `https://www.linguee.com/<src>-<tgt>/search?source=auto&query=<word>`
   - Google Translate: `https://translate.google.com/?sl=<src>&tl=<tgt>&text=<word>&op=translate`
   Also offer Web Speech TTS inline (already in scope).
   *Why:* it is the only integration all four terms permit, and it is 100% server-free. Do not advertise these as "integrated dictionaries" — call them "dictionary shortcuts".

2. **Single word — optional inline translation (BYOK).**
   When a key is present, fetch directly:
   - `POST https://translation.googleapis.com/language/translate/v2?key=…` (CORS-OK; restrict the key by HTTP referrer on web), and/or
   - the user's LLM with a "translate this word, give POS + 2 short senses" prompt.
   Show the dictionary deep links **alongside** the inline result, so the user still gets the rich reference view.

3. **Phrase / paragraph — BYOK LLM, streaming.**
   Provider adapter seam with per-provider auth quirks encapsulated:
   - OpenAI: `Authorization: Bearer`, body `stream:true` → SSE.
   - Anthropic: `x-api-key`, `anthropic-version: 2023-06-01`, **`anthropic-dangerous-direct-browser-access: true`**, `stream:true` → SSE.
   - Gemini: `x-goog-api-key` header, `stream:true` (Interactions API) → SSE.
   - Ollama: `POST http://localhost:11434/api/chat`, `stream` default on → NDJSON; surface an actionable error if the origin is not in `OLLAMA_ORIGINS`.
   Parse streams with `fetch` + `ReadableStream`; render tokens incrementally. Fall back to the §1 deep links when no key is configured (matches the product brief's "AI is optional / progressive disclosure").

4. **Key storage.**
   - Android: a Keystore/Keychain plugin (`@aparajita/capacitor-secure-storage`; never `@capacitor/preferences`).
   - Web: default to **session-only (in-memory/`sessionStorage`)**; offer an explicit "remember on this device" that uses **WebCrypto AES-GCM + non-extractable key in IndexedDB**, with copy that says plainly this protects at-rest copies but not XSS.
   - **Never write keys to the vault** (enforce at the vault-store seam), and mask inputs + scrub logs.

5. **Do not build:** iframe embeds of any dictionary (blocked/SOP), any scraper, or the undocumented Google `translate_a` endpoints. If inline dictionaries become a hard requirement, budget for a **licensed API** (Cloud Translation v2 is the only no-server one today) or a DeepL-style local proxy as a power-user option.

---

## Appendix — raw header captures (2026-09-27)

```
# OpenAI — browser allowed
OPTIONS https://api.openai.com/v1/chat/completions   Origin: https://example.com
  -> 200  access-control-allow-origin: https://example.com
          access-control-allow-headers: authorization,content-type
          access-control-allow-methods: GET, OPTIONS, POST
          access-control-max-age: 86400
GET     https://api.openai.com/v1/models            Origin: https://example.com
  -> 401  access-control-allow-origin: *

# Anthropic — browser allowed with the direct-browser header
OPTIONS https://api.anthropic.com/v1/messages
  -> 200  access-control-allow-origin: *
          access-control-allow-headers: x-api-key,anthropic-version,
            anthropic-dangerous-direct-browser-access,content-type
          access-control-allow-methods: DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT
          access-control-allow-credentials: true
          vary: Origin, Access-Control-Request-Headers

# Gemini — browser allowed
OPTIONS https://generativelanguage.googleapis.com/v1beta/models   Origin: https://example.com
  -> 200  access-control-allow-origin: https://example.com
          access-control-allow-headers: x-goog-api-key,content-type
GET     https://generativelanguage.googleapis.com/v1beta/models?key=FAKE
  -> 400  access-control-allow-origin: https://example.com

# Google Cloud Translation v2 — browser allowed (official, keyed)
OPTIONS https://translation.googleapis.com/language/translate/v2?key=FAKE   Origin: https://example.com
  -> 200  access-control-allow-origin: https://example.com
          access-control-allow-headers: content-type
GET     https://translation.googleapis.com/language/translate/v2?key=FAKE&q=hello&target=fr
  -> 400  access-control-allow-origin: https://example.com

# DeepL — browser BLOCKED
OPTIONS https://api-free.deepl.com/v2/translate   (auth header requested)
  -> 204  (no access-control-allow-origin)
POST    https://api-free.deepl.com/v2/translate   Origin: https://example.com
  -> 403  (no access-control-allow-origin)

# WordReference — bot challenge; not frameable
GET https://www.wordreference.com/enfr/hello
  -> 418  (nginx anti-bot page)
GET (with Cookie: nginx_wr_human=1)
  -> 200  x-frame-options: SAMEORIGIN
          content-security-policy: frame-ancestors 'self' https://wordreference.com https://*.wordreference.com
            https://lextutor.ca ... https://app.formative.com ... https://exam.net ...;

# Reverso — Cloudflare challenge; not frameable
GET https://context.reverso.net/translation/english-french/hello
  -> 403  cf-mitigated: challenge  server: cloudflare
          x-frame-options: SAMEORIGIN   cross-origin-embedder-policy: require-corp

# Linguee — no ACAO; no framing headers
GET https://www.linguee.com/english-french/search?source=auto&query=hello
  -> 200  access-control-expose-headers: Server-Timing, X-Trace-ID
          (no access-control-allow-origin, no x-frame-options, no frame-ancestors)

# Google Translate site — not frameable
GET https://translate.google.com/?sl=en&tl=fr&text=hello&op=translate
  -> 302  x-frame-options: SAMEORIGIN

# Undocumented Google translate endpoints
GET translate.googleapis.com/translate_a/single?client=gtx&...   -> 429 (headers unread)
GET translate.googleapis.com/translate_a/t?client=dict-chrome-ex&... -> 200 access-control-allow-origin: *
GET clients5.google.com/translate_a/t?client=dict-chrome-ex&...      -> 200 access-control-allow-origin: *
GET clients5.google.com/translate_a/single?client=gtx&...            -> 429 (headers unread)
```

---

## References

**Dictionary terms, APIs and framing**
- WordReference Terms of Service — https://www.wordreference.com/english/TermsOfService.aspx
- WordReference legacy API page (404) — https://www.wordreference.com/docs/api.aspx
- WordReference deep-link examples — https://www.wordreference.com/enfr/hello · https://www.wordreference.com/enes/non
- Reverso Terms & Conditions of Use — https://www.reverso.net/disclaimer.aspx?lang=EN
- Reverso Context (about / deep-link host) — https://context.reverso.net/translation/about · https://context.reverso.net/translation/english-french/hello
- Reverso Corporate / enterprise API — https://www.corporate-translation.reverso.com/
- Linguee robots.txt (anti-crawl / anti-MT clause) — https://www.linguee.com/robots.txt
- Linguee operator (DeepL SE) — https://www.linguee.com/pages/privacy.html

**Google Translate / Cloud Translation**
- Cloud Translation v2 REST reference (API key) — https://cloud.google.com/translate/docs/reference/rest/v2/translate
- Cloud Translation setup — https://docs.cloud.google.com/translate/docs/setup
- Google Cloud API-key restrictions — https://cloud.google.com/docs/authentication/api-keys
- Gemini "Using API keys" (never expose client-side; auth keys) — https://ai.google.dev/gemini-api/docs/api-key
- DeepL "CORS requests" (browser calls forbidden) — https://developers.deepl.com/docs/best-practices/cors-requests
- DeepL local Node proxy — https://github.com/DeepL/deepl-api-nodejs-proxy

**LLM APIs**
- Anthropic API overview — https://platform.claude.com/docs/en/api/overview
- Anthropic streaming (SSE) — https://platform.claude.com/docs/en/build-with-claude/streaming
- Anthropic TS SDK (`anthropic-dangerous-direct-browser-access`, `dangerouslyAllowBrowser`) — https://github.com/anthropics/anthropic-sdk-typescript/blob/main/src/client.ts · https://github.com/anthropics/anthropic-sdk-typescript#readme
- OpenAI Chat Completions (`stream`) — https://platform.openai.com/docs/api-reference/chat/create
- OpenAI Node SDK (`dangerouslyAllowBrowser`) — https://github.com/openai/openai-node#readme
- OpenAI API key safety — https://help.openai.com/en/articles/5112595-best-practices-for-api-key-safety
- Gemini streaming — https://ai.google.dev/gemini-api/docs/streaming
- Ollama API (`stream`) — https://docs.ollama.com/api
- Ollama FAQ (`OLLAMA_ORIGINS`) — https://docs.ollama.com/faq

**Secret storage / comparable apps**
- MDN SubtleCrypto — https://developer.mozilla.org/en-US/docs/Web/API/SubtleCrypto
- MDN IndexedDB — https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API
- MDN Storage quotas and eviction — https://developer.mozilla.org/en-US/docs/Web/API/Storage_API/Storage_quotas_and_eviction_criteria
- `@aparajita/capacitor-secure-storage` (Keychain / Keystore) — https://github.com/aparajita/capacitor-secure-storage
- `capacitor-secure-storage-plugin` — https://www.npmjs.com/package/capacitor-secure-storage-plugin
- `@capawesome/capacitor-secure-preferences` — https://capawesome.io/docs/sdks/capacitor/secure-preferences/
- Capacitor Preferences (not secure) — https://capacitorjs.com/docs/apis/preferences
- Obsidian SecretStorage — https://docs.obsidian.md/plugins/guides/secret-storage
- Joplin E2EE (sync, not local at rest) — https://joplinapp.org/help/apps/sync/e2ee/
- Standard Notes device encryption — https://standardnotes.com/help/79/how-does-standard-notes-encrypt-data-on-my-device

**Companion report**
- `docs/research/cross-platform-file-access.md` (vault storage seams; key-storage constraints referenced here)
