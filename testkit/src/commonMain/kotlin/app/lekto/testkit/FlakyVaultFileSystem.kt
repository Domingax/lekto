package app.lekto.testkit

import app.lekto.core.vault.VaultFileSystem

/**
 * A [VaultFileSystem] that can be told to fail a write by path.
 *
 * It exists to test the vault's crash behaviour: a write that fails must leave
 * the previous record readable and the vault consistent. A test wraps an
 * [InMemoryVaultFileSystem] and sets [failOn] to the path it wants to fail.
 */
class FlakyVaultFileSystem(private val delegate: VaultFileSystem = InMemoryVaultFileSystem()) : VaultFileSystem {

    /** Fails the write of any path this predicate matches. */
    var failOn: (String) -> Boolean = { false }

    override fun read(path: String): ByteArray? = delegate.read(path)

    override fun writeAtomically(path: String, bytes: ByteArray) {
        if (failOn(path)) throw SimulatedWriteFailure()
        delegate.writeAtomically(path, bytes)
    }

    override fun delete(path: String) {
        delegate.delete(path)
    }

    override fun listFiles(): List<String> = delegate.listFiles()

    override fun storedPath(path: String): String = delegate.storedPath(path)
}

/** The failure [FlakyVaultFileSystem] throws in place of a real I/O error. */
class SimulatedWriteFailure : Exception("simulated vault write failure")
