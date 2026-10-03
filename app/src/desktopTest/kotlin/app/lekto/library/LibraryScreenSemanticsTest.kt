package app.lekto.library

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import kotlin.test.Test
/**
 * The library screen's behaviour through semantics (issue #15): the empty state,
 * the book list and its open tap, the inline import progress, and the inline
 * failure with a way to dismiss it.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryScreenSemanticsTest {

    private val book = Book(
        id = "b1",
        title = "The Lantern Keeper",
        language = "en",
        format = BookFormat.EPUB,
        fileName = "lantern.epub",
    )

    @Test
    fun showsTheEmptyStateUntilABookExists() = runComposeUiTest {
        setContent { Library(LibraryUiState()) }

        onNodeWithText("No books yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun listsBooksAndOpensTheTappedOne() = runComposeUiTest {
        var opened: Book? = null
        setContent { Library(LibraryUiState(books = listOf(book)), onOpen = { opened = it }) }

        onNodeWithText("The Lantern Keeper").assertIsDisplayed()
        onNodeWithText("The Lantern Keeper").performClick()

        kotlin.test.assertEquals(book, opened)
    }

    @Test
    fun showsProgressWhileImporting() = runComposeUiTest {
        setContent { Library(LibraryUiState(importing = true, progress = 0.4f)) }

        onNodeWithText("Importing", substring = true).assertIsDisplayed()
    }

    @Test
    fun reportsAFailureAndDismissesIt() = runComposeUiTest {
        var dismissed = false
        setContent {
            Library(
                LibraryUiState(error = "Could not read 'broken.epub'."),
                onDismissError = { dismissed = true },
            )
        }

        onNodeWithText("Could not read 'broken.epub'.", substring = true).assertIsDisplayed()
        onNodeWithText("Dismiss").performClick()

        kotlin.test.assertTrue(dismissed)
    }

    @Test
    fun theImportButtonIsDisabledWhileImporting() = runComposeUiTest {
        setContent { Library(LibraryUiState(importing = true)) }

        onNodeWithText("Import").assertIsNotEnabled()
    }

    @Composable
    private fun Library(state: LibraryUiState, onOpen: (Book) -> Unit = {}, onDismissError: () -> Unit = {}) {
        MaterialTheme {
            LibraryScreen(
                state = state,
                actions = app.lekto.library.LibraryActions(
                    onOpen = onOpen,
                    onDismissError = onDismissError,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
