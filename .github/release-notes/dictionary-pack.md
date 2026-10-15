Offline dictionary packs and word-difficulty tables built from Wiktionary and
wordfreq.

- **Source:** raw Wiktextract extract — <{{SOURCE_URL}}>
- **Source SHA-256:** `{{SOURCE_SHA256}}`
- **Upstream extraction date:** {{EXTRACTION_DATE}}

This release publishes one **Dictionary pack** per (Reading language × Translation
language) pair and one **Difficulty table** per reading language. The full
catalogue — every offered language, its difficulty table and each pack's
translation language — is in `language-manifest.json`.

- **Catalogue (stable, latest):** <https://github.com/{{REPOSITORY}}/releases/latest/download/language-manifest.json>

**Verify:** download the assets and run `sha256sum -c SHA256SUMS`.

**Licence:** data by **Wiktionary contributors**, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
(and GFDL), extracted, trimmed, normalised and re-formatted by Lekto; the derived
packs remain ShareAlike. The difficulty bands are derived from **wordfreq** by
**Robyn Speer** (data [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/);
code Apache-2.0), which contains data from Google Books Ngram Viewer, OpenSubtitles
and the SUBTLEX word lists by Brysbaert et al. Full texts travel inside the packs
and the tables and in the attached `NOTICE-*` assets. Lekto is not affiliated
with, nor endorsed by, the Wikimedia Foundation, Wiktionary or wordfreq.
