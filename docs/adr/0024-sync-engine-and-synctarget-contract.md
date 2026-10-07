# The sync engine's contract: opaque revisions, a device-local view, and the merge untouched

ADR-0009 puts synchronisation behind a `SyncTarget` seam and ADR-0015 makes the
merge a pure function over `VersionedRecord`s, deferring the engine's tombstone
store and retention to "a later engine ticket". This is that ticket (#26); it
fixes the shape of the seam and the engine, and records the two things it does
**not** yet do.

## The `SyncTarget` contract

A driver exchanges `VersionedRecord`s — a live `VaultRecord` or a `Tombstone` —
each tagged with an opaque `Revision` (a WebDAV ETag, a Dropbox `rev`). The
engine never interprets the revision; it only hands it back to condition a write.
Four operations cover the seam: `list()` (every item), `get(id)`, `changes(cursor)`
(what moved since a `SyncCursor`), and `put(version, expected)`, which returns
`Written(revision)` or `Conflicted(current)`. A small `SyncCapabilities` query
says whether the target has a change cursor and whether it compares-and-swaps;
the engine reads it and degrades — a listing diff where there is no cursor, a
plain write where there is no CAS — but the record-by-record last-writer-wins it
applies is the merge ADR-0004/0015 already fixed.

The remote **layout** — which files a driver writes, where tombstones live — is
the driver's, not the engine's. WebDAV (ticket #27) decides it.

## The engine's device-local state

A cursor reports only what changed, so merging a delta against the local vault
alone would make an unchanged remote record look absent and re-upload it. The
engine therefore keeps a `RemoteIndex` — the last item seen per id — and the last
`SyncCursor`; a listing rebuilds the index, a change set updates it. Both are
**session state**: they are not persisted across launches yet, so a fresh engine
starts with a full `changes(null)` and then becomes incremental. Persisting them
would put sync bookkeeping next to the vault, which ADR-0005 reserves for user
data, so it is left to the composition root rather than the domain.

Local tombstones live in the engine's own `TombstoneStore`, written as
`tombstones.json` beside the vault (its own root, never the vault and never an
export). A `delete` removes the record *and* leaves the tombstone the merge
compares on; a record saved again after its delete shares its id, so the local
side is collapsed to the newest version per id before the merge.

## Not yet: attachments and tombstone purge

**Attachments are not synced.** ADR-0016 stores a book's original as a binary
attachment on its record, and ADR-0005's vault includes it. `SyncTarget` moves
records and tombstones only, so a full sync currently carries every JSON record
but not a book original. Attachment sync needs its own design — a presence
listing, a byte channel, deletion, and no compare-and-swap on a blob — and is
tracked separately rather than smuggled into this contract.

**Tombstones are retained, not yet purged.** ADR-0015 lets the engine drop a
tombstone past the longest offline window and treat a returning device as new.
That "new device, full resync" path does not exist yet, so purging a tombstone
would let an old live copy resurrect; the engine keeps every tombstone until that
path lands. The store is small (an id and an envelope per deletion), which is the
property ADR-0015 already relied on.

## Consequences

The engine is pure of clock and dispatcher: `delete` reads the injected clock and
`sync` is `suspend` on the caller's context, so the whole engine runs under
`runTest`'s virtual time. A target with neither capability still syncs, one
round of listing at a time. A lost conditional write is recovered by re-reading
the target's item and re-applying the merge, bounded by a small attempt count;
what cannot be settled is reported, not lost. Two devices with disjoint history
converge to the union of their records, a deletion propagates as a tombstone, and
a tie resolves on `deviceId`.

**Rejected**: per-record attachment sync now (needs its own contract); storing
tombstones or the remote index in the vault (they are not user data, ADR-0003 /
ADR-0005); letting each driver invent its own merge or ignore a capability the
other honours (the difference would leak into the merge, which ADR-0009 exists to
prevent).
