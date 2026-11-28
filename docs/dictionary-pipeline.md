# Dictionary pipeline

`tools/dictionaries` is a standalone JVM CLI that turns Kaikki's raw Wiktextract
extract for each **Reading language** into that language's **trimmed SQLite
dictionary packs** and its **Difficulty table**. The build is driven by a
committed language set, `tools/dictionaries/languages.json`: the
(Reading language × Translation language) pairs to build and the reading
languages that get a table.

The pipeline is published by the reusable workflow
`.github/workflows/dictionary-data.yml`. The data repository
(`Domingax/lekto-data`) holds only its triggers. The application's
`releases/latest` is its latest version, never a data snapshot.

## Commands

```sh
./gradlew :tools:dictionaries:check          # unit tests, golden, ktlint and detekt
./gradlew :tools:dictionaries:run --args="--input raw.jsonl.gz \
  --languages-config languages.json --output-dir out \
  --frequency en=wordfreq-en.tsv.gz --frequency es=wordfreq-es.tsv.gz \
  --source-url <url> --source-sha256 <hex> --extraction-date <YYYY-MM-DD>"
```

The CLI streams the raw `.jsonl[.gz]` once per **Reading language**, keeping the
`lang_code == "<reading>"` entries that carry a `<translation>` translation,
dropping multi-word surfaces, and normalising every key with the same rule as
`core`'s `normaliseSurface` (NFC + lower case). It writes a read-only SQLite file
whose `user_version` is the pack format version, beside a `NOTICE` (attribution)
and a `manifest.json` (provenance and counts).

## Input model

One Wiktionary edition is processed per **Reading language**. The edition yields:

- one shared **form index** for that language: the surface→lemma table built from
  entries carrying at least one translation into a configured target;
- one content file per target, in the pack's fixed order.

The shared index keeps every pack and table of a reading language identical. A
catalogue run groups the pairs by reading language, so one edition pass yields
every pack of that language and its **Difficulty table**. The table is keyed by
the same index as the packs, not by whichever pair came first (ADR-0045).

## Build stages

### Extraction

```sh
./gradlew :tools:dictionaries:run --args="--input fr-extract.jsonl.gz \
  --reading-language fr --extract-target en --extract-target es \
  --output-dir intermediate \
  --source-url <url> --source-sha256 <hex> --extraction-date <YYYY-MM-DD>"
```

`--extract-target` writes the intermediate the two-stage build needs, without
building a pack: `form-index-<reading>.json` and one
`content-<reading>-<translation>.json` per target. Both carry the edition's
provenance, contain no wall clock, and are byte-identical across runs on the same
input. They are workflow artefacts, never release artefacts.

### Pair assembly

```sh
./gradlew :tools:dictionaries:run --args="--content content-en-fr.json \
  --form-index form-index-en.json --output-dir out \
  --previous-lemma-count <n>"
```

`--content` and `--form-index` write one pair's
`lekto-dictionary-<reading>-<translation>.sqlite`, its `NOTICE` and its
`manifest-*.json`. The pack records the edition the content file carries, not a
flag passed now. `--previous-lemma-count` is this pair's count in its last
published manifest; a pair below 80% of it fails its own job, while a pair with
no previous count is not gated.

### Difficulty assembly

```sh
./gradlew :tools:dictionaries:run --args="--form-index form-index-en.json \
  --frequency-input wordfreq-en.tsv.gz --output-dir out"
```

`--form-index` and `--frequency-input` key one language's frequency list by the
shared index and write `lekto-difficulty-<language>` beside its two sidecars. It
records the edition the index carries.

`--frequency-input` is one `form<TAB>weight` line per surface form (for wordfreq,
`10 ** zipf_frequency(form)`). The builder aggregates each form's weight onto the
lemma the reading language's pack form index knows — or the normalised surface
form — ranks the keys and cuts them into CEFR bands A1–C2 at the calibrated
sizes, writing a table behind its own `user_version` handshake and its own
wordfreq `NOTICE`.

### Sample catalogue

```sh
./gradlew :tools:dictionaries:run --args="--sample-source-dir samples \
  --sample-artifacts out --sample-manifest out/manifest.json \
  --sample-output ../core/src/commonMain/resources/samples/catalogue.json"
```

