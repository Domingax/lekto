# The dictionary pipeline builds a committed language set into one release

`tools/dictionaries` shipped one hard-coded artefact: an EN→FR pack and, later,
`difficulty-en`. The app's **Languages** surface lists what the **Language
manifest** publishes (parent #141), and several artefacts must be offered —
English into French and Spanish, and a **Difficulty table** per reading language.
The transform and the workflow were fixed to `lang_code == "en"` with French
translations.

Only **English-source packs are derivable from this source**: Wiktextract's
`translations` are extracted from the English Wiktionary's Translations sections,
which exist for English words. The French extract carries **none** — 403,269
French entries, zero translations — so a reading language that is not English
gets a **Difficulty table** (wordfreq needs no pack) but no dictionary pack. The
committed set therefore lists English→French and English→Spanish packs, and
English, Spanish and French difficulty tables.

The reading and translation languages become **parameters** of the transform, and
the set of artefacts the pipeline publishes becomes a **committed file**:
`tools/dictionaries/languages.json` lists the (Reading language × Translation
language) pairs and the reading languages that get a **Difficulty table**. One run
reads that set and builds every artefact into **one** release.

The release is one because the app fetches its catalogue and every artefact it
names from the same alias: `releases/latest/download/language-manifest.json` and
`releases/latest/download/<asset>`. `latest` resolves to the newest release, and
an asset URL only resolves for an asset on that release, so the manifest and
everything it names must be attached to one release. A per-language release would
make the other languages' assets unreachable from `latest`; the alternative — a
manifest carrying each artefact's absolute URL — is a format change the app would
have to read (below, rejected for now). The workflow therefore builds the whole
set per run, as it already rebuilt the EN→FR pack monthly.

A **Difficulty table** borrows the reading language's pack form index when the run
built a pack for that language — the first configured pair, which the app's fixed
French translation target makes the one its tokeniser uses — and keys by the
normalised surface form otherwise (ADR-0029): the tokeniser knows a lemma only
where a pack supplies one, so Spanish, with no pack in the starting set, is keyed
by surface form.

We rejected **a manifest carrying absolute URLs** (per-artefact releases), which
would let one language be added without republishing the others but needs a
manifest-format bump and an app that reads the URL rather than building it; **a
rolling release mutated in place**, which avoids the rebuild but keeps no dated
snapshot and must stay `latest`; and **a GitHub Actions matrix** over the set,
which would still have to merge one manifest and one release, moving the same work
into CI without removing it.

**Consequences**: adding a pair or a difficulty is one entry in
`languages.json`, and the next run (or the monthly schedule) publishes a fresh,
coherent release; the asset names are date-free so their URLs stay stable. The
app, its manifest format and the pack format are unchanged — only the producer
gained a second reading of the same source and a second difficulty language. The
sanity gate's previous-count is looked up per reading language, so a language
newly added to the set is not gated on its first run. The release no longer
carries dated asset copies beside the date-free ones: the release tag dates the
snapshot, and every asset the manifest names is date-free. The difficulty build
streams the source once per pack, so the run reads the ~3 GiB source once per
configured pair.
