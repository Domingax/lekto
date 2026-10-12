package app.lekto.core.readinglevel

import app.lekto.core.CefrLevel
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The **Reader level** over the vault (ADR-0030): a declared level survives a
 * restart, is keyed by the language's base tag, and is read back exactly. It is
 * user-authored data, so it lives in the vault and travels with an export.
 */
class VaultReaderLevelsTest :
    FunSpec({

        fun levels(vault: InMemoryVaultStore = InMemoryVaultStore()): ReaderLevels =
            VaultReaderLevels(vault, deterministicSeams(), DeviceId("device-a"))

        test("a language with no level set has none") {
            levels().levelFor("en") shouldBe null
        }

        test("a set level is read back for the language") {
            val store = levels()

            store.setLevel("en", CefrLevel.B1)

            store.levelFor("en") shouldBe CefrLevel.B1
        }

        test("the level is keyed by the language's base tag") {
            val store = levels()

            store.setLevel("en-US", CefrLevel.A2)

            store.levelFor("en-GB") shouldBe CefrLevel.A2
            store.levels() shouldBe mapOf("en" to CefrLevel.A2)
        }

        test("a level survives a restart over the same vault") {
            val vault = InMemoryVaultStore()
            levels(vault).setLevel("fr", CefrLevel.C1)

            levels(vault).levelFor("fr") shouldBe CefrLevel.C1
        }

        test("setting a level replaces the previous one for that language") {
            val store = levels()
            store.setLevel("en", CefrLevel.A1)

            store.setLevel("en", CefrLevel.C2)

            store.levelFor("en") shouldBe CefrLevel.C2
            store.levels() shouldBe mapOf("en" to CefrLevel.C2)
        }

        test("a language with no level, and a null language, have none") {
            val store = levels()
            store.setLevel("en", CefrLevel.B2)

            store.levelFor("de") shouldBe null
            store.levelFor(null) shouldBe null
        }

        test("every declared language is listed, keyed by base tag") {
            val store = levels()

            store.setLevel("en", CefrLevel.B2)
            store.setLevel("fr-FR", CefrLevel.A1)

            store.levels() shouldBe mapOf("en" to CefrLevel.B2, "fr" to CefrLevel.A1)
        }

        test("a level is a vault record, so it travels in an export") {
            val vault = InMemoryVaultStore()
            levels(vault).setLevel("en", CefrLevel.B1)

            val restored = InMemoryVaultStore()
            restored.importBundle(vault.exportBundle())

            levels(restored).levelFor("en") shouldBe CefrLevel.B1
        }

        test("the no-op ReaderLevels knows none and stores nothing") {
            val none = ReaderLevels.None

            none.levelFor("en") shouldBe null
            none.levelFor(null) shouldBe null
            none.levels() shouldBe emptyMap()
            none.setLevel("en", CefrLevel.B2)
            none.levelFor("en") shouldBe null
        }
    })
