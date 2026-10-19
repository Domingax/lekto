package app.lekto.testkit

import app.lekto.core.vault.VaultFileSystem
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * A [VaultFileSystem] held in memory: the fake the vault is tested through
 * without touching a disk (docs/research/testing-harness.md §7).
 *
 * It satisfies the same contract as the real filesystem — [writeAtomically]
 * replaces whole values, so a reader never sees a partial write — and the
 * vault's own logic is the real one, so a test exercises behaviour rather than
 * a stand-in for it.
 *
 * The fake is shared by the whole application's tests, which read and write the
 * vault from several threads the way the app does (issue #194), so the files are
 * an immutable snapshot every change replaces rather than a mutable map. A
 * listing therefore sees one consistent tree, never a map caught mid-write, and
 * two writers cannot lose each other's records.
 */
@OptIn(ExperimentalAtomicApi::class)
class InMemoryVaultFileSystem : VaultFileSystem {

    private val files = AtomicReference<Map<String, ByteArray>>(emptyMap())

    override fun read(path: String): ByteArray? = files.load()[path]?.copyOf()

    override fun writeAtomically(path: String, bytes: ByteArray) {
        replace { current -> current + (path to bytes.copyOf()) }
    }

    override fun delete(path: String) {
        replace { current -> current - path }
    }

    override fun listFiles(): List<String> = files.load().keys.sorted()

    override fun storedPath(path: String): String = path

    /** Applies [change] to the current snapshot, retrying when another writer landed first. */
    private inline fun replace(change: (Map<String, ByteArray>) -> Map<String, ByteArray>) {
        while (true) {
            val current = files.load()
            if (files.compareAndSet(current, change(current))) return
        }
    }
}
