# The sync engine's contract: opaque revisions, a device-local view, and the merge untouched

ADR-0009 puts synchronisation behind a `SyncTarget` seam and ADR-0015 makes the
merge a pure function over `VersionedRecord`s, deferring the engine's tombstone
store and retention to "a later engine ticket". This is that ticket (#26); it
fixes the shape of the seam and the engine, and records the one thing it does
**not** yet do.

## The `SyncTarget` contract

A driver exchanges `VersionedRecord`s — a live `VaultRecord` or a `Tombstone` —
each tagged with an opaque `Revision` (a WebDAV ETag, a Dropbox `rev`). The
engine never interprets the revision; it only hands it back to condition a write.
Four operations cover the records: `list()` (every item), `get(id)`,
`changes(cursor)` (what moved since a `SyncCursor`), and `put(version, expected)`,
which returns `Written(revision)` or `Conflicted(current)`. A small
`SyncCapabilities` query says whether the target has a change cursor and whether
it compares-and-swaps; the engine reads it and degrades — a listing diff where
there is no cursor, a plain write where there is no CAS — but the record-by-record
last-writer-wins it applies is the merge ADR-0004/0015 already fixed.

A **binary attachment channel** carries a book's original file, which lives on its
record rather than in it (ADR-0016): `attachmentIds()` lists the ids that have
one, `attachment(id)` reads the bytes, and `putAttachment(id, bytes)` writes them.
Presence is listed separately from the records so the engine never downloads a
blob just to learn it exists; an attachment is immutable content keyed by a fresh
record id, so it carries no revision and last-writer-wins on presence is enough.
Writing a tombstone drops the id's attachment, so a deleted book's original does
not linger on the target.

The remote **layout** — which files a driver writes, where tombstones and
attachments live — is the driver's, not the engine's. WebDAV (ticket #27) decides
it.

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

## Not yet: tombstone purge

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
converge to the union of their records **and their book originals**, a deletion
propagates as a tombstone that takes the original with it, and a tie resolves on
`deviceId`.

**Rejected**: storing attachments in the record's JSON body (they are binary,
ADR-0016) or giving them a revision and compare-and-swap (a book original is
immutable and keyed by a fresh id, so presence is the whole comparison); storing
tombstones or the remote index in the vault (they are not user data, ADR-0003 /
ADR-0005); letting each driver invent its own merge or ignore a capability the
other honours (the difference would leak into the merge, which ADR-0009 exists to
prevent).

## Update — the remote index is persisted, and the listing is told what the engine holds (issue #119)

The "session state" note in "The engine's device-local state" is superseded: the
`RemoteIndex` is now **persisted** through a `RemoteIndexStore` (`remote-index.json`
beside `tombstones.json`), and the seam's listing is told the revisions the engine
already holds — `list(known)` returns a `SyncListing(items, unchanged)` — so a
driver reads only the new or changed items. ADR-0027 records why the memory
stores the last item (revision **and** body, not the revision alone) and why this
is device-local, never the vault. The merge, the capabilities and the record-by-
record last-writer-wins are unchanged.

## Update — tombstones are purged, and a stale device resyncs (issue #120)

The "Not yet: tombstone purge" section is superseded. The engine now drops a
tombstone older than the longest offline window (`TOMBSTONE_RETENTION`, 90 days)
from its `TombstoneStore`, from the `RemoteIndexStore`, and — through a new
`SyncTarget.delete(id)` — from the target, so the record count is bounded on both
sides. A tombstone whose deletion has not reached the other side is kept: the
merge re-propagates it first, so the guard remains the "no live copy survives"
rule this ADR already fixed.

The `RemoteIndexStore` now also remembers when the device last completed a sync.
A device whose last completed sync is older than the window is treated as new: it
clears its local vault, tombstones and remote index and takes the target's
records in a full resync, which is why a dropped tombstone cannot let an old live
copy resurrect. A device that has never synced is not new, so its first sync
merges and uploads the records it already holds rather than discarding them.

**Accepted transition limit.** A device that synced before this change carries a
persisted remote index but no recorded sync time, so it is not new until its own
first sync records one. Treating that state as new would discard the local
changes of every device at the moment it upgrades, which is worse; the cost is
that a device already offline past the window when this lands can, on that first
sync, re-upload a live copy whose tombstone another device has since purged. The
merge, the capabilities and the record-by-record last-writer-wins are unchanged.

## Update — a delete's tombstone is written by the domain writer, not by the engine (issue #131)

The `SyncEngine.delete` in "The engine's device-local state" is **not** the path
the app deletes through. A user-facing writer removes its own record and writes
the tombstone beside it, into the same `TombstoneStore` the engine reads: the
vocabulary has done this since issue #23, and the library's `BookLibrary.delete`
now does the same (issue #131). Deleting a book removes its record, its original
attachment, its derived parsed text and its reading position, and leaves a
tombstone for the book id and for the reading-position id — each written only
when the id exists — in an order that leaves a failed delete fully listed and
re-deletable. The engine's `delete` is retained for the engine's own use and is
reached by no composition root; reconciling the two is issue #137.

The merge, the `TombstoneStore` and the binary attachment channel are unchanged: a
tombstone a writer writes is the same comparison key the engine already merges
against, so a deleted book's record and original travel as a deletion on both
devices rather than resurrecting.

## Update — the engine holds no delete of its own (issue #137)

The writer-side `Update` (#131) retained `SyncEngine.delete` "for the engine's own
use" and left the reconciliation to this ticket. It is **removed**, which
supersedes the two places that still named an engine `delete`: the sentence in "The
engine's device-local state" and the `delete` clause in "Consequences". The engine
never used the method, and the only path a deletion takes to the merge is a
tombstone a writer leaves in the `TombstoneStore` the engine reads. The `deviceId`
the engine used to stamp that tombstone went with it — the engine writes no record
or tombstone of its own, so it needs no device identity. The `SyncEngineTest`
fixtures now write the tombstone directly, exactly as the writers do, so the
deletion's propagation and its attachment removal are covered unchanged; deleting
an id the store does not hold is the writer's no-op, not the engine's.

The merge, the `TombstoneStore` and the binary attachment channel are unchanged.
