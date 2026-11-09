# Dictionary-data workflow

Dictionary data lives in its own repository (`Domingax/lekto-data`), so the
application's `releases/latest` keeps meaning its latest application version
(ADR-0047, ADR-0048, issue #306).

The four-layer build is the application's reusable workflow,
[`.github/workflows/dictionary-data.yml`](../../../.github/workflows/dictionary-data.yml).
The data repository holds only the triggers, in
[`lekto-data.yml`](lekto-data.yml), copied to its
`.github/workflows/dictionary-data.yml`. ADR-0048 records why the build itself
stays with the CLI it drives.

To reinstall the caller after a change:

```sh
tmp="$(mktemp -d)"
git clone --depth 1 https://github.com/Domingax/lekto-data "$tmp/lekto-data"
cp tools/dictionaries/workflows/lekto-data.yml "$tmp/lekto-data/.github/workflows/dictionary-data.yml"
git -C "$tmp/lekto-data" add .github/workflows/dictionary-data.yml
git -C "$tmp/lekto-data" commit -m "ci(dictionary-data): install the caller"
git -C "$tmp/lekto-data" push
rm -rf "$tmp"
```

The data repository's releases are data snapshots only. Its `releases/latest`
points at the current snapshot, and only `manifest.json` is a moving address: the
app follows it and then the absolute, dated URL each artefact entry carries. The
repository's README states the licence the data inherits: the packs and tables are
derived from Wiktionary by Wiktextract / Kaikki and remain **CC BY-SA 4.0** (and
GFDL); the difficulty bands derive from **wordfreq** by Robyn Speer (data CC BY-SA
4.0, code Apache-2.0). Every pack and table keeps its `NOTICE-*` beside it.
