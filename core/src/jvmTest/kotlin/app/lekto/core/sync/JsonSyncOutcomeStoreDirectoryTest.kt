package app.lekto.core.sync

import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.SyncOutcomeStoreContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files

/**
 * The JSON outcome store over a real directory (issue #121; ADR-0028): the same
 * [SyncOutcomeStoreContract] the in-memory store passes, plus the file layout
 * only the filesystem can show. The store is rooted at its own directory, beside
 * the vault and the sync settings rather than inside the vault.
 */
class JsonSyncOutcomeStoreDirectoryTest :
    FunSpec({

        val roots = mutableListOf<File>()
        fun newRoot(): File = Files.createTempDirectory("lekto-sync-outcome").toFile().also(roots::add)

        afterSpec { roots.forEach(File::deleteRecursively) }

        SyncOutcomeStoreContract {
            JsonSyncOutcomeStore(JvmVaultFileSystem(newRoot()))
        }.cases().forEach { case ->
            test(case.name) { case.body() }
        }

        test("the outcome is kept in one document under the store's root") {
            val root = newRoot()
            JsonSyncOutcomeStore(JvmVaultFileSystem(root)).save(SyncOutcome(succeeded = true, message = "Synced."))

            root.resolve(JsonSyncOutcomeStore.PATH).isFile shouldBe true
        }
    })
