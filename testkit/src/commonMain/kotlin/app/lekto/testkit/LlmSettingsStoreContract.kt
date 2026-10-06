package app.lekto.testkit

import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmSettingsStore

/**
 * The specification of the [LlmSettingsStore] seam, as executable cases (issue
 * #88; ADR-0022, ADR-0023). Framework-free like [SecretStoreContract], so the
 * in-memory fake runs it in `core/commonTest` and the JSON document store runs
 * the same cases over an in-memory filesystem there and over a real directory in
 * `core/jvmTest` — the two implementations cannot silently differ
 * (docs/testing.md, "Test levels"). Each case builds a fresh store from
 * [newStore], so cases cannot leak state.
 */
class LlmSettingsStoreContract(private val newStore: () -> LlmSettingsStore) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("an unconfigured store loads the default configuration") { loadsDefault() },
        ContractCase("a saved configuration round-trips") { roundTrips() },
        ContractCase("a later save replaces the previous configuration") { replaces() },
        ContractCase("a custom base URL round-trips") { roundTripsCustomUrl() },
    )

    private fun loadsDefault() {
        expectEquals(LlmProviderConfig.DEFAULT, newStore().load(), "an unconfigured store")
    }

    private fun roundTrips() {
        val store = newStore()
        val config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini")
        store.save(config)
        expectEquals(config, store.load(), "a saved configuration")
    }

    private fun replaces() {
        val store = newStore()
        store.save(LlmProviderConfig(LlmProvider.OPENAI, "first"))
        store.save(LlmProviderConfig(LlmProvider.ANTHROPIC, "second"))
        expectEquals(LlmProviderConfig(LlmProvider.ANTHROPIC, "second"), store.load(), "a replaced configuration")
    }

    private fun roundTripsCustomUrl() {
        val store = newStore()
        val config = LlmProviderConfig(LlmProvider.CUSTOM, "my-model", customBaseUrl = "https://example.test/v1")
        store.save(config)
        expectEquals(config, store.load(), "a custom configuration")
    }
}
