# Deletions merge as body-less tombstones, and the merge is a pure function between two sides

The sync engine's merge is a **pure function** over two sides of a vault —
`VaultMerge.merge(local, remote): MergeOutcome` — built on a per-record
comparison, `RecordMerge`. Each side is a list of `VersionedRecord`s: a
`VaultRecord` for a live item or a `Tombstone` for a deletion. A tombstone
carries the item's `id`, `updatedAt` and `deviceId` but **not its body**; the
merge compares the two by `updatedAt` with the `deviceId` tie-break ADR-0004
fixes, and the winner decides what the other side writes or deletes.

A deletion cannot be the mere absence of a file: an offline device that has not
seen the delete would copy the live record back, and a partial listing would make
an item that simply failed to upload look deleted (ADR-0009; the tombstone itself
comes from ADR-0004's rejection of CRDTs — for a single-user vault, a marker with
a timestamp is enough). Keeping only the envelope and not the body is what makes
a tombstone cheap enough to retain past the longest offline window, and it means
a deletion never leaves the content the user asked to remove lying in a
tombstone.

The merge reads no clock and writes nothing, so the properties the product needs
fall out of the comparison being a total order over versions with distinct
device ids: merging is idempotent, commutative when the device ids differ, and a
fold in any order converges. Keeping it pure is what makes those properties
testable as properties (ticket #14; `docs/testing.md`), and it keeps the engine
that moves bytes — `SyncTarget`, conditional writes, cursors — a separate concern
behind its own seam. When the same device wrote both versions (equal `updatedAt`
and equal `deviceId`), the comparison is not a strict order — the two are
indistinguishable keys — and `winner` returns its first argument; the fold visits
each id's versions in a stable order, so the merge stays deterministic still.

We rejected making a tombstone a `VaultRecord` with a boolean: a record's body is
user data, and a tombstone must be able to say "deleted" without carrying it.
We also rejected resolving the merge inside the store: `VaultStore` stays a
single-vault seam, and a two-sided reconcile belongs to sync, not to the on-disk
format.

**Consequences**: a vault on disk still holds only live records (ADR-0003); the
tombstones a sync needs are carried by the engine's own store, whose layout this
ADR does not fix and a later engine ticket decides. `RecordMerge.winner` returns
its first argument when one device wrote both versions — a repeated `updatedAt`
is an indistinguishable key, and the fold visits each id's versions in a stable
order, so the merge stays deterministic. When the same record is edited on two
devices offline, the later write silently wins and the earlier one is lost, as
ADR-0004 already accepts for a single-user vault.
