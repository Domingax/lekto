package app.lekto.settings

import app.lekto.core.llm.LlmClient
import app.lekto.core.llm.LlmConnectionResult
import app.lekto.core.llm.LlmModelsResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.llm.LlmSettingsStore
import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.text.AppMessage
import app.lekto.text.TextKey
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
    val message: AppMessage

    /** The action completed; [message] is a short confirmation. */
    data class Success(override val message: AppMessage) : ProviderResult

    /** The action did not complete; [message] is an honest reason, never carrying a key. */
    data class Failure(override val message: AppMessage) : ProviderResult
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
    /**
     * The models the active provider listed (issue #93), device-local UI state the
     * reader chooses from. Empty until a list is loaded, and cleared when the
     * provider changes — a model name is provider-specific. It never enters the
     * vault or a log.
     */
    val models: List<String> = emptyList(),
    /** Whether a model list is being fetched, so the control can say so and not double-fire. */
    val loadingModels: Boolean = false,
    /**
     * A neutral prompt about the model list (issue #93) — a missing key, say — shown
     * discreetly beside the model field rather than as a red failure, because it
     * asks the reader for something rather than reporting a fault.
     */
    val modelsHint: AppMessage? = null,
    /**
     * Whether the reader has chosen a provider yet (issue #93). A fresh install
     * starts unchosen, so the picker reads "Choose a provider" rather than a preset
     * the reader never picked; choosing one reveals the model and key controls and
     * loads the model list. A stored, non-default configuration, or a stored key,
     * reads as already chosen.
     */
    val providerChosen: Boolean = false,
) {
    /** The provider in effect, or `null` while the reader has not chosen one. */
    val selectedProvider: LlmProvider?
        get() = config.provider.takeIf { providerChosen }
}

/**
 * The LLM provider's state holder (issue #88; ADR-0022): it chooses one active
 * **LLM provider**, keeps the non-secret configuration app-privately, and runs a
 * connection test off the UI thread. A successful test stores the key in the
 * **Secret store** (ADR-0021); a failure changes nothing. Either way the outcome
 * is reported inline.
 *
 * The [dispatcher] is injected because the connection test blocks, and a
 * hard-coded one cannot be driven by virtual time; the [scope] carries that work
 * (docs/testing.md, "Deterministic seams").
 */
