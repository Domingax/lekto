package app.lekto.translation

import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmSettingsStore
import app.lekto.core.llm.LlmTranslationEvent
import app.lekto.core.llm.LlmTranslationRequest
import app.lekto.core.llm.translationPrompt
import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.settings.ProviderController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the phrase panel is doing for the selected phrase (issue #89): the
 * zero-configuration fallback [NoProvider], a translation [Streaming] or
 * [Done], or an inline [Failed] the reader is never interrupted by.
 */
sealed interface PhraseTranslationState {

    /** No usable provider is connected, so the panel offers the shortcut and a link to settings. */
    data object NoProvider : PhraseTranslationState

    /** A translation is arriving; [text] is what has streamed in so far. */
    data class Streaming(val text: String) : PhraseTranslationState

    /** The provider finished; [text] is the complete translation. */
    data class Done(val text: String) : PhraseTranslationState

    /** The provider or the network failed; [message] is honest and safe to show inline. */
    data class Failed(val message: String) : PhraseTranslationState
}

/**
 * What the phrase panel renders (issue #89): the connected [provider]'s label,
 * the exact [context] that leaves the device — so the panel is transparent about
 * it — and the [state] the translation is in. The **API key** is never here.
 */
data class PhraseTranslationUiState(
    val provider: String? = null,
    val context: String = "",
    val state: PhraseTranslationState = PhraseTranslationState.NoProvider,
)

/**
 * The phrase-translation state holder (issue #89; ADR-0022): it reads the active
 * **LLM provider** and its **API key** from the **Secret store**, asks the
 * adapter to stream the translation, and cancels the stream when the panel
 * closes. Without a usable provider it settles on [PhraseTranslationState.NoProvider]
 * rather than offering a dead action.
 *
 * The [dispatcher] is injected because the transport blocks and a hard-coded one
 * cannot be driven by virtual time (docs/testing.md, "Deterministic seams"); the
 * [scope] carries the streaming work.
 */
class PhraseTranslationController(
    private val settings: LlmSettingsStore,
    private val secrets: SecretStore,
    private val client: LlmClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(PhraseTranslationUiState())
    val state: StateFlow<PhraseTranslationUiState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Starts streaming a translation of [selection] in the context of
     * [sentence], cancelling any previous stream. Without a complete
     * configuration and a key (or a keyless provider) it reports
     * [PhraseTranslationState.NoProvider] instead of calling the provider.
     */
    fun translate(selection: String, sentence: String?, targetLanguage: String?) {
        job?.cancel()
        val config = settings.load()
        val provider = config.provider
        val apiKey = (secrets.get(ProviderController.providerSecretKey(provider)) as? SecretResult.Found)
            ?.value
            .orEmpty()
        if (!config.isComplete || (apiKey.isBlank() && provider.requiresKey)) {
            _state.value = PhraseTranslationUiState(
                provider = provider.label,
                state = PhraseTranslationState.NoProvider,
            )
            return
        }
        val prompt = translationPrompt(selection, sentence, targetLanguage)
        _state.value = PhraseTranslationUiState(
            provider = provider.label,
            context = prompt.context,
            state = PhraseTranslationState.Streaming(""),
        )
        job = scope.launch {
            withContext(dispatcher) {
                client.translate(LlmTranslationRequest(config, apiKey, prompt.messages))
                    .collect { event -> apply(event) }
            }
            _state.update { current ->
                val streaming = current.state as? PhraseTranslationState.Streaming
                if (streaming == null) current else current.copy(state = PhraseTranslationState.Done(streaming.text))
            }
        }
    }

    /** Stops the stream — the panel closed — and returns the panel to its resting state. */
    fun dismiss() {
        job?.cancel()
        job = null
        _state.value = PhraseTranslationUiState()
    }

    private fun apply(event: LlmTranslationEvent) {
        when (event) {
            is LlmTranslationEvent.Delta -> _state.update { current ->
                val text = (current.state as? PhraseTranslationState.Streaming)?.text.orEmpty() + event.text
                current.copy(state = PhraseTranslationState.Streaming(text))
            }

            is LlmTranslationEvent.Failed -> _state.update { current ->
                current.copy(state = PhraseTranslationState.Failed(event.message))
            }
        }
    }
}
