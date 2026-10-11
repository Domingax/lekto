# Sync re-reads only what changed: a listing told the known revisions, and a device-local remote index

ADR-0024 fixed the `SyncTarget` seam and left one thing open, and ADR-0025
recorded it: "With no change cursor, every sync lists every item." A WebDAV
`list()` reconstructs each item with a `GET`, so a vault of N records costs ~N
HTTP requests every run even when nothing changed — thousands of requests once a
learner has thousands of saved words. The listing already carries each item's
`ETag`; the driver just did not use it. This is that ticket (#119).

## The listing is told the revisions the caller already has

The seam's listing now takes, per id, the revision the caller last saw, and a
driver reads the body only of items that are new or whose revision moved:

- `SyncTarget.list(known: Map<String, Revision>)` — `known` is what the caller
  already holds; an empty map reads every item, so a first sync keeps its shape.
- The result is a `SyncListing(items, unchanged)`: `items` are the bodies the
  caller must read, and `unchanged` are the ids whose revision the caller already
  held, so their body was not read.

`Revision` is already the seam's opaque token — a WebDAV `ETag`, a Dropbox `rev`
— so passing it back to the driver leaks no revision model into the engine
(ADR-0009): the engine never interprets the value, it only compares identity. A
driver that claims the optimisation must honour it, and the shared
`SyncTargetContract` gains the case that says so.

## The engine remembers the last item it saw, device-locally

The engine's `RemoteIndex` — the last `SyncItem` per id (ADR-0024) — is what turns
"this revision is unchanged" into a merge input: for an unchanged id it reuses the
body it already holds. ADR-0024 kept that index as session state; it is now
**persisted**, through a `RemoteIndexStore` seam:

- `JsonRemoteIndexStore` writes `remote-index.json` over a `VaultFileSystem`.
- It is rooted with the tombstone store, under the client's own `sync/`
  directory, and **never** in the **Vault** (ADR-0005) and never an export.
- It stores the last item — revision **and** body — not the revision alone: on a
  fresh launch the engine still needs the body to merge, so a memory of revisions
  could not skip the `GET`. The body is a cache of what the target already holds
  and is re-derivable with one listing, so it is not user data.
- Losing the document costs one full read, never a record: an absent or corrupt
  document reads as empty and the engine falls back to reading every item.

Persisting it is not decoration: the composition root builds a fresh engine for
each manual sync, so without the document there would be no "last sync" to compare
against and the saving would never happen.

## Consequences

- A steady-state sync is one `PROPFIND` plus a `GET` per new or changed item. A
  vault of thousands of records that has not changed costs one listing.
- The remote layout is unchanged (ADR-0025): still one JSON file per item and one
  binary file per book original. This is a read-side optimisation only.
- The device-local document is derived and reproducible, so it is excluded from
  the vault and its export like the tombstones beside it.
- The revision is the driver's opaque token as the **listing** reported it: the
  driver tags a body it reads with that listing `ETag`, so a later listing
  compares like with like. A revision that moves between the listing and the
  `GET` is caught on the next listing and re-read, and a stale revision used to
  condition a write is settled by the merge (ADR-0024), never silently applied.

## Rejected

- **A change cursor (RFC 6578 `sync-collection`)** — server-dependent, so it
  cannot be claimed for WebDAV (ADR-0025); the known-revision listing needs no
  server feature.
- **Persisting the revision alone** — unsound: the merge needs the body, so a
  revision-only memory would still fetch every unchanged item.
- **A remote manifest of id→revision** — a write hot spot with no safe conditional
  update on every server (research §8.4), and it does not remove the `GET`s by
  itself.
- **Storing the index in the vault** — it is sync bookkeeping, not user data
  (ADR-0003 / ADR-0005); a vault holds one file per record and is exported whole.
- **Reusing a cached body without comparing the revision** — the revision is the
  whole change detector; skipping the compare would resurface an edit as a stale
  body.
