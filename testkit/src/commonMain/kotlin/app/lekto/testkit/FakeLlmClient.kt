package app.lekto.testkit

import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmTranslationEvent
import app.lekto.core.llm.LlmTranslationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A scripted [LlmClient] for tests (issues #88, #89): it records the
 * configuration and key it was asked to test — and the request it was asked to
 * translate — and returns the outcome or streams the deltas the test set, so the
 * settings controller and the reader are driven without a socket and without a
 * real key. [outcome] and [translation] can be changed between calls to prove a
 * retry or a fresh stream.
 */
class FakeLlmClient(
    /** The outcome the next `testConnection` returns; [LlmConnectionResult.Connected] by default. */
    var outcome: LlmConnectionResult = LlmConnectionResult.Connected,
    /** The text deltas `translate` emits, in order. */
    var translation: List<String> = emptyList(),
    /** When set, a failure `translate` emits after the deltas; `null` means the stream completes. */
    var translationFailure: String? = null,
) : LlmClient {

    /** Every `(config, apiKey)` the adapter was asked to test, in order. */
    val tested: MutableList<Pair<LlmProviderConfig, String>> = mutableListOf()

    /** Every translation request the adapter was asked to run, in order. */
    val translated: MutableList<LlmTranslationRequest> = mutableListOf()

    override fun testConnection(config: LlmProviderConfig, apiKey: String): LlmConnectionResult {
        tested += config to apiKey
        return outcome
    }

    override fun translate(request: LlmTranslationRequest): Flow<LlmTranslationEvent> = flow {
        translated += request
        translation.forEach { delta -> emit(LlmTranslationEvent.Delta(delta)) }
        translationFailure?.let { message -> emit(LlmTranslationEvent.Failed(message)) }
    }
}
