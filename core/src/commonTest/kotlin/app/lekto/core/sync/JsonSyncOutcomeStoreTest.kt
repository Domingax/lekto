package app.lekto.core.sync

import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The JSON outcome store's resilience (issue #121): a document this version
 * cannot read — corrupt bytes, or a shape a future version wrote — reads as "no
 * outcome yet" rather than a crash, and a save lands at the store's own path so
 * the outcome never touches a vault record.
 */
class JsonSyncOutcomeStoreTest :
    FunSpec({

        test("a corrupt document reads as no outcome") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonSyncOutcomeStore.PATH, "not json at all".encodeToByteArray())

            JsonSyncOutcomeStore(files).load() shouldBe null
        }

        test("a document of the wrong shape reads as no outcome") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonSyncOutcomeStore.PATH, """{"succeeded":"not-a-boolean"}""".encodeToByteArray())

            JsonSyncOutcomeStore(files).load() shouldBe null
        }

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonSyncOutcomeStore(files).save(SyncOutcome(succeeded = true, message = "Synced: uploaded 1."))

            files.listFiles() shouldBe listOf(JsonSyncOutcomeStore.PATH)
        }

        test("clearing removes the document") {
            val files = InMemoryVaultFileSystem()
            val store = JsonSyncOutcomeStore(files)
            store.save(SyncOutcome(succeeded = false, message = "The last sync didn't finish."))

            store.clear()

            files.listFiles() shouldBe emptyList()
            store.load() shouldBe null
        }
    })