@Suppress("LongParameterList", "TooManyFunctions") // Each seam and each one-step action is its own public method.
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
     * Makes [provider] the single active provider and loads the models it offers
     * (issue #93), so the list is ready without a separate action. Switching is a
     * deliberate reset — the previous provider's model does not carry over, because
     * a model name is provider-specific — while a custom base URL is kept for the
     * next time Custom is chosen.
     */
    fun selectProvider(provider: LlmProvider) {
        _state.update { current ->
            current.copy(
                config = current.config.copy(provider = provider, model = ""),
                hasStoredKey = storedKey(provider) != null,
                result = null,
                models = emptyList(),
                providerChosen = true,
            )
        }
        persistConfig()
        loadModels(apiKey = "", silent = true)
    }

    /**
     * Sets the model the active provider is called with and writes it at once:
     * choosing a model is reversible, so it applies on change with no explicit
     * save (docs/design.md §7.5; issue #213).
     */
    fun setModel(model: String) {
        _state.update { current -> current.copy(config = current.config.copy(model = model)) }
        persistConfig()
    }

    /**
     * Sets the base URL a [LlmProvider.CUSTOM] provider is called with and writes
     * it at once; a new host invalidates its list (issue #213).
     */
    fun setBaseUrl(baseUrl: String) {
        _state.update { current ->
            current.copy(config = current.config.copy(customBaseUrl = baseUrl), models = emptyList())
        }
        persistConfig()
    }

    /**
     * The screen's one action (issue #213): test the active provider, then store
     * [apiKey] only if it answered. Testing first means a wrong key never replaces
     * a working one; a blank field re-tests the stored key without rewriting it.
     * The non-secret configuration is written app-privately on success, and the key
     * goes to the **Secret store** (ADR-0021). An unavailable store is reported
     * inline rather than silently dropping the key.
     */
    fun connect(apiKey: String) {
        if (_state.value.testing) return
        val config = _state.value.config
        _state.update { current -> current.copy(testing = true, result = null) }
        scope.launch {
            val outcome = withContext(dispatcher) { client.testConnection(config, keyFor(apiKey, config.provider)) }
            val result = when (outcome) {
                LlmConnectionResult.Connected -> persistSuccessfulConnection(apiKey, config)
                is LlmConnectionResult.Failed -> ProviderResult.Failure(AppMessage.core(outcome.message))
            }
            _state.update { current ->
                current.copy(
                    testing = false,
                    hasStoredKey = storedKey(current.config.provider) != null,
                    result = result,
                )
            }
        }
    }

    /** Writes the configuration and, when [apiKey] is not blank, the key, after a successful test. */
    private fun persistSuccessfulConnection(apiKey: String, config: LlmProviderConfig): ProviderResult {
        settings.save(config)
        if (apiKey.isBlank()) return ProviderResult.Success(CONNECTED)
        return when (val outcome = secrets.put(providerSecretKey(config.provider), apiKey)) {
            is SecretResult.Unavailable -> ProviderResult.Failure(AppMessage.core(outcome.message))
            else -> ProviderResult.Success(CONNECTED)
        }
    }

    /**
     * Loads the active provider's model list from `GET /models` (issue #93),
     * using [apiKey] when the field is not blank and the stored key otherwise, so
     * a saved key can load the list without retyping. The call blocks, so it runs
     * off the UI thread. A missing key is a discreet [ProviderUiState.modelsHint],
     * never a red failure, because it asks the reader for something; an unsupported
     * or failed list clears any previous list and is reported inline. When [silent]
     * — an automatic load on choosing a provider — any other failure stays quiet,
     * so switching provider cannot raise an error before the reader has acted. A
     * list that arrives after the provider (or a custom base URL) changed is dropped
     * — it belongs to the provider that was asked.
     */
    fun loadModels(apiKey: String, silent: Boolean = false) {
        if (_state.value.loadingModels) return
        val config = _state.value.config
        val requested = config.provider to config.baseUrl
        _state.update { current -> current.copy(loadingModels = true, result = null, modelsHint = null) }
        scope.launch {
            val outcome = withContext(dispatcher) { client.listModels(config, keyFor(apiKey, config.provider)) }
            _state.update { current ->
                val stale = (current.config.provider to current.config.baseUrl) != requested
                when {
                    stale -> current.copy(loadingModels = false)

                    outcome is LlmModelsResult.Loaded ->
                        current.copy(loadingModels = false, models = outcome.models, result = null, modelsHint = null)

                    outcome is LlmModelsResult.Failed && outcome.message == LlmModelsResult.MISSING_KEY ->
                        current.copy(
                            loadingModels = false,
                            models = emptyList(),
                            modelsHint = AppMessage.core(outcome.message),
                        )

                    silent -> current.copy(loadingModels = false, models = emptyList())

                    else -> current.copy(
                        loadingModels = false,
                        models = emptyList(),
                        result = ProviderResult.Failure(AppMessage.core((outcome as LlmModelsResult.Failed).message)),
                    )
                }
            }
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
                _state.update { current ->
                    current.copy(result = ProviderResult.Failure(AppMessage.core(outcome.message)))
                }

            else ->
                _state.update { current ->
                    current.copy(hasStoredKey = false, result = ProviderResult.Success(KEY_REMOVED))
                }
        }
    }

    /** Writes the non-secret configuration now: everything reversible applies on change (issue #213). */
    private fun persistConfig() {
        settings.save(_state.value.config)
    }

    private fun initialState(): ProviderUiState {
        val config = settings.load()
        val hasStoredKey = storedKey(config.provider) != null
        return ProviderUiState(
            available = true,
            providers = providers,
            config = config,
            hasStoredKey = hasStoredKey,
            providerChosen = config != LlmProviderConfig.DEFAULT || hasStoredKey,
        )
    }

    /** The key to call [provider] with: the one just typed, or the stored one, so a saved key need not be retyped. */
    private fun keyFor(apiKey: String, provider: LlmProvider): String =
        apiKey.takeIf { it.isNotBlank() } ?: storedKey(provider).orEmpty()

    private fun storedKey(provider: LlmProvider): String? =
        (secrets.get(providerSecretKey(provider)) as? SecretResult.Found)?.value

    companion object {
        /** The key slot is per provider, so switching back finds a key already stored. */
        internal fun providerSecretKey(provider: LlmProvider): String = "llm-api-key-${provider.name.lowercase()}"
        val KEY_REMOVED: AppMessage = AppMessage.Text(TextKey.MESSAGE_KEY_REMOVED)
        val CONNECTED: AppMessage = AppMessage.Text(TextKey.MESSAGE_PROVIDER_CONNECTED)
    }
}
