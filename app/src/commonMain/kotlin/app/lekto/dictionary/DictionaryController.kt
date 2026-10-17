package app.lekto.dictionary

import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.language.OfferedLanguage
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

/** What the **Languages** surface shows about one dictionary pack (issue #149). */
data class DictionaryUiState(
    val status: DictionaryPackState = DictionaryPackState.NotInstalled,
    val installing: Boolean = false,
    val error: AppMessage? = null,
)

/**
 * The dictionary packs' state holder, one state per (Reading language ×
 * **Translation language**) pair (issue #149): it reports what is installed for
 * each pair the **Language manifest** publishes, runs the on-demand download off
 * the UI thread, and answers a word query on a background dispatcher. The
 * [dispatcher] is injected because a hard-coded one cannot be driven by virtual
 * time (docs/testing.md, "Deterministic seams").
 *
 * States are keyed by the artifact name the manifest published, which names the
 * pair uniquely; the query itself resolves its pack by the book's language.
 */
class DictionaryController(
    private val services: DictionaryServices,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _states = MutableStateFlow<Map<String, DictionaryUiState>>(emptyMap())

    /** Each published pair's pack state, keyed by its manifest artifact name. */
    val states: StateFlow<Map<String, DictionaryUiState>> = _states.asStateFlow()

    /**
     * Reads each published pair's installed state, so the surface reports what is
     * already there. It inspects the derived store (a cheap, blocking read, as the
     * pack's status always was), so it runs where the surface composes rather than
     * per frame; a pair already downloading keeps its progress and error.
     */
    fun refresh(languages: List<OfferedLanguage>) {
        val current = _states.value
        val inspected = current + languages.flatMap { language ->
            language.dictionaries.map { dictionary ->
                dictionary.name to (current[dictionary.name] ?: DictionaryUiState()).copy(
                    status = services.installer(language.language, dictionary.translationLanguage).status(),
                )
            }
        }
        _states.value = inspected
    }

    /**
     * Downloads and installs the pack [artifact] from the manifest for the
     * [readingLanguage] × [translationLanguage] pair, reporting progress and
     * catching failure so the surface stays usable.
     */
    @Suppress("TooGenericExceptionCaught") // A failed download becomes a message; the app stays usable.
    fun install(readingLanguage: String, translationLanguage: String, artifact: String) {
        if (stateOf(artifact).installing) return
        // Resolve the installer on this thread, so its per-pair cache is only
        // ever written from the surface's own thread (docs/testing.md, "Deterministic seams").
        val installer = services.installer(readingLanguage, translationLanguage)
        update(artifact) { current -> current.copy(installing = true, error = null) }
        scope.launch {
            try {
                val status = withContext(dispatcher) { installer.install(DictionaryRelease.url(artifact)) }
                update(artifact) { current -> current.copy(status = status, installing = false) }
            } catch (failure: Exception) {
                update(artifact) { current ->
                    current.copy(
                        installing = false,
                        error = AppMessage.failure(failure, TextKey.MESSAGE_DICTIONARY_DOWNLOAD_FAILED),
                    )
                }
            }
        }
    }

    /** Dismisses [artifact]'s current error, so the app stays usable after a failure. */
    fun dismissError(artifact: String) {
        update(artifact) { current -> current.copy(error = null) }
    }

    /**
     * Answers [surface] from the installed pack for the [language] ×
     * [translationLanguage] pair, off the UI thread. The pair is resolved on the
     * caller's thread, so the services' per-pair cache is only ever written from
     * one thread; the blocking query runs on [dispatcher]. An unchosen translation
     * language has no pack, so the lookup reports it honestly.
     */
    suspend fun lookUp(surface: String, language: String?, translationLanguage: String?): WordLookup {
        val lookup = services.lookup(language, translationLanguage)
        return withContext(dispatcher) { lookup.lookup(surface, language) }
    }

    private fun stateOf(artifact: String): DictionaryUiState = _states.value[artifact] ?: DictionaryUiState()

    private fun update(artifact: String, transform: (DictionaryUiState) -> DictionaryUiState) {
        _states.update { current -> current + (artifact to transform(current[artifact] ?: DictionaryUiState())) }
    }
}
