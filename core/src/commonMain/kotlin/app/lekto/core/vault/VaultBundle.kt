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
 */
@Serializable
data class VaultBundle(
    val formatVersion: Int = VaultFormat.VERSION,
    val manifest: VaultManifest,
    val records: List<VaultRecord>,
)
