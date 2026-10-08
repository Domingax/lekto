package app.lekto.core.sync

import app.lekto.core.vault.VersionedRecord
import kotlin.jvm.JvmInline

/** The sentinel a cursor-less target's default [SyncTarget.changes] mints. */
private const val NO_CHANGE_CURSOR = "no-change-cursor"

/**
 * A driver's opaque token for one remote item's version — a WebDAV ETag,
 * a Dropbox `rev`. The engine never interprets it; it only hands the token back
 * to condition a write, so a driver cannot leak its revision model into the
 * merge rules (ADR-0009).
 */
@JvmInline
value class Revision(val value: String) {
    init {
        require(value.isNotBlank()) { "revision must not be blank" }
    }
}

/**
 * A driver's opaque change cursor — an RFC 6578 `sync-token`, a Dropbox
 * `list_folder` cursor. Passing one to [SyncTarget.changes] asks for what moved
 * since the target last issued it.
 */
@JvmInline
value class SyncCursor(val value: String) {
    init {
        require(value.isNotBlank()) { "sync cursor must not be blank" }
    }
}

/**
 * What a target can do, so the engine degrades instead of assuming (ADR-0009).
 *
 * A backend that lacks a primitive must not change the merge rules: the engine
 * reads these two flags and takes a different path, but the record-by-record
 * last-writer-wins it applies is the same either way.
 */
data class SyncCapabilities(
    /**
     * The target rejects a write whose expected [Revision] has moved — a real
     * compare-and-swap (WebDAV's `If` header, Dropbox `WriteMode.update(rev)`).
     */
    val conditionalWrites: Boolean,

    /**
     * The target can list only what changed since a [SyncCursor] (RFC 6578
     * `sync-collection`) rather than every item on each run.
     */
    val changeCursor: Boolean,
)

/**
 * One item the target holds: a live [VaultRecord][app.lekto.core.vault.VaultRecord]
 * or a [Tombstone][app.lekto.core.vault.Tombstone] for a deletion, plus the
 * driver's [Revision] for it. A tombstone is a remote item like any other, so a
 * listing or a change set carries deletions the same way it carries records.
 */
data class SyncItem(val version: VersionedRecord, val revision: Revision) {
    /** The record id this item is a version of. */
    val id: String get() = version.id
}

/**
 * What changed on the target since a cursor: the items to consider and the
 * cursor to hand back on the next incremental run.
 */
data class SyncChanges(val items: List<SyncItem>, val cursor: SyncCursor)

/** The result of writing one item: the new revision, or the conflict that stopped it. */
sealed interface WriteOutcome {
    /** The write landed; [revision] is the item's new revision. */
    data class Written(val revision: Revision) : WriteOutcome

    /**
     * A conditional write lost its race: the item has a different revision than
     * the write expected. [current] is the item the target held, when the driver
     * can return it without another round trip.
     */
    data class Conflicted(val current: SyncItem?) : WriteOutcome
}

/**
 * The seam the sync engine moves bytes through (ADR-0009). A driver is a thin
 * adapter — WebDAV, Dropbox — that lists the remote vault, reports what it can
 * do, and writes one record or tombstone at a time; the merge rules never see
 * the difference.
 *
 * Every method is `suspend` and switches no dispatcher of its own, so a driver
 * and the engine built on it run on the caller's context. The engine therefore
 * runs under `runTest`'s virtual time rather than a hard-coded background
 * dispatcher (docs/testing.md, "Deterministic seams").
 */
interface SyncTarget {

    /** What this target can do, so the engine can degrade rather than assume. */
    fun capabilities(): SyncCapabilities

    /** Every item the target holds, live records and tombstones alike. */
    suspend fun list(): List<SyncItem>

    /** The item with [id], or `null` when the target holds none. */
    suspend fun get(id: String): SyncItem?

    /**
     * The items changed since [cursor], or every item when [cursor] is `null`.
     * Only meaningful when [capabilities] reports [SyncCapabilities.changeCursor];
     * a target without one is never asked, and the default below lists everything
     * so such a driver need not implement a stub.
     */
    suspend fun changes(cursor: SyncCursor?): SyncChanges = SyncChanges(list(), SyncCursor(NO_CHANGE_CURSOR))

    /**
     * Writes [version] as its id's item. When [expected] is non-null and the
     * target supports [SyncCapabilities.conditionalWrites], the write applies
     * only if the item's current revision equals it; otherwise it is rejected as
     * [WriteOutcome.Conflicted]. A `null` [expected] replaces unconditionally
     * where the target has no compare-and-swap, and creates only if absent where
     * it does.
     *
     * Writing a [VersionedRecord.Deleted] tombstone also drops the id's
     * [attachment]: a tombstone says the id is gone, so a driver must not keep a
     * deleted book's original behind.
     */
    suspend fun put(version: VersionedRecord, expected: Revision?): WriteOutcome

    /**
     * The ids the target holds a binary **attachment** for — a book's original
     * file (ADR-0016). Presence is listed on its own so the engine can reconcile
     * attachments without downloading every blob.
     */
    suspend fun attachmentIds(): Set<String>

    /** [id]'s attachment bytes, or `null` when the target holds none. */
    suspend fun attachment(id: String): ByteArray?

    /**
     * Stores [bytes] as [id]'s attachment, replacing any previous one. An
     * attachment is immutable content keyed by a record id, so it carries no
     * revision: last-writer-wins on presence is enough.
     */
    suspend fun putAttachment(id: String, bytes: ByteArray)
}
