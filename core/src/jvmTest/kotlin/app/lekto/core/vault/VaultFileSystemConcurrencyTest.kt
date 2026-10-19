package app.lekto.core.vault

import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The storage seam is read and written from several threads: composition and
 * the reader read the vault while a save lands on the injected dispatcher, and
 * sync reads it on its own (issue #194). Both implementations must answer a
 * listing consistently, without throwing because a writer changed the tree
 * mid-walk.
 */
class VaultFileSystemConcurrencyTest :
    FunSpec({

        test("the in-memory filesystem lists while a writer changes the tree") {
            InMemoryVaultFileSystem().also(::seed).raceAListing()
        }

        test("the directory filesystem lists while a writer changes the tree") {
            val root = Files.createTempDirectory("lekto-vault-race").toFile()
            try {
                val reader = JvmVaultFileSystem(root).also(::seed)
                // A second instance over the same root: the app opens several for
                // its derived assets, so the lock must be shared per root.
                reader.raceAListing(writer = JvmVaultFileSystem(root))
            } finally {
                root.deleteRecursively()
            }
        }

        test("concurrent writers to different paths all land") {
            val files = InMemoryVaultFileSystem()
            val writers = List(WRITERS) { writer ->
                thread(name = "vault-writer-$writer") {
                    repeat(WRITES_PER_WRITER) { index ->
                        files.writeAtomically("books/$writer-$index.json", byteArrayOf(writer.toByte()))
                    }
                }
            }
            writers.forEach(Thread::join)

            files.listFiles().size shouldBe WRITERS * WRITES_PER_WRITER
            files.listFiles() shouldBe files.listFiles().sorted()
        }
    })

/** Enough records that a listing takes long enough to race a write. */
private const val SEEDED_RECORDS: Int = 1_000

/** How many create/delete pairs the writer runs against the listing. */
private const val MUTATIONS: Int = 10_000

private const val WRITERS: Int = 4
private const val WRITES_PER_WRITER: Int = 100

/** Seeds [this] with [SEEDED_RECORDS] records, so a listing has a tree to walk. */
private fun seed(files: VaultFileSystem) {
    repeat(SEEDED_RECORDS) { index ->
        files.writeAtomically("books/$index.json", byteArrayOf(1))
    }
}

/**
 * Lists [this] continuously while another thread creates and deletes records.
 *
 * A path listed and then deleted reads as absent, which is not a failure; the
 * guard is that [VaultFileSystem.listFiles] itself never throws because the
 * tree moved underneath it.
 */
private fun VaultFileSystem.raceAListing(writer: VaultFileSystem = this) {
    val start = CountDownLatch(1)
    val stop = AtomicBoolean(false)
    val failure = AtomicReference<Throwable?>(null)
    val reader = thread(name = "vault-listing-reader") {
        try {
            start.await()
            while (!stop.get()) listFiles().forEach { read(it) }
        } catch (thrown: Throwable) {
            failure.compareAndSet(null, thrown)
        }
    }
    val writerThread = thread(name = "vault-listing-writer") {
        try {
            start.await()
            repeat(MUTATIONS) { index ->
                writer.writeAtomically("books/w$index.json", byteArrayOf(1))
                writer.delete("books/w$index.json")
            }
        } catch (thrown: Throwable) {
            failure.compareAndSet(null, thrown)
        }
    }
    start.countDown()
    writerThread.join()
    stop.set(true)
    reader.join()

    failure.get()?.let { thrown -> throw AssertionError("the listing raced a writer", thrown) }
}
