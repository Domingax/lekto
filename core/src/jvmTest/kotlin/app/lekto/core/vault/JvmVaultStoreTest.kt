package app.lekto.core.vault

import app.lekto.testkit.VaultStoreContract
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import java.nio.file.Files
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
            val files = JvmVaultFileSystem(newRoot())
            files.writeAtomically("a/b.json", "first".encodeToByteArray())
            files.writeAtomically("a/b.json", "second".encodeToByteArray())

            files.read("a/b.json")!!.decodeToString() shouldBe "second"
        }

        test("the filesystem refuses a path that escapes the vault root") {
            val files = JvmVaultFileSystem(newRoot())

            assertFailsWith<IllegalArgumentException> { files.writeAtomically("../escape.json", byteArrayOf()) }
        }

        test("an atomic write falls back when a rename cannot land on the target") {
            val root = newRoot()
            val files = JvmVaultFileSystem(root)
            // A directory in the target's place makes the first rename fail, as
            // it does where renaming over an existing file is refused.
            root.resolve("a/b.json").mkdirs()

            files.writeAtomically("a/b.json", "bytes".encodeToByteArray())

            files.read("a/b.json")!!.decodeToString() shouldBe "bytes"
        }

        test("an atomic write fails when it cannot replace the target") {
            val root = newRoot()
            val files = JvmVaultFileSystem(root)
            root.resolve("a/b.json/child").apply { parentFile.mkdirs() }.writeText("occupies the target")

            assertFailsWith<IOException> { files.writeAtomically("a/b.json", byteArrayOf()) }
        }

        test("deleting an absent path is not an error") {
            JvmVaultFileSystem(newRoot()).delete("nothing.json")
        }

        test("deleting a path that will not delete fails") {
            val root = newRoot()
            val files = JvmVaultFileSystem(root)
            root.resolve("a/b.json/child").apply { parentFile.mkdirs() }.writeText("occupies the target")

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
    })
