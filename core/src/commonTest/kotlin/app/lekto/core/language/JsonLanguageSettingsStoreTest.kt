package app.lekto.core.language

import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * The language settings' JSON document store (ADR-0034; ADR-0023's pattern): a
 * document this version cannot read reads as "not chosen" rather than a crash, an
 * absent field stays unset, and the choices land at the store's own path so they
 * never touch a vault record.
 */
class JsonLanguageSettingsStoreTest :
    FunSpec({

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonLanguageSettingsStore(files).save(LanguageSettings(appLanguage = "fr"))

            files.listFiles() shouldBe listOf(JsonLanguageSettingsStore.PATH)
        }

        test("a corrupt document reads as not chosen") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonLanguageSettingsStore.PATH, "not json at all".encodeToByteArray())

            JsonLanguageSettingsStore(files).load() shouldBe LanguageSettings.NONE
        }

        test("a document with no fields reads as not chosen") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonLanguageSettingsStore.PATH, """{"appLanguage":""}""".encodeToByteArray())

            JsonLanguageSettingsStore(files).load() shouldBe LanguageSettings.NONE
        }

        test("an absent document reads as not chosen") {
            JsonLanguageSettingsStore(InMemoryVaultFileSystem()).load() shouldBe LanguageSettings.NONE
        }

        test("the choices never land in the vault or its export (ADR-0034)") {
            val settings = InMemoryVaultFileSystem()
            val vault = InMemoryVaultStore()

            JsonLanguageSettingsStore(settings).save(LanguageSettings(appLanguage = "fr", translationLanguage = "es"))
            vault.put(testVaultRecord("vocabulary"))

            // The choices live under the settings root, not the vault's kinds, so they
            // are structurally absent from an export and a sync (ADR-0005, ADR-0034).
            settings.listFiles() shouldBe listOf(JsonLanguageSettingsStore.PATH)
            vault.all().map { record -> record.kind } shouldBe listOf("vocabulary")
            vault.exportBundle().decodeToString() shouldNotContain "language-settings"
        }
    })
