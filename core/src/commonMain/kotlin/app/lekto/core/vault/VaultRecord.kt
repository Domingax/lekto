package app.lekto.core.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Instant

/**
 * The uniform envelope every vault record carries (ADR-0003).
 *
 * A record is **one immutable JSON file**: [kind] names its directory and [id]
 * its filename, and the whole file is rewritten through an atomic
 * [VaultFileSystem.writeAtomically] when the record changes — never edited in
 * place. The four fields the ADR requires are [id], [schemaVersion],
 * [updatedAt] and [deviceId]; [body] is the record's own payload, kept as JSON
 * so a later record type (a vocabulary entry, reading progress) can be added
 * without this envelope changing.
 */
@Serializable
data class VaultRecord(
    val id: String,
    val kind: String,
    val schemaVersion: Int,
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant,
    val deviceId: DeviceId,
    val body: JsonObject = JsonObject(emptyMap()),
)
