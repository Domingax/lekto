# Open-licensed dictionaries: inline lookup with **no backend server**

**Date:** 2026-09-27
**Scope:** For Lekto (React/Vite/TypeScript web + Android via Capacitor; hard constraint: **no backend server**), determine whether an inline word-lookup experience can be built legally and server-free from **open-licensed dictionary data** — live or offline — for an EN↔FR learner, language-generically.
**Method:** Primary sources only — official API docs and policies, the licence texts/copyright pages, the project repos, and the dump/download indexes themselves. Live endpoints and their CORS headers were probed with `curl` from this host on 2026-09-27; every header capture is reproduced in the Appendix. File sizes are the byte counts returned by `Content-Length` from the canonical download hosts on that date.
**Companion:** `docs/research/dictionary-and-ai-integration.md` established that WordReference, Reverso, Linguee and Google Translate **cannot** be embedded or called client-side. This report explores the open-licence alternative.

> **Headline:** Wiktionary is the only source that is simultaneously **open-licensed (CC BY-SA 4.0 + GFDL)**, **rich** (definitions, senses, translations, IPA, etymology, inflections), **directly callable from the browser** (MediaWiki's `origin=*` CORS grant, verified), and **available offline** as machine-readable extracts (Wiktextract/Kaikki). The catch is size and licence obligation: the full English extract is ~3.3 GB JSONL (523 MB gz), the French-language extract is ~584 MB JSONL (58 MB gz), and any shipped copy is a **ShareAlike** distribution that must attribute Wiktionary contributors and be relicensed compatibly. FreeDict gives a genuinely tiny (~450 KB total) GPL-2.0 baseline for EN↔FR but only ~8.5–8.8 k headwords. Everything else in §4 is either server-dependent (`dictionaryapi.dev`, MyMemory, LibreTranslate public instance, Apertium APY) or English-only semantic data (WordNet/OMW). **The credible server-free design is: a build-time-derived Wiktionary pack (SQLite) bundled/downloaded into the vault + the live Wiktionary API as enrichment/fallback.**

---

## 1. Wiktionary as a live API (MediaWiki Action API)

Wiktionary is a MediaWiki wiki; its API is the **MediaWiki Action API** at `/w/api.php` on each language edition (e.g. `en.wiktionary.org`, `fr.wiktionary.org`). The whole of §1 was verified live on 2026-09-27.

### 1.1 Anonymous CORS works — with one non-obvious preflight rule

MediaWiki's documented rule: unauthenticated cross-origin requests set the query parameter **`origin=*`**; MediaWiki then replies with `Access-Control-Allow-Origin: *` and `Access-Control-Allow-Credentials: false`, treating the caller as logged out ([API:Cross-site requests](https://www.mediawiki.org/wiki/API:Cross-site_requests)).

Verified on both editions:

```
GET https://en.wiktionary.org/w/api.php?action=query&meta=siteinfo&format=json&origin=*
  Origin: https://example.com
HTTP/2 200
access-control-allow-origin: *
access-control-allow-credentials: false
```

Same on `fr.wiktionary.org`. A plain `GET` is a CORS "simple request", so no preflight occurs.

**The preflight gotcha (verified):** if you send a **non-safelisted header** — most importantly `Api-User-Agent` (see §1.5) — the browser issues a **preflight `OPTIONS`**, and MediaWiki only emits the CORS headers if `origin=*` is present **in the query string of the preflight URL**. The docs state this explicitly: *"these parameters must be included in any pre-flight request, and so should be included in the query string portion of the request URI even for POST requests."* Verified:

```
# With origin=* in the query -> preflight succeeds
OPTIONS https://en.wiktionary.org/w/api.php?action=query&meta=siteinfo&format=json&origin=*
  Access-Control-Request-Method: GET
  Access-Control-Request-Headers: api-user-agent
-> 200
   access-control-allow-origin: *
   access-control-allow-headers: api-user-agent
   access-control-allow-methods: POST, GET, HEAD

# Same OPTIONS on the bare path (no origin=* in query) -> NO CORS headers
OPTIONS https://en.wiktionary.org/w/api.php   (+ Access-Control-Request-Headers: api-user-agent)
-> 200   (no access-control-allow-origin)
```

So: **always put `origin=*` in the URL**, even for OPTIONS. The API responses also carry `x-frame-options: DENY` and a `default-src 'self'` CSP — irrelevant to `fetch`, but they confirm Wiktionary is not an iframe target (unlike the commercial sites, this doesn't matter because the data is licensed).

### 1.2 Endpoints and exact request patterns

All patterns below were executed successfully. `formatversion=2` yields the modern, array-shaped JSON.

**(a) Title search** — find candidate entries / spelling suggestions:

```
GET https://en.wiktionary.org/w/api.php
  ?action=query&list=search&srsearch=hello
  &srlimit=10&format=json&formatversion=2&origin=*
```

Returns `query.searchinfo.totalhits` plus ranked `query.search[].title/snippet/pageid/size/wordcount`. e.g. `hello` → 1,696 hits; `fr.wiktionary` `manger` → 8,106 hits.

**(b) Plain-text extract (definitions in prose)** — the quickest "definition text":

```
GET https://en.wiktionary.org/w/api.php
  ?action=query&prop=extracts&explaintext=1
  &titles=hello&format=json&formatversion=2&origin=*
```

⚠ **Verified gotcha:** adding `exintro=1` returns an **empty** extract for Wiktionary pages, because the page body begins with a language heading (`== English ==`) and *all* content is "after the intro". Use extracts **without** `exintro`. `explaintext=1` on `hello` returned 3,814 characters of readable prose (etymology + definitions). Extracts are produced by the [TextExtracts](https://www.mediawiki.org/wiki/Extension:TextExtracts) extension, which warns *"HTML may be malformed and/or unbalanced and may omit inline images"* — and, confirmed here, **it strips templates**, so it drops **translations and IPA** (which live in `{{tt}}` / `{{IPA}}` templates). Extracts are prose-only.

**(c) Full wikitext** — everything: translations, IPA, etymology, inflection templates:

```
GET https://en.wiktionary.org/w/api.php
  ?action=parse&page=hello&prop=wikitext&format=json&origin=*

# or, equivalently:
GET .../w/api.php?action=query&prop=revisions&rvprop=content&rvslots=main
      &titles=hello&format=json&formatversion=2&origin=*
```

Verified: `parse&prop=wikitext` on `hello` returns 39,188 characters including the `Translations` section (`{{trans-top|greeting}} … * French: {{tt+|fr|bonjour}} …`), the `Pronunciation` section (`{{IPA|en|/hɛˈloʊ/}}`, `{{audio|en|En-uk-hello.ogg}}`, `{{rhymes|en|…}}`), and `Etymology`. `prop=revisions` returned the same 39,188-char wikitext.

**(d) Section index** — cheap table of contents (used to jump straight to "French" on an English page, or to a translations block):

```
GET .../w/api.php?action=parse&page=hello&prop=sections&format=json&origin=*
```

Returns every heading with `line`, `anchor`, `byteoffset`. (Note: `prop=sections` is deprecated in favour of `prop=tocdata`.)

**(e) Batch titles** — one request for many words (the etiquette-recommended pattern):

```
.../w/api.php?action=query&prop=extracts&explaintext=1&titles=hello|world
  &format=json&formatversion=2&origin=*
```

Verified: returns both pages in one response.

**(f) French edition** works identically. `fr.wiktionary` `parse&page=mangeais&prop=wikitext` returned the inflection template `{{fr-verbe-flexion|manger|ind.i.1s=oui|ind.i.2s=oui}}`, the definition *"Première personne du singulier de l'imparfait de l'indicatif de [[manger]]"*, pronunciation `{{pron|mɑ̃.ʒɛ|fr}}`, and audio links — i.e. **the inflected form points back to its lemma** even in wikitext.

### 1.3 What rich data is reachable per word

| Data | Reachable live? | Where | Verified example |
|---|---|---|---|
| Definitions / senses | ✅ | `prop=extracts` (prose) or wikitext | `hello` → 3,814-char plain extract |
| **Translations** | ✅ via wikitext; ✅ via Wiktextract (§2) | `{{trans-top}}` / `{{tt}}` templates | `hello` → `French: bonjour` |
| **IPA / pronunciation** | ✅ via wikitext | `{{IPA|en|…}}`, `{{audio|en|…}}`, `{{rhymes}}` | `hello` → `/hɛˈloʊ/`, `En-uk-hello.ogg` |
| Etymology | ✅ | `{{etymology}}` / prose | `hello` → "First attested in 1826…" |
| Inflections | ✅ | fr `{{fr-verbe-flexion}}`, en conjugation tables | `mangeais` → lemma `manger` |
| Categories / POS | ✅ via wikitext and `parse&prop=sections` | headings + `[[Category:…]]` | `hello` → `== English ==` → `=== Interjection ===` |

The catch: **extracts lose translations and IPA**; if you want them you must parse wikitext (or use Wiktextract offline, §2). Parsing MediaWiki wikitext in the browser is a real cost — this is the strongest argument for *pre-extracting* with Wiktextract and shipping the result.

### 1.4 Rate limits and etiquette

From [Wikimedia APIs/Rate limits](https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits) (new in 2026):

| Client class | Limit |
|---|---|
| Requests with no identifying info (bare IP) | **10 req/min** |
| **Browser, unauthenticated** (our case) | **200 req/min** |
| Unauthenticated bot with compliant User-Agent | 200 req/min |
| Authenticated established editor | 2,000 req/min |

Plus, from [API:Etiquette](https://www.mediawiki.org/wiki/API:Etiquette):

- **"limit the number of concurrent requests to 3 or fewer"** (rate-limit page, Best practices).
- **There is no hard speed limit on *read* requests**, but *"be considerate and try not to take a site down… Making your requests in series rather than in parallel… should result in a safe request rate."*
- **Batch**: use `titles=A|B|C` and generators instead of one request per word.
- **Cache** responses aggressively.
- Prefer **GET** for reads (cacheable and hits a nearer datacentre).
- On `429`/`503`, honour `Retry-After`, else wait ≥5 s or exponential-back off.
- Non-interactive clients should send **`maxlag`**; interactive (user-waiting) lookups arguably should not.
- Read-only actions are **not** action-rate-limited (the `ratelimited` error concerns writes/edits).

### 1.5 User-Agent policy (and why it triggers a preflight)

Wikimedia requires a meaningful User-Agent for all requests and blocks non-descriptive/generic agents ([User-Agent policy](https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy)). Browsers force their own UA and JS cannot change it; the policy explicitly accommodates this: *"Browser-based applications written in JavaScript are typically forced to send the same User-Agent header as the browser… such applications are encouraged to include the `Api-User-Agent` header."* The docs show the exact `fetch` pattern:

```js
fetch( url, { headers: new Headers( { 'Api-User-Agent': 'Lekto/0.1 (<repo-url>)' } ) } )
```

Because `Api-User-Agent` is not a CORS-safelisted header, **adding it makes every call preflighted** — hence §1.1's rule that the URL must contain `origin=*`.

**Usage guidelines** ([Policy:…API Usage Guidelines](https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_API_Usage_Guidelines)): operators may not *"sublicense, lease, assign, or guarantee the availability or functionality of a Wikimedia Foundation-managed API to any third party"*, and may not white-label in a way that obscures that Wikimedia is the source. Attaching a licence notice and naming Wiktionary as the source is required anyway by CC BY-SA.

### 1.6 Licence of live data

Every Wiktionary entry is dual-licensed **CC BY-SA 4.0 + GFDL** ([Wiktionary:Copyrights](https://en.wiktionary.org/wiki/Wiktionary:Copyrights)); the dumps page repeats this ([dumps legal](https://dumps.wikimedia.org/legal.html)). Using the live API does **not** avoid the licence: displaying definitions is redistribution of CC BY-SA material and triggers attribution + ShareAlike obligations.

---

## 2. Wiktionary as offline data (dumps, Wiktextract / Kaikki)

### 2.1 Raw Wikimedia dumps

Snapshot listings and byte counts verified 2026-09-27.

| File | Size (bytes) | Human |
|---|---|---|
| `enwiktionary-latest-pages-articles.xml.bz2` | 1,632,298,458 | ~1.52 GiB |
| `frwiktionary-latest-pages-articles.xml.bz2` | 880,553,059 | ~839 MiB |
| `enwiktionary-latest-all-titles-in-ns0.gz` | 28,040,879 | ~27 MiB |
| `frwiktionary-latest-all-titles-in-ns0.gz` | 19,680,372 | ~19 MiB |

- Hosts: [enwiktionary/latest](https://dumps.wikimedia.org/enwiktionary/latest/), [frwiktionary/latest](https://dumps.wikimedia.org/frwiktionary/latest/).
- **Cadence:** *"These snapshots are provided monthly."* The latest completed runs are dated `20260901` for both editions ([backup-index](https://dumps.wikimedia.org/backup-index.html), which also logs `enwiktionary/20260901: Dump complete`).
- **Download etiquette:** the index warns *"we have rate limited downloaders and we are capping the number of per-ip connections to 3."*
- **Deprecation note:** the index states the XML dumps *"are deprecated, please use MediaWiki Content File Exports"*.
- **Licence:** CC BY-SA 4.0 + GFDL ([dumps legal](https://dumps.wikimedia.org/legal.html)).

These are **raw wikitext** — a ~1.5 GiB bz2 is not bundleable into an app, and parsing MediaWiki wikitext at runtime is a large engineering cost. The extract route (§2.2) is the practical one.

### 2.2 Wiktextract / Kaikki machine-readable extracts

**Wiktextract** is the tool that turns Wiktionary wikitext into structured JSONL: *"one object per line"*, each object containing `word`, `lang`/`lang_code`, `pos`, `senses`, `forms`, `sounds`, `translations`, `etymology_text`, `categories`, etc. It is **MIT-licensed software** ([wiktextract repo](https://github.com/tatuylonen/wiktextract)), while its **output is derived from Wiktionary, hence CC BY-SA 4.0 / GFDL** (the repo states *"Certain files under tests/ are under Wiktionary license (CC-BY-SA or GFDL at your choice)"*).

**Kaikki.org** publishes the extracted data ([raw data page](https://kaikki.org/dictionary/rawdata.html); extracted 2026-09-25 from the 2026-09-02 enwiktionary dump):

| Dataset | Uncompressed | Compressed | Notes |
|---|---|---|---|
| **enwiktionary raw Wiktextract JSONL** (all languages, English glosses) | ~23.9 GB | 2.8 GB `.gz` | hundreds of languages in one file |
| Kaikki **English** (enwiktionary words whose language is English) | 3,335,555,506 B (~3.11 GiB) | 523,337,972 B (~499 MiB) | per-language post-processed file |
| Kaikki **French** (enwiktionary words whose language is French) | 583,674,544 B (~557 MiB) | **58,232,854 B (~55 MiB)** | best EN→FR gloss source |
| French **edition** extract (`downloads/fr/fr-extract.jsonl`, definitions in French) | ~6.4 GB | 699.9 MB `.gz` | fr.wiktionary itself |
| German edition extract (for comparison) | ~2.9 GB | 294.3 MB `.gz` | |

- **Update cadence:** the raw data page says *"updated regularly (usually at least once a week)"* (2026-09-25 build shown).
- **Coverage:** the English-edition extract has *"data for hundreds of languages, and has glosses and other metadata in English"*.
- **Formats:** JSONL (one JSON object per line); `.gz` variants.
- **Attribution/citation:** Kaikki asks to cite Ylonen, *Wiktextract: Wiktionary as Machine-Readable Structured Data* (LREC 2022).
- ⚠ **Caveat:** the per-language post-processed files (e.g. `kaikki.org/dictionary/French/kaikki.org-dictionary-French.jsonl`) are flagged on the raw-data page as **[DEPRECATED], will be removed in the near future**, to be replaced by the raw Wiktextract downloads. Their site footer also notes the post-processed data *"has been post-processed and various details… removed, some information disambiguated, and additional data merged from other sources"*. Pin a copy rather than hot-linking.

**Can a single-language extract be bundled / downloaded on demand?** Yes:

- The **French** per-language JSONL is **~55 MiB gzip** — downloadable-on-demand into the vault, and trimmable at build time (drop categories, rare POS, multiword constructions) to a smaller SQLite pack.
- The **English** per-language JSONL is ~499 MiB gz — too big to bundle blindly, but a build-time filter that keeps only entries carrying a French translation, plus lemma/IPA/forms, shrinks it enormously.
- The bare **headword lists** (`*-all-titles-in-ns0.gz`, ~19–27 MiB) give a cheap lemma index without any parsing.

### 2.3 Attribution implications for a shipped app

CC BY-SA 4.0 requires, at minimum: **attribute** the source, **link to the licence**, **indicate modifications**, keep the **ShareAlike** licence on adaptations, and not imply endorsement ([Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); [Wiktionary:Copyrights](https://en.wiktionary.org/wiki/Wiktionary:Copyrights)). Wikimedia's [Developer App Guidelines](https://foundation.wikimedia.org/wiki/Legal:Wikimedia_Developer_App_Guidelines) add app-specific rules:

- Naming Wiktionary as the source of content is fine without a trademark licence (*"this app uses content from Wikipedia"* pattern); **using a Wikimedia wordmark as the app's name/icon requires a trademark licence**.
- Show a clear attribution and *"indicate any changes you made to the content"*.
- Don't imply partnership/endorsement.

Because Lekto is itself open-source and non-commercial, ShareAlike is a **non-blocker** — but the shipped data files must remain CC BY-SA, and an in-app "Attributions / Licences" screen is mandatory, not optional.

---

## 3. FreeDict

[FreeDict](https://freedict.org/) publishes *"truly free"* bilingual dictionaries in a format-neutral **TEI P5 XML** source, exported to **StarDict**, **DICT** and **SLOB** ([downloads](https://freedict.org/downloads/); [about](https://freedict.org/about/)). The hand-written dictionaries live at [github.com/freedict/fd-dictionaries](https://github.com/freedict/fd-dictionaries); a separate `download.freedict.org/generated` set holds imported ones (often from other sources with different licences).

**EN↔FR offerings (verified on the downloads page):**

| Dictionary | Version | Headwords | Download size |
|---|---|---|---|
| English → French (`eng-fra`) | 0.1.6 | 8,799 | 218,960 B (StarDict `.tar.xz`) |
| French → English (`fra-eng`) | 0.4.1 | 8,505 | 226,632 B (StarDict `.tar.xz`) |

**Licence:** the `eng-fra` source carries a **GNU GPL v2** `COPYING` file ([raw COPYING](https://raw.githubusercontent.com/freedict/fd-dictionaries/master/eng-fra/COPYING)) — note FreeDict's own repo root has no single LICENSE; licences are **per-dictionary** and some generated ones differ, so check each.

**Quality/limits:** tiny and hand-curated (~8.5–8.8 k lemmas for FR); no IPA, no audio, little inflection or example data; the `eng-deu`/`deu-eng` pairs are much larger (460 k / 517 k headwords) but those are the imported, differently-licensed ones. For an MVP it is a genuinely free, truly offline **fallback**, not a competitor to Wiktionary.

**Verdict:** ✅ offline-bundleable, ✅ client-safe, ❌ too small to be the primary EN↔FR dictionary. GPL-2 is copyleft: keep the data files as a separate, GPL-licensed aggregation (Lekto is open source, so this is fine, but don't ship it purely under MIT).

---

## 4. Other open / free options

### 4.1 `dictionaryapi.dev` (Free Dictionary API)

- **Endpoint:** `GET https://api.dictionaryapi.dev/api/v2/entries/en/<word>` — **English only** ([site](https://dictionaryapi.dev/), [repo](https://github.com/meetDeveloper/freeDictionaryAPI)).
- **Licence:** the project is **GPL-3.0**; the *data* is scraped — the repo's own topics include `scraper` and `google-dictionary`. That is a legal red flag: scraped Google content is not open-licensed.
- **Server:** **required**, and it is a single hobby server whose README says usage has ramped to *">10 million requests per month"* and that AWS costs make it *"difficult… to keep the server running."* On 2026-09-27 the live endpoint returned **HTTP 522** (Cloudflare origin timeout) — i.e. down.
- **CORS:** the 522 error carried `x-frame-options: SAMEORIGIN` and no usable ACAO; the normal service has historically emitted permissive CORS, but a down/overloaded single instance is not a dependable dependency.
- **Verdict:** ❌ not server-free, ❌ not EN↔FR, ⚠ data provenance unclear. Do not build on it.

### 4.2 WordNet / Open Multilingual WordNet (OMW)

- **Princeton WordNet:** *"WordNet® is unencumbered, and may be used in commercial applications"* under the permissive **WordNet License** ([license page](https://wordnet.princeton.edu/license-and-commercial-use)). English-only.
- **OMW** aggregates 60 wordnets for 49 languages and is *"open… freely used, modified, and shared by anyone for any purpose"* ([omwn.org](https://omwn.org/)); the [omw-data](https://github.com/omwn/omw-data) repo packages OMW 2 in GWA format.
- **French wordnet = WOLF**, licensed **CeCILL-C**, per `index.toml`: `license = "http://www.cecill.info/licenses/Licence_CeCILL-C_V1-en.html"`.
- **What it gives:** synonym sets, hypernyms/hyponyms and cross-language *concept* links — useful for thesaurus-like features and fuzzy matching, **not** learner-oriented translation glosses. OMW also embeds Wiktionary/CLDR-derived data for 150+ languages, so the same attribution discipline applies.
- **Verdict:** ✅ offline-bundleable, ✅ client-safe, but ⚠ not a translation dictionary. A nice-to-have enrichment layer, not the inline lookup.

### 4.3 Apertium

- The **EN↔FR pair exists**: [apertium-fra-eng](https://github.com/apertium/apertium-fra-eng), **GPL-3.0**, containing a bilingual XML dictionary `apertium-fra-eng.fra-eng.dix` (**1,411,918 B ≈ 1.4 MB** — verified). The repo is tagged `apertium-incubator` (8 stars) — low maturity, rule-based MT, not a lexicographic dictionary.
- **APY public API** (`apertium.org/apy`) is **CORS-open** (`Access-Control-Allow-Origin: *` — verified) but returned `400 "That pair is not installed"` for `eng|fra` on 2026-09-27, and it is **server-based**.
- **Offline:** the `.dix` is plain XML you *could* parse, but you'd be reimplementing an MT engine; Apertium's own morphology data (GPL) is more useful as a lemmatiser source than as a learner dictionary.
- **Verdict:** ⚠ GPL-3 data downloadable, but low-quality pair and no ready JS engine. Not recommended as the primary source.

### 4.4 LibreTranslate

- **Licence:** the server software is **AGPL-3.0** ([repo](https://github.com/LibreTranslate/LibreTranslate)); the engine is [Argos Translate](https://github.com/argosopentech/argos-translate) (also open).
- **CORS:** the public instance at `libretranslate.com` sends `Access-Control-Allow-Origin: *` and allows `Authorization, Content-Type` (verified 2026-09-27) — so it *is* callable from the browser.
- **But:** it *is a server* — either the project's (rate-limited, no SLA, and using it is a network dependency) or one the user self-hosts (which reintroduces a process, forbidden by Lekto's "no server, ever" rule unless the user opts in). It is **sentence MT**, not a dictionary, and **not offline** in a browser (Argos is Python; there is no Android/Web embedding path today).
- **Verdict:** ❌ not server-free, ❌ not offline. Optional user-run escape hatch at best.

### 4.5 MyMemory

- **CORS:** `GET https://api.mymemory.translated.net/get?q=hello&langpair=en|fr` returned `access-control-allow-origin: *` (verified) — browser-callable.
- **Server:** **required** (their infrastructure).
- **Limits:** *"Free, anonymous usage is limited to 5000 chars/day"*; 50,000 chars/day with an email; 150,000 only for whitelisted established CAT tools; contributions (`set`) are unlimited ([usage limits](https://mymemory.translated.net/doc/usagelimits.php), [API spec](https://mymemory.translated.net/doc/spec.php)). It is a **translation memory** (sentence pairs), not a lemma dictionary, and requires accepting their ToS.
- **Verdict:** ❌ not server-free, ❌ not offline, ⚠ quota + ToS. Not suitable as the core lookup.

---

## 5. Inflection / lemma data — can a bundled lexicon replace a lemmatiser?

**Yes, for lookup keying — verified directly in the Wiktextract output.**

- **French:** a lemma entry carries a `forms` array. For `manger` ([Kaikki French `manger.jsonl`](https://kaikki.org/dictionary/French/meaning/m/ma/manger.html)) we observed forms including `mangeais` tagged `["first-person","imperfect","indicative","singular"]` (and the same surface form tagged second-person), `mangeait`, `mangeâmes`, `mangerai`, plus gerund/participle/imperative/subjunctive forms and multiword constructions like `"avoir + past participle"` tagged `multiword-construction`.
- **The inflected entry independently points at its lemma.** The `mangeais` entry ([Kaikki](https://kaikki.org/dictionary/French/meaning/m/ma/mangeais.html)) has `form_of: [{ "word": "manger" }]` with tags `first-person`, `second-person`, `singular`, `imperfect`, `indicative`, `form-of`, and gloss *"first/second-person singular imperfect indicative of manger"*. So the extract carries **both directions**: lemma→forms and form→lemma.
- **English:** `ran` carries `form_of: [{"word":"run"}]` with tag `form-of, past` (plus a second, colloquial/participial sense) — verified via the Kaikki English entry.

**Practical consequence.** A bundled extract can serve as **both the dictionary and the lemmatiser**: build a reverse index `surface_form → lemma` from the `forms` arrays (or the `form_of` entries) at build time, and Lekto can key vocabulary by lemma without shipping a statistical lemmatiser. The reverse index for French is what makes "click `mangeais`, save `manger`" possible fully offline.

Two caveats to encode:

1. **Ambiguity.** A surface form can map to multiple lemmas; pick by POS/candidate scoring, or fall back to the raw form. (`mangeais` → `manger` is clean; many English forms are not.)
2. **Noise.** Wiktextract emits multiword constructions (e.g. `"avoir + past participle"`, `"present indicative of avoir + past participle"`). Filter these out when building the index — keep only single-token surface forms with real tags.

This is a genuine **gain over the commercial sites**: a lemma↔form lexicon is exactly what a reading app needs to unify inflected tokens into one vocabulary card, and it is available here, offline, for free.

---

## 6. Practical assessment

### 6.1 The smallest credible offline inline-dictionary design (EN↔FR)

**Tier 0 — bundled baseline (always offline, zero download).**
FreeDict `eng-fra` + `fra-eng` StarDict/TEI: **~446 KB total**, GPL-2.0, ~8.5–8.8 k headwords. Convert to SQLite at build time. This guarantees *some* inline answer with no network and no vault download. Acceptable as a starter, not as the product.

**Tier 1 — the product dictionary (download-once into the vault).**
Generate a **derived pack from Kaikki's French extract** (`kaikki.org-dictionary-French.jsonl`, ~557 MiB raw / **~55 MiB gz**), trimmed at build time to: lemma, POS, English glosses/senses, IPA, audio URL, `forms`, and French→English links. Expect roughly **10–30 MiB gz** after trimming. Ship it as SQLite:
- **Web:** [SQLite WASM](https://sqlite.org/wasm/doc/trunk/persistence.md) with the **OPFS** VFS (`opfs-sahpool` when you don't want COOP/COEP headers; `opfs` when you do) so the pack lives in origin-private storage and survives reloads. Note the docs' warning that OPFS storage can be evicted and that Safari <17 needs `opfs-sahpool`/WASMFS.
- **Android (Capacitor):** ship the pack in app assets and/or copy it into the vault directory; query with the same SQLite WASM build in the WebView, or a native SQLite plugin.
- **Optional reverse (EN→FR) pack:** filter the English extract to entries with a French translation and keep only lemma + French gloss + IPA + forms.

**Tier 2 — live enrichment/fallback.**
The `en.wiktionary.org` / `fr.wiktionary.org` Action API with `origin=*` (and `Api-User-Agent` in the URL, §1.1), **≤3 concurrent**, cached, `Retry-After`/exponential backoff. Use it for words missing from the pack and for the full reference view (all senses, etymology, translations into other languages).

**Licence notice (required):** an in-app "Attributions / Licences" entry stating that dictionary content is derived from **Wiktionary contributors, licensed CC BY-SA 4.0 and GFDL**, with a link to the licence and to Wiktionary, a note that Lekto **modified** the data (extracted, trimmed, reformatted), and a ShareAlike statement. Do not use Wikimedia wordmarks in the app name or icon; do not imply endorsement. FreeDict data (if bundled) is separately credited under **GPL-2.0**.

### 6.2 Where the live API degrades gracefully offline

| Capability | Online (live API) | Offline (bundled pack) |
|---|---|---|
| Core definition / gloss | ✅ | ✅ (pack) |
| IPA / audio URL | ✅ | ✅ if stored in the pack |
| Inflections → lemma | ✅ (wikitext) or via pack | ✅ via reverse `forms` index |
| Translations | ✅ | ✅ for the pack's languages |
| Etymology / full sense list | ✅ | ⚠ only what the pack retained |
| Unknown / rare word | ✅ | ❌ → show "not in offline dictionary; connect to look up" + deep links |
| Fresh/updated entries | ✅ | ❌ until the pack is rebuilt/downloaded |

The graceful pattern: **consult the pack first (instant, offline); hit the API only on a miss or on an explicit "more details" action; cache every API result back into the vault; never block reading on the network.**

### 6.3 Genuinely lost vs the four commercial sites, and what is gained

**Lost (vs WordReference / Reverso / Linguee / Google Translate):**

- **Bilingual example sentences in context** — Reverso Context and Linguee are built on large parallel corpora; Wiktionary has quotations and some usage examples, but not the "how is this actually used in real translated sentences" view.
- **Collocations, idioms, and phrase-level nuance** — the commercial sites' editorial layer.
- **Curated register/regional nuance and long-tail coverage** as a learner-facing product.
- **High-quality phrase/paragraph translation** — Wiktionary gives glosses, not fluent translation (that stays in the BYOK-LLM lane).
- **Some audio** — Wiktionary has many `{{audio}}` files but coverage is uneven per word.

**Gained:**

- **Offline, by construction** — data in the vault; no dependency on anyone's server.
- **No ToS/automation risk** — the content is explicitly licensed for reuse and modification; nothing to scrape.
- **No per-provider rate limits or framing blocks** — only Wikimedia's own published limits (200 req/min browser, ≤3 concurrent), and those apply *only* when you choose to go online.
- **Structured data the commercial sites don't expose** — **lemma↔inflected-form mappings**, **IPA**, **etymology**, **category/POS tags**, **translations into many languages** — all machine-readable.
- **Language-generic** — the same design works for any pair Wiktionary covers (hundreds), not just EN↔FR.

---

## Verdict table

| Source | Licence | Client-callable without a server? | Offline-bundleable? | Coverage EN↔FR | Verdict |
|---|---|---|---|---|---|
| **Wiktionary live API** (`en`/`fr.wiktionary.org`) | CC BY-SA 4.0 + GFDL | ✅ **Yes** — `origin=*`, CORS `ACAO: *` verified; preflight needs `origin=*` in query | ❌ (it's live) | ✅ Exceptional (defs, translations, IPA, etymology, inflections) | **Recommended — live enrichment/fallback.** Attribute + ShareAlike. |
| **Wiktextract / Kaikki French extract** | Data CC BY-SA 4.0 (+GFDL); tool MIT | n/a (a file) | ✅ **Yes** — ~557 MiB raw / **~55 MiB gz** | ✅ Excellent (English glosses for French words + forms) | **Recommended — the offline product pack.** Trim → SQLite. |
| **Wiktextract / Kaikki English extract** | as above | n/a | ✅ Yes — 3.11 GiB raw / 499 MiB gz | ✅ (EN→FR via `translations`) but huge; needs build-time filtering | Use filtered; don't ship raw. |
| **Wikimedia raw XML dumps** | CC BY-SA 4.0 + GFDL | n/a | ⚠ Technically, but ~839 MiB–1.52 GiB bz2 + wikitext parsing | ✅ | Not practical to bundle; source for extracts only. |
| **FreeDict `eng-fra` / `fra-eng`** | **GPL-2.0** (per-dict; verify) | n/a | ✅ **Yes** — ~446 KB total | ⚠ ~8.5–8.8 k headwords; no IPA/inflections | **Recommended as tiny baseline/fallback.** Copyleft data. |
| **`dictionaryapi.dev`** | GPL-3.0 code; data **scraped (Google)** | ⚠ HTTP only (single hobby server) | ❌ | ❌ **English-only** | **Avoid** — server-dependent, down (522) on 2026-09-27, provenance unclear. |
| **Princeton WordNet** | WordNet License (permissive, commercial OK) | n/a | ✅ Yes | ❌ English-only, no translations | Enrichment only. |
| **OMW (French = WOLF)** | Open; WOLF = **CeCILL-C** (per-wordnet) | n/a | ✅ Yes | ⚠ Semantic links, not learner glosses | Enrichment only (synonyms/thesaurus). |
| **Apertium `fra-eng`** | **GPL-3.0** | ⚠ APY public API CORS-open, but `eng|fra` **not installed** (400) | ✅ `.dix` ~1.4 MB download, but no JS engine | ⚠ Incubator pair, rule-based MT; low quality | Not recommended as primary. |
| **LibreTranslate** | **AGPL-3.0** software | ✅ Public instance CORS-open | ❌ (self-host = a server) | ✅ EN↔FR (MT, not dictionary) | Not server-free/offline; optional user-run. |
| **MyMemory** | Service ToS; CORS-open `ACAO: *` | ⚠ (their server) | ❌ | ⚠ TM sentence pairs | Not server-free; 5 k chars/day anonymous. |
| **Commercial four (WR/Reverso/Linguee/GT)** | Proprietary / anti-automation | ❌ | ❌ | ✅ Context/example richness | **Lost feature set** — deep-link only. |

---

## Recommended inline dictionary design (no server)

1. **Data provenance.** Build the pack at release time from the **Kaikki/Wiktextract French extract** (derived from `enwiktionary`, CC BY-SA 4.0). Keep the build reproducible: record the source dump date, the Kaikki/Wiktextract commit, and the trim rules.
2. **Pack contents.** Per lemma: `lemma`, `pos`, ordered English glosses, IPA, audio URL(s), `forms[]` (for the reverse index), and `translations[]` (to build the EN→FR direction). Strip categories, multiword constructions, and non-lemma noise. Emit **SQLite** with a `lemma` index and a `forms(surface → lemma)` index.
3. **Storage.**
   - **Web:** SQLite WASM over **OPFS** (`opfs-sahpool` if you cannot set COOP/COEP; `opfs`/`opfs-wl` otherwise). Accept that OPFS is origin-private and evictable — treat the pack as a **cache that can be re-downloaded**, and verify with `sqlite3_js_kvs`-style checks.
   - **Android:** bundle a baseline pack in assets; store the downloaded pack in the **vault** (so it is portable/syncable if the user wishes) and open it with the same WASM build or a native SQLite plugin.
4. **Lookup pipeline:** token → reverse `forms` index → lemma → pack query. Show glosses instantly. If absent, show "not in offline dictionary" **plus** the existing deep links **plus**, if online, a live Wiktionary lookup.
5. **Live tier:** `en.wiktionary.org`/`fr.wiktionary.org` with `action=query&prop=extracts` for prose and `action=parse&prop=wikitext` for the full entry (translations/IPA/etymology). Always include **`origin=*` in the URL**; optionally set **`Api-User-Agent`** (which forces the preflight to carry `origin=*`). Cap concurrency at **≤3**, cache every response in the vault, honour `Retry-After`, exponential back-off.
6. **Attribution (mandatory):** an "Attributions / Licences" screen naming **Wiktionary contributors (CC BY-SA 4.0 / GFDL)**, linking the licence and Wiktionary, stating the data was **modified** (extracted → trimmed → SQLite) and remains **ShareAlike**; separately credit **FreeDict** (GPL-2.0) if bundled; never use Wikimedia marks in the name/icon; state non-affiliation.
7. **Do not ship:** `dictionaryapi.dev` (server + scraped provenance), MyMemory (server + quota), a bundled LibreTranslate server, or a full raw Wiktextract file. Do not parse raw wikitext at runtime on the critical path — pre-extract it.
8. **Escape hatch (optional):** if a user *wants* richer EN↔FR translation now, that is the **BYOK LLM** lane already specified in `docs/research/dictionary-and-ai-integration.md`; the open dictionary gives the offline, licence-clean **reference** layer.

**If licence purity matters more than richness:** ship only FreeDict (~446 KB, GPL-2.0) offline and rely on deep links + live Wiktionary for depth. **If richness matters more:** ship the trimmed Kaikki French pack (CC BY-SA) and accept ShareAlike + attribution. Both are legal, server-free, and offline-capable.

---

## Appendix A — raw header captures (2026-09-27)

```
# CORS grant on both editions — simple GET
GET https://en.wiktionary.org/w/api.php?action=query&meta=siteinfo&format=json&origin=*
  Origin: https://example.com
-> 200  access-control-allow-origin: *
        access-control-allow-credentials: false
        access-control-expose-headers: MediaWiki-API-Error, Retry-After, ...
        x-frame-options: DENY
GET https://fr.wiktionary.org/w/api.php?action=query&meta=siteinfo&format=json&origin=*
-> 200  access-control-allow-origin: *
        access-control-allow-credentials: false

# Preflight — MUST include origin=* in the query for CORS headers to appear
OPTIONS https://en.wiktionary.org/w/api.php?action=query&meta=siteinfo&format=json&origin=*
  Access-Control-Request-Method: GET
  Access-Control-Request-Headers: api-user-agent
-> 200  access-control-allow-origin: *
        access-control-allow-headers: api-user-agent
        access-control-allow-methods: POST, GET, HEAD
        access-control-allow-credentials: false
OPTIONS https://en.wiktionary.org/w/api.php            (no origin=* in query)
-> 200  (no access-control-allow-origin)

# dictionaryapi.dev — observed DOWN (single hobby server)
GET https://api.dictionaryapi.dev/api/v2/entries/en/hello
-> 522  content-type: text/plain; charset=UTF-8
        x-frame-options: SAMEORIGIN

# MyMemory — CORS-open, but server-dependent
GET https://api.mymemory.translated.net/get?q=hello&langpair=en|fr
-> 200  access-control-allow-origin: *
        content-type: application/json

# Apertium APY — CORS-open, but eng|fra pair not installed
GET https://apertium.org/apy/translate?langpair=eng|fra&q=hello
-> 400  Access-Control-Allow-Origin: *
        Access-Control-Allow-Methods: GET,POST,OPTIONS
        {"status":"error","code":400,"message":"Bad Request",
         "explanation":"That pair is not installed"}

# LibreTranslate public instance — CORS-open, but it is a server
OPTIONS https://libretranslate.com/translate
  Origin: https://example.com
  Access-Control-Request-Method: POST
  Access-Control-Request-Headers: content-type
-> 200  access-control-allow-origin: *
        access-control-allow-headers: Authorization, Content-Type
        access-control-allow-methods: GET, POST
        access-control-allow-credentials: true
```

## Appendix B — verified extract examples

```
# FR->EN definition + lemma link (Wiktextract/Kaikki, French)
mangeais  pos=verb
  form_of: [{"word": "manger"}]
  tags: [first-person, form-of, imperfect, indicative, second-person, singular]
  gloss: "first/second-person singular imperfect indicative of manger"
  ipa: /mɑ̃.ʒɛ/   audio: LL-Q150 (fra)-Pamputt-mangeais.wav

# lemma -> all inflected forms (same source)
manger  pos=verb
  forms: mange / manges / mangeons / mangez / mangent / mangeais / mangeait /
         mangions / mangiez / mangeaient / mangeai / mangeas / mangea /
         mangerai / mangeras / mangera / ... / mange / mangeons / mangez ...
  sounds: ["mɑ̃ʒ", ...]

# EN lemma -> inflected form (Wiktextract/Kaikki, English)
ran  pos=verb
  form_of: [{"word": "run"}]  tags: [form-of, past]

# Live API: translations and IPA are in wikitext, not in plain extracts
en.wiktionary "hello" wikitext        -> 39,188 chars (includes Translations + IPA)
en.wiktionary "hello" explaintext     ->  3,814 chars (prose only; templates dropped)
en.wiktionary "hello" extracts&exintro=1 -> "" (EMPTY — page starts with a heading)
```

---

## References

**Wiktionary / MediaWiki API**
- API:Cross-site requests — https://www.mediawiki.org/wiki/API:Cross-site_requests
- API:Etiquette — https://www.mediawiki.org/wiki/API:Etiquette
- Wikimedia APIs/Rate limits — https://www.mediawiki.org/wiki/Wikimedia_APIs/Rate_limits
- Extension:TextExtracts (caveats) — https://www.mediawiki.org/wiki/Extension:TextExtracts
- Wikimedia Foundation User-Agent policy — https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_User-Agent_Policy
- Wikimedia Foundation API Usage Guidelines — https://foundation.wikimedia.org/wiki/Policy:Wikimedia_Foundation_API_Usage_Guidelines
- Wikimedia Developer App Guidelines — https://foundation.wikimedia.org/wiki/Legal:Wikimedia_Developer_App_Guidelines
- Wiktionary:Copyrights — https://en.wiktionary.org/wiki/Wiktionary:Copyrights

**Dumps, Wiktextract, Kaikki**
- Wikimedia dumps legal/licensing — https://dumps.wikimedia.org/legal.html
- Wikimedia backup index (cadence) — https://dumps.wikimedia.org/backup-index.html
- enwiktionary latest dump — https://dumps.wikimedia.org/enwiktionary/latest/
- frwiktionary latest dump — https://dumps.wikimedia.org/frwiktionary/latest/
- Wiktextract (repo, MIT) — https://github.com/tatuylonen/wiktextract
- Kaikki raw data downloads — https://kaikki.org/dictionary/rawdata.html
- Kaikki French dictionary — https://kaikki.org/dictionary/French/
- Kaikki English dictionary — https://kaikki.org/dictionary/English/
- Kaikki example entries (`manger`, `mangeais`) — https://kaikki.org/dictionary/French/meaning/m/ma/manger.html · https://kaikki.org/dictionary/French/meaning/m/ma/mangeais.html
- Ylonen, *Wiktextract* (LREC 2022) — http://www.lrec-conf.org/proceedings/lrec2022/pdf/2022.lrec-1.140.pdf

**FreeDict**
- FreeDict downloads — https://freedict.org/downloads/
- FreeDict about — https://freedict.org/about/
- fd-dictionaries repo — https://github.com/freedict/fd-dictionaries
- FreeDict `eng-fra` COPYING (GPL-2.0) — https://raw.githubusercontent.com/freedict/fd-dictionaries/master/eng-fra/COPYING

**Other sources**
- dictionaryapi.dev — https://dictionaryapi.dev/ · repo https://github.com/meetDeveloper/freeDictionaryAPI
- Open Multilingual WordNet — https://omwn.org/ · https://github.com/omwn/omw-data · `index.toml` (WOLF = CeCILL-C) — https://raw.githubusercontent.com/omwn/omw-data/main/index.toml
- Princeton WordNet licence — https://wordnet.princeton.edu/license-and-commercial-use
- Apertium EN↔FR pair (GPL-3.0) — https://github.com/apertium/apertium-fra-eng · wiki https://wiki.apertium.org/wiki/Apertium
- LibreTranslate (AGPL-3.0) — https://github.com/LibreTranslate/LibreTranslate
- MyMemory API spec — https://mymemory.translated.net/doc/spec.php · usage limits — https://mymemory.translated.net/doc/usagelimits.php
- SQLite WASM persistent storage (OPFS/kvvfs) — https://sqlite.org/wasm/doc/trunk/persistence.md

**Companions**
- `docs/research/dictionary-and-ai-integration.md` (the four commercial sites; BYOK LLM)
- `docs/research/cross-platform-file-access.md` (vault / OPFS seams)
- `docs/adr/0002-no-backend-server.md` (the hard constraint)

---

### Unverified / flagged

- **Kaikki per-language post-processed files are marked DEPRECATED** on the raw-data page; the exact removal date is not stated. Pin a dated copy.
- **Kaikki post-processed data** merges *"additional data from other sources"* (per its footer); the precise extra sources are not enumerated there, so treat the pack's licence as CC BY-SA/GFDL inherited from Wiktionary, and re-verify before adding further merges.
- **FreeDict licences are per-dictionary**; the repo root has no single LICENSE. `eng-fra` is GPL-2.0 (verified), but the `download.freedict.org/generated` sets differ — check each.
- **`dictionaryapi.dev` was unreachable (522)** during capture; its steady-state CORS/data behaviour is inferred from its docs/repo, not re-observed live.
- **Apertium's public APY** did not have `eng|fra` installed on 2026-09-27; pair availability on the public instance may change.
- **Wikimedia rate limits** are described as *"new in 2026"* and *"subject to experimentation and change"*.
- The `.dix` file size (1,411,918 B) is a raw-git byte count, not a release artifact size; no headword count was published for the pair.
