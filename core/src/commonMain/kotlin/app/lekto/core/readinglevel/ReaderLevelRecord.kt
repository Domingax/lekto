package app.lekto.core.readinglevel

import app.lekto.core.CefrLevel
import app.lekto.core.text.baseLanguage
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vault.VaultRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlin.time.Instant

/** The contents of a **Reader level** record: the language and the level declared in it. */
data class ReaderLevel(val language: String, val level: CefrLevel)

/** The record body, kept private so the stored shape is this file's business alone. */
@Serializable
private data class ReaderLevelBody(val language: String, val level: CefrLevel)

/**
 * How a **Reader level** is stored in the vault (ADR-0030; CONTEXT.md, "Reader
 * level"): a record of kind [KIND] whose body is the level and the language it
 * is held for.
 *
 * The id is derived from the language ([idOf]) — collapsed to its base tag as the
 * pack already does (ADR-0018) — so every device derives the same id and the
 * records merge last-writer-wins (ADR-0004) instead of duplicating. The level is
 * user-authored data, so it lives in the vault and travels in an export and a
 * sync like any record (ADR-0005, ADR-0014).
 */
object ReaderLevelRecord {

    /** The vault `kind` directory reader levels live under. */
    const val KIND: String = "reader-level"

    /** The schema version of the body this build writes. */
    const val SCHEMA_VERSION: Int = 1

    /** The deterministic record id for [language], taken from its base tag. */
    fun idOf(language: String): String = "level-${baseLanguage(language)}"

    /** The record that stores [level] for [language], stamped with [updatedAt] and [deviceId]. */
    fun of(language: String, level: CefrLevel, updatedAt: Instant, deviceId: DeviceId): VaultRecord {
        val base = baseLanguage(language)
        val body = ReaderLevelBody(language = base, level = level)
        return VaultRecord(
            id = idOf(base),
            kind = KIND,
            schemaVersion = SCHEMA_VERSION,
            updatedAt = updatedAt,
            deviceId = deviceId,
            body = VaultCodec.json.encodeToJsonElement(ReaderLevelBody.serializer(), body).jsonObject,
        )
    }

    /** The [ReaderLevel] a record of kind [KIND] carries, or `null` when it cannot be read. */
    fun read(record: VaultRecord): ReaderLevel? {
        if (record.kind != KIND) return null
        return runCatching {
            val body = VaultCodec.json.decodeFromJsonElement(ReaderLevelBody.serializer(), record.body)
            ReaderLevel(body.language, body.level)
        }.getOrNull()
    }
}
