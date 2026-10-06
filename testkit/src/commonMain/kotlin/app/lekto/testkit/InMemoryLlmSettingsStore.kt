package app.lekto.testkit

import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmSettingsStore

/**
 * An in-memory [LlmSettingsStore] for tests (issue #88; ADR-0022, ADR-0023): the
 * app-private configuration behind the seam, so a consumer's logic runs without
 * a file or a platform root. A store starts unconfigured — [LlmProviderConfig.DEFAULT]
 * — exactly as a fresh install does.
 */
class InMemoryLlmSettingsStore(initial: LlmProviderConfig = LlmProviderConfig.DEFAULT) : LlmSettingsStore {

    private var stored: LlmProviderConfig = initial

    override fun load(): LlmProviderConfig = stored

    override fun save(config: LlmProviderConfig) {
        stored = config
    }
}
