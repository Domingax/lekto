package app.lekto.core.sync

import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The JSON remote-index store's resilience (ADR-0027): a document this version
 * cannot read is an empty memory rather than a crash, so the engine falls back to
 * a full listing, and a replace lands at the store's own path so the memory never
 * touches a vault record.
 */
class JsonRemoteIndexStoreTest :
    FunSpec({

        test("a corrupt document reads as an empty memory") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonRemoteIndexStore.FILE, "not json at all".encodeToByteArray())

            JsonRemoteIndexStore(files).all() shouldBe emptyList()
        }

        test("a replace writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonRemoteIndexStore(files).replaceAll(emptyList())

            files.listFiles() shouldBe listOf(JsonRemoteIndexStore.FILE)
        }
    })
