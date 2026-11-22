package app.lekto.core.guide

import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * The first-launch guide's JSON progress document (ADR-0023's pattern; issue
 * #277): the one fact it keeps is whether the reader has seen the guide, it
 * lives at its own path so it never touches a vault record or an export, and a
 * document this version cannot read reads as **not seen** — the recoverable
 * direction, because showing the guide once more is a nuisance while hiding it
 * from a reader who never saw it loses the explanation the product promises.
 */
class JsonGuideProgressStoreTest :
    FunSpec({

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonGuideProgressStore(files).save(GuideProgress.SEEN)

            files.listFiles() shouldBe listOf(JsonGuideProgressStore.PATH)
        }

        test("an absent document reads as not seen") {
            JsonGuideProgressStore(InMemoryVaultFileSystem()).load() shouldBe GuideProgress.NOT_SEEN
        }

        test("a seen document round-trips") {
            val files = InMemoryVaultFileSystem()

            JsonGuideProgressStore(files).save(GuideProgress.SEEN)

            JsonGuideProgressStore(files).load() shouldBe GuideProgress.SEEN
        }

        test("a corrupt document reads as not seen") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonGuideProgressStore.PATH, "not json at all".encodeToByteArray())

            JsonGuideProgressStore(files).load() shouldBe GuideProgress.NOT_SEEN
        }

        test("a value this build does not know reads as not seen") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonGuideProgressStore.PATH, """{"seen":"perhaps"}""".encodeToByteArray())

            JsonGuideProgressStore(files).load() shouldBe GuideProgress.NOT_SEEN
        }

        test("the progress never lands in the vault or its export (ADR-0005)") {
            val settings = InMemoryVaultFileSystem()
            val vault = InMemoryVaultStore()

            JsonGuideProgressStore(settings).save(GuideProgress.SEEN)
            vault.put(testVaultRecord("vocabulary"))

            // The progress lives under the settings root, not the vault's kinds, so
            // it is structurally absent from an export and a sync (ADR-0005).
            settings.listFiles() shouldBe listOf(JsonGuideProgressStore.PATH)
            vault.all().map { record -> record.kind } shouldBe listOf("vocabulary")
            vault.exportBundle().decodeToString() shouldNotContain "guide.json"
        }
    })
