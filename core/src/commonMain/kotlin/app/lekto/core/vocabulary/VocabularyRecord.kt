@file:Suppress("MagicNumber") // The FNV-1a constants are the algorithm, named by the comment.

package app.lekto.core.vocabulary

import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultRecord
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/**
 * How a [VocabularyEntry] is stored in the vault (ADR-0003): a record of kind
 * [KIND] whose body is the entry itself.
 *
 * The record id is derived from the entry's [WordKey] ([idOf]), so every device
 * that saves the same word derives the same id and the two records merge
 * last-writer-wins (ADR-0004) instead of becoming duplicates. It is a stable
 * hash rather than the key itself: a word key is arbitrary Unicode and a long
 * phrase could overflow a filename, while a fixed 16-hex-digit digest is always
 * a legal one. A collision would merge two words' entries, which is accepted for
 * a single-user vault at 2⁻⁶⁴ odds.
 */
object VocabularyRecord {

    /** The vault `kind` directory vocabulary entries live under. */
    const val KIND: String = "vocabulary"

    /** The schema version of the body this build writes. */
    const val SCHEMA_VERSION: Int = 1

    /** The deterministic record id for [key]. */
    fun idOf(key: WordKey): String = "word-${fnv1a64(key.language.orEmpty() + '\u0000' + key.key)}"

    /** The record that stores [entry], stamped with [updatedAt] and [deviceId]. */
    fun of(entry: VocabularyEntry, updatedAt: Instant, deviceId: DeviceId): VaultRecord = VaultRecord(
        id = idOf(entry.key),
        kind = KIND,
        schemaVersion = SCHEMA_VERSION,
        updatedAt = updatedAt,
        deviceId = deviceId,
        body = VaultCodec.json.encodeToJsonElement(VocabularyEntry.serializer(), entry).jsonObject,
    )

    /** The [VocabularyEntry] a record of kind [KIND] carries. */
    fun entryOf(record: VaultRecord): VocabularyEntry =
        VaultCodec.json.decodeFromJsonElement(VocabularyEntry.serializer(), record.body)

    /**
     * FNV-1a, 64-bit, over [text]'s UTF-8 bytes: a deterministic, filename-safe
     * digest that is identical on every platform Kotlin targets.
     */
    private fun fnv1a64(text: String): String {
        var hash = 14695981039346656037UL.toLong()
        text.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and 0xFF)
            hash *= 1099511628211UL.toLong()
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }
}
