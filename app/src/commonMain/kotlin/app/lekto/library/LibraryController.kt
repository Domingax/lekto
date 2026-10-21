package app.lekto.library

import app.lekto.LocalChanges
import app.lekto.core.book.Book
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.BookProgress
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
 * every state — empty, importing, failed, searching — is directly testable.
 *
 * [listing] is the split the vault hands over: the recently read books first,
 * then the rest by title (issue #188). [query] is the reader's title search, view
 * state that never reaches the vault (issue #173).
 */
data class LibraryUiState(
    val listing: LibraryListing = LibraryListing(),
    val query: String = "",
    val importing: Boolean = false,
    val progress: Float = 0f,
    val error: AppMessage? = null,
) {
    /**
     * Every book the vault lists, in the order it hands them over. Stays the whole
     * set whatever the query says: it is what the Languages surface counts, and a
     * search must not narrow it (issue #173).
     */
    val books: List<Book> get() = listing.lastRead + listing.others

    /**
     * The books the screen shows: the query's matches when it has one, else the
     * vault's listing unchanged.
     */
    val visibleBooks: List<Book> get() = listing.matching(query)

    /** Whether a query narrows the list, so the screen drops the `Continue reading` split. */
    val searching: Boolean get() = query.isNotBlank()

    /**
     * Whether the vault holds no book at all — a query cannot have emptied it.
     *
     * It reads the vault's own listing, not a narrowed view: when the
     * **Reading state** filter lands (#200), the state must keep this reading of
     * the whole vault rather than of the filtered list, or a filter that hides
     * every book would hide the search field too.
     */
    val vaultEmpty: Boolean get() = listing.lastRead.isEmpty() && listing.others.isEmpty()
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
 *
 * A book with a **Reading position** whose rendered length is still missing is
 * measured on that same dispatcher, so constructing the controller never parses
 * a book and the row moves from `…` to its percentage when the measurement lands
 * (issue #196). A derived write is not a sync trigger, so no [LocalChanges]
 * signal is raised.
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

    /**
     * Books whose length was already measured or whose measurement failed, so a
     * book that cannot be parsed is not re-parsed on every return to the Library.
     */
    private val attempted = MutableStateFlow<Set<String>>(emptySet())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        measureMissingLengths(_state.value.listing)
    }

    /** Imports [bytes] as [fileName], reporting progress and catching failure. */
    @Suppress("TooGenericExceptionCaught") // Any import failure becomes a message; the app stays usable.
    fun import(fileName: String, bytes: ByteArray) {
        if (_state.value.importing) return
        _state.update { current -> current.copy(importing = true, progress = 0f, error = null) }
        scope.launch {
            try {
                library.import(fileName, bytes) { progress -> report(progress) }
                changes.contentChanged()
                val listing = loadListing()
                _state.update { current ->
                    current.copy(listing = listing, importing = false, progress = 1f, error = null)
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
     * Narrows the list to the books whose title matches [query] (issue #173).
     * The query is view state: it never reaches the vault, and the controller
     * clears it when the reader leaves the Library.
     */
    fun search(query: String) {
        _state.update { current -> current.copy(query = query) }
    }

    /** Empties the query, restoring the vault's own listing (issue #173). */
    fun clearSearch() {
        search("")
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
                val listing = loadListing()
                _state.update { current -> current.copy(listing = listing, error = null) }
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
                val listing = loadListing()
                _state.update { current -> current.copy(listing = listing) }
            }
        }
    }

    /**
     * Lists the vault and repairs every **Reading progress** whose rendered
     * length is missing (issue #196). The listing itself is cheap: it reads a
     * position record and a tiny length entry per book, never the parsed text.
     */
    private fun loadListing(): LibraryListing {
        val listing = library.listing()
        measureMissingLengths(listing)
        return listing
    }

    /**
     * Measures each positioned book whose length is still unknown, off the
     * caller's thread, and re-lists once so the rows move from `…` to their
     * percentage. A book that cannot be parsed keeps `…`: no length is invented
     * and the row never falls back to `Not started`. Each book is attempted once
     * per controller, so a permanent failure does not re-parse on every refresh.
     * Nothing is signalled to sync, because a **Derived asset** is not
     * user-authored data.
     */
    private fun measureMissingLengths(listing: LibraryListing) {
        val pending = (listing.lastRead + listing.others).filter { book ->
            listing.progressOf(book) is BookProgress.Unmeasured && book.id !in attempted.value
        }
        if (pending.isEmpty()) return
        scope.launch {
            val measured = pending.mapNotNull { book -> runCatching { library.measure(book.id) }.getOrNull() }
            attempted.update { ids -> ids + pending.map { book -> book.id } }
            if (measured.isNotEmpty()) refresh()
        }
    }

    private fun report(progress: ImportProgress) {
        _state.update { current -> current.copy(progress = progress.fraction) }
    }
}
