package app.lekto.core.vault

import kotlinx.serialization.Serializable

/**
 * The whole-vault export: everything [VaultStore.exportBundle] writes into a
 * single file, and everything [VaultStore.importBundle] needs to restore a
 * vault exactly (ADR-0010, "portability is export/import").
 *
 * It is a [formatVersion]ed document carrying the [manifest] and every record
 * whole, which is why an import cannot be observationally partial: the vault is
 * replaced with the document's contents in one operation.
 *
 * [attachments] carries the binary content that is not a JSON record — a book's
 * original file — keyed by record id and Base64-encoded, because a JSON document
 * is the one-file export ADR-0014 chose. It is empty for a vault with no binary
 * content.
 */
@Serializable
data class VaultBundle(
    val formatVersion: Int = VaultFormat.VERSION,
    val manifest: VaultManifest,
    val records: List<VaultRecord>,
    val attachments: Map<String, String> = emptyMap(),
)
