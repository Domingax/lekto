package app.lekto.testkit

import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmMessage
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmTranslationEvent
import app.lekto.core.llm.LlmTranslationRequest
import kotlinx.coroutines.flow.toList

/**
 * The specification of the [app.lekto.core.llm.LlmClient] seam, as executable
 * cases (issues #88, #89; ADR-0022). Its only in-memory implementation is
 * [FakeLlmClient], so both halves of the contract are run against the fake in
 * `core/commonTest`; the OpenAI-compatible transport is proved against a local
 * HTTP server in `core/jvmTest` (`OpenAiCompatibleLlmClientTest`), where its
 * request shape, status mapping and server-sent-event parsing are pinned.
 *
 * The contract records the seam's promise: a connection test reports the
 * provider's outcome as a first-class result, a translation streams its deltas,
 * and every failure is an event — never a thrown exception.
 */
class LlmClientContract(private val newClient: () -> FakeLlmClient) {

    /** Every blocking behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("a connected provider is reported as connected") { reportsConnected() },
        ContractCase("a failed provider is reported as a failure, not thrown") { reportsFailure() },
        ContractCase("the configuration and key reach the adapter unchanged") { passesTheCallThrough() },
    )

    /** Every streaming behaviour the seam promises; the bodies suspend on the flow. */
    fun translationCases(): List<TranslationContractCase> = listOf(
        TranslationContractCase("the deltas stream in order and complete") { streamsDeltas() },
        TranslationContractCase("a streamed failure is an event, not thrown") { reportsStreamedFailure() },
        TranslationContractCase("the translation request reaches the adapter unchanged") { passesTheRequestThrough() },
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

    private suspend fun streamsDeltas() {
        val client = newClient()
        client.translation = listOf("Bon", "jour")
        expectEquals(
            listOf(LlmTranslationEvent.Delta("Bon"), LlmTranslationEvent.Delta("jour")),
            client.translate(request()).toList(),
            "the streamed deltas",
        )
    }

    private suspend fun reportsStreamedFailure() {
        val client = newClient()
        client.translationFailure = "The provider answer was cut off."
        expectEquals(
            listOf(LlmTranslationEvent.Failed("The provider answer was cut off.")),
            client.translate(request()).toList(),
            "a streamed failure",
        )
    }

    private suspend fun passesTheRequestThrough() {
        val client = newClient()
        val request = request()
        client.translate(request).toList()
        expectEquals(request, client.translated.single(), "the recorded translation request")
    }

    private fun request(): LlmTranslationRequest = LlmTranslationRequest(
        config = CONFIG,
        apiKey = A_KEY,
        messages = listOf(LlmMessage("system", "translate"), LlmMessage("user", "Selected phrase: the lantern")),
    )

    private companion object {
        val CONFIG = LlmProviderConfig(LlmProvider.ANTHROPIC, "claude-3")
        const val A_KEY = "sk-contract-0123456789"
    }
}

/** A streaming contract case: a body that may suspend while it collects a flow. */
data class TranslationContractCase(val name: String, val body: suspend () -> Unit)
