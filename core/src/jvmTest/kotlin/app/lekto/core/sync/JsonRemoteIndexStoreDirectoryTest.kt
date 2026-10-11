package app.lekto.core.sync

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.core.vault.VersionedRecord
import app.lekto.testkit.RemoteIndexStoreContract
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

/**
 * The JSON remote-index store over a real directory: the same
 * [RemoteIndexStoreContract] the in-memory store passes, plus the file layout
 * only the filesystem can show (ADR-0027). The store is rooted at its own
 * directory, beside the vault and the tombstones rather than inside the vault.
 */
class JsonRemoteIndexStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()
        fun newRoot(): File = Files.createTempDirectory("lekto-remote-index").toFile().also(roots::add)

        afterSpec { roots.forEach(File::deleteRecursively) }

        RemoteIndexStoreContract {
            JsonRemoteIndexStore(JvmVaultFileSystem(newRoot()))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }

        test("the index is kept in one document under the store's root") {
            val root = newRoot()
            JsonRemoteIndexStore(JvmVaultFileSystem(root)).replaceAll(
                listOf(SyncItem(VersionedRecord.of(testVaultRecord("a")), Revision("rev-a"))),
            )

            root.resolve("remote-index.json").isFile shouldBe true
        }
    })
