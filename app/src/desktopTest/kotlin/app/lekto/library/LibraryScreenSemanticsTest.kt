package app.lekto.library

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.text.AppMessage
import app.lekto.ui.CONFIRM_DIALOG_CANCEL_TAG
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import kotlin.test.Test
/**
 * The library screen's behaviour through semantics (issue #15): the empty state,
 * the book list and its open tap, the inline import progress, the inline failure
 * with a way to dismiss it, and the delete action behind its confirmation (issue
 * #131).
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
                LibraryUiState(error = AppMessage.core("Could not read 'broken.epub'.")),
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

    @Test
    fun theDeleteActionAsksForConfirmationBeforeDeleting() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(LibraryUiState(books = listOf(book)), onDelete = { deleted = it }) }

        onNodeWithTag(libraryDeleteTag(book.id)).performClick()

        onNodeWithText("Delete « The Lantern Keeper »?").assertIsDisplayed()
        onNodeWithText("This cannot be undone.").assertIsDisplayed()
        kotlin.test.assertNull(deleted, "the vault must be untouched until the user confirms")
    }

    @Test
    fun confirmingTheDialogDeletesTheBook() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(LibraryUiState(books = listOf(book)), onDelete = { deleted = it }) }

        onNodeWithTag(libraryDeleteTag(book.id)).performClick()
        onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        kotlin.test.assertEquals(book, deleted)
    }

    @Test
    fun cancellingTheDialogLeavesTheBook() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(LibraryUiState(books = listOf(book)), onDelete = { deleted = it }) }

        onNodeWithTag(libraryDeleteTag(book.id)).performClick()
        onNodeWithTag(CONFIRM_DIALOG_CANCEL_TAG).performClick()

        onNodeWithText("Delete « The Lantern Keeper »?").assertDoesNotExist()
        kotlin.test.assertNull(deleted)
    }

    @Composable
    private fun Library(
        state: LibraryUiState,
        onOpen: (Book) -> Unit = {},
        onDelete: (Book) -> Unit = {},
        onDismissError: () -> Unit = {},
    ) {
        MaterialTheme {
            LibraryScreen(
                state = state,
                actions = app.lekto.library.LibraryActions(
                    onOpen = onOpen,
                    onDelete = onDelete,
                    onDismissError = onDismissError,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
