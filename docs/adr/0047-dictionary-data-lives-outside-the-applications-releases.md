---
status: accepted
---

# Dictionary data is published outside the application's releases

Today the packs, the difficulty tables and the **Language manifest** are assets of
a dated release in the application's repository, and the app addresses them
through `releases/latest`. That alias is the most recent release in the
repository, so it is shared by three consumers who mean three different things by
it: a visitor wanting the application, the app wanting its catalogue, and the
pipeline wanting its own last output.

The collision is not hypothetical. The newest release in the repository is
`Dictionary data 20261101`, marked **latest**, while the guide's download links
point at `https://github.com/Domingax/lekto/releases/latest`. Nothing breaks today
only because no application release has shipped yet; the first `v1.0.0` would be
displaced by the next monthly run.

The alias is also not atomic. The app reads the manifest and then fetches each
asset, both through `latest`; a release published between the two requests yields
a manifest and a file from different generations, and the date-free asset names
hide it. Decomposing the pipeline makes this worse rather than better: if a pair
can be republished alone, the newest release may hold one pack and nothing else,
so `latest/download/<other-name>` stops resolving.

**A separate data repository, a movable manifest, immutable assets.** Dictionary
data lives in its own repository, whose releases are data snapshots and where no
application version exists to be confused with them. Its `releases/latest` points
at the current **Language manifest** and nothing else — one small file, the only
moving target the app follows. Every artefact is addressed by the dated release
that carries it, so the bytes behind a URL never change.

```
manifest (moves)     …/lekto-data/releases/latest/download/manifest.json
asset (immutable)    …/lekto-data/releases/download/dictionary-20261101/lekto-dictionary-fr-it.sqlite.gz
```

**The manifest is the lock, not the index.** Its format becomes version 2 and each
entry carries `name`, `url`, `sizeBytes` and `sha256`. The app stops building a URL
from a name and follows the one the manifest published, so the manifest names
exactly the bytes it measured, and a download becomes verifiable. Asset names may
be dated again, because the manifest pins them; ADR-0031 dropped their dates only
to keep URLs stable.

**The manifest is published last, and built from what succeeded.** It is written
after every asset is uploaded, and from the files the run produced rather than
from `languages.json`, so it can never advertise an artefact that does not exist.
A pair that fails is absent from the manifest, which is the same rule that drops a
thin pair.

**Every run publishes a complete snapshot.** The source changes weekly upstream,
so reusing a previous run's assets would hand a reader dictionaries built from
different snapshots of Wiktionary without saying so, and a source refresh would
never reach the reused pairs. Rebuilding is affordable because the stages are
matrices: the run costs about ten minutes whatever the number of pairs. The two or
three most recent snapshots are kept and older ones are deleted, which is safe
precisely because the current manifest names only the current one.

Rejected: keeping the data releases in the application's repository, which leaves
the `latest` collision and buries application versions under data assets; GitHub
Pages, whose published site is capped at 1 GB and whose soft bandwidth is 100 GB a
month; a git branch of assets, where a file is blocked above 100 MiB and a
repository is recommended below 1 GB; GitHub Packages, which requires an access
token to install even a public package, so an app could not download one;
`ghcr.io` OCI artefacts, which avoid releases entirely but make the client perform
a token exchange and a manifest read before its first byte — kept as the fallback
if no release may exist anywhere; a per-language release behind `latest`, which
leaves the alias ambiguous for every other language.

**Consequences**: the app's release list holds only application versions; the
pack downloaders read `url` from the manifest and verify `sha256`; the
`DictionaryRelease` and `DifficultyRelease` URL builders go away; the manifest
format version moves to 2 and the synchronisation tests follow (ADR-0013); the
publishing workflow moves to the data repository and needs a token that may write
there; ADR-0018's address paragraph and ADR-0031's single-release argument are
superseded here, while both ADRs' format and installation rules stand.
