package app.lekto.core.theme

import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.testVaultRecord
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * The **App theme**'s JSON document store (ADR-0036; ADR-0023's pattern): a
 * document this version cannot read reads as **System**, a paper the app no
 * longer knows degrades the same way, and the choice lands at the store's own path
 * so it never touches a vault record or its export.
 */
class JsonAppThemeStoreTest :
    FunSpec({

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonAppThemeStore(files).save(AppTheme.DARK)

            files.listFiles() shouldBe listOf(JsonAppThemeStore.PATH)
        }

        test("a corrupt document reads as System") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonAppThemeStore.PATH, "not json at all".encodeToByteArray())

            JsonAppThemeStore(files).load() shouldBe AppTheme.SYSTEM
        }

        test("a paper this build does not know reads as System") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonAppThemeStore.PATH, """{"theme":"NEON"}""".encodeToByteArray())

            JsonAppThemeStore(files).load() shouldBe AppTheme.SYSTEM
        }

        test("an absent document reads as System") {
            JsonAppThemeStore(InMemoryVaultFileSystem()).load() shouldBe AppTheme.SYSTEM
        }

        test("the theme never lands in the vault or its export (ADR-0036)") {
            val settings = InMemoryVaultFileSystem()
            val vault = InMemoryVaultStore()

            JsonAppThemeStore(settings).save(AppTheme.SEPIA)
            vault.put(testVaultRecord("vocabulary"))

            // The theme lives under the settings root, not the vault's kinds, so it is
            // structurally absent from an export and a sync (ADR-0005, ADR-0036).
            settings.listFiles() shouldBe listOf(JsonAppThemeStore.PATH)
            vault.all().map { record -> record.kind } shouldBe listOf("vocabulary")
            vault.exportBundle().decodeToString() shouldNotContain "app-theme"
        }
    })
