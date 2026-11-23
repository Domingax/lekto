package app.lekto.core.book

import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * The sample's `sampleDismissed` document (issue #278): a document this version
 * cannot read reads as not dismissed rather than a crash or an invented removal,
 * and the record lands at the store's own path so it never touches a vault
 * record or its export. It is a document of its own so a corrupt first-launch
 * guide cannot resurrect a sample the reader removed (#277).
 */
class JsonSampleDismissedStoreTest :
    FunSpec({

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonSampleDismissedStore(files).save(SampleDismissed("fr"))

            files.listFiles() shouldBe listOf(JsonSampleDismissedStore.PATH)
        }

        test("a corrupt document reads as not dismissed") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonSampleDismissedStore.PATH, "not json at all".encodeToByteArray())

            JsonSampleDismissedStore(files).load() shouldBe SampleDismissed.NONE
        }

        test("a document written by a future shape reads as not dismissed") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(
                JsonSampleDismissedStore.PATH,
                """{"language":"fr","removedBy":"a later build"}""".encodeToByteArray(),
            )

            JsonSampleDismissedStore(files).load() shouldBe SampleDismissed.NONE
        }

        test("an absent document reads as not dismissed") {
            JsonSampleDismissedStore(InMemoryVaultFileSystem()).load() shouldBe SampleDismissed.NONE
        }

        test("the dismissal never lands in the vault or its export (issue #278)") {
            val settings = InMemoryVaultFileSystem()
            val vault = InMemoryVaultStore()

            JsonSampleDismissedStore(settings).save(SampleDismissed("fr"))
            vault.put(testVaultRecord("vocabulary"))

            // The record lives under the settings root, not the vault's kinds, so it
            // is structurally absent from an export and a sync (ADR-0005).
            settings.listFiles() shouldBe listOf(JsonSampleDismissedStore.PATH)
            vault.all().map { record -> record.kind } shouldBe listOf("vocabulary")
            vault.exportBundle().decodeToString() shouldNotContain "sample-dismissed"
        }
    })
