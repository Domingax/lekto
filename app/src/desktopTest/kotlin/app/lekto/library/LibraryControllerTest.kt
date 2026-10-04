package app.lekto.library

import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ImportException
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.ImportStage
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The library controller: imports run off the main thread, progress reaches the
 * state, a failure lands as a message instead of an exception, and the app stays
 * usable afterwards (issue #15).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryControllerTest {

    @Test
    fun aBookImportedThroughTheControllerAppearsInTheList() = runTest {
        val library = FakeLibrary()
        val controller = LibraryController(library, StandardTestDispatcher(testScheduler), this)

        controller.import("book.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertEquals(library.books(), controller.state.value.books)
        assertEquals(false, controller.state.value.importing)
        assertNull(controller.state.value.error)
    }

    @Test
    fun aFinishedImportLeavesProgressAtOne() = runTest {
        val library = FakeLibrary()
        val controller = LibraryController(library, StandardTestDispatcher(testScheduler), this)

        controller.import("book.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertEquals(1f, controller.state.value.progress)
    }

    @Test
    fun aFailedImportIsCapturedAsAnErrorAndStopsImporting() = runTest {
        val library = FakeLibrary(failure = ImportException("Could not read 'broken.epub'."))
        val controller = LibraryController(library, StandardTestDispatcher(testScheduler), this)

        controller.import("broken.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertEquals(false, controller.state.value.importing)
        assertEquals("Could not read 'broken.epub'.", controller.state.value.error)
    }

    @Test
    fun dismissingAnErrorClearsItAndLeavesTheLibraryUsable() = runTest {
        val library = FakeLibrary(failure = ImportException("nope"))
        val controller = LibraryController(library, StandardTestDispatcher(testScheduler), this)
        controller.import("broken.epub", byteArrayOf(1))
        advanceUntilIdle()

        controller.dismissError()

        assertNull(controller.state.value.error)
    }

    @Test
    fun progressReportedByTheImportReachesTheState() = runTest {
        val library = FakeLibrary(progress = REPORTED_PROGRESS)
        val controller = LibraryController(library, StandardTestDispatcher(testScheduler), this)

        controller.import("book.epub", byteArrayOf(1))
        advanceUntilIdle()

        assertTrue(controller.state.value.progress >= REPORTED_PROGRESS)
    }

    @Test
    fun aRefreshPicksUpBooksStoredOutsideTheController() = runTest {
        val library = FakeLibrary()
        val controller = LibraryController(library, StandardTestDispatcher(testScheduler), this)

        // A vault import replaces the store under the controller; it only sees
        // the restored book after a refresh (issue #20).
        library.recognize(Book("b1", "Restored", "en", BookFormat.TXT, "restored.txt"))
        controller.refresh()
        advanceUntilIdle()

        assertEquals(listOf("b1"), controller.state.value.books.map { book -> book.id })
    }
}

/** The progress the fake import reports mid-way, below the final 1.0. */
private const val REPORTED_PROGRESS = 0.4f

private class FakeLibrary(private val progress: Float = 0f, private val failure: Exception? = null) : BookLibrary {

    private var stored: List<Book> = emptyList()

    override fun books(): List<Book> = stored

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

    override fun open(id: String): ReadingSession? = null

    override fun position(bookId: String): ReadingPosition? = null

    override fun savePosition(position: ReadingPosition) = Unit
}
