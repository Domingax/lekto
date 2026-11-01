package app.lekto.core.book

import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultRecord
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * How a [FinishedBook] is stored in the vault (ADR-0038): a record of kind
 * [KIND] whose id is derived from the book's id ([idOf]) and whose body is the
 * declaration itself — `progress/<bookId>-finished.json`.
 *
 * The id is derived rather than equal to the book's, because a record's identity
 * is its `id` alone: a finished record with the book's id would replace the book
 * record. The separate id also keeps the fact out of the position record, whose
 * `updatedAt` is **Last read**: declaring a book finished is not a page turn and
 * must not move it (ADR-0038).
 */
object FinishedBookRecord {

    /** The vault `kind` directory a finished declaration is stored under. */
    const val KIND: String = "progress"

    /** The schema version of the body this build writes. */
    const val SCHEMA_VERSION: Int = 1

    /** The finished record id for [bookId]: distinct from the book's and from its position's. */
    fun idOf(bookId: String): String = "$bookId-finished"

    /** The record that stores [finished], stamped with [updatedAt] and [deviceId]. */
    fun of(finished: FinishedBook, updatedAt: Instant, deviceId: DeviceId): VaultRecord = VaultRecord(
        id = idOf(finished.bookId),
        kind = KIND,
        schemaVersion = SCHEMA_VERSION,
        updatedAt = updatedAt,
        deviceId = deviceId,
        body = VaultCodec.json.encodeToJsonElement(FinishedBook.serializer(), finished).jsonObject,
    )

    /** The [FinishedBook] a record of kind [KIND] carries. */
    fun finishedOf(record: VaultRecord): FinishedBook =
        VaultCodec.json.decodeFromJsonElement(FinishedBook.serializer(), record.body)
}
