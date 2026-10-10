package app.lekto.core.vault

import app.lekto.testkit.VaultStoreContract
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith

/**
 * The vault over a real directory: the same [VaultStoreContract] the in-memory
 * store passes, plus the file-layout and atomic-write facts only the filesystem
 * can show (ticket #12; ADR-0003).
 */
class JvmVaultStoreTest :
    FunSpec({

        val roots = mutableListOf<File>()
        fun newRoot(): File = Files.createTempDirectory("lekto-vault").toFile().also(roots::add)

        afterSpec { roots.forEach(File::deleteRecursively) }

        VaultStoreContract { JsonVaultStore(JvmVaultFileSystem(newRoot())) }.cases().forEach { case ->
            test(case.name) { case.body() }
        }

        test("records are written one JSON file each under <kind>/<id>.json") {
            val root = newRoot()
            val store = JsonVaultStore(JvmVaultFileSystem(root))
            store.put(testVaultRecord("a"))
            store.put(testVaultRecord("b", kind = "progress"))

            root.resolve("manifest.json").isFile shouldBe true
            root.resolve("vocabulary/a.json").isFile shouldBe true
            root.resolve("progress/b.json").isFile shouldBe true
        }

        test("an atomic write replaces the previous bytes without a temporary file left behind") {
            val root = newRoot()
            val files = JvmVaultFileSystem(root)
            files.writeAtomically("a/b.json", "first".encodeToByteArray())
            files.writeAtomically("a/b.json", "second".encodeToByteArray())

            files.read("a/b.json")!!.decodeToString() shouldBe "second"
            root.resolve("a").listFiles()!!.map { it.name } shouldBe listOf("b.json")
        }

        test("the filesystem refuses a path that escapes the vault root") {
            val files = JvmVaultFileSystem(newRoot())

            assertFailsWith<IllegalArgumentException> { files.writeAtomically("../escape.json", byteArrayOf()) }
            assertFailsWith<IllegalArgumentException> { files.writeAtomically("a/../../escape.json", byteArrayOf()) }
        }

        test("an atomic write replaces the target in one move rather than deleting it first") {
            val root = newRoot()
            val files = JvmVaultFileSystem(root)
            // A rename cannot replace a directory with a file. The write must fail
            // and leave the target alone, not delete it to make room — the retired
            // delete-then-rename fallback opened a window with no file (ADR-0003).
            root.resolve("a/b.json").mkdirs()

            assertFailsWith<IOException> { files.writeAtomically("a/b.json", "bytes".encodeToByteArray()) }
            root.resolve("a/b.json").isDirectory shouldBe true
        }

        test("a reader never observes a half-written file while the store replaces it") {
            val files = JvmVaultFileSystem(newRoot())
            // A body large enough that a non-atomic write would be observable
            // mid-write; the atomic move means a reader only ever sees one whole
            // version or the other, and never the moment-with-no-file the retired
            // delete-then-rename fallback opened.
            val first = ByteArray(TORN_WRITE_BYTES) { 0x11 }
            val second = ByteArray(TORN_WRITE_BYTES) { 0x22 }
            files.writeAtomically("a/b.json", first)

            val writing = AtomicBoolean(true)
            val reads = AtomicInteger()
            val observedFailure = AtomicReference<Throwable?>(null)
            val reader = thread(name = "vault-reader") {
                try {
                    while (writing.get()) {
                        val bytes = files.read("a/b.json")
                            ?: error("a reader observed a moment with no file")
                        reads.incrementAndGet()
                        if (!bytes.contentEquals(first) && !bytes.contentEquals(second)) {
                            error("a reader observed a half-written file: ${bytes.size} bytes")
                        }
                    }
                } catch (failure: Throwable) {
                    observedFailure.set(failure)
                }
            }
            try {
                repeat(REPLACEMENTS) { index ->
                    files.writeAtomically("a/b.json", if (index % 2 == 0) second else first)
                }
            } finally {
                writing.set(false)
                reader.join()
            }

            observedFailure.get()?.let { throw it }
            // The reader raced the replacements, so the guards above were exercised.
            (reads.get() > 0) shouldBe true
        }

        test("an atomic write fails when it cannot replace the target") {
            val root = newRoot()
            val files = JvmVaultFileSystem(root)
            nonEmptyDirectoryAt(root, "a/b.json")

            assertFailsWith<IOException> { files.writeAtomically("a/b.json", byteArrayOf()) }
        }

        test("deleting an absent path is not an error") {
            JvmVaultFileSystem(newRoot()).delete("nothing.json")
        }

        test("deleting a path that will not delete fails") {
            val root = newRoot()
            val files = JvmVaultFileSystem(root)
            nonEmptyDirectoryAt(root, "a/b.json")

            assertFailsWith<IOException> { files.delete("a/b.json") }
        }

        test("the export is a single file that restores the vault") {
            val store = JsonVaultStore(JvmVaultFileSystem(newRoot()))
            store.put(testVaultRecord("a"))
            val export = newRoot().resolve("vault.export")
            export.writeBytes(store.exportBundle())

            val restored = JsonVaultStore(JvmVaultFileSystem(newRoot()))
            restored.importBundle(export.readBytes())

            export.isFile shouldBe true
            restored.all().map { it.id } shouldBe listOf("a")
        }
    }) {

    private companion object {
        /** Large enough that a non-atomic write would be caught mid-flight. */
        const val TORN_WRITE_BYTES: Int = 128 * 1024

        /** The number of replacements the reader races against. */
        const val REPLACEMENTS: Int = 200
    }
}

/** A non-empty directory at [path] under [root], so no file can be written or deleted in its place. */
private fun nonEmptyDirectoryAt(root: File, path: String) {
    root.resolve("$path/child").apply { parentFile.mkdirs() }.writeText("occupies the target")
}
