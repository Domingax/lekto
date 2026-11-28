package app.lekto.difficulty

import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.OfferedArtifact
import app.lekto.core.text.baseLanguage
import app.lekto.text.AppMessage
import app.lekto.text.TextKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val error: AppMessage? = null,
)

/**
 * The **Difficulty table**'s state holder, one state per **Reading language**
 * (ADR-0029; issue #148): it reports what is installed for each offered language
 * and runs the on-demand download off the UI thread. The [dispatcher] is injected
 * because a hard-coded one cannot be driven by virtual time (docs/development.md,
 * "Deterministic seams").
 */
class DifficultyController(
    private val services: DifficultyServices,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _states = MutableStateFlow<Map<String, DifficultyUiState>>(emptyMap())

    /** The download each language currently has in flight, so a stop can cancel it. */
    private val downloads = mutableMapOf<String, Job>()

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
     * Downloads and installs [language]'s table from the [artifact] the manifest
     * published, following the manifest's own URL and checking its digest, and
     * reporting progress and catching failure so the surface stays usable. On
     * success it notifies [onInstalled], so the mastery composition recolours the
     * open book the way a save does (ADR-0030) — the download's effect is visible
     * without a relaunch (issue #305).
     */
    @Suppress("TooGenericExceptionCaught") // A failed download becomes a message; the app stays usable.
    fun install(language: String, artifact: OfferedArtifact, onInstalled: () -> Unit = {}) {
        val base = baseLanguage(language)
        if (stateOf(base).installing) return
        // Resolve the installer on this thread, so its per-language cache is only
        // ever written from the surface's own thread (docs/development.md, "Deterministic seams").
        val installer = services.installer(base)
        update(base) { current -> current.copy(installing = true, error = null) }
        downloads[base] = scope.launch {
            try {
                val status = withContext(dispatcher) { installer.install(artifact.url, artifact.sha256) }
                update(base) { current -> current.copy(status = status, installing = false) }
                if (status is DifficultyState.Ready) onInstalled()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                update(base) { current ->
                    current.copy(
                        installing = false,
                        error = AppMessage.failure(failure, TextKey.MESSAGE_DIFFICULTY_DOWNLOAD_FAILED),
                    )
                }
            }
        }
    }

    /**
     * Stops [language]'s download in flight, if any, and clears its installing
     * flag at once (issue #277's `Stop`). A download already past the point of no
     * return may still finish writing its bytes, but the cancelled coroutine never
     * lands its result, so the surface neither reports a table the reader stopped
     * nor claims one is still coming. A stop with nothing running is a no-op
     * beyond clearing a stale error.
     */
    fun stop(language: String) {
        val base = baseLanguage(language)
        downloads.remove(base)?.cancel()
        update(base) { current -> current.copy(installing = false, error = null) }
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
