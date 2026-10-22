package app.lekto.library

import app.lekto.LocalChanges
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.BookProgress
import app.lekto.core.book.ImportException
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.ImportStage
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingProgress
import app.lekto.core.book.ReadingSession
import app.lekto.text.AppMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The library controller: imports run off the main thread, progress reaches the
 * state, a failure lands as a message instead of an exception, and the app stays
 * usable afterwards (issue #15).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One test per behaviour; the list is the controller's.
class LibraryControllerTest {

    @Test
    fun aBookImportedThroughTheControllerAppearsInTheList() = runTest {
        val library = FakeLibrary()
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.import("book.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertEquals(library.books(), controller.state.value.books)
        assertEquals(false, controller.state.value.importing)
        assertNull(controller.state.value.error)
    }

    @Test
    fun aFinishedImportLeavesProgressAtOne() = runTest {
        val library = FakeLibrary()
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.import("book.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertEquals(1f, controller.state.value.progress)
    }

    @Test
    fun aFailedImportIsCapturedAsAnErrorAndStopsImporting() = runTest {
        val library = FakeLibrary(failure = ImportException("Could not read 'broken.epub'."))
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.import("broken.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertEquals(false, controller.state.value.importing)
        assertEquals(AppMessage.core("Could not read 'broken.epub'."), controller.state.value.error)
    }

    @Test
    fun dismissingAnErrorClearsItAndLeavesTheLibraryUsable() = runTest {
        val library = FakeLibrary(failure = ImportException("nope"))
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)
        controller.import("broken.epub", byteArrayOf(1))
        advanceUntilIdle()

        controller.dismissError()

        assertNull(controller.state.value.error)
    }

    @Test
    fun progressReportedByTheImportReachesTheState() = runTest {
        val library = FakeLibrary(progress = REPORTED_PROGRESS)
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.import("book.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertTrue(controller.state.value.progress >= REPORTED_PROGRESS)
    }

    @Test
    fun aRefreshReportsALoadingShelfWhileItReadsTheVault() = runTest {
        // The vault read runs off the caller's thread, so an empty shelf shows
        // skeleton rows rather than claiming it holds no book (issue #208). The
        // state is observed from inside the read: that is the moment a real
        // dispatcher leaves it visible.
        val library = FakeLibrary()
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)
        library.isLoading = { controller.state.value.loading }

        controller.refresh()
        advanceUntilIdle()

        assertTrue(library.readWhileLoading, "the shelf must say it is loading while it reads")
        assertFalse(controller.state.value.loading, "the list landed")
    }

    @Test
    fun aRefreshPicksUpBooksStoredOutsideTheController() = runTest {
        val library = FakeLibrary()
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        // A vault import replaces the store under the controller; it only sees
        // the restored book after a refresh (issue #20).
        library.recognize(Book("b1", "Restored", "en", BookFormat.TXT, "restored.txt"))
        controller.refresh()
        advanceUntilIdle()

        assertEquals(listOf("b1"), controller.state.value.books.map { book -> book.id })
    }

    @Test
    fun aRefreshPicksUpTheReadingOrderFromTheVault() = runTest {
        val library = FakeLibrary().apply {
            recognize(book("b1"))
            recognize(book("b2"))
            markRead("b2")
        }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        // Reading b1 later, outside the controller, moves it to the front on the
        // next refresh — what the app does when the reader hands back (issue #188).
        library.markRead("b1")
        controller.refresh()
        advanceUntilIdle()

        assertEquals(listOf("b1", "b2"), controller.state.value.listing.lastRead.map { stored -> stored.id })
        assertEquals(emptyList(), controller.state.value.listing.others)
    }

    @Test
    fun aLandedImportNotifiesTheLocalChangeSignal() = runTest {
        // A book import is a local change, so sync pushes it rather than waiting
        // for its timer (issue #121).
        val changes = LocalChanges()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.content.collect { received += it } }
        val controller = LibraryController(FakeLibrary(), changes, StandardTestDispatcher(testScheduler), this)

        controller.import("book.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertTrue(received.isNotEmpty(), "an import must notify the local-change signal")
    }

    @Test
    fun aQueryNarrowsTheListedBooksAndFlagsTheSearch() = runTest {
        val library = FakeLibrary().apply {
            recognize(book("b1", "The Lantern Keeper"))
            recognize(book("b2", "A Smaller Book"))
        }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.search("small")

        assertEquals(listOf("b2"), controller.state.value.visibleBooks.map { listed -> listed.id })
        assertTrue(controller.state.value.searching)
    }

    @Test
    fun clearingTheSearchRestoresTheWholeList() = runTest {
        val library = FakeLibrary().apply {
            recognize(book("b1", "The Lantern Keeper"))
            recognize(book("b2", "A Smaller Book"))
        }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)
        controller.search("small")

        controller.clearSearch()

        assertEquals(listOf("b2", "b1"), controller.state.value.visibleBooks.map { listed -> listed.id })
        assertFalse(controller.state.value.searching)
    }

    @Test
    fun aRefreshKeepsTheQueryTheReaderTyped() = runTest {
        val library = FakeLibrary().apply {
            recognize(book("b1", "The Lantern Keeper"))
            recognize(book("b2", "A Smaller Book"))
        }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)
        controller.search("small")

        controller.refresh()
        advanceUntilIdle()

        assertEquals("small", controller.state.value.query)
        assertEquals(listOf("b2"), controller.state.value.visibleBooks.map { listed -> listed.id })
    }

    @Test
    fun deletingABookRemovesItFromTheList() = runTest {
        val library = FakeLibrary().apply { recognize(book("b1")) }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.delete("b1")
        advanceUntilIdle()

        assertEquals(emptyList(), controller.state.value.books)
        assertNull(controller.state.value.error)
    }

    @Test
    fun aDeleteRunsOffTheCallersThread() = runTest {
        val library = FakeLibrary().apply { recognize(book("b1")) }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.delete("b1")

        // The blocking vault write is launched on the injected dispatcher, not
        // run on the caller's thread (docs/testing.md, "Deterministic seams").
        assertEquals(listOf("b1"), library.books().map { stored -> stored.id })
        advanceUntilIdle()
        assertEquals(emptyList(), library.books())
    }

    @Test
    fun aFailedDeleteIsCapturedAsAnErrorAndLeavesTheBookListed() = runTest {
        val library = FakeLibrary(deleteFailure = RuntimeException("Could not delete 'Lantern'."))
            .apply { recognize(book("b1")) }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        controller.delete("b1")
        advanceUntilIdle()

        assertEquals(AppMessage.core("Could not delete 'Lantern'."), controller.state.value.error)
        assertEquals(listOf("b1"), controller.state.value.books.map { stored -> stored.id })
    }

    @Test
    fun aLandedDeleteNotifiesTheLocalChangeSignal() = runTest {
        // A deletion is a local change, so sync pushes it rather than waiting for
        // its timer (issue #131; ADR-0028).
        val changes = LocalChanges()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.content.collect { received += it } }
        val library = FakeLibrary().apply { recognize(book("b1")) }
        val controller = LibraryController(library, changes, StandardTestDispatcher(testScheduler), this)

        controller.delete("b1")
        advanceUntilIdle()

        assertTrue(received.isNotEmpty(), "a delete must notify the local-change signal")
    }

    @Test
    fun aKnownLengthIsListedAsAPercentageWithoutMeasuring() = runTest {
        val library = FakeLibrary().apply {
            recognize(book("b1"))
            readAt("b1", offset = KNOWN_OFFSET, length = MEASURED_LENGTH)
        }

        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)
        advanceUntilIdle()

        assertEquals(BookProgress.Measured(KNOWN_PERCENT), controller.state.value.listing.progressOf(book("b1")))
        assertEquals(emptyList(), library.measured, "a known length needs no measurement")
    }

    @Test
    fun aMissingLengthIsMeasuredOffTheCallersThread() = runTest {
        val library = FakeLibrary().apply {
            recognize(book("b1"))
            readAt("b1", offset = MEASURED_OFFSET)
        }

        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)

        // Construction listed the vault and scheduled the measurement in the
        // background; it did not parse anything on the calling thread (issue #196).
        assertEquals(BookProgress.Unmeasured, controller.state.value.listing.progressOf(book("b1")))
        assertEquals(emptyList(), library.measured)

        advanceUntilIdle()

        assertEquals(BookProgress.Measured(MEASURED_PERCENT), controller.state.value.listing.progressOf(book("b1")))
        assertEquals(listOf("b1"), library.measured)
    }

    @Test
    fun aFailedMeasurementLeavesTheRowUnmeasured() = runTest {
        val library = FakeLibrary(measureFailure = true).apply {
            recognize(book("b1"))
            readAt("b1", offset = MEASURED_OFFSET)
        }

        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)
        advanceUntilIdle()

        assertEquals(BookProgress.Unmeasured, controller.state.value.listing.progressOf(book("b1")))
    }

    @Test
    fun aFailedMeasurementIsNotRetriedOnEveryRefresh() = runTest {
        // A parse that fails will not heal by itself, so a return to the Library
        // must not re-parse the whole book every time (issue #196).
        val library = FakeLibrary(measureFailure = true).apply {
            recognize(book("b1"))
            readAt("b1", offset = MEASURED_OFFSET)
        }
        val controller = LibraryController(library, LocalChanges(), StandardTestDispatcher(testScheduler), this)
        advanceUntilIdle()

        controller.refresh()
        advanceUntilIdle()

        assertEquals(listOf("b1"), library.measured)
    }

    @Test
    fun measuringALengthSignalsNothingToSync() = runTest {
        // The length is a Derived asset: writing it is not a change the user made,
        // so neither the content nor the reading signal may fire (ADR-0005).
        val changes = LocalChanges()
        val content = mutableListOf<Unit>()
        val progress = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.content.collect { content += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.progress.collect { progress += it } }
        val library = FakeLibrary().apply {
            recognize(book("b1"))
            readAt("b1", offset = MEASURED_OFFSET)
        }

        LibraryController(library, changes, StandardTestDispatcher(testScheduler), this)
        advanceUntilIdle()

        assertTrue(library.measured.isNotEmpty(), "the length must have been measured")
        assertTrue(content.isEmpty(), "a derived write is not a content change")
        assertTrue(progress.isEmpty(), "a derived write is not a reading change")
    }
}

