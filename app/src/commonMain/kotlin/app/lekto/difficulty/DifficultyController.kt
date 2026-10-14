package app.lekto.difficulty

import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.text.baseLanguage
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

/** What the **Languages** surface shows about one language's **Difficulty table** (issue #148). */
data class DifficultyUiState(
    val status: DifficultyState = DifficultyState.NotInstalled,
    val installing: Boolean = false,
    val error: String? = null,
)

/**
 * The **Difficulty table**'s state holder, one state per **Reading language**
 * (ADR-0029; issue #148): it reports what is installed for each offered language
 * and runs the on-demand download off the UI thread. The [dispatcher] is injected
 * because a hard-coded one cannot be driven by virtual time (docs/testing.md,
 * "Deterministic seams").
 */
class DifficultyController(
    private val services: DifficultyServices,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _states = MutableStateFlow<Map<String, DifficultyUiState>>(emptyMap())

    /** Each offered language's table state, keyed by its base tag. */
    val states: StateFlow<Map<String, DifficultyUiState>> = _states.asStateFlow()

    /**
     * Reads each offered [language]'s installed state, so the surface reports what
     * is already there. It inspects the derived store (a cheap, blocking read, as
     * the table's status always was), so it runs where the surface composes rather
     * than per frame; a language already downloading keeps its progress and error.
     */
    fun refresh(languages: List<String>) {
        val current = _states.value
        val inspected = current + languages.associate { language ->
            val base = baseLanguage(language)
            base to (current[base] ?: DifficultyUiState()).copy(status = services.installer(base).status())
        }
        _states.value = inspected
    }

    /**
     * Downloads and installs [language]'s table from the manifest's [artifact],
     * reporting progress and catching failure so the surface stays usable. On
     * success it notifies [onInstalled], so the mastery composition recolours the
     * open book the way a save does (ADR-0030) — the download's effect is visible
     * without a relaunch.
     */
    @Suppress("TooGenericExceptionCaught") // A failed download becomes a message; the app stays usable.
    fun install(language: String, artifact: String, onInstalled: () -> Unit = {}) {
        val base = baseLanguage(language)
        if (stateOf(base).installing) return
        // Resolve the installer on this thread, so its per-language cache is only
        // ever written from the surface's own thread (docs/testing.md, "Deterministic seams").
        val installer = services.installer(base)
        update(base) { current -> current.copy(installing = true, error = null) }
        scope.launch {
            try {
                val status = withContext(dispatcher) { installer.install(DifficultyRelease.url(artifact)) }
                update(base) { current -> current.copy(status = status, installing = false) }
                if (status is DifficultyState.Ready) onInstalled()
            } catch (failure: Exception) {
                update(base) { current ->
                    current.copy(installing = false, error = failure.message ?: "The download failed.")
                }
            }
        }
    }

    /** Dismisses [language]'s current error, so the app stays usable after a failure. */
    fun dismissError(language: String) {
        update(baseLanguage(language)) { current -> current.copy(error = null) }
    }

    private fun stateOf(base: String): DifficultyUiState = _states.value[base] ?: DifficultyUiState()

    private fun update(base: String, transform: (DifficultyUiState) -> DifficultyUiState) {
        _states.update { current -> current + (base to transform(current[base] ?: DifficultyUiState())) }
    }
}

/** An honest one-line summary of a **Difficulty table**'s state, for the **Languages** surface. */
fun DifficultyState.summary(): String = when (this) {
    DifficultyState.NotInstalled ->
        "Word-difficulty data isn't installed yet. Download it to colour words by how hard they are."

    is DifficultyState.Ready ->
        "Word-difficulty data is installed. Words are coloured by how hard they are for your level."

    is DifficultyState.Incompatible ->
        "The installed word-difficulty data is format $found, but this app reads format $expected. Download it again."

    is DifficultyState.Corrupt ->
        "The installed word-difficulty data is damaged and couldn't be read. Download it again."
}
