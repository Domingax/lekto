package app.lekto.language

import app.lekto.core.book.Book
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.language.orderOfferedLanguages
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
 * What the **Languages** surface renders (issue #146): the offered **Reading
 * language**s in the order the surface lists them, whether a manifest is
 * available at all, whether a fetch is in flight, and the honest message when
 * one failed.
 */
data class LanguagesUiState(
    val languages: List<OfferedLanguage> = emptyList(),
    val available: Boolean = false,
    val reloading: Boolean = false,
    val error: AppMessage? = null,
)

/**
 * The **Languages** surface's state holder (issue #146): it reads the cached
 * **Language manifest** on first entry, refetches on demand off the UI thread,
 * and orders the offered languages by how many `Book`s the reader has in each.
 *
 * The [dispatcher] is injected because a hard-coded one cannot be driven by
 * virtual time, and the [books] accessor is a function because the library's
 * list changes as books are imported (docs/testing.md, "Deterministic seams").
 */
class LanguageController(
    private val catalog: LanguageCatalog,
    private val books: () -> List<Book>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _state = MutableStateFlow(LanguagesUiState())
    val state: StateFlow<LanguagesUiState> = _state.asStateFlow()

    private var started = false

    /**
     * Reads the cached catalogue the first time the surface opens. When nothing
     * is cached it fetches, so a first launch offers the languages without a tap;
     * a later launch reads the cache and fetches nothing.
     */
    fun start() {
        if (started) return
        started = true
        val cached = catalog.status()
        if (cached is LanguageCatalogState.Available) publish(cached) else reload()
    }

    /** Refetches the manifest, reporting progress and catching failure so the surface stays usable. */
    @Suppress("TooGenericExceptionCaught") // A failed fetch becomes an honest message; the surface stays usable.
    fun reload() {
        if (_state.value.reloading) return
        _state.update { current -> current.copy(reloading = true, error = null) }
        scope.launch {
            try {
                val status = withContext(dispatcher) { catalog.reload() }
                _state.update { current -> current.copy(reloading = false, error = null).withStatus(status) }
            } catch (failure: Exception) {
                _state.update { current ->
                    current.copy(
                        reloading = false,
                        error = AppMessage.failure(failure, TextKey.MESSAGE_LANGUAGES_LOAD_FAILED),
                    )
                }
            }
        }
    }

    private fun publish(status: LanguageCatalogState) {
        _state.update { current -> current.withStatus(status) }
    }

    /** [status]'s languages, ordered by the reader's books; an unavailable manifest offers none. */
    private fun LanguagesUiState.withStatus(status: LanguageCatalogState): LanguagesUiState = copy(
        available = status is LanguageCatalogState.Available,
        languages = when (status) {
            is LanguageCatalogState.Available -> orderOfferedLanguages(status.languages, books())
            LanguageCatalogState.Unavailable -> emptyList()
        },
    )
}
