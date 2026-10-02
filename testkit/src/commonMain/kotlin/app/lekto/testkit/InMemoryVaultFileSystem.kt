package app.lekto.testkit

import app.lekto.core.vault.VaultFileSystem

/**
 * A [VaultFileSystem] held in memory: the fake the vault is tested through
 * without touching a disk (docs/research/testing-harness.md §7).
 *
 * It satisfies the same contract as the real filesystem — [writeAtomically]
 * replaces whole values, so a reader never sees a partial write — and the
 * vault's own logic is the real one, so a test exercises behaviour rather than
 * a stand-in for it.
 */
class InMemoryVaultFileSystem : VaultFileSystem {

    private val files = sortedMapOf<String, ByteArray>()

    override fun read(path: String): ByteArray? = files[path]?.copyOf()

    override fun writeAtomically(path: String, bytes: ByteArray) {
        files[path] = bytes.copyOf()
    }

    override fun delete(path: String) {
        files.remove(path)
    }

    override fun listFiles(): List<String> = files.keys.toList()
}
