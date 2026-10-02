package app.lekto.core.vault

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The constants of the on-disk vault format, named once so the store, the
 * export and the tests cannot drift apart (ADR-0003).
 */
object VaultFormat {
    /** The vault format this build reads and writes. */
    const val VERSION: Int = 1

    /** The vault index every vault carries alongside its record directories. */
    const val MANIFEST_FILE: String = "manifest.json"

    /** The extension of every record file. */
    const val RECORD_EXTENSION: String = "json"
}

/**
 * The vault index: the records present, as [RecordRef]s, without their bodies.
 *
 * ADR-0003 requires a `manifest.json` beside the per-record files. It is an
 * index, not a second source of truth: a store rebuilds it from the record
 * files when it is missing, so a vault stays readable even if the index is
 * lost, and it can never disagree with the files it describes.
 */
@Serializable
data class VaultManifest(val formatVersion: Int = VaultFormat.VERSION, val records: List<RecordRef> = emptyList())

/** The metadata half of a [VaultRecord], as listed in a [VaultManifest]. */
@Serializable
data class RecordRef(
    val id: String,
    val kind: String,
    val schemaVersion: Int,
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant,
    val deviceId: DeviceId,
) {
    companion object {
        /** The reference that indexes [record] in a manifest. */
        fun of(record: VaultRecord): RecordRef = RecordRef(
            id = record.id,
            kind = record.kind,
            schemaVersion = record.schemaVersion,
            updatedAt = record.updatedAt,
            deviceId = record.deviceId,
        )
    }
}
