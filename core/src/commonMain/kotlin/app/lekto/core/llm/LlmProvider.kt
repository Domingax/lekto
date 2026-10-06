package app.lekto.core.llm

import kotlinx.serialization.Serializable

/**
 * A named **LLM provider** preset (CONTEXT.md, "LLM provider"; ADR-0022): the
 * display [label] the settings screen offers and the OpenAI-compatible [baseUrl]
 * the adapter calls. Every service Lekto supports speaks the same
 * chat-completions wire format, so a provider is data — a base URL — not a code
 * path. [CUSTOM] carries no URL of its own; the user supplies one in
 * [LlmProviderConfig.customBaseUrl].
 *
 * Ollama is a local server, reachable only from a desktop (ADR-0022); it is the
 * one preset that needs no API key. The base URLs are owned by each service and
 * can move without a code change to the adapter, which is why they live here as
 * data rather than in the transport.
 */
enum class LlmProvider(val label: String, val baseUrl: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1"),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    OPENCODE_ZEN("OpenCode Zen", "https://opencode.ai/zen/v1"),
    OPENCODE_GO("OpenCode Go", "https://opencode.ai/zen/go/v1"),
    OLLAMA("Ollama", "http://localhost:11434/v1"),
    CUSTOM("Custom", ""),
    ;

    /** Whether this provider needs an API key; a local Ollama does not (ADR-0022). */
    val requiresKey: Boolean get() = this != OLLAMA
}

/**
 * The active **LLM provider** configuration (issue #88; ADR-0022): the chosen
 * [provider] preset, the [model] to call, and — for [LlmProvider.CUSTOM] — the
 * [customBaseUrl] the user supplied. It is non-secret and app-private: it never
 * enters the **Vault** (ADR-0005) or an export. The **API key** is held
 * separately in the **Secret store** (ADR-0021) and is never part of this value.
 *
 * One provider is active at a time (ADR-0022), so this is a single configuration
 * rather than a list; choosing another preset replaces it.
 */
@Serializable
data class LlmProviderConfig(
    val provider: LlmProvider = LlmProvider.OPENAI,
    val model: String = "",
    val customBaseUrl: String = "",
) {

    /**
     * The base URL the adapter calls: the user's own URL for [LlmProvider.CUSTOM]
     * and the preset's fixed URL otherwise, with any trailing slash removed so it
     * composes with the endpoint path.
     */
    val baseUrl: String
        get() = when (provider) {
            LlmProvider.CUSTOM -> customBaseUrl.trim().trimEnd('/')
            else -> provider.baseUrl
        }

    /** Whether the configuration names both a base URL and a model, so it can be called. */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        /** What an unconfigured app holds: the default preset and no model yet. */
        val DEFAULT: LlmProviderConfig = LlmProviderConfig()
    }
}
