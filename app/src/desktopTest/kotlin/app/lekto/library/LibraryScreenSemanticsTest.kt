package app.lekto.library

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.LibraryListing
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import app.lekto.ui.CONFIRM_DIALOG_CANCEL_TAG
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import kotlin.test.Test
import kotlin.test.assertTrue

/** The whole percentage the measured-row test uses; [FULL_PERCENT] is its denominator. */
private const val MEASURED_PERCENT = 28
private const val FULL_PERCENT = 100

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
        onNodeWithText("Other books").assertDoesNotExist()
        onNodeWithText(book.title).assertIsDisplayed()
    }

    @Test
    fun theUnreadBooksAreSetApartFromTheRecentlyReadOnes() = runComposeUiTest {
        // Two named groups, so the shelf says where "read recently" ends instead
        // of leaving one heading floating over every book (issue #208 review).
        setContent {
            Library(LibraryUiState(listing = LibraryListing(lastRead = listOf(book), others = listOf(otherBook))))
        }

        val recent = onNodeWithText(book.title).getBoundsInRoot()
        val heading = onNodeWithText("Other books").assertIsDisplayed().getBoundsInRoot()
        val other = onNodeWithText(otherBook.title).getBoundsInRoot()

        assertTrue(recent.bottom < heading.top, "the heading parts the two groups: $recent vs $heading")
        assertTrue(heading.bottom < other.top, "the second group follows its heading: $heading vs $other")
    }

    @Test
    fun showsTheEmptyStateUntilABookExists() = runComposeUiTest {
        setContent { Library(LibraryUiState()) }

        onNodeWithText("No books yet").assertIsDisplayed()
        onNodeWithText("An EPUB or a text file", substring = true).assertIsDisplayed()
        onNodeWithTag(LIBRARY_SKELETONS_TAG).assertDoesNotExist()
    }

    @Test
    fun theEmptyStateCarriesTheImportAction() = runComposeUiTest {
        // The filled button is the empty state's, and the header keeps its word
        // to itself so the action is not offered twice (issue #208).
        var imported = false
        setContent { Library(LibraryUiState(), onImport = { imported = true }) }

        onNodeWithTag(LIBRARY_EMPTY_IMPORT_TAG).assertIsDisplayed().performClick()

        assertTrue(imported, "the empty state's one action imports")
    }

    @Test
    fun aShelfBeingReadShowsSkeletonRowsAndNotTheEmptyMessage() = runComposeUiTest {
        setContent { Library(LibraryUiState(loading = true)) }

        onNodeWithTag(LIBRARY_SKELETONS_TAG)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Loading your books")
        onNodeWithText("No books yet").assertDoesNotExist()
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
        // A first import is not an empty shelf, and not a list being read: its
        // progress is the state, so neither the empty words nor a second action
        // stands under it (issue #208).
        onNodeWithText("No books yet").assertDoesNotExist()
        onNodeWithTag(LIBRARY_EMPTY_IMPORT_TAG).assertDoesNotExist()
        onNodeWithTag(LIBRARY_SKELETONS_TAG).assertDoesNotExist()
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
    fun theHeaderImportIsDisabledWhileImporting() = runComposeUiTest {
        setContent {
            Library(LibraryUiState(listing = LibraryListing(others = listOf(book)), importing = true))
        }

        onNodeWithText("Import").assertIsNotEnabled()
    }

    @Test
    fun swipingARowAsksForConfirmationBeforeDeleting() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(books(book), onDelete = { deleted = it }) }

        // The touch idiom: a swipe uncovers the action, and the gesture only ever
        // asks — nothing is deleted until the dialog is confirmed (issue #208).
        onNodeWithTag(libraryRowTag(book.id)).performTouchInput { swipeLeft() }

        onNodeWithText("Delete « The Lantern Keeper »?").assertIsDisplayed()
        // The file name left the row (issue #196), so the confirmation is where it
        // still identifies the imported file.
        onNodeWithText("lantern.epub", substring = true).assertIsDisplayed()
        onNodeWithText("This cannot be undone.", substring = true).assertIsDisplayed()
        kotlin.test.assertNull(deleted, "the vault must be untouched until the user confirms")
    }

    @Test
    fun theDeleteActionAppearsWhenTheRowIsHovered() = runComposeUiTest {
        // The mouse idiom on desktop: the trash is not on the shelf until the
        // reader points at the row, and the confirmation guards it all the same.
        var deleted: Book? = null
        setContent { Library(books(book), onDelete = { deleted = it }) }

        onNodeWithTag(libraryDeleteTag(book.id)).assertDoesNotExist()
        onNodeWithTag(libraryRowTag(book.id)).performMouseInput { moveTo(center) }
        onNodeWithTag(libraryDeleteTag(book.id)).assertIsDisplayed().performClick()

        onNodeWithText("Delete « The Lantern Keeper »?").assertIsDisplayed()
        kotlin.test.assertNull(deleted)
    }

    @Test
    fun confirmingTheDialogDeletesTheBook() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(books(book), onDelete = { deleted = it }) }

        onNodeWithTag(libraryRowTag(book.id)).performTouchInput { swipeLeft() }
        onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        kotlin.test.assertEquals(book, deleted)
    }

    @Test
    fun cancellingTheDialogLeavesTheBook() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(books(book), onDelete = { deleted = it }) }

        onNodeWithTag(libraryRowTag(book.id)).performTouchInput { swipeLeft() }
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
    fun theImportActionSitsBesideTheTitleAndTheSectionsDoNot() = runComposeUiTest {
        // Import is the Library's action and never a section (issue #185): once
        // there is a shelf it is a word beside the title, and the way to another
        // section is the shell's bar, not this header.
        var imported = false
        setContent { Library(books(book), onImport = { imported = true }) }

        onNodeWithText("Import").assertIsDisplayed().performClick()

        assertTrue(imported, "the word beside the title imports")
        onNodeWithText("Settings").assertDoesNotExist()
        onNodeWithText("Vocabulary").assertDoesNotExist()
    }

    @Test
    fun theHeaderKeepsItsTitleAndActionReadableInFrench() = runComposeUiTest {
        // The French title is wider than the English one, so on a phone the action
        // wraps below it rather than being squeezed into a one-character column
        // (ADR-0034).
        setContent {
            CompositionLocalProvider(LocalAppTexts provides appTextsFor("fr")) {
                Library(books(book))
            }
        }

        onNodeWithText("Bibliothèque").assertIsDisplayed()
        val importer = onNodeWithText("Importer").assertIsDisplayed().getBoundsInRoot()
        assertTrue(importer.bottom - importer.top < 80.dp, "the action must stay on one line: $importer")
        onNodeWithText("Langues").assertDoesNotExist()
        onNodeWithText("Réglages").assertDoesNotExist()
    }

    @Test
    fun aRowShowsItsLanguageVignetteAndItsTitle() = runComposeUiTest {
        // The vignette's hue is decoration; its two letters are the signal, and
        // there are no covers because a Book carries none (issue #208).
        val french = Book(
            id = "b3",
            title = "Le Petit Prince",
            language = "fr",
            format = BookFormat.EPUB,
            fileName = "petit-prince.epub",
        )
        setContent { Library(books(french)) }

        onNodeWithText("FR").assertIsDisplayed()
        onNodeWithText(french.title).assertIsDisplayed()
    }

    @Test
    fun aRowIsAtLeastTheMinimumTouchTargetTall() = runComposeUiTest {
        setContent { Library(books(book)) }

        val row = onNodeWithTag(libraryRowTag(book.id)).getBoundsInRoot()

        assertTrue(row.bottom - row.top >= 48.dp, "a row is never shorter than 48 dp: $row")
    }

    @Test
    fun aMeasuredReadingProgressShowsItsWholePercentageAndRule() = runComposeUiTest {
        setContent { Library(stateWith(BookProgress.Measured(MEASURED_PERCENT))) }

        onNodeWithText("EPUB · $MEASURED_PERCENT %").assertIsDisplayed()
        // The rule fills to the same whole percentage the line says (issue #196).
        // It is read from the unmerged tree: the row's clickable column merges its
        // children, and the rule is a child with no text of its own.
        onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true)
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(current = MEASURED_PERCENT / FULL_PERCENT.toFloat(), range = 0f..1f),
            )
        onNodeWithText(book.fileName).assertDoesNotExist()
    }

    @Test
    fun aBookWithNoReadingPositionSaysNotStartedWithoutARule() = runComposeUiTest {
        setContent { Library(books(book)) }

        onNodeWithText("EPUB · Not started").assertIsDisplayed()
        onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(book.fileName).assertDoesNotExist()
    }

    @Test
    fun aBookAwaitingMeasurementShowsTheUnknownStateWithoutARule() = runComposeUiTest {
        setContent { Library(stateWith(BookProgress.Unmeasured)) }

        onNodeWithText("EPUB · …")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reading progress is being measured.")
        onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    /** A state whose books are all unread, so only the title list shows. */
    private fun books(vararg books: Book): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = books.toList()))

    /** A state whose one book carries [progress], so a test can render each row state. */
    private fun stateWith(progress: BookProgress): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = listOf(book), progressByBook = mapOf(book.id to progress)))

    @Composable
    @Suppress("LongParameterList") // The screen's actions, one per behaviour the tests drive.
    private fun Library(
        state: LibraryUiState,
        onImport: () -> Unit = {},
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
                    onImport = onImport,
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
