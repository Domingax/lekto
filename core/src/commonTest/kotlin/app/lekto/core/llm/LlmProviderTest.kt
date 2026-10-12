package app.lekto.core.llm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

/**
 * The **LLM provider** catalogue and configuration (issue #88; ADR-0022): every
 * named preset addresses an HTTPS endpoint, a custom configuration uses the
 * user's own base URL, and a configuration is only callable with both a base URL
 * and a model. The values are pure data, so they are pinned here.
 */
class LlmProviderTest :
    FunSpec({

        test("every remote preset has a secure base URL") {
            LlmProvider.entries
                .filter { provider -> provider != LlmProvider.CUSTOM && provider != LlmProvider.OLLAMA }
                .forEach { provider -> provider.baseUrl shouldStartWith "https://" }
        }

        test("Ollama points at the local server over plain HTTP") {
            LlmProvider.OLLAMA.baseUrl shouldBe "http://localhost:11434/v1"
        }

        test("only a custom provider has no base URL of its own") {
            LlmProvider.CUSTOM.baseUrl shouldBe ""
        }

        test("Ollama needs no API key; every other provider does") {
            LlmProvider.OLLAMA.requiresKey shouldBe false
            LlmProvider.entries.filter { provider -> provider != LlmProvider.OLLAMA }.forEach { provider ->
                provider.requiresKey shouldBe true
            }
        }

        test("the OpenCode gateways and Ollama list models without a key; every other provider does not") {
            // Issue #93: the OpenCode gateways publish their catalogue, and a local
            // Ollama never asks for a key. Everything else authenticates its list.
            LlmProvider.OPENCODE_ZEN.listsModelsWithoutKey shouldBe true
            LlmProvider.OPENCODE_GO.listsModelsWithoutKey shouldBe true
            LlmProvider.OLLAMA.listsModelsWithoutKey shouldBe true
            listOf(LlmProvider.OPENAI, LlmProvider.ANTHROPIC, LlmProvider.GEMINI, LlmProvider.CUSTOM)
                .forEach { provider -> provider.listsModelsWithoutKey shouldBe false }
        }

        test("a preset configuration resolves to the preset's base URL") {
            LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini").baseUrl shouldBe LlmProvider.OPENAI.baseUrl
        }

        test("a custom configuration resolves to the user's own base URL, trimmed of trailing slashes") {
            val config =
                LlmProviderConfig(LlmProvider.CUSTOM, "my-model", customBaseUrl = "  https://example.test/v1/  ")

            config.baseUrl shouldBe "https://example.test/v1"
        }

        test("a configuration is complete only with a base URL and a model") {
            LlmProviderConfig(LlmProvider.OPENAI, "").isComplete shouldBe false
            LlmProviderConfig(LlmProvider.CUSTOM, "m", customBaseUrl = "").isComplete shouldBe false
            LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini").isComplete shouldBe true
        }
    })
