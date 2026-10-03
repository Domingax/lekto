package app.lekto.core.vault

import kotlin.io.encoding.Base64

/**
 * A record's binary content, stored one file per record under the reserved
 * `_attachments` directory (ADR-0016).
 *
 * It is split from [JsonVaultStore] for the same reason [VaultIndex] is: each
 * class stays small and owns one concern. An attachment has no independent
 * identity — it is written under its record's id, removed with it, and carried
 * in an export beside it.
 */
internal class VaultAttachments(private val files: VaultFileSystem) {

    fun put(id: String, bytes: ByteArray) {
        files.writeAtomically(VaultPaths.attachmentPath(id), bytes)
    }

    fun get(id: String): ByteArray? = files.read(VaultPaths.attachmentPath(id))

    fun remove(id: String) {
        files.delete(VaultPaths.attachmentPath(id))
    }

    /** Every record id that currently has an attachment. */
    fun ids(): List<String> {
        val prefix = "${VaultFormat.ATTACHMENT_DIRECTORY}/"
        return files.listFiles().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }
    }

    /** The attachments as Base64 text, for the JSON export bundle. */
    fun encodeAll(): Map<String, String> = ids().associateWith { id ->
        get(id)?.let(Base64::encode).orEmpty()
    }

    /** Replaces every attachment with [encoded], so an import is exact. */
    fun replaceAll(encoded: Map<String, String>) {
        ids().forEach(::remove)
        encoded.forEach { (id, text) -> put(id, Base64.decode(text)) }
    }
}