/** A book with [id] and [title], so a controller test has something to list and delete. */
private fun book(id: String, title: String = "Lantern"): Book =
    Book(id, title, "en", BookFormat.TXT, "${title.lowercase().replace(' ', '-')}.txt")

/** The progress the fake import reports mid-way, below the final 1.0. */
private const val REPORTED_PROGRESS = 0.4f

/** The rendered length the fake measures. */
private const val MEASURED_LENGTH = 1000

/** A position in the fake's 1000-character book, and the percentage it should read. */
private const val KNOWN_OFFSET = 250
private const val KNOWN_PERCENT = 25
private const val MEASURED_OFFSET = 280
private const val MEASURED_PERCENT = 28

private class FakeLibrary(
    private val progress: Float = 0f,
    private val failure: Exception? = null,
    private val deleteFailure: Exception? = null,
    private val measureFailure: Boolean = false,
) : BookLibrary {

    private var stored: List<Book> = emptyList()
    private val lastRead = mutableMapOf<String, Long>()
    private val positions = mutableMapOf<String, Int>()
    private val lengths = mutableMapOf<String, Int>()

    /** The books whose length the controller asked to measure, so a test can pin the background work. */
    val measured: MutableList<String> = mutableListOf()
    private var reads = 0L

    /** A test hook: the caller's loading flag, read at the moment the vault is listed. */
    var isLoading: () -> Boolean = { false }

    /** Whether the vault was read while the caller's state said a list was loading. */
    var readWhileLoading: Boolean = false
        private set

    override fun books(): List<Book> = stored

    override fun listing(): LibraryListing {
        readWhileLoading = isLoading()
        return LibraryListing.of(
            books = stored,
            lastReadByBook = lastRead.mapValues { (_, order) -> Instant.fromEpochMilliseconds(order) },
            progressByBook = positions.mapValues { (id, offset) ->
                BookProgress.forPosition(ReadingPosition(id, offset), lengths[id])
            },
        )
    }

    /** Test hook: pretend the book's reading position last moved after the others. */
    fun markRead(bookId: String) {
        lastRead[bookId] = ++reads
    }

    /** Test hook: the book has a position, and optionally an already-cached length. */
    fun readAt(bookId: String, offset: Int, length: Int? = null) {
        positions[bookId] = offset
        if (length != null) lengths[bookId] = length
    }

    /** Test hook: pretend the vault now holds [book], as a restored import would. */
    fun recognize(book: Book) {
        stored = stored + book
    }

    override fun measure(bookId: String): ReadingProgress? {
        measured += bookId
        val offset = positions[bookId]
        if (measureFailure || offset == null) return null
        return ReadingProgress(offset, lengths.getOrPut(bookId) { MEASURED_LENGTH })
    }

    override fun import(fileName: String, bytes: ByteArray, onProgress: (ImportProgress) -> Unit): Book {
        onProgress(ImportProgress(ImportStage.PARSING, progress))
        failure?.let { throw it }
        val book = Book(fileName, "Title", "en", BookFormat.EPUB, fileName)
        stored = stored + book
        return book
    }

    override fun delete(id: String) {
        deleteFailure?.let { throw it }
        stored = stored.filterNot { book -> book.id == id }
    }

    override fun open(id: String): ReadingSession? = null

    override fun position(bookId: String): ReadingPosition? = null

    override fun savePosition(position: ReadingPosition) = Unit
}
