package app.lekto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.lekto.core.MasteryLookup
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookLibrary
import app.lekto.core.book.ImportProgress
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.ReadingSession
import app.lekto.core.text.BlockKind
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test

/**
 * The application's reading-loop flow through semantics (issues #15 and #16): it
 * opens on the library, an import lands a book, tapping that book starts a
 * reading session over its parsed text, and leaving and reopening the book
 * resumes at the page the reader left.
 */
@OptIn(ExperimentalTestApi::class)
class AppSemanticsTest {

    @Test
    fun opensOnTheLibraryAndImportsABook() = runComposeUiTest {
        val library = InMemoryLibrary()
        setContent { App(environment(library, pickFile = { PickedFile("lantern.epub", byteArrayOf(1)) })) }

        onNodeWithText("No books yet", substring = true).assertIsDisplayed()
        onNodeWithText("Import").performClick()

        onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun openingABookStartsAReadingSession() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithText("The Lantern Keeper").performClick()

        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun reopeningABookResumesWhereTheReaderLeftOff() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent { App(environment(library)) }

        onNodeWithText("The Lantern Keeper").performClick()
        onNodeWithText("Next page").performClick()
        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
        // Persisting runs off the UI thread; wait for it before leaving the book.
        waitUntil { library.savedOffset() != null }

        onNodeWithText("Library").performClick()
        onNodeWithText("The Lantern Keeper").performClick()

        onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
    }

    private fun environment(library: BookLibrary, pickFile: (suspend () -> PickedFile?)? = null) = AppEnvironment(
        segmenter = WhitespaceTextSegmenter(),
        library = library,
        mastery = MasteryLookup.AllKnown,
        pickFile = pickFile,
    )
}

/** Enough paragraphs to span several pages at any test viewport. */
private const val PARAGRAPHS = 8

/** A [BookLibrary] held in memory: import, open and reading progress without a vault. */
private class InMemoryLibrary : BookLibrary {

    private var stored: List<Book> = emptyList()
    private val positions = mutableMapOf<String, Int>()

    override fun books(): List<Book> = stored

    override fun import(fileName: String, bytes: ByteArray, onProgress: (ImportProgress) -> Unit): Book {
        val book = Book(
            id = "b${stored.size + 1}",
            title = "The Lantern Keeper",
            language = "en",
            format = BookFormat.ofFileName(fileName) ?: BookFormat.TXT,
            fileName = fileName,
        )
        stored = stored + book
        return book
    }

    override fun open(id: String): ReadingSession? {
        val book = stored.firstOrNull { it.id == id } ?: return null
        return ReadingSession(
            book = book,
            text = longText(book),
            position = positions[book.id]?.let { offset -> ReadingPosition(book.id, offset) },
        )
    }

    override fun position(bookId: String): ReadingPosition? = positions[bookId]?.let { ReadingPosition(bookId, it) }

    override fun savePosition(position: ReadingPosition) {
        positions[position.bookId] = position.offset
    }

    /** The last offset written, so a test can wait for the asynchronous save. */
    fun savedOffset(): Int? = positions.values.firstOrNull()

    /** Enough paragraphs to span several pages at any test viewport. */
    private fun longText(book: Book): StructuredText = StructuredText(
        title = book.title,
        language = book.language,
        blocks = List(PARAGRAPHS) {
            TextBlock(
                BlockKind.PARAGRAPH,
                listOf(
                    TextRun(
                        "On the quiet evening when the harbour lights came on, the keeper lit the lantern " +
                            "and waited for the boats, and the sea beyond the headland was larger than any map.",
                    ),
                ),
            )
        },
    )
}
