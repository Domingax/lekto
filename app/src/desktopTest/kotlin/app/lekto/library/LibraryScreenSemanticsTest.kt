package app.lekto.library

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.LibraryListing
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import app.lekto.ui.CONFIRM_DIALOG_CANCEL_TAG
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import kotlin.test.Test
import kotlin.test.assertTrue
/**
 * The library screen's behaviour through semantics (issue #15): the empty state,
 * the book list and its open tap, the inline import progress, the inline failure
 * with a way to dismiss it, and the delete action behind its confirmation (issue
 * #131).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One test per behaviour; the list is the screen's.
class LibraryScreenSemanticsTest {

    private val book = Book(
        id = "b1",
        title = "The Lantern Keeper",
        language = "en",
        format = BookFormat.EPUB,
        fileName = "lantern.epub",
    )

    private val otherBook = Book(
        id = "b2",
        title = "A Smaller Book",
        language = "en",
        format = BookFormat.TXT,
        fileName = "smaller.txt",
    )

    @Test
    fun theContinueReadingSectionLeadsTheTitleList() = runComposeUiTest {
        setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(lastRead = listOf(book), others = listOf(otherBook)),
                ),
            )
        }

        onNodeWithText("Continue reading").assertIsDisplayed()
        val lastRead = onNodeWithText(book.title).getBoundsInRoot()
        val other = onNodeWithText(otherBook.title).getBoundsInRoot()
        assertTrue(lastRead.top < other.top, "the recently read book must lead: $lastRead vs $other")
    }

    @Test
    fun theContinueReadingHeadingIsAbsentUntilABookIsRead() = runComposeUiTest {
        setContent { Library(books(book)) }

        onNodeWithText("Continue reading").assertDoesNotExist()
        onNodeWithText(book.title).assertIsDisplayed()
    }

    @Test
    fun showsTheEmptyStateUntilABookExists() = runComposeUiTest {
        setContent { Library(LibraryUiState()) }

        onNodeWithText("No books yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun listsBooksAndOpensTheTappedOne() = runComposeUiTest {
        var opened: Book? = null
        setContent { Library(books(book), onOpen = { opened = it }) }

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
        setContent { Library(books(book), onDelete = { deleted = it }) }

        onNodeWithTag(libraryDeleteTag(book.id)).performClick()

        onNodeWithText("Delete « The Lantern Keeper »?").assertIsDisplayed()
        onNodeWithText("This cannot be undone.").assertIsDisplayed()
        kotlin.test.assertNull(deleted, "the vault must be untouched until the user confirms")
    }

    @Test
    fun confirmingTheDialogDeletesTheBook() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(books(book), onDelete = { deleted = it }) }

        onNodeWithTag(libraryDeleteTag(book.id)).performClick()
        onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        kotlin.test.assertEquals(book, deleted)
    }

    @Test
    fun cancellingTheDialogLeavesTheBook() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(books(book), onDelete = { deleted = it }) }

        onNodeWithTag(libraryDeleteTag(book.id)).performClick()
        onNodeWithTag(CONFIRM_DIALOG_CANCEL_TAG).performClick()

        onNodeWithText("Delete « The Lantern Keeper »?").assertDoesNotExist()
        kotlin.test.assertNull(deleted)
    }

    @Test
    fun theSearchFieldIsHiddenUntilTheVaultHoldsABook() = runComposeUiTest {
        setContent { Library(LibraryUiState()) }

        onNodeWithTag(LIBRARY_SEARCH_TAG).assertDoesNotExist()
    }

    @Test
    fun typingInTheFieldRaisesTheQuery() = runComposeUiTest {
        var query: String? = null
        setContent { Library(books(book), onSearch = { typed -> query = typed }) }

        // The label names the field for a screen reader, and the field waits to be
        // asked rather than taking focus on arrival.
        onNodeWithText("Search").assertIsDisplayed()
        onNodeWithTag(LIBRARY_SEARCH_TAG).assertIsNotFocused()

        onNodeWithTag(LIBRARY_SEARCH_TAG).performTextInput("lant")

        kotlin.test.assertEquals("lant", query)
    }

    @Test
    fun aQueryShowsOnlyItsMatchesAndDropsTheContinueReadingHeading() = runComposeUiTest {
        setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(lastRead = listOf(book), others = listOf(otherBook)),
                    query = "smaller",
                ),
            )
        }

        onNodeWithText(otherBook.title).assertIsDisplayed()
        onNodeWithText(book.title).assertDoesNotExist()
        onNodeWithText("Continue reading").assertDoesNotExist()
    }

    @Test
    fun noMatchNamesTheQueryBackAndOffersToClearIt() = runComposeUiTest {
        var cleared = false
        setContent {
            Library(
                LibraryUiState(listing = LibraryListing(others = listOf(book)), query = "zzz"),
                onClearSearch = { cleared = true },
            )
        }

        onNodeWithText("No books match \"zzz\".").assertIsDisplayed()
        onNodeWithText("Clear search").performClick()

        kotlin.test.assertTrue(cleared)
    }

    @Test
    fun theFieldClearActionEmptiesTheQuery() = runComposeUiTest {
        var cleared = false
        setContent {
            Library(
                LibraryUiState(listing = LibraryListing(others = listOf(book)), query = "lan"),
                onClearSearch = { cleared = true },
            )
        }

        onNodeWithTag(LIBRARY_SEARCH_CLEAR_TAG).performClick()

        kotlin.test.assertTrue(cleared)
    }

    @Test
    fun theHeaderKeepsItsNavigationReadableInFrench() = runComposeUiTest {
        // The French title is wider than the English one, so on a phone the
        // navigation cannot share its line: it must wrap below, not be squeezed
        // into a one-character column (ADR-0034).
        setContent {
            CompositionLocalProvider(LocalAppTexts provides appTextsFor("fr")) {
                Library(LibraryUiState())
            }
        }

        onNodeWithText("Bibliothèque").assertIsDisplayed()
        onNodeWithText("Langues").assertIsDisplayed()
        onNodeWithText("Vocabulaire").assertIsDisplayed()
        val settings = onNodeWithText("Réglages").assertIsDisplayed().getBoundsInRoot()
        val height = settings.bottom - settings.top
        assertTrue(height < 80.dp, "the nav label must stay on one line: $height")
    }

    /** A state whose books are all unread, so only the title list shows. */
    private fun books(vararg books: Book): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = books.toList()))

    @Composable
    @Suppress("LongParameterList") // The screen's actions, one per behaviour the tests drive.
    private fun Library(
        state: LibraryUiState,
        onOpen: (Book) -> Unit = {},
        onDelete: (Book) -> Unit = {},
        onDismissError: () -> Unit = {},
        onSearch: (String) -> Unit = {},
        onClearSearch: () -> Unit = {},
    ) {
        MaterialTheme {
            LibraryScreen(
                state = state,
                actions = app.lekto.library.LibraryActions(
                    onOpen = onOpen,
                    onDelete = onDelete,
                    onDismissError = onDismissError,
                    onSearch = onSearch,
                    onClearSearch = onClearSearch,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
