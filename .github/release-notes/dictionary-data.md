Offline dictionary packs and word-difficulty tables built from Wiktionary and
wordfreq.

- **Snapshot:** {{DATE}}
- **Catalogue (movable, latest):** <https://github.com/{{REPOSITORY}}/releases/latest/download/manifest.json>

This release publishes one **Dictionary pack** per (Reading language × Translation
language) pair and one **Difficulty table** per reading language. Every pack and
table keeps its own `NOTICE-*` and `manifest-*.json`: each provenance manifest
records the edition it was built from, its URL, its SHA-256 and its extraction
date. The catalogue names only the files this snapshot actually produced.

**Verify:** download the assets and run `sha256sum -c SHA256SUMS`.

**Licence:** data by **Wiktionary contributors**, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/)
(and GFDL), extracted, trimmed, normalised and re-formatted by Lekto; the derived
packs remain ShareAlike. The difficulty bands are derived from **wordfreq** by
**Robyn Speer** (data [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/);
code Apache-2.0), which contains data from Google Books Ngram Viewer, OpenSubtitles
and the SUBTLEX word lists by Brysbaert et al. Full texts travel inside the packs
and the tables and in the attached `NOTICE-*` assets. Lekto is not affiliated
with, nor endorsed by, the Wikimedia Foundation, Wiktionary or wordfreq.
