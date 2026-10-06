package app.lekto.testkit

import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig

/**
 * The specification of the [LlmClient] seam, as executable cases (issue #88;
 * ADR-0022). The seam is a `fun interface`, and its only in-memory implementation
 * is [FakeLlmClient], so the contract is run against the fake in
 * `core/commonTest`; the OpenAI-compatible transport is proved against a local
 * HTTP server in `core/jvmTest` (`OpenAiCompatibleLlmClientTest`), where its
 * request shape and status mapping are pinned.
 *
 * The contract records the seam's promise: a connection test reports the
 * provider's outcome as a first-class result — never a thrown exception — and the
 * configuration and key reach the adapter unchanged.
 */
class LlmClientContract(private val newClient: () -> FakeLlmClient) {

    /** Every behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("a connected provider is reported as connected") { reportsConnected() },
        ContractCase("a failed provider is reported as a failure, not thrown") { reportsFailure() },
        ContractCase("the configuration and key reach the adapter unchanged") { passesTheCallThrough() },
    )

    private fun reportsConnected() {
        val client = newClient()
        client.outcome = LlmConnectionResult.Connected
        expectEquals(LlmConnectionResult.Connected, client.testConnection(CONFIG, A_KEY), "a connected provider")
    }

    private fun reportsFailure() {
        val client = newClient()
        val failure = LlmConnectionResult.Failed("The provider rejected the API key.")
        client.outcome = failure
        expectEquals(failure, client.testConnection(CONFIG, A_KEY), "a failed provider")
    }

    private fun passesTheCallThrough() {
        val client = newClient()
        client.testConnection(CONFIG, A_KEY)
        expectEquals(CONFIG to A_KEY, client.tested.single(), "the recorded call")
    }

    private companion object {
        val CONFIG = LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3")
        const val A_KEY = "sk-contract-0123456789"
    }
}
