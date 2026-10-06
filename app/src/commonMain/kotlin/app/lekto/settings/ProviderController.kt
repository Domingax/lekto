package app.lekto.settings

import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmSettingsStore
import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The inline outcome of a save, a connection test or a key removal (issue #88):
 * a [Success] or a [Failure], so the section renders the failure in the error
 * colour and nothing has to read a boolean flag.
 */
sealed interface ProviderResult {
    val message: String

    /** The action completed; [message] is a short confirmation. */
    data class Success(override val message: String) : ProviderResult

    /** The action did not complete; [message] is an honest reason, never carrying a key. */
    data class Failure(override val message: String) : ProviderResult
}

/**
 * What the settings screen renders for the LLM provider (issue #88). It never
 * holds the **API key**: the key is entered in a field the screen owns and handed
 * to the controller only for a save or a test, so a secret cannot leak through
 * this value's `toString`.
 *
 * [available] is `false` only where no provider wiring is present, so the section
 * can say so rather than offer a dead action; [providers] is the presets this
 * platform can reach, so a local Ollama is not offered where it cannot run
 * (ADR-0022). Neither gates the reader.
 */
data class ProviderUiState(
    val available: Boolean = false,
    val providers: List<LlmProvider> = LlmProvider.entries,
    val config: LlmProviderConfig = LlmProviderConfig.DEFAULT,
    val hasStoredKey: Boolean = false,
    val testing: Boolean = false,
    val result: ProviderResult? = null,
)

/**
 * The LLM provider's state holder (issue #88; ADR-0022): it chooses one active
 * **LLM provider**, saves the non-secret configuration app-privately and the key
 * in the **Secret store** (ADR-0021), and runs a connection test off the UI
 * thread, reporting success or an inline failure.
 *
 * The [dispatcher] is injected because the connection test blocks, and a
 * hard-coded one cannot be driven by virtual time; the [scope] carries that work
 * (docs/testing.md, "Deterministic seams").
 */
@Suppress("LongParameterList") // settings/secrets/client/providers are independent seams the composition wires.
class ProviderController(
    private val settings: LlmSettingsStore,
    private val secrets: SecretStore,
    private val client: LlmClient,
    private val providers: List<LlmProvider> = LlmProvider.entries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ProviderUiState> = _state.asStateFlow()

    /**
     * Makes [provider] the single active provider. Switching is a deliberate
     * reset — the previous provider's model does not carry over, because a model
     * name is provider-specific — while a custom base URL is kept for the next
     * time Custom is chosen.
     */
    fun selectProvider(provider: LlmProvider) {
        _state.update { current ->
            current.copy(
                config = current.config.copy(provider = provider, model = ""),
                hasStoredKey = storedKey(provider) != null,
                result = null,
            )
        }
    }

    /** Sets the model the active provider is called with. */
    fun setModel(model: String) {
        _state.update { current -> current.copy(config = current.config.copy(model = model)) }
    }

    /** Sets the base URL a [LlmProvider.CUSTOM] provider is called with. */
    fun setBaseUrl(baseUrl: String) {
        _state.update { current -> current.copy(config = current.config.copy(customBaseUrl = baseUrl)) }
    }

    /**
     * Persists the active configuration app-privately and, when [apiKey] is not
     * blank, stores the key in the **Secret store**. Both never enter the vault;
     * an unavailable store is reported inline rather than silently dropping the
     * key.
     */
    fun save(apiKey: String) {
        val config = _state.value.config
        settings.save(config)
        val keyFailure = apiKey.takeIf { it.isNotBlank() }?.let { value -> putKey(config.provider, value) }
        _state.update { current ->
            current.copy(
                hasStoredKey = storedKey(current.config.provider) != null,
                result = keyFailure?.let { message -> ProviderResult.Failure(message) }
                    ?: ProviderResult.Success(SAVED),
            )
        }
    }

    /**
     * Tests the active provider, using [apiKey] when the field is not blank and
     * the stored key otherwise, so a saved key can be re-tested without retyping.
     */
    fun test(apiKey: String) {
        if (_state.value.testing) return
        val config = _state.value.config
        val typed = apiKey.takeIf { it.isNotBlank() }
        _state.update { current -> current.copy(testing = true, result = null) }
        scope.launch {
            val key = typed ?: storedKey(config.provider).orEmpty()
            val outcome = withContext(dispatcher) { client.testConnection(config, key) }
            _state.update { current -> current.copy(testing = false, result = outcome.toProviderResult()) }
        }
    }

    /**
     * Removes the stored key for the active provider, reporting honestly when the
     * **Secret store** is unreachable rather than claiming the key is gone.
     */
    fun removeKey() {
        val provider = _state.value.config.provider
        when (val outcome = secrets.delete(providerSecretKey(provider))) {
            is SecretResult.Unavailable ->
                _state.update { current -> current.copy(result = ProviderResult.Failure(outcome.message)) }

            else ->
                _state.update { current ->
                    current.copy(hasStoredKey = false, result = ProviderResult.Success(KEY_REMOVED))
                }
        }
    }

    /** Clears the last result, so the section returns to its resting state. */
    fun dismissResult() {
        _state.update { current -> current.copy(result = null) }
    }

    private fun initialState(): ProviderUiState {
        val config = settings.load()
        return ProviderUiState(
            available = true,
            providers = providers,
            config = config,
            hasStoredKey = storedKey(config.provider) != null,
        )
    }

    /** Stores [value] under [provider]'s key slot, returning an honest message when the store is unavailable. */
    private fun putKey(provider: LlmProvider, value: String): String? =
        when (val outcome = secrets.put(providerSecretKey(provider), value)) {
            is SecretResult.Unavailable -> outcome.message
            else -> null
        }

    private fun storedKey(provider: LlmProvider): String? =
        (secrets.get(providerSecretKey(provider)) as? SecretResult.Found)?.value

    companion object {
        /** The key slot is per provider, so switching back finds a key already stored. */
        internal fun providerSecretKey(provider: LlmProvider): String = "llm-api-key-${provider.name.lowercase()}"

        const val SAVED: String = "Saved."
        const val KEY_REMOVED: String = "Key removed from secure storage."
        const val CONNECTED: String = "The provider answered. Your model is ready."
    }
}

/** The reader-facing result of a connection test. */
private fun LlmConnectionResult.toProviderResult(): ProviderResult = when (this) {
    LlmConnectionResult.Connected -> ProviderResult.Success(ProviderController.CONNECTED)
    is LlmConnectionResult.Failed -> ProviderResult.Failure(message)
}
