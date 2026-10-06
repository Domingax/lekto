package app.lekto.testkit

import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmProviderConfig

/**
 * A scripted [LlmClient] for tests (issue #88): it records the configuration and
 * key it was asked to test and returns the outcome the test set, so the settings
 * controller is driven without a socket and without a real key. [outcome] can be
 * changed between calls to prove a retry, and [tested] records what reached the
 * adapter.
 */
class FakeLlmClient(
    /** The outcome the next `testConnection` returns; [LlmConnectionResult.Connected] by default. */
    var outcome: LlmConnectionResult = LlmConnectionResult.Connected,
) : LlmClient {

    /** Every `(config, apiKey)` the adapter was asked to test, in order. */
    val tested: MutableList<Pair<LlmProviderConfig, String>> = mutableListOf()

    override fun testConnection(config: LlmProviderConfig, apiKey: String): LlmConnectionResult {
        tested += config to apiKey
        return outcome
    }
}