`--sample-source-dir`, `--sample-artifacts`, `--sample-manifest` and
`--sample-output` read every authored text in the source directory, the published
**Language manifest**, and the built **Difficulty table** and **Dictionary
packs**, then write the one **Sample catalogue** the app ships.

The source directory holds one JSON text per reading language (`samples/en.json`:
title, author, attribution and paragraphs). The launch roster is eight languages
— English, French, Spanish, German, Italian, Portuguese, Turkish and Japanese.
The tool keys its words by the reader's word identity — the shared form index's
lemma, or the normalised surface form — records the table's band for each word,
composes the **Difficulty table** with the default-mastery rule into six maps, and
extracts one compact glossary per dictionary the manifest publishes for that
language. It also records the surface forms whose key differs from the spelling,
so the guide's page can key a tap before any pack is installed.

A language the manifest offers no difficulty table or no dictionary for is
refused: the guide must not show a colour it cannot compute or a tap it cannot
answer. The build fails if a glossary misses a word the sample highlights, and it
refuses a sample whose demonstrated paragraph misses the guide's coverage bars: at
A1 one word at every highlighted **Mastery level**, and from A1 to B2 at least
three coloured words over two levels. The paragraph is the one the guide would
show — the one covering the most distinct levels, ties keeping the earlier.

The catalogue is a **Derived asset** shipped in the app under
`core/src/commonMain/resources/samples/catalogue.json`; regenerate it when the
table or any published pack changes, in the same commit. The data workflow's
`sample` job runs the same stage over each snapshot it publishes, so a future text
cannot silently regress the bars. The desktop client reads it through
`ShippedSampleCatalogue` in `core/jvmSharedMain`; an Android app's classloader
does not see a library's Kotlin resources, so `app/build.gradle.kts` packages the
same directory with `assets.srcDir` and the Android client reads those assets —
one copy of the bytes, two ways in. Its languages are the embedded roster the
guide and the **Languages** surface list (`SampleRoster.LANGUAGES` in `core`);
`CommittedSampleCatalogTest` fails if the two drift.

### Release assembly

```sh
./gradlew :tools:dictionaries:run --args="--assemble-release out \
  --asset-url-base https://github.com/<owner>/<data-repo>/releases/download/dictionary-<YYYYMMDD>"
```

`--assemble-release` reads a directory holding the run's per-artefact
`manifest-*.json` files and their compressed bytes, drops any pack below the
2,000-lemma entry floor, and writes the one movable `manifest.json` naming
exactly the files that exist. It is built from the produced files, never from
`languages.json`: a failed, drifted or thin pair is simply absent. A pair below
the 2,000-lemma floor is dropped, never published as an exception. Every entry is
completed with the compressed asset's dated URL, the `sha256` of the bytes behind
it and its size; an entry whose file is missing fails assembly rather than
advertising a dead address.

### Single-artefact run

```sh
./gradlew :tools:dictionaries:run --args="--input raw.jsonl.gz --output pack.sqlite \
  --reading-language en --translation-language es \
  --frequency-input wordfreq-en.tsv.gz --difficulty-output difficulty-en.sqlite \
  --source-url <url> --source-sha256 <hex> --extraction-date <YYYY-MM-DD>"
```

`--reading-language`, `--translation-language` and `--difficulty-language` build
one pack and, optionally, one reading language's **Difficulty table** from a
wordfreq frequency list, keyed by the reading language's word identity.
`--difficulty-language` defaults to the reading language.

A single-artefact run has no sibling packs, so its index covers the one target it
names. The catalogue and extraction modes cover the reading language's whole
configured target set, which keeps the shared `form` table identical across that
language's packs (ADR-0045).

### Coverage pass

```sh
./gradlew :tools:dictionaries:run --args="--measure-input raw.jsonl.gz \
  --measure-target en --measure-target es --measure-output counts.json"
```

`--measure-input`, `--measure-target` and `--measure-output` stream each candidate
edition once and write the per-pair lemma counts a roster decision needs, without
building an artefact. The measurement is in
[`research/dictionary-coverage.md`](research/dictionary-coverage.md); the roster
is `tools/dictionaries/languages.json`, and the app's sample roster is
`SampleRoster.LANGUAGES` in `core`.

