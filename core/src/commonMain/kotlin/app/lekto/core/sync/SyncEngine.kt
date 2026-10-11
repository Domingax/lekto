package app.lekto.core.sync

import app.lekto.core.vault.MergeOutcome
import app.lekto.core.vault.RecordMerge
import app.lekto.core.vault.VaultMerge
import app.lekto.core.vault.VaultStore
import app.lekto.core.vault.VersionedRecord
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The longest offline window the product supports (ADR-0015): a tombstone is
 * kept at least this long, so a device that has been away for less is still told
 * the item was deleted. Past it the engine drops the tombstone — from its own
 * store, the device-local remote index, and the target — and treats a returning
 * device as new, so a stale live copy cannot resurrect a deleted item.
 */
val TOMBSTONE_RETENTION: Duration = 90.days

/**
 * What one [SyncEngine.sync] moved: live records each way, deletions, the binary
 * attachments (book originals) it carried, and writes the target refused to
 * settle.
 *
 * [unresolved] is the honest count of conditional writes that kept losing their
 * race; a non-zero value is recoverable — the next sync retries — so a caller
 * reports it rather than treating it as loss.
 */
@Suppress("LongParameterList") // One field per axis the caller reports on.
data class SyncReport(
    /** Live records the local vault won and the target had to adopt. */
    val uploaded: Int,
    /** Live records the target won and the local vault adopted. */
    val downloaded: Int,
    /** Deletions applied on either side. */
    val deletions: Int,
    /** Attachments carried either way — a book's original file (ADR-0016). */
    val attachments: Int,
    /** Writes the target rejected and the engine could not settle. */
    val unresolved: Int,
    /**
     * Whether this sync treated the device as new because it had been away past
     * [TOMBSTONE_RETENTION] (ADR-0015): its local records were replaced by the
     * target's, so the caller can tell the user the vault restarted from the
     * server rather than appearing to have lost data silently.
     */
    val resynced: Boolean = false,
)

/**
 * Synchronisation as the app owns it, behind the [SyncTarget] seam (ADR-0009;
 * ticket #26).
 *
 * A [sync] reads the target — every item, or only what a cursor names — and the
 * local vault, then hands both to the pure [VaultMerge]. The outcome tells the
 * engine what each side must write or delete; it applies that to the vault and
 * through the target, conditioning each remote write on the revision it saw
 * where the target supports a compare-and-swap. It then reconciles the binary
 * attachments that travel with live records, so an imported book's original
 * follows its record (ADR-0016).
 *
 * The engine remembers the last item it saw per id in a device-local
 * [RemoteIndexStore] beside the tombstones, and passes those revisions to a
 * listing, so a sync reads only the records that changed rather than the whole
 * target every run (ADR-0027). The same memory records when the device last
 * completed a sync, so a device that has been away past [TOMBSTONE_RETENTION] is
 * treated as new and takes the target's records in a full resync (ADR-0015).
 *
 * **Deletions travel as tombstones.** The engine has no delete of its own: the
 * user-facing writers remove their own record and leave the tombstone the merge
 * compares on beside it, in the same [TombstoneStore] the engine reads (ADR-0015;
 * issue #131). Without that tombstone a removed record is only absent — the next
 * sync would copy it back from a device that has not seen the delete. A tombstone
 * older than [TOMBSTONE_RETENTION] is dropped on the next sync, from the local
 * store, the device-local index and the target, so the deletion does not
 * accumulate forever.
 *
 * The engine reads no wall clock and pins no dispatcher: [sync] takes its time
 * from the injected [clock] and runs on the caller's context, so the whole engine
 * is exercised under `runTest`'s virtual time (docs/testing.md, "Deterministic
 * seams").
 */
