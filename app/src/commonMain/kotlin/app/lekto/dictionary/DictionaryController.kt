package app.lekto.dictionary

import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.WordLookup
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

/** What the settings screen shows about the dictionary pack. */
data class DictionaryUiState(
    val status: DictionaryPackState = DictionaryPackState.NotInstalled,
    val installing: Boolean = false,
    val error: String? = null,
)

/**
 * The dictionary's state holder (issue #18): it reports what is installed, runs
 * the on-demand download off the UI thread, and answers a word query on a
 * background dispatcher. The [dispatcher] is injected because a hard-coded one
 * cannot be driven by virtual time (docs/testing.md, "Deterministic seams").
 */
class DictionaryController(
    private val services: DictionaryServices,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _state = MutableStateFlow(DictionaryUiState(status = services.installer.status()))
    val state: StateFlow<DictionaryUiState> = _state.asStateFlow()

    /** Downloads and installs the pack, reporting progress and catching failure. */
    @Suppress("TooGenericExceptionCaught") // A failed download becomes a message; the app stays usable.
    fun install() {
        if (_state.value.installing) return
        _state.update { current -> current.copy(installing = true, error = null) }
        scope.launch {
            try {
                val status = withContext(dispatcher) { services.installer.install() }
                _state.update { current -> current.copy(status = status, installing = false) }
            } catch (failure: Exception) {
                _state.update { current ->
                    current.copy(installing = false, error = failure.message ?: "The dictionary download failed.")
                }
            }
        }
    }

    /** Dismisses the current error, so the app stays usable after a failure. */
    fun dismissError() {
        _state.update { current -> current.copy(error = null) }
    }

    /** Answers [surface] from the installed pack, off the UI thread. */
    suspend fun lookUp(surface: String, language: String?): WordLookup =
        withContext(dispatcher) { services.lookup.lookup(surface, language) }
}

/** An honest one-line summary of the pack's state, for the settings screen. */
fun DictionaryPackState.summary(): String = when (this) {
    DictionaryPackState.NotInstalled ->
        "The offline dictionary isn't installed yet. Download it to look words up with no network."

    is DictionaryPackState.Ready ->
        "The offline dictionary is installed. Words are looked up with no network."

    is DictionaryPackState.Incompatible ->
        "The installed dictionary is format $found, but this app reads format $expected. Download it again."

    is DictionaryPackState.Corrupt ->
        "The installed dictionary is damaged and couldn't be read. Download it again."
}
