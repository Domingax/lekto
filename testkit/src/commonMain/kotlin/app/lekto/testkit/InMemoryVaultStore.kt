package app.lekto.testkit

import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.VaultStore

/**
 * The in-memory [VaultStore] every consumer tests against: the real
 * [JsonVaultStore] over an [InMemoryVaultFileSystem].
 *
 * The store's behaviour is the production behaviour; only the bytes' home
 * differs, which is exactly the storage seam ADR-0010 draws.
 */
class InMemoryVaultStore(
    /** The backing filesystem, exposed so a test can damage it deliberately. */
    val files: InMemoryVaultFileSystem = InMemoryVaultFileSystem(),
) : VaultStore by JsonVaultStore(files)
