package app.lekto.core.reading

import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * The reading preferences' JSON document store (ADR-0035; ADR-0023's pattern): a
 * document this version cannot read reads as the defaults rather than a crash, a
 * value the app no longer knows degrades the same way, and the preference lands at
 * the store's own path so it never touches a vault record or its export.
 */
class JsonReadingPreferencesStoreTest :
    FunSpec({

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonReadingPreferencesStore(files).save(ReadingPreferences(size = 24f))

            files.listFiles() shouldBe listOf(JsonReadingPreferencesStore.PATH)
        }

        test("a corrupt document reads as the defaults") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonReadingPreferencesStore.PATH, "not json at all".encodeToByteArray())

            JsonReadingPreferencesStore(files).load() shouldBe ReadingPreferences.DEFAULT
        }

        test("a value this build does not know reads as the defaults") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(
                JsonReadingPreferencesStore.PATH,
                """{"face":"COMIC_SANS","size":18.0,"margin":"COMFORTABLE"}""".encodeToByteArray(),
            )

            JsonReadingPreferencesStore(files).load() shouldBe ReadingPreferences.DEFAULT
        }

        test("an absent document reads as the defaults") {
            JsonReadingPreferencesStore(InMemoryVaultFileSystem()).load() shouldBe ReadingPreferences.DEFAULT
        }

        test("the preference never lands in the vault or its export (ADR-0035)") {
            val settings = InMemoryVaultFileSystem()
            val vault = InMemoryVaultStore()

            JsonReadingPreferencesStore(settings).save(ReadingPreferences(size = 24f))
            vault.put(testVaultRecord("vocabulary"))

            // The preference lives under the settings root, not the vault's kinds, so
            // it is structurally absent from an export and a sync (ADR-0005, ADR-0035).
            settings.listFiles() shouldBe listOf(JsonReadingPreferencesStore.PATH)
            vault.all().map { record -> record.kind } shouldBe listOf("vocabulary")
            vault.exportBundle().decodeToString() shouldNotContain "reading-preferences"
        }
    })
