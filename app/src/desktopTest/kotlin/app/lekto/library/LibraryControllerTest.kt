package app.lekto.library

import app.lekto.LocalChanges
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ImportException
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.ImportStage
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingPosition
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
}

/** A book with [id] and [title], so a controller test has something to list and delete. */
private fun book(id: String, title: String = "Lantern"): Book =
    Book(id, title, "en", BookFormat.TXT, "${title.lowercase().replace(' ', '-')}.txt")

/** The progress the fake import reports mid-way, below the final 1.0. */
private const val REPORTED_PROGRESS = 0.4f

private class FakeLibrary(
    private val progress: Float = 0f,
    private val failure: Exception? = null,
    private val deleteFailure: Exception? = null,
) : BookLibrary {

    private var stored: List<Book> = emptyList()
    private val lastRead = mutableMapOf<String, Long>()
    private var reads = 0L

    override fun books(): List<Book> = stored

    override fun listing(): LibraryListing = LibraryListing.of(
        books = stored,
        lastReadByBook = lastRead.mapValues { (_, order) -> Instant.fromEpochMilliseconds(order) },
    )

    /** Test hook: pretend the book's reading position last moved after the others. */
    fun markRead(bookId: String) {
        lastRead[bookId] = ++reads
    }

    /** Test hook: pretend the vault now holds [book], as a restored import would. */
    fun recognize(book: Book) {
        stored = stored + book
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
