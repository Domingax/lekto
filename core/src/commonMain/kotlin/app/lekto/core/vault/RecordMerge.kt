package app.lekto.core.vault

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A record's content at a point in time, without the envelope the merge compares
 * on (ADR-0004): a [Tombstone] for a deleted record, or a [VaultRecord] for a
 * live one.
 *
 * The envelope's fields ([id], [updatedAt], [deviceId]) still drive the choice,
 * which is why this is a distinct type rather than a nullable record: a
 * `VaultRecord?` could not carry the deletion's own timestamp, and no body means
 * the merge never has to invent a payload for a tombstone.
 */
@Serializable
sealed interface VersionedRecord {
    /** The record's identity, the unit last-writer-wins resolves at (ADR-0004). */
    val id: String

    /** When this version was written; the later one wins. */
    val updatedAt: Instant

    /** Which device wrote this version; the tie-break for equal [updatedAt]. */
    val deviceId: DeviceId

    /** A live record. */
    @Serializable
    data class Live(val record: VaultRecord) : VersionedRecord {
        override val id: String get() = record.id
        override val updatedAt: Instant get() = record.updatedAt
        override val deviceId: DeviceId get() = record.deviceId
    }

    /** A deletion: the record's envelope without its body. */
    @Serializable
    data class Deleted(val tombstone: Tombstone) : VersionedRecord {
        override val id: String get() = tombstone.id
        override val updatedAt: Instant get() = tombstone.updatedAt
        override val deviceId: DeviceId get() = tombstone.deviceId
    }

    companion object {
        /** The [VersionedRecord] a live [record] becomes. */
        fun of(record: VaultRecord): VersionedRecord = Live(record)

        /** The [VersionedRecord] a [tombstone] becomes. */
        fun of(tombstone: Tombstone): VersionedRecord = Deleted(tombstone)
    }
}

/**
 * Last-writer-wins per record, the comparison-and-fold at the heart of sync
 * (ADR-0004; ticket #14).
 *
 * The winner is the version with the later [VersionedRecord.updatedAt]; a tie is
 * broken deterministically on [VersionedRecord.deviceId], so two devices that
 * edited the same record offline converge on one value without a server
 * deciding. Merging is *pure*: no clock is read and nothing is written, so the
 * same inputs always give the same result.
 *
 * The properties the ticket asks for fall out of [winner] being a **total
 * order** on versions of one record:
 *
 * - **idempotent** — `winner(a, a)` is `a`;
 * - **commutative** for distinct device ids — `winner` ignores its arguments'
 *   order, and with distinct `deviceId`s the tie-break leaves no tie, so
 *   `winner(a, b) == winner(b, a)`;
 * - **convergent** — [mergeAll] is a fold of an associative, commutative
 *   operation, so the same set yields the same result in any order.
 *
 * When the same device wrote both versions (a repeated `updatedAt`), [winner]
 * returns its *first* argument: the two are indistinguishable as keys, and the
 * merge stays deterministic because the fold visits each id's versions in a
 * stable order.
 */
object RecordMerge {

    /**
     * The winner between [a] and [b]: the later [VersionedRecord.updatedAt],
     * [a] on an exact tie, else the greater [VersionedRecord.deviceId] — the
     * deterministic tie-break ADR-0004 requires.
     */
    fun winner(a: VersionedRecord, b: VersionedRecord): VersionedRecord = when {
        a.updatedAt > b.updatedAt -> a
        b.updatedAt > a.updatedAt -> b
        b.deviceId.value > a.deviceId.value -> b
        else -> a
    }

    /**
     * The single version that survives among [versions] of one record.
     *
     * @throws IllegalArgumentException when [versions] is empty, because an
     *   empty merge has no winner and silently returning nothing would hide a
     *   caller's mistake.
     */
    fun mergeAll(versions: List<VersionedRecord>): VersionedRecord {
        require(versions.isNotEmpty()) { "cannot merge an empty list of versions" }
        return versions.reduce(::winner)
    }
}
