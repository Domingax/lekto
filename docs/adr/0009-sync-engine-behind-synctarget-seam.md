# Synchronisation is an app-level engine behind a `SyncTarget` seam

Lekto syncs through **its own engine** rather than delegating to an external folder-sync
tool. The vault keeps its per-record JSON shape and per-record last-writer-wins
(ADR-0003 / ADR-0004); the engine adds tombstones for deletions, listing- or cursor-based
change detection, and conditional writes where the backend supports them. A small
capability query on the driver lets the engine degrade when a backend lacks a primitive,
instead of the difference leaking into the engine.

Drivers are thin adapters at the `SyncTarget` seam.

**WebDAV ships first.** It needs no OAuth app registration and no maintainer secret (so
forks are unaffected), it lists with ETags and compare-and-swaps with `If-Match`, and it
covers Nextcloud, ownCloud, Synology and Infomaniak kDrive — the self-hoster persona.
**Dropbox is the second driver** (PKCE with no secret, a maintained Kotlin Android SDK, a
cursor delta, and `WriteMode.update(rev)` CAS).

Rejected: an external folder-sync tool as the only mechanism (fragile on Android — the
official Syncthing Android app is retired — and it forbids the app from owning sync);
**Google Drive** as a first target (no documented conditional write, no refresh token on
Android without a server, and data bound to the OAuth client ID, which structurally breaks
forks) — deferred, perhaps permanently. See `docs/research/cloud-sync-backends.md`.
