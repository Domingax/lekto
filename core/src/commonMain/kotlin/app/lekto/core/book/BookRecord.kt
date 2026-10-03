package app.lekto.core.book

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultRecord
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant
/**
 * How a [Book] is stored in the vault (ADR-0003): a record of kind [KIND] whose
 * id is the book's id and whose JSON [VaultRecord.body] is the [Book] itself.
 *
 * The book's original bytes are the record's attachment, not part of the body,
 * because they are binary (ADR-0016); the parsed text is derived and kept
 * elsewhere (ADR-0005).
 */
object BookRecord {

    /** The vault `kind` directory books are stored under. */
    const val KIND: String = "books"

    /** The schema version of the body this build writes. */
    const val SCHEMA_VERSION: Int = 1

    /** The record that stores [book], stamped with [updatedAt] and [deviceId]. */
    fun of(book: Book, updatedAt: Instant, deviceId: DeviceId): VaultRecord = VaultRecord(
        id = book.id,
        kind = KIND,
        schemaVersion = SCHEMA_VERSION,
        updatedAt = updatedAt,
        deviceId = deviceId,
        body = VaultCodec.json.encodeToJsonElement(Book.serializer(), book).jsonObject,
    )

    /** The [Book] a record of kind [KIND] carries. */
    fun bookOf(record: VaultRecord): Book = VaultCodec.json.decodeFromJsonElement(Book.serializer(), record.body)
}
