package app.lekto.library

import app.lekto.LocalChanges
import app.lekto.core.book.Book
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.LibraryListing
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What the library screen shows. The screen renders this and nothing else, so
 * every state — empty, importing, failed — is directly testable.
 *
 * [listing] is the split the vault hands over: the recently read books first,
 * then the rest by title (issue #188).
 */
data class LibraryUiState(
    val listing: LibraryListing = LibraryListing(),
    val importing: Boolean = false,
    val progress: Float = 0f,
    val error: AppMessage? = null,
) {
    /** Every listed book, in the order the screen shows them. */
    val books: List<Book> get() = listing.lastRead + listing.others
}

/**
 * The library's state holder: it lists the vault's books, runs imports off the
 * main thread (issue #15), and deletes a book (issue #131).
 *
 * Import and delete are occasional actions that must not block the app, so each
 * launches on an injected [dispatcher], reports its outcome, and lands a failure
 * in [LibraryUiState.error] rather than throwing. The [dispatcher] is injected
 * because a hard-coded one cannot be driven by virtual time (docs/testing.md,
 * "Deterministic seams").
 */
class LibraryController(
    private val library: BookLibrary,
    /** Notified after an import lands, so sync pushes the new book (issue #121). */
    private val changes: LocalChanges,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    private val _state = MutableStateFlow(LibraryUiState(listing = library.listing()))
    private val refreshMutex = Mutex()
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Imports [bytes] as [fileName], reporting progress and catching failure. */
    @Suppress("TooGenericExceptionCaught") // Any import failure becomes a message; the app stays usable.
    fun import(fileName: String, bytes: ByteArray) {
        if (_state.value.importing) return
        _state.update { current -> current.copy(importing = true, progress = 0f, error = null) }
        scope.launch {
            try {
                library.import(fileName, bytes) { progress -> report(progress) }
                changes.contentChanged()
                _state.update { current ->
                    current.copy(listing = library.listing(), importing = false, progress = 1f, error = null)
                }
            } catch (failure: Exception) { // Any import failure must surface as a message, not a crash.
                _state.update { current ->
                    current.copy(importing = false, error = AppMessage.failure(failure, TextKey.MESSAGE_IMPORT_FAILED))
                }
            }
        }
    }

    /** Dismisses the current error, so the app stays usable after a failure. */
    fun dismissError() {
        _state.update { current -> current.copy(error = null) }
    }

    /**
     * Deletes the book with [bookId] from the vault — its record, its original,
     * its cached text and its reading position — off the main thread, refreshes
     * the list and notifies the local-change signal so sync pushes the deletion
     * (issue #131). A failure lands in [LibraryUiState.error] rather than
     * throwing, exactly as an import does.
     */
    @Suppress("TooGenericExceptionCaught") // Any delete failure becomes a message; the app stays usable.
    fun delete(bookId: String) {
        scope.launch {
            try {
                library.delete(bookId)
                changes.contentChanged()
                _state.update { current -> current.copy(listing = library.listing(), error = null) }
            } catch (failure: Exception) { // Any delete failure must surface as a message, not a crash.
                _state.update { current ->
                    current.copy(error = AppMessage.failure(failure, TextKey.MESSAGE_BOOK_DELETE_FAILED))
                }
            }
        }
    }

    /**
     * Re-reads the vault's books. Called after a vault import replaces the store
     * (issue #20) so the library shows the restored books rather than the ones it
     * listed before, and when the reader returns (issue #188) so the book just
     * read leads the list.
     *
     * Refreshes are serialised: a read that started earlier must not land after
     * a later one, or a page turn still being written would reorder the list
     * from a stale snapshot (issue #188).
     */
    fun refresh() {
        scope.launch {
            refreshMutex.withLock {
                val listing = library.listing()
                _state.update { current -> current.copy(listing = listing) }
            }
        }
    }

    private fun report(progress: ImportProgress) {
        _state.update { current -> current.copy(progress = progress.fraction) }
    }
}
