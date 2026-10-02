package app.lekto.core.vault

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The mark a deleted record leaves behind, so the deletion survives a sync
 * (ADR-0009, "the engine adds tombstones for deletions").
 *
 * Without it an absent record is indistinguishable from a record that has not
 * synced yet — every other device still holds it and would copy it back, so a
 * delete would resurrect. A tombstone keeps the record's [id] and its
 * last-writer-wins metadata ([updatedAt] / [deviceId]) but drops the [body]:
 * an id and a timestamp are all that is needed to out-vote a live record, and
 * keeping the body would retain exactly what the user asked to delete.
 *
 * It is not a [VaultRecord]; a tombstone is not user data the vault stores or
 * exports, it is a comparison key the merge in [RecordMerge] reads alongside a
 * record.
 */
@Serializable
data class Tombstone(
    val id: String,
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant,
    val deviceId: DeviceId,
)
