package app.lekto.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.LibraryListing
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import app.lekto.ui.CONFIRM_DIALOG_CANCEL_TAG
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The library screen on a **simulated Android runtime** (issue #74): the
 * same-named twin of `app/desktopTest`'s `LibraryScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The desktop lane proves
 * the empty state, the book list and its open tap, the inline import progress,
 * and the inline failure with a dismiss; this renders the same `LibraryScreen`
 * on the runtime Android uses, which the desktop lane cannot stand in for, with
 * the same-named tests so the two suites cannot drift apart unseen.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // One test per behaviour; the list is the screen's.
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class LibraryScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
    fun theContinueReadingSectionLeadsTheTitleList() {
        compose.setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(lastRead = listOf(book), others = listOf(otherBook)),
                ),
            )
        }

        compose.onNodeWithText("Continue reading").assertIsDisplayed()
        val lastRead = compose.onNodeWithText(book.title).getBoundsInRoot()
        val other = compose.onNodeWithText(otherBook.title).getBoundsInRoot()
        assertTrue("the recently read book must lead: $lastRead vs $other", lastRead.top < other.top)
    }

    @Test
    fun theContinueReadingHeadingIsAbsentUntilABookIsRead() {
        compose.setContent { Library(books(book)) }

        compose.onNodeWithText("Continue reading").assertDoesNotExist()
        compose.onNodeWithText(book.title).assertIsDisplayed()
    }

    @Test
    fun showsTheEmptyStateUntilABookExists() {
        compose.setContent { Library(LibraryUiState()) }

        compose.onNodeWithText("No books yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun listsBooksAndOpensTheTappedOne() {
        var opened: Book? = null
        compose.setContent { Library(books(book), onOpen = { opened = it }) }

        compose.onNodeWithText("The Lantern Keeper").assertIsDisplayed()
        compose.onNodeWithText("The Lantern Keeper").performClick()

        assertEquals(book, opened)
    }

    @Test
    fun showsProgressWhileImporting() {
        compose.setContent { Library(LibraryUiState(importing = true, progress = 0.4f)) }

        compose.onNodeWithText("Importing", substring = true).assertIsDisplayed()
    }

    @Test
    fun reportsAFailureAndDismissesIt() {
        var dismissed = false
        compose.setContent {
            Library(
                LibraryUiState(error = AppMessage.core("Could not read 'broken.epub'.")),
                onDismissError = { dismissed = true },
            )
        }

        compose.onNodeWithText("Could not read 'broken.epub'.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun theImportButtonIsDisabledWhileImporting() {
        compose.setContent { Library(LibraryUiState(importing = true)) }

        compose.onNodeWithText("Import").assertIsNotEnabled()
    }

    @Test
    fun theDeleteActionAsksForConfirmationBeforeDeleting() {
        var deleted: Book? = null
        compose.setContent { Library(books(book), onDelete = { deleted = it }) }

        compose.onNodeWithTag(libraryDeleteTag(book.id)).performClick()

        compose.onNodeWithText("Delete « The Lantern Keeper »?").assertIsDisplayed()
        compose.onNodeWithText("This cannot be undone.").assertIsDisplayed()
        assertNull("the vault must be untouched until the user confirms", deleted)
    }

    @Test
    fun confirmingTheDialogDeletesTheBook() {
        var deleted: Book? = null
        compose.setContent { Library(books(book), onDelete = { deleted = it }) }

        compose.onNodeWithTag(libraryDeleteTag(book.id)).performClick()
        compose.onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        assertEquals(book, deleted)
    }

    @Test
    fun cancellingTheDialogLeavesTheBook() {
        var deleted: Book? = null
        compose.setContent { Library(books(book), onDelete = { deleted = it }) }

        compose.onNodeWithTag(libraryDeleteTag(book.id)).performClick()
        compose.onNodeWithTag(CONFIRM_DIALOG_CANCEL_TAG).performClick()

        compose.onNodeWithText("Delete « The Lantern Keeper »?").assertDoesNotExist()
        assertNull(deleted)
    }

    @Test
    fun theHeaderKeepsItsNavigationReadableInFrench() {
        // The French title is wider than the English one, so on a phone the
        // navigation cannot share its line: it must wrap below, not be squeezed
        // into a one-character column (ADR-0034).
        compose.setContent {
            CompositionLocalProvider(LocalAppTexts provides appTextsFor("fr")) {
                Library(LibraryUiState())
            }
        }

        compose.onNodeWithText("Bibliothèque").assertIsDisplayed()
        compose.onNodeWithText("Langues").assertIsDisplayed()
        compose.onNodeWithText("Vocabulaire").assertIsDisplayed()
        val settings = compose.onNodeWithText("Réglages").assertIsDisplayed().getBoundsInRoot()
        val height = settings.bottom - settings.top
        assertTrue("the nav label must stay on one line: $height", height < 80.dp)
    }

    /** A state whose books are all unread, so only the title list shows. */
    private fun books(vararg books: Book): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = books.toList()))

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
                actions = LibraryActions(
                    onOpen = onOpen,
                    onDelete = onDelete,
                    onDismissError = onDismissError,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
