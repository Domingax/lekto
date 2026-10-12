package app.lekto.difficulty

import app.lekto.core.difficulty.DifficultyState
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

/** What the settings screen shows about the difficulty table. */
data class DifficultyUiState(
    val status: DifficultyState = DifficultyState.NotInstalled,
    val installing: Boolean = false,
    val error: String? = null,
)

/**
 * The difficulty table's state holder (ADR-0029): it reports what is installed
 * and runs the on-demand download off the UI thread. The [dispatcher] is
 * injected because a hard-coded one cannot be driven by virtual time
 * (docs/testing.md, "Deterministic seams").
 */
class DifficultyController(
    private val services: DifficultyServices,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _state = MutableStateFlow(DifficultyUiState(status = services.installer.status()))
    val state: StateFlow<DifficultyUiState> = _state.asStateFlow()

    /** Downloads and installs the table, reporting progress and catching failure. */
    @Suppress("TooGenericExceptionCaught") // A failed download becomes a message; the app stays usable.
    fun install(onInstalled: () -> Unit = {}) {
        if (_state.value.installing) return
        _state.update { current -> current.copy(installing = true, error = null) }
        scope.launch {
            try {
                val status = withContext(dispatcher) { services.installer.install() }
                _state.update { current -> current.copy(status = status, installing = false) }
                // The table changed what the reader would paint, so the mastery
                // composition recolours the open book the way a save does (ADR-0030).
                if (status is DifficultyState.Ready) onInstalled()
            } catch (failure: Exception) {
                _state.update { current ->
                    current.copy(installing = false, error = failure.message ?: "The download failed.")
                }
            }
        }
    }

    /** Dismisses the current error, so the app stays usable after a failure. */
    fun dismissError() {
        _state.update { current -> current.copy(error = null) }
    }
}

/** An honest one-line summary of the difficulty table's state, for the settings screen. */
fun DifficultyState.summary(): String = when (this) {
    DifficultyState.NotInstalled ->
        "Reading-level data isn't installed yet. Download it to colour words by how hard they are."

    is DifficultyState.Ready ->
        "Reading-level data is installed. Words are coloured by how hard they are for your level."

    is DifficultyState.Incompatible ->
        "The installed reading-level data is format $found, but this app reads format $expected. Download it again."

    is DifficultyState.Corrupt ->
        "The installed reading-level data is damaged and couldn't be read. Download it again."
}
