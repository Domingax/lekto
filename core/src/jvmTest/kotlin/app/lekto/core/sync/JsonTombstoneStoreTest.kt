package app.lekto.core.sync

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.TombstoneStoreContract
import app.lekto.testkit.testTombstone
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

/**
 * The JSON tombstone store over a real directory: the same
 * [TombstoneStoreContract] the in-memory store passes, plus the file layout only
 * the filesystem can show (ticket #26; ADR-0015). The store is rooted at its own
 * directory, beside the vault rather than inside it.
 */
class JsonTombstoneStoreTest :
    FunSpec({

        val roots = mutableListOf<File>()
        fun newRoot(): File = Files.createTempDirectory("lekto-tombstones").toFile().also(roots::add)

        afterSpec { roots.forEach(File::deleteRecursively) }

        TombstoneStoreContract { JsonTombstoneStore(JvmVaultFileSystem(newRoot())) }.cases().forEach { case ->
            test(case.name) { case.body() }
        }

        test("tombstones are kept in one document under the store's root") {
            val root = newRoot()
            JsonTombstoneStore(JvmVaultFileSystem(root)).put(testTombstone("a"))

            root.resolve("tombstones.json").isFile shouldBe true
        }
    })
