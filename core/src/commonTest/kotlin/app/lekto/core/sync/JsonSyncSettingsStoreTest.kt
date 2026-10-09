package app.lekto.core.sync

import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The JSON document store's resilience (issue #28): a document this version
 * cannot read — corrupt bytes, or a shape a future version wrote — is the
 * default configuration rather than a crash, and a save lands at the store's own
 * path so the endpoint never touches a vault record.
 */
class JsonSyncSettingsStoreTest :
    FunSpec({

        test("a corrupt document reads as the default configuration") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonSyncSettingsStore.PATH, "not json at all".encodeToByteArray())

            JsonSyncSettingsStore(files).load() shouldBe SyncSettings.DEFAULT
        }

        test("a document of the wrong shape reads as the default configuration") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonSyncSettingsStore.PATH, """{"enabled":"not-a-boolean"}""".encodeToByteArray())

            JsonSyncSettingsStore(files).load() shouldBe SyncSettings.DEFAULT
        }

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonSyncSettingsStore(
                files,
            ).save(SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "r"))

            files.listFiles() shouldBe listOf(JsonSyncSettingsStore.PATH)
        }
    })
