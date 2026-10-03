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
import app.lekto.core.book.ReadingSession
import app.lekto.core.text.BlockKind
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test

/**
 * The application's reading-loop flow through semantics (issue #15): it opens on
 * the library, an import lands a book, and tapping that book starts a reading
 * session over its parsed text.
 */
@OptIn(ExperimentalTestApi::class)
class AppSemanticsTest {

    @Test
    fun opensOnTheLibraryAndImportsABook() = runComposeUiTest {
        val library = InMemoryLibrary()
        setContent {
            App(
                AppEnvironment(
                    segmenter = WhitespaceTextSegmenter(),
                    library = library,
                    mastery = MasteryLookup.AllKnown,
                    pickFile = { PickedFile("lantern.epub", byteArrayOf(1)) },
                ),
            )
        }

        onNodeWithText("No books yet", substring = true).assertIsDisplayed()
        onNodeWithText("Import").performClick()

        onNodeWithText("The Lantern Keeper").assertIsDisplayed()
    }

    @Test
    fun openingABookStartsAReadingSession() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent {
            App(
                AppEnvironment(
                    segmenter = WhitespaceTextSegmenter(),
                    library = library,
                    mastery = MasteryLookup.AllKnown,
                ),
            )
        }

        onNodeWithText("The Lantern Keeper").performClick()

        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Library").assertIsDisplayed()
    }
}

/** A [BookLibrary] held in memory: import and open behave without a vault. */
private class InMemoryLibrary : BookLibrary {

    private var stored: List<Book> = emptyList()

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
            text = StructuredText(
                title = book.title,
                language = book.language,
                blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("On the quiet evening.")))),
            ),
        )
    }
}
