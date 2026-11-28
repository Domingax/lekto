package app.lekto.library

import app.lekto.LocalChanges
import app.lekto.core.book.Book
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.BookProgress
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingState
import app.lekto.core.book.SampleBookRow
import app.lekto.core.book.SampleLibrary
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
 * every state — empty, loading, importing, failed, searching — is directly
 * testable.
 *
 * [listing] is the split the vault hands over: the recently read books first,
 * then the rest by title (issue #188). [query] is the reader's title search, view
 * state that never reaches the vault (issue #173). [loading] is true while a
 * re-read of the vault is in flight, so an empty shelf shows skeleton rows
 * rather than the empty message it would claim (issue #208; docs/design.md §8).
 * [selectedStates] is the **Reading state** filter (issue #200): a multi-select
 * view state that opens on Unread and In progress, survives leaving the Library
 * and returning, resets on app restart, and never reaches the vault.
 *
 * [sample] is the **Sample book** projected beside the vault's books (issue
 * #278): one row above them, never one of them, so it is absent from
 * [books] and from the **Languages** counts. [sampleFailure] is the one line a
 * failed materialisation leaves, so the shelf stays in its empty state rather
 * than showing a row that points at files it could not write.
 */
data class LibraryUiState(
    val listing: LibraryListing = LibraryListing(),
    val query: String = "",
    val selectedStates: Set<ReadingState> = DEFAULT_STATES,
    val loading: Boolean = false,
    val importing: Boolean = false,
    val progress: Float = 0f,
    val error: AppMessage? = null,
    val sample: SampleBookRow? = null,
    val sampleFailure: AppMessage? = null,
) {
    /**
     * Every book the vault lists, in the order it hands them over. Stays the whole
     * set whatever the query or the filter says: it is what the Languages surface
     * counts, and a filter must not hide the search field (issues #173, #200).
     */
    val books: List<Book> get() = listing.lastRead + listing.others

    /**
     * The books the screen shows: the query's matches, then the **Reading state**
     * filter. The two apply together, so a book the filter hides stays hidden by
     * a query that matches it (#173, #200).
     */
    val visibleBooks: List<Book>
        get() = listing.matching(query).filter { book -> listing.readingStateOf(book) in selectedStates }

    /** The recently read group after the filter, so a hidden book takes no heading with it. */
    val visibleLastRead: List<Book>
        get() = listing.lastRead.filter { book -> listing.readingStateOf(book) in selectedStates }

    /** The title-ordered rest after the filter, finished books included when their chip is on. */
    val visibleOthers: List<Book>
        get() = listing.others.filter { book -> listing.readingStateOf(book) in selectedStates }

    /**
     * The book the **Resume block** leads with (issue #288): the most recently
     * read unfinished book, when the block is shown. The block obeys the same two
     * narrowings as the list under it, so it is absent while a query is non-empty
     * and when the filter excludes **In progress**. `null` is today's shelf: the
     * books keep their rows and their headings.
     */
    val resumeBook: Book?
        get() = when {
            searching -> null
            ReadingState.IN_PROGRESS !in selectedStates -> null
            else -> visibleLastRead.firstOrNull { book -> listing.readingStateOf(book) == ReadingState.IN_PROGRESS }
        }

    /** Whether a query narrows the list, so the screen drops the grouped shelf. */
    val searching: Boolean get() = query.isNotBlank()

    /** Whether the query matches no title at all, whatever the filter would then hide. */
    val queryMatchesNothing: Boolean get() = searching && listing.matching(query).isEmpty()

    /** Whether every listed book is **Finished**, which is why the filter hides them all. */
    val everyBookIsFinished: Boolean
        get() = books.isNotEmpty() && books.all { book -> listing.readingStateOf(book) == ReadingState.FINISHED }

    /**
     * Whether the vault holds no book at all — a query cannot have emptied it.
     *
     * It reads the vault's own listing, not a narrowed view: a filter that hides
     * every book must not hide the search field too.
     */
    val vaultEmpty: Boolean get() = listing.lastRead.isEmpty() && listing.others.isEmpty()

    /**
     * Whether the shelf holds a row at all — a vault book or the **Sample book**
     * (issue #278). It is the value the empty state and the header's `Import`
     * word read: a vault with no book but a sample is a shelf, not an empty one.
     */
    val hasRows: Boolean get() = books.isNotEmpty() || sample != null

    companion object {

        /**
         * What the Library opens on: what is still to read, not the books already
         * finished (docs/design.md §7.1; ADR-0038).
         */
        val DEFAULT_STATES: Set<ReadingState> = setOf(ReadingState.UNREAD, ReadingState.IN_PROGRESS)
    }
}

/**
 * The library's state holder: it lists the vault's books, runs imports off the
 * main thread (issue #15), and deletes a book (issue #131).
 *
 * Import and delete are occasional actions that must not block the app, so each
 * launches on an injected [dispatcher], reports its outcome, and lands a failure
 * in [LibraryUiState.error] rather than throwing. The [dispatcher] is injected
 * because a hard-coded one cannot be driven by virtual time (docs/development.md,
 * "Deterministic seams").
 *
 * A book with a **Reading position** whose rendered length is still missing is
 * measured on that same dispatcher, so constructing the controller never parses
 * a book and the row moves from `…` to its percentage when the measurement lands
 * (issue #196). A derived write is not a sync trigger, so no [LocalChanges]
 * signal is raised.
 */
@Suppress("TooManyFunctions") // One function per Library action; they share the one state and splitting scatters it.
class LibraryController(
    private val library: BookLibrary,
    /** Notified after an import lands, so sync pushes the new book (issue #121). */
    private val changes: LocalChanges,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
    /**
     * The **Sample book** this build can materialise, or `null` when the platform
     * wires none (issue #278). It is observed here, never prepended to the
     * vault's listing: the shelf shows one row above the books, and nothing the
     * vault counts or syncs can see it.
     */
    private val samples: SampleLibrary? = null,
) {

    private val _state = MutableStateFlow(LibraryUiState(listing = library.listing(), sample = samples?.row()))
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
                    current.copy(
                        listing = listing,
                        sample = samples?.row(),
                        importing = false,
                        progress = 1f,
                        error = null,
                    )
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
     * Materialises [language]'s **Sample book** off the main thread (issue #278):
     * the first launch's language answer calls it, and the shelf then shows one
     * row above the vault's books. A second call — the reader adding a language
     * in Settings — writes nothing, because the device already holds its one
     * sample. A write that fails lands one line in [LibraryUiState.sampleFailure]
     * and leaves the shelf in its empty state, never a row pointing at files that
     * could not be written.
     */
    @Suppress("TooGenericExceptionCaught") // Any write failure becomes one line; the shelf stays usable.
    fun materialiseSample(language: String) {
        val source = samples ?: return
        scope.launch {
            try {
                source.materialise(language)
                _state.update { current -> current.copy(sample = source.row(), sampleFailure = null) }
            } catch (_: Exception) { // The failure is the fact, not its reason: the reader gets one honest line.
                _state.update { current ->
                    current.copy(sample = null, sampleFailure = AppMessage.of(TextKey.LIBRARY_SAMPLE_FAILED))
                }
            }
        }
    }

    /**
     * Removes the **Sample book** from the shelf (issue #278): one confirmation is
     * asked by the screen, and this writes the dismissal, deletes its files and
     * clears the row. A failure leaves the row and says so, because the sample is
     * still there. Nothing the reader authored is touched.
     */
    @Suppress("TooGenericExceptionCaught") // Any delete failure becomes one line; the shelf stays usable.
    fun dismissSample() {
        val source = samples ?: return
        val row = _state.value.sample ?: return
        scope.launch {
            try {
                source.dismiss(row.book.id)
                _state.update { current -> current.copy(sample = source.row()) }
            } catch (_: Exception) { // The row is still there, so the line says the removal did not happen.
                _state.update { current ->
                    current.copy(sampleFailure = AppMessage.of(TextKey.LIBRARY_SAMPLE_REMOVE_FAILED))
                }
            }
        }
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
     * Adds [state] to the filter or removes it (issue #200). The last selected
     * state cannot be turned off, so the shelf never shows nothing because of a
     * tap that was meant to narrow it: the reader uses **Show all books** to see
     * everything. The filter is view state and never reaches the vault.
     */
    fun toggleState(state: ReadingState) {
        _state.update { current ->
            val next = if (state in current.selectedStates) {
                current.selectedStates - state
            } else {
                current.selectedStates + state
            }
            if (next.isEmpty()) current else current.copy(selectedStates = next)
        }
    }

    /**
     * Clears both narrowings at once (issue #200; docs/design.md §7.1): every
     * **Reading state** is selected and the query is emptied, so the shelf shows
     * the whole vault again. It is what the empty and no-match states offer.
     */
    fun showAllBooks() {
        _state.update { current ->
            current.copy(selectedStates = ReadingState.entries.toSet(), query = "")
        }
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
                _state.update { current -> current.copy(listing = listing, sample = samples?.row(), error = null) }
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
     * The read runs off the caller's thread, so the screen is told a list is
     * coming ([LibraryUiState.loading]) and shows skeleton rows rather than the
     * empty message it would otherwise claim (issue #208).
     *
     * Refreshes are serialised: a read that started earlier must not land after
     * a later one, or a page turn still being written would reorder the list
     * from a stale snapshot (issue #188).
     */
    fun refresh() {
        scope.launch {
            refreshMutex.withLock {
                _state.update { current -> current.copy(loading = true) }
                try {
                    val listing = loadListing()
                    _state.update { current -> current.copy(listing = listing, sample = samples?.row()) }
                } finally {
                    _state.update { current -> current.copy(loading = false) }
                }
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
