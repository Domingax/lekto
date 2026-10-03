package app.lekto.core.book

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultRecord
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * How a [ReadingPosition] is stored in the vault (ADR-0003): a record of kind
 * [KIND] whose body is the position itself.
 *
 * A record's identity is its `id` alone, so it cannot be the book's id as well —
 * that would make the position record replace the book record. The id is
 * therefore derived from the book's id ([idOf]), deterministic so every device
 * derives the same one and the two position records merge, and carrying the book
 * in the body so the mapping survives an id-scheme change.
 */
object ReadingPositionRecord {

    /** The vault `kind` directory a reading position is stored under. */
    const val KIND: String = "progress"

    /** The schema version of the body this build writes. */
    const val SCHEMA_VERSION: Int = 1

    /** The position record id for [bookId]: distinct from the book's own id. */
    fun idOf(bookId: String): String = "$bookId-position"

    /** The record that stores [position], stamped with [updatedAt] and [deviceId]. */
    fun of(position: ReadingPosition, updatedAt: Instant, deviceId: DeviceId): VaultRecord = VaultRecord(
        id = idOf(position.bookId),
        kind = KIND,
        schemaVersion = SCHEMA_VERSION,
        updatedAt = updatedAt,
        deviceId = deviceId,
        body = VaultCodec.json.encodeToJsonElement(ReadingPosition.serializer(), position).jsonObject,
    )

    /** The [ReadingPosition] a record of kind [KIND] carries. */
    fun positionOf(record: VaultRecord): ReadingPosition =
        VaultCodec.json.decodeFromJsonElement(ReadingPosition.serializer(), record.body)
}
