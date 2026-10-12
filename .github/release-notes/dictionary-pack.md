Offline **English → French** dictionary pack built from Wiktionary.

- **Asset:** `{{ASSET}}` ({{SIZE}})
- **Stable download (latest):** <{{STABLE_URL}}>
- **Format version:** {{FORMAT_VERSION}}
- **Coverage:** {{LEMMAS}} lemmas · {{ENTRIES}} entries · {{FORMS}} forms · {{TRANSLATIONS}} translations
- **Source:** raw Wiktextract extract — <{{SOURCE_URL}}>
- **Source SHA-256:** `{{SOURCE_SHA256}}`
- **Upstream extraction date:** {{EXTRACTION_DATE}}
- **Asset SHA-256:** `{{ASSET_SHA256}}`

## Word-difficulty table (English)

The same run produces the per-language **difficulty table** the reader uses to
colour words by their CEFR band (ADR-0029).

- **Asset:** `{{DIFFICULTY_ASSET}}` ({{DIFFICULTY_SIZE}})
- **Stable download (latest):** <{{DIFFICULTY_STABLE_URL}}>
- **Format version:** {{DIFFICULTY_FORMAT_VERSION}}
- **Banded words:** {{DIFFICULTY_WORDS}}
- **Asset SHA-256:** `{{DIFFICULTY_SHA256}}`

**Verify:** download the assets and run `sha256sum -c SHA256SUMS`.

**Licence:** data by **Wiktionary contributors**, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
(and GFDL), extracted, trimmed, normalised and re-formatted by Lekto; the derived
pack remains ShareAlike. The difficulty bands are derived from **wordfreq** by
**Robyn Speer** (data [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/);
code Apache-2.0), which contains data from Google Books Ngram Viewer, OpenSubtitles
and the SUBTLEX word lists by Brysbaert et al. Full texts in the attached `NOTICE`
and `DIFFICULTY-NOTICE`. Lekto is not affiliated with, nor endorsed by, the
Wikimedia Foundation, Wiktionary or wordfreq.

## Language manifest

The same run writes `language-manifest.json`, the catalogue the app reads for
reading: every **Reading language** with the difficulty table it publishes and the
dictionary packs published for its pairs, each naming its translation language.

- **Stable download (latest):** <https://github.com/{{REPOSITORY}}/releases/latest/download/language-manifest.json>

