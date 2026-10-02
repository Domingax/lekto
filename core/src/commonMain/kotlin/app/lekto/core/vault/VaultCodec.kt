package app.lekto.core.vault

import kotlinx.serialization.json.Json

/**
 * The one place the vault's JSON is configured, so the format is a decision this
 * module owns rather than a property spread across call sites.
 *
 * `prettyPrint` keeps a record readable and diffable, which ADR-0003 gives as the
 * reason for per-record JSON over an opaque database. `encodeDefaults` writes
 * `schemaVersion` and `formatVersion` even when they equal the current default:
 * a record read five years from now must say what it was written as, not leave
 * it to this build's default. `ignoreUnknownKeys` lets a newer build's export
 * be read by an older one for the fields it knows.
 */
object VaultCodec {

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encodeRecord(record: VaultRecord): String = json.encodeToString(VaultRecord.serializer(), record)

    fun decodeRecord(text: String): VaultRecord = json.decodeFromString(VaultRecord.serializer(), text)

    fun encodeManifest(manifest: VaultManifest): String = json.encodeToString(VaultManifest.serializer(), manifest)

    fun encodeBundle(bundle: VaultBundle): String = json.encodeToString(VaultBundle.serializer(), bundle)

    fun decodeBundle(text: String): VaultBundle = json.decodeFromString(VaultBundle.serializer(), text)
}
