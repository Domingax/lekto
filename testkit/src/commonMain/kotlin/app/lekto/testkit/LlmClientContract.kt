package app.lekto.testkit

import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmMessage
import app.lekto.core.llm.LlmModelsResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmTranslationEvent
import app.lekto.core.llm.LlmTranslationRequest
import kotlinx.coroutines.flow.toList

/**
 * The specification of the [app.lekto.core.llm.LlmClient] seam, as executable
 * cases (issues #88, #89, #93; ADR-0022). Its only in-memory implementation is
 * [FakeLlmClient], so both halves of the contract are run against the fake in
 * `core/commonTest`; the OpenAI-compatible transport is proved against a local
 * HTTP server in `core/jvmTest` (`OpenAiCompatibleLlmClientTest`), where its
 * request shape, status mapping and server-sent-event parsing are pinned.
 *
 * The contract records the seam's promise: a connection test reports the
 * provider's outcome as a first-class result, a model list is a first-class
 * result too, a translation streams its deltas, and every failure is an event —
 * never a thrown exception.
 */
@Suppress("TooManyFunctions") // One seam, one small case per promise; splitting the class hides the contract.
class LlmClientContract(private val newClient: () -> FakeLlmClient) {

    /** Every blocking behaviour the seam promises, as `(name, run)` pairs. */
    fun cases(): List<ContractCase> = listOf(
        ContractCase("a connected provider is reported as connected") { reportsConnected() },
        ContractCase("a failed provider is reported as a failure, not thrown") { reportsFailure() },
        ContractCase("the configuration and key reach the adapter unchanged") { passesTheCallThrough() },
    )

    /** Every model-listing behaviour the seam promises (issue #93), as `(name, run)` pairs. */
    fun modelCases(): List<ContractCase> = listOf(
        ContractCase("the listed models are returned as a first-class result") { listsModels() },
        ContractCase("a failed model list is a failure, not thrown") { reportsModelFailure() },
        ContractCase("the configuration and key reach the model listing unchanged") { passesTheModelQueryThrough() },
    )

    /** Every streaming behaviour the seam promises; the bodies suspend on the flow. */
    fun translationCases(): List<SuspendContractCase> = listOf(
        SuspendContractCase("the deltas stream in order and complete") { streamsDeltas() },
        SuspendContractCase("a streamed failure is an event, not thrown") { reportsStreamedFailure() },
        SuspendContractCase("the translation request reaches the adapter unchanged") { passesTheRequestThrough() },
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

    private fun listsModels() {
        val client = newClient()
        val models = listOf("gpt-4o-mini", "gpt-4o")
        client.models = LlmModelsResult.Loaded(models)
        expectEquals(LlmModelsResult.Loaded(models), client.listModels(CONFIG, A_KEY), "the listed models")
    }

    private fun reportsModelFailure() {
        val client = newClient()
        val failure = LlmModelsResult.Failed(LlmModelsResult.UNSUPPORTED)
        client.models = failure
        expectEquals(failure, client.listModels(CONFIG, A_KEY), "an unsupported list")
    }

    private fun passesTheModelQueryThrough() {
        val client = newClient()
        client.listModels(CONFIG, A_KEY)
        expectEquals(CONFIG to A_KEY, client.listed.single(), "the recorded model query")
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
