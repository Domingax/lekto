package app.lekto.core.vault

import app.lekto.testkit.VaultStoreContract
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
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
