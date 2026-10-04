package app.lekto.library

import app.lekto.core.book.Book
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ImportProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the library screen shows. The screen renders this and nothing else, so
 * every state — empty, importing, failed — is directly testable.
 */
data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val importing: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
)

/**
 * The library's state holder: it lists the vault's books and runs imports off
 * the main thread (issue #15).
 *
 * Import is an occasional action that must not block the app, so it launches on
 * an injected [dispatcher], reports [ImportProgress], and lands a failure in
 * [LibraryUiState.error] rather than throwing. The [dispatcher] is injected
 * because a hard-coded one cannot be driven by virtual time (docs/testing.md,
 * "Deterministic seams").
 */
class LibraryController(
    private val library: BookLibrary,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _state = MutableStateFlow(LibraryUiState(books = library.books()))
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Imports [bytes] as [fileName], reporting progress and catching failure. */
    @Suppress("TooGenericExceptionCaught") // Any import failure becomes a message; the app stays usable.
    fun import(fileName: String, bytes: ByteArray) {
        if (_state.value.importing) return
        _state.update { current -> current.copy(importing = true, progress = 0f, error = null) }
        scope.launch {
            try {
                library.import(fileName, bytes) { progress -> report(progress) }
                _state.update { current ->
                    current.copy(books = library.books(), importing = false, progress = 1f, error = null)
                }
            } catch (failure: Exception) { // Any import failure must surface as a message, not a crash.
                _state.update { current ->
                    current.copy(importing = false, error = failure.message ?: "The import failed.")
                }
            }
        }
    }

    /** Dismisses the current error, so the app stays usable after a failure. */
    fun dismissError() {
        _state.update { current -> current.copy(error = null) }
    }

    /**
     * Re-reads the vault's books. Called after a vault import replaces the store
     * (issue #20), so the library shows the restored books rather than the ones
     * it listed before.
     */
    fun refresh() {
        scope.launch {
            val books = library.books()
            _state.update { current -> current.copy(books = books) }
        }
    }

    private fun report(progress: ImportProgress) {
        _state.update { current -> current.copy(progress = progress.fraction) }
    }
}