@Suppress("TooManyFunctions") // Each method is one step of the sync.
class SyncEngine(
    private val local: VaultStore,
    private val tombstones: TombstoneStore,
    private val remoteIndex: RemoteIndexStore,
    private val target: SyncTarget,
    private val clock: Clock,
) {

    private val remote = RemoteIndex().apply { replaceAll(remoteIndex.all()) }
    private var cursor: SyncCursor? = null
    private var lastSyncedAt: Instant? = remoteIndex.lastSyncedAt()

    /** Reconciles the local vault and the target, and reports what moved. */
    suspend fun sync(): SyncReport {
        val now = clock.now()
        val resynced = isResync(now)
        if (resynced) resetLocal()
        fetchRemote()
        purgeTombstones(now)
        val outcome = VaultMerge.merge(local = localVersions(), remote = remote.all().map { it.version })
        val appliedLocally = applyLocally(outcome)
        val remoteWrites = applyRemotely(outcome)
        val attachments = reconcileAttachments()
        remoteIndex.replaceAll(remote.all())
        remoteIndex.markSynced(now)
        lastSyncedAt = now
        return SyncReport(
            uploaded = remoteWrites.uploaded,
            downloaded = appliedLocally + remoteWrites.downloaded,
            deletions = outcome.toDeleteLocally.size + outcome.toDeleteRemotely.size,
            attachments = attachments,
            unresolved = remoteWrites.unresolved,
            resynced = resynced,
        )
    }

    /**
     * Whether this device has been away past [TOMBSTONE_RETENTION] and must be
     * treated as new (ADR-0015): it has synced before, but not within the window,
     * so its local records are replaced by the target's in a full resync rather
     * than merged — a stale live copy could otherwise resurrect a deleted item
     * whose tombstone the engine has dropped. A device that has never synced is
     * not new: its first sync merges, so local records it already holds are
     * uploaded rather than discarded.
     */
    private fun isResync(now: Instant): Boolean =
        lastSyncedAt?.let { last -> now - last > TOMBSTONE_RETENTION } ?: false

    /**
     * Drops the device's local history before a full resync: the vault, the
     * tombstones, the remembered target and the change cursor, so the merge sees
     * a new device and reads the target whole rather than pushing local records.
     */
    private fun resetLocal() {
        local.all().forEach { local.remove(it.id) }
        tombstones.all().forEach { tombstones.remove(it.id) }
        remote.replaceAll(emptyList())
        remoteIndex.replaceAll(emptyList())
        cursor = null
    }

    /**
     * Drops every tombstone older than [TOMBSTONE_RETENTION], from the local
     * store, the device-local index and the target, so a deletion does not
     * accumulate forever (ADR-0015). A tombstone whose deletion has not reached
     * the other side — its id still has a live copy there — is kept: dropping it
     * would let that copy resurrect, so the merge re-propagates the delete first.
     */
    private suspend fun purgeTombstones(now: Instant) {
        val cutoff = now - TOMBSTONE_RETENTION
        val liveLocally = local.all().mapTo(mutableSetOf()) { it.id }
        remote.all()
            .filter { it.version is VersionedRecord.Deleted && it.version.updatedAt < cutoff && it.id !in liveLocally }
            .forEach { item ->
                target.delete(item.id)
                remote.remove(item.id)
            }
        val liveRemotely = remote.all()
            .filter { it.version is VersionedRecord.Live }
            .mapTo(mutableSetOf()) { it.id }
        tombstones.all()
            .filter { it.updatedAt < cutoff && it.id !in liveRemotely }
            .forEach { tombstones.remove(it.id) }
    }

    /**
     * The target's current items: a full listing where there is no cursor, or a
     * change set applied to the index where there is. A null cursor asks a change
     * set for everything, so the first sync is the same shape either way.
     *
     * A listing is told [RemoteIndex.knownRevisions], so a driver reads only the
     * items that are new or whose revision moved and names the rest unchanged
     * (ADR-0027); the index then keeps the body it already held for those,
     * turning the listing into a full picture to merge.
     */
    private suspend fun fetchRemote() {
        if (!target.capabilities().changeCursor) {
            remote.replaceAll(resolveUncached(target.list(remote.knownRevisions())))
        } else {
            val changes = target.changes(cursor)
            cursor = changes.cursor
            remote.apply(changes.items)
        }
    }

    /**
     * A driver promises not to name an id [unchanged][SyncListing.unchanged]
     * unless the caller already holds it, so an unchanged id the index does not
     * hold is a driver bug: it is re-read here rather than silently dropped from
     * the merge. A correct driver makes this a no-op.
     */
    private suspend fun resolveUncached(listing: SyncListing): SyncListing {
        val uncached = listing.unchanged.filter { remote.get(it) == null }
        if (uncached.isEmpty()) return listing
        return SyncListing(
            items = listing.items + uncached.mapNotNull { target.get(it) },
            unchanged = listing.unchanged - uncached.toSet(),
        )
    }

    /**
     * The local side of the merge: live records and tombstones, with the newest
     * version per id when the two overlap (a record saved again after its delete
     * shares its id, ADR-0003, "a record's identity is its id alone"). An overlap
     * is reconciled here, so the superseded version does not linger.
     */
    private fun localVersions(): List<VersionedRecord> {
        val byId = mutableMapOf<String, MutableList<VersionedRecord>>()
        local.all().forEach { record -> byId.getOrPut(record.id) { mutableListOf() } += VersionedRecord.of(record) }
        tombstones.all().forEach { mark -> byId.getOrPut(mark.id) { mutableListOf() } += VersionedRecord.of(mark) }
        return byId.map { (_, versions) ->
            val winner = RecordMerge.mergeAll(versions)
            if (versions.size > 1) adopt(winner)
            winner
        }
    }

    /** Adopts the local half of the outcome, clearing the version each write replaces. */
    private fun applyLocally(outcome: MergeOutcome): Int {
        outcome.toWriteLocally.forEach { adopt(VersionedRecord.of(it)) }
        outcome.toDeleteLocally.forEach { adopt(VersionedRecord.of(it)) }
        return outcome.toWriteLocally.size
    }

    /** Writes the remote half of the outcome, one conditional write at a time. */
    private suspend fun applyRemotely(outcome: MergeOutcome): RemoteWrites {
        val versions = outcome.toWriteRemotely.map { VersionedRecord.of(it) } +
            outcome.toDeleteRemotely.map { VersionedRecord.of(it) }
        val results = versions.map { version -> writeRemotely(version) }
        return RemoteWrites(
            uploaded = results.count { it == WriteResult.Landed },
            downloaded = results.count { it == WriteResult.Adopted },
            unresolved = results.count { it == WriteResult.Unresolved },
        )
    }

    /**
     * Writes [version] to the target, retrying against a fresh revision after a
     * lost compare-and-swap. A race the target's version wins is [WriteResult.Adopted];
     * attempts that run out are [WriteResult.Unresolved], which the next sync retries.
     */
    private suspend fun writeRemotely(version: VersionedRecord): WriteResult {
        var result = WriteResult.Unresolved
        var attempts = 0
        while (result == WriteResult.Unresolved && attempts < MAX_WRITE_ATTEMPTS) {
            attempts++
            val expected = if (target.capabilities().conditionalWrites) remote.get(version.id)?.revision else null
            when (val outcome = target.put(version, expected)) {
                is WriteOutcome.Written -> {
                    remote.upsert(SyncItem(version, outcome.revision))
                    result = WriteResult.Landed
                }

                is WriteOutcome.Conflicted -> result = settle(version, outcome)
            }
        }
        return result
    }

    /**
     * Reconciles the binary attachments — a book's original file (ADR-0016) — for
     * the live records, after the record merge: uploads one the target lacks, and
     * downloads one the vault lacks. An attachment is immutable content keyed by
     * a record id, so presence is the whole comparison and last-writer-wins on it
     * is enough. A deletion needs no work here: writing the tombstone dropped the
     * id's remote attachment and adopting it dropped the local one.
     */
    private suspend fun reconcileAttachments(): Int {
        val remoteIds = target.attachmentIds()
        var moved = 0
        local.all().forEach { record ->
            val bytes = local.getAttachment(record.id)
            when {
                bytes != null && record.id !in remoteIds -> {
                    target.putAttachment(record.id, bytes)
                    moved++
                }

                bytes == null && record.id in remoteIds -> target.attachment(record.id)?.let { downloaded ->
                    local.putAttachment(record.id, downloaded)
                    moved++
                }
            }
        }
        return moved
    }

    /**
     * Reconciles a conditional write that lost its race: re-read the target's
     * item, re-apply last-writer-wins, and either retry (the local version still
     * wins), adopt the target's version, or — when the target already holds this
     * exact version — treat the write as landed.
     */
    private suspend fun settle(version: VersionedRecord, outcome: WriteOutcome.Conflicted): WriteResult {
        val current = outcome.current ?: target.get(version.id)
        val result = if (current == null) {
            remote.remove(version.id)
            WriteResult.Unresolved
        } else {
            remote.upsert(current)
            when {
                version == current.version -> WriteResult.Landed

                RecordMerge.winner(version, current.version) == version -> WriteResult.Unresolved

                else -> {
                    adopt(current.version)
                    WriteResult.Adopted
                }
            }
        }
        return result
    }

    /** Makes [version] the local truth, clearing the opposite version for its id. */
    private fun adopt(version: VersionedRecord) {
        when (version) {
            is VersionedRecord.Live -> {
                local.put(version.record)
                tombstones.remove(version.id)
            }

            is VersionedRecord.Deleted -> {
                local.remove(version.id)
                tombstones.put(version.tombstone)
            }
        }
    }

    /** How the remote half of an outcome landed: uploaded, adopted or unresolved. */
    private data class RemoteWrites(val uploaded: Int, val downloaded: Int, val unresolved: Int)

    /** What one remote write did: land, lose to a newer target version, or run out of attempts. */
    private enum class WriteResult { Landed, Adopted, Unresolved }

    private companion object {
        /** Attempts per remote write before it is reported unresolved; the next sync retries. */
        const val MAX_WRITE_ATTEMPTS = 3
    }
}
