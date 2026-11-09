---
status: accepted
---

# The dictionary-data build is a reusable workflow

ADR-0047 moves dictionary data out of the application's releases: the data
repository holds each dated snapshot and one movable `manifest.json`, and the
application's `releases/latest` keeps meaning its latest version. Its consequence
says "the publishing workflow moves to the data repository". This ADR records
where the build itself lives.

**The triggers and the publication context live in the data repository.** Its
`.github/workflows/dictionary-data.yml` holds the monthly `schedule`, the manual
`workflow_dispatch`, the concurrency group and the `contents: write` permission.
A run's `github.repository` is the data repository, so its release, its
`SHA256SUMS` and its asset URLs are that repository's by construction.

**The four build layers live with the CLI they drive.** The application keeps the
reusable workflow `.github/workflows/dictionary-data.yml`, which checks out the
application at a given ref and runs `tools/dictionaries`. The data repository's
caller is a dozen lines and pins that reusable workflow at `@main`.

**Why not copy the whole workflow into the data repository.** The workflow's body
is the CLI's command line: every layer names a flag (`--extract-target`,
`--content`, `--form-index`, `--assemble-release`) that only the application
defines. Two copies drift silently, and every CLI change would need a second
commit in another repository before it could take effect. The reusable workflow
keeps one reviewable source of truth, under the same `check` that tests the flags.

**Why not a token in the application's workflow.** A workflow in the application
that published into the data repository would need a personal access token with
write access to a second repository, kept as a secret. The caller gives the data
repository's own `GITHUB_TOKEN` exactly the permissions its run needs, and the
trigger stays where the data lives, so the application never publishes data on a
schedule of its own.

**The snapshot is published atomically.** `gh release create` makes a release
latest immediately, so a manifest attached afterwards would leave
`releases/latest/download/manifest.json` either 404 or pointing at the previous
snapshot for the duration of the upload. The assembly therefore creates the
release as a **draft**, uploads every asset, attaches the manifest last, and only
then runs `gh release edit --draft=false --latest`. The movable address never
names a byte that is not there.

Rejected: the whole workflow copied into the data repository (drift, and a second
place to change for every flag); the application's lane publishing into the data
repository with a PAT (a long-lived write token, and the application still owns a
data schedule); a git submodule of the application in the data repository (a
checkout with none of the review guarantees of a reusable workflow).

**Consequences**: the application's retired `dictionary-pack.yml` is gone; the
data repository holds only the caller, its README and the licence statement; the
two preparation jobs (`prepare` and `frequencies`) and the four layers are pinned
by `DictionaryDataPipelineTest` in `architecture`, which also proves the
draft-then-publish order; the app follows
`https://github.com/Domingax/lekto-data/releases/latest/download/manifest.json`.
