package app.lekto.core.vault

/**
 * The outcome of merging two sides of a vault: what changed on each side for the
 * other to adopt, and what was deleted where.
 *
 * The merge itself is in [VaultMerge.merge]; this value is how the engine that
 * actually moves bytes learns what to copy, without the merge touching a
 * [VaultStore].
 */
data class MergeOutcome(
    /** Records the local side must write: the ones the remote side won. */
    val toWriteLocally: List<VaultRecord>,

    /** Records the remote side must write: the ones the local side won. */
    val toWriteRemotely: List<VaultRecord>,

    /** Tombstones the local side must store, replacing its old live copy. */
    val toDeleteLocally: List<Tombstone>,

    /** Tombstones the remote side must store, replacing its old live copy. */
    val toDeleteRemotely: List<Tombstone>,
)

/**
 * Merges two sides of a vault record by record (ADR-0004; ticket #14).
 *
 * For each record id present on either side, the versions are compared with
 * [RecordMerge] and the winner decides: a live winner is written to the side
 * that lacks it (or holds a stale copy), and a tombstone winner has its
 * tombstone stored on the losing side in place of the old live copy. Ids known
 * to neither side do not appear in the outcome.
 *
 * The merge is a pure fold over the two lists — it reads no clock and writes
 * nothing — so calling it with the sides swapped yields the mirror outcome when
 * every contested id carries distinct device ids, and applying the outcome
 * always converges. [RecordMerge] proves the order-independence; this only
 * groups by id, which the property tests pin for the whole vault.
 *
 * @throws IllegalArgumentException when the same side carries two versions of
 *   one id: a vault holds one file per id (ADR-0003), so a side that violates
 *   that is corrupt rather than a merge to resolve.
 */
object VaultMerge {

    fun merge(local: List<VersionedRecord>, remote: List<VersionedRecord>): MergeOutcome {
        requireDistinct(local, "local")
        requireDistinct(remote, "remote")

        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }

        val builder = OutcomeBuilder()
        (localById.keys + remoteById.keys).forEach { id ->
            val current = localById[id]
            val other = remoteById[id]
            val winner = when {
                current == null -> other ?: error("id '$id' is present but has no version")
                other == null -> current
                else -> RecordMerge.winner(current, other)
            }
            if (current != winner) builder.side(toLocal = true, winner)
            if (other != winner) builder.side(toLocal = false, winner)
        }
        return builder.build()
    }

    private fun requireDistinct(versions: List<VersionedRecord>, side: String) {
        val ids = versions.map { it.id }
        require(ids.size == ids.toSet().size) {
            "the $side vault carries more than one version of an id; a vault holds one file per id"
        }
    }
}

/**
 * Accumulates what each side must adopt, so [VaultMerge.merge] reads as the shape
 * of the outcome rather than four parallel mutations.
 */
private class OutcomeBuilder {
    private val toWriteLocally = mutableListOf<VaultRecord>()
    private val toWriteRemotely = mutableListOf<VaultRecord>()
    private val toDeleteLocally = mutableListOf<Tombstone>()
    private val toDeleteRemotely = mutableListOf<Tombstone>()

    /** Records that [local] must adopt [winner]; [toLocal] says which side lacks it. */
    fun side(toLocal: Boolean, winner: VersionedRecord) {
        val writes = if (toLocal) toWriteLocally else toWriteRemotely
        val deletes = if (toLocal) toDeleteLocally else toDeleteRemotely
        when (winner) {
            is VersionedRecord.Live -> writes += winner.record
            is VersionedRecord.Deleted -> deletes += winner.tombstone
        }
    }

    fun build(): MergeOutcome = MergeOutcome(
        toWriteLocally = toWriteLocally.sortedBy { it.id },
        toWriteRemotely = toWriteRemotely.sortedBy { it.id },
        toDeleteLocally = toDeleteLocally.sortedBy { it.id },
        toDeleteRemotely = toDeleteRemotely.sortedBy { it.id },
    )
}