## Determinism

Both assembly modes are deterministic: fixed ordering, no wall clock, and
byte-identical SQLite across runs on one input. The build is deterministic — fixed
row ordering, no timestamps, gzip `-n` — and `DictionaryPackGoldenTest` proves
the transform on a committed fixture. A sanity gate fails a run only on a changed
source schema or a coverage collapse below 80% of the previous build's lemma
count, per artefact.

## Workflow

`.github/workflows/dictionary-data.yml` is a reusable workflow in four layers;
the data repository holds only its triggers
(`tools/dictionaries/workflows/lekto-data.yml` is the reviewed caller). Two
preparation jobs feed the layers: `prepare` reads the language set into the
matrices, and `frequencies` builds one wordfreq list per difficulty language.
Each layer is a job matrix:

1. **extract** — one job per **Reading language**: download that edition once,
   stream it once, and upload its form index and one content file per pair as
   workflow artefacts (three-day retention);
2. **pair** — one job per pair: read one content file and the shared index, write
   one pack, and resolve the drift gate against that pair's own previous count;
3. **difficulty** — one job per language: key the shared index by the wordfreq
   frequency list and write one table;
4. **assemble** — read what succeeded, apply the 2,000-lemma entry floor,
   compress, publish the release's assets, and only then its `manifest.json`.

A **sample** job then rebuilds the app's sample catalogue from the snapshot the
run published and refuses it when a language's maps miss the guide's coverage
bars; it uploads the catalogue for the review that commits it, and never changes
the data release.

A failed language loses its own pairs; a failed or thin pair is absent from the
manifest while its siblings publish. Every run publishes a complete snapshot from
that run's own bytes, tagged `dictionary-<YYYYMMDD>` in the data repository. The
release is created as a draft, every asset is uploaded, and the manifest is
attached last; only then does `gh release edit --draft=false --latest` publish the
snapshot, so the address the app follows never names bytes that do not exist yet
(ADR-0048). The three most recent snapshots are kept and older ones are deleted,
which is safe because only the current manifest is referenced.

The frequency lists are built once for the run with `wordfreq[cjk]`: the `cjk`
extra brings the jieba and mecab tokenisers that Chinese, Japanese and Korean
need, and the plain package raises on the first CJK word without them.

The release body is rendered from
`.github/release-notes/dictionary-data.md`; the full CC BY-SA 4.0 text stays in
the `NOTICE` sidecars and in each pack's `license_text` metadata.

## Language manifest

The run writes a **Language manifest** (`manifest.json`): each **Reading
language** with the **Difficulty table** published for it and the **Dictionary
packs** published for its pairs, each pair naming its **Translation language**.
Its format is 2: every artefact carries the `url` the app follows, the `sha256`
of the bytes behind it, and its compressed size in bytes. The local catalogue and
single-artefact modes write a `language-manifest.json` for a one-process run
(`--language-manifest`); the decomposed workflow hands its asset directory to
the CLI's assembly mode, which builds the same manifest from the produced
per-artefact files and writes `manifest.json`.

The app follows the constant address
`https://github.com/Domingax/lekto-data/releases/latest/download/manifest.json`.
Its format version is declared by the producer
(`LanguageManifestFormat.VERSION`) and by the consumer (`core`'s
`LanguageManifest.FORMAT_VERSION`), kept in step by an architecture test, and
`DictionaryDataPipelineTest` pins the workflow's layers, publication order and
data-repository split.

## App consumption

The app consumes the pack through `core`'s `DictionaryPack` seam: it downloads
the address the manifest published into the device-local **derived store**,
checks the bytes' SHA-256 against the manifest's digest and refuses a mismatch
rather than installing it, then validates the format handshake and opens the
SQLite file in place — `sqlite-jdbc` on the JVM, the Android framework's SQLite on
Android (ADR-0018, ADR-0047). See ADR-0017, ADR-0018 and
[`research/open-dictionaries.md`](research/open-dictionaries.md).

The pipeline's decisions are ADR-0044, ADR-0045, ADR-0046, ADR-0047, ADR-0048
and ADR-0049.
