package app.lekto.core.llm

import app.lekto.testkit.InMemoryVaultFileSystem
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The JSON document store's resilience (issue #88; ADR-0023): a document this
 * version cannot read — corrupt bytes, or a preset a future version added — is
 * "not configured" rather than a crash, and a save lands at the store's own
 * path so the configuration never touches a vault record.
 */
class JsonLlmSettingsStoreTest :
    FunSpec({

        test("a corrupt document reads as the default configuration") {
            val files = InMemoryVaultFileSystem()
            files.writeAtomically(JsonLlmSettingsStore.PATH, "not json at all".encodeToByteArray())

            JsonLlmSettingsStore(files).load() shouldBe LlmProviderConfig.DEFAULT
        }

        test("a document naming an unknown provider reads as the default configuration") {
            val files = InMemoryVaultFileSystem()
            val document = """{"provider":"MADE_UP","model":"m","customBaseUrl":""}"""
            files.writeAtomically(JsonLlmSettingsStore.PATH, document.encodeToByteArray())

            JsonLlmSettingsStore(files).load() shouldBe LlmProviderConfig.DEFAULT
        }

        test("a save writes one document under the store's own path") {
            val files = InMemoryVaultFileSystem()

            JsonLlmSettingsStore(files).save(LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"))

            files.listFiles() shouldBe listOf(JsonLlmSettingsStore.PATH)
        }
    })
