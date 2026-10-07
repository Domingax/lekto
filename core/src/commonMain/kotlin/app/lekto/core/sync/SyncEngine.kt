package app.lekto.core.sync

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.MergeOutcome
import app.lekto.core.vault.RecordMerge
import app.lekto.core.vault.Tombstone
import app.lekto.core.vault.VaultMerge
import app.lekto.core.vault.VaultStore
import app.lekto.core.vault.VersionedRecord
import kotlin.time.Clock

/**
 * What one [SyncEngine.sync] moved, in the four directions it can move: live
 * records each way, deletions, and writes the target refused to settle.
 *
 * [unresolved] is the honest count of conditional writes that kept losing their
 * race; a non-zero value is recoverable — the next sync retries — so a caller
 * reports it rather than treating it as loss.
 */
data class SyncReport(
    /** Live records the local vault won and the target had to adopt. */
    val uploaded: Int,
    /** Live records the target won and the local vault adopted. */
    val downloaded: Int,
    /** Deletions applied on either side. */
    val deletions: Int,
    /** Writes the target rejected and the engine could not settle. */
    val unresolved: Int,
)

/**
 * Synchronisation as the app owns it, behind the [SyncTarget] seam (ADR-0009;
 * ticket #26).
 *
 * A [sync] reads the target — every item, or only what a cursor names — and the
 * local vault, then hands both to the pure [VaultMerge]. The outcome tells the
 * engine what each side must write or delete; it applies that to the vault and
 * through the target, conditioning each remote write on the revision it saw
 * where the target supports a compare-and-swap.
 *
 * **Deletions go through [delete].** A record removed from the vault with no
 * tombstone is only absent — the next sync would copy it back from a device that
 * has not seen the delete — so [delete] removes the record *and* leaves the
 * tombstone the merge compares on (ADR-0015).
 *
 * The engine reads no wall clock and pins no dispatcher: [delete] takes its time
 * from the injected [clock] and [sync] runs on the caller's context, so the
 * whole engine is exercised under `runTest`'s virtual time (docs/testing.md,
 * "Deterministic seams").
 */
class SyncEngine(
    private val local: VaultStore,
    private val tombstones: TombstoneStore,
    private val target: SyncTarget,
    private val clock: Clock,
    private val deviceId: DeviceId,
) {

    private val remote = RemoteIndex()
    private var cursor: SyncCursor? = null

    /**
     * Removes [id] from the local vault and leaves a tombstone for it, so the
     * deletion propagates on the next [sync] instead of resurrecting. Deleting
     * an id the vault does not hold is a no-op, matching the vocabulary's own
     * "deleting an unsaved word is not an error".
     */
    fun delete(id: String) {
        if (local.get(id) == null) return
        local.remove(id)
        tombstones.put(Tombstone(id = id, updatedAt = clock.now(), deviceId = deviceId))
    }

    /** Reconciles the local vault and the target, and reports what moved. */
    suspend fun sync(): SyncReport {
        val remoteVersions = fetchRemote()
        val outcome = VaultMerge.merge(local = localVersions(), remote = remoteVersions)
        val appliedLocally = applyLocally(outcome)
        val remoteWrites = applyRemotely(outcome)
        return SyncReport(
            uploaded = remoteWrites.uploaded,
            downloaded = appliedLocally + remoteWrites.downloaded,
            deletions = outcome.toDeleteLocally.size + outcome.toDeleteRemotely.size,
            unresolved = remoteWrites.unresolved,
        )
    }

    /**
     * The target's current items: a full listing where there is no cursor, or a
     * change set applied to the index where there is. A null cursor asks a change
     * set for everything, so the first sync is the same shape either way.
     */
    private suspend fun fetchRemote(): List<VersionedRecord> {
        if (!target.capabilities().changeCursor) {
            return target.list().also(remote::replaceAll).map { it.version }
        }
        val changes = target.changes(cursor)
        cursor = changes.cursor
        remote.apply(changes.items)
        return remote.all().map { it.version }
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
        val results = versionsOf(outcome).map { version -> writeRemotely(version) }
        return RemoteWrites(
            uploaded = results.count { it == WriteResult.Landed },
            downloaded = results.count { it == WriteResult.Adopted },
            unresolved = results.count { it == WriteResult.Unresolved },
        )
    }

    /** The versions the local side won, records and tombstones alike. */
    private fun versionsOf(outcome: MergeOutcome): List<VersionedRecord> =
        outcome.toWriteRemotely.map { VersionedRecord.of(it) } +
            outcome.toDeleteRemotely.map { VersionedRecord.of(it) }

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
            when (val outcome = target.put(version, expectedRevision(version))) {
                is WriteOutcome.Written -> {
                    remote.upsert(SyncItem(version, outcome.revision))
                    result = WriteResult.Landed
                }

                is WriteOutcome.Conflicted -> result = settle(version, outcome)
            }
        }
        return result
    }

    /** The revision to condition [version]'s write on, where the target compares-and-swaps. */
    private fun expectedRevision(version: VersionedRecord): Revision? =
        if (target.capabilities().conditionalWrites) remote.get(version.id)?.revision else null

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
