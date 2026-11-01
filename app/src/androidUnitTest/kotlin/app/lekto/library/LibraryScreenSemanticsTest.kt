package app.lekto.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingState
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

/** The whole percentage the measured-row test uses; [FULL_PERCENT] is its denominator. */
private const val MEASURED_PERCENT = 28
private const val FULL_PERCENT = 100

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
        compose.onNodeWithText("Other books").assertDoesNotExist()
        compose.onNodeWithText(book.title).assertIsDisplayed()
    }

    @Test
    fun theUnreadBooksAreSetApartFromTheRecentlyReadOnes() {
        // Two named groups, so the shelf says where "read recently" ends instead
        // of leaving one heading floating over every book (issue #208 review).
        compose.setContent {
            Library(LibraryUiState(listing = LibraryListing(lastRead = listOf(book), others = listOf(otherBook))))
        }

        val recent = compose.onNodeWithText(book.title).getBoundsInRoot()
        val heading = compose.onNodeWithText("Other books").assertIsDisplayed().getBoundsInRoot()
        val other = compose.onNodeWithText(otherBook.title).getBoundsInRoot()

        assertTrue("the heading parts the two groups: $recent vs $heading", recent.bottom < heading.top)
        assertTrue("the second group follows its heading: $heading vs $other", heading.bottom < other.top)
    }

    @Test
    fun showsTheEmptyStateUntilABookExists() {
        compose.setContent { Library(LibraryUiState()) }

        compose.onNodeWithText("No books yet").assertIsDisplayed()
        compose.onNodeWithText("An EPUB or a text file", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_SKELETONS_TAG).assertDoesNotExist()
    }

    @Test
    fun theEmptyStateCarriesTheImportAction() {
        // The filled button is the empty state's, and the header keeps its word
        // to itself so the action is not offered twice (issue #208).
        var imported = false
        compose.setContent { Library(LibraryUiState(), onImport = { imported = true }) }

        compose.onNodeWithTag(LIBRARY_EMPTY_IMPORT_TAG).assertIsDisplayed().performClick()

        assertTrue("the empty state's one action imports", imported)
    }

    @Test
    fun aShelfBeingReadShowsSkeletonRowsAndNotTheEmptyMessage() {
        compose.setContent { Library(LibraryUiState(loading = true)) }

        compose.onNodeWithTag(LIBRARY_SKELETONS_TAG)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Loading your books")
        compose.onNodeWithText("No books yet").assertDoesNotExist()
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
        // A first import is not an empty shelf, and not a list being read: its
        // progress is the state, so neither the empty words nor a second action
        // stands under it (issue #208).
        compose.onNodeWithText("No books yet").assertDoesNotExist()
        compose.onNodeWithTag(LIBRARY_EMPTY_IMPORT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(LIBRARY_SKELETONS_TAG).assertDoesNotExist()
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
    fun theHeaderImportIsDisabledWhileImporting() {
        compose.setContent {
            Library(LibraryUiState(listing = LibraryListing(others = listOf(book)), importing = true))
        }

        compose.onNodeWithText("Import").assertIsNotEnabled()
    }

    @Test
    fun theDeleteActionAsksForConfirmationBeforeDeleting() {
        var deleted: Book? = null
        compose.setContent { Library(books(book), onDelete = { deleted = it }) }

        // The touch idiom: a swipe uncovers the action, and the gesture only ever
        // asks — nothing is deleted until the dialog is confirmed (issue #208).
        compose.onNodeWithTag(libraryRowTag(book.id)).performTouchInput { swipeLeft() }

        compose.onNodeWithText("Delete « The Lantern Keeper »?").assertIsDisplayed()
        // The file name left the row (issue #196), so the confirmation is where it
        // still identifies the imported file.
        compose.onNodeWithText("lantern.epub", substring = true).assertIsDisplayed()
        compose.onNodeWithText("This cannot be undone.", substring = true).assertIsDisplayed()
        assertNull("the vault must be untouched until the user confirms", deleted)
    }

    @Test
    fun confirmingTheDialogDeletesTheBook() {
        var deleted: Book? = null
        compose.setContent { Library(books(book), onDelete = { deleted = it }) }

        compose.onNodeWithTag(libraryRowTag(book.id)).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        assertEquals(book, deleted)
    }

    @Test
    fun cancellingTheDialogLeavesTheBook() {
        var deleted: Book? = null
        compose.setContent { Library(books(book), onDelete = { deleted = it }) }

        compose.onNodeWithTag(libraryRowTag(book.id)).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(CONFIRM_DIALOG_CANCEL_TAG).performClick()

        compose.onNodeWithText("Delete « The Lantern Keeper »?").assertDoesNotExist()
        assertNull(deleted)
    }

    @Test
    fun theSearchFieldIsHiddenUntilTheVaultHoldsABook() {
        compose.setContent { Library(LibraryUiState()) }

        compose.onNodeWithTag(LIBRARY_SEARCH_TAG).assertDoesNotExist()
    }

    @Test
    fun typingInTheFieldRaisesTheQuery() {
        var query: String? = null
        compose.setContent { Library(books(book), onSearch = { typed -> query = typed }) }

        // The label names the field for a screen reader, and the field waits to be
        // asked rather than taking focus on arrival.
        compose.onNodeWithText("Search").assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_SEARCH_TAG).assertIsNotFocused()

        compose.onNodeWithTag(LIBRARY_SEARCH_TAG).performTextInput("lant")

        assertEquals("lant", query)
    }

    @Test
    fun aQueryShowsOnlyItsMatchesAndDropsTheContinueReadingHeading() {
        compose.setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(lastRead = listOf(book), others = listOf(otherBook)),
                    query = "smaller",
                ),
            )
        }

        compose.onNodeWithText(otherBook.title).assertIsDisplayed()
        compose.onNodeWithText(book.title).assertDoesNotExist()
        compose.onNodeWithText("Continue reading").assertDoesNotExist()
    }

    @Test
    fun noMatchNamesTheQueryBackAndOffersToClearIt() {
        var cleared = false
        compose.setContent {
            Library(
                LibraryUiState(listing = LibraryListing(others = listOf(book)), query = "zzz"),
                onClearSearch = { cleared = true },
            )
        }

        compose.onNodeWithText("No books match \"zzz\".").assertIsDisplayed()
        compose.onNodeWithText("Clear search").performClick()

        assertTrue(cleared)
    }

    @Test
    fun theImportActionSitsBesideTheTitleAndTheSectionsDoNot() {
        // Import is the Library's action and never a section (issue #185): once
        // there is a shelf it is a word beside the title, and the way to another
        // section is the shell's bar, not this header.
        var imported = false
        compose.setContent { Library(books(book), onImport = { imported = true }) }

        compose.onNodeWithText("Import").assertIsDisplayed().performClick()

        assertTrue("the word beside the title imports", imported)
        compose.onNodeWithText("Settings").assertDoesNotExist()
        compose.onNodeWithText("Vocabulary").assertDoesNotExist()
    }

    @Test
    fun theHeaderKeepsItsTitleAndActionReadableInFrench() {
        // The French title is wider than the English one, so on a phone the action
        // wraps below it rather than being squeezed into a one-character column
        // (ADR-0034).
        compose.setContent {
            CompositionLocalProvider(LocalAppTexts provides appTextsFor("fr")) {
                Library(books(book))
            }
        }

        compose.onNodeWithText("Bibliothèque").assertIsDisplayed()
        val importer = compose.onNodeWithText("Importer").assertIsDisplayed().getBoundsInRoot()
        assertTrue("the action must stay on one line: $importer", importer.bottom - importer.top < 80.dp)
        compose.onNodeWithText("Langues").assertDoesNotExist()
        compose.onNodeWithText("Réglages").assertDoesNotExist()
    }

    @Test
    fun aRowShowsItsLanguageVignetteAndItsTitle() {
        // The vignette's hue is decoration; its two letters are the signal, and
        // there are no covers because a Book carries none (issue #208).
        val french = Book(
            id = "b3",
            title = "Le Petit Prince",
            language = "fr",
            format = BookFormat.EPUB,
            fileName = "petit-prince.epub",
        )
        compose.setContent { Library(books(french)) }

        compose.onNodeWithText("FR").assertIsDisplayed()
        compose.onNodeWithText(french.title).assertIsDisplayed()
    }

    @Test
    fun aRowIsAtLeastTheMinimumTouchTargetTall() {
        compose.setContent { Library(books(book)) }

        val row = compose.onNodeWithTag(libraryRowTag(book.id)).getBoundsInRoot()

        assertTrue("a row is never shorter than 48 dp: $row", row.bottom - row.top >= 48.dp)
    }

    @Test
    fun aMeasuredReadingProgressShowsItsWholePercentageAndRule() {
        compose.setContent { Library(stateWith(BookProgress.Measured(MEASURED_PERCENT))) }

        compose.onNodeWithText("EPUB · $MEASURED_PERCENT %").assertIsDisplayed()
        // The rule fills to the same whole percentage the line says (issue #196).
        // It is read from the unmerged tree: the row's clickable column merges its
        // children, and the rule is a child with no text of its own.
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true)
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(current = MEASURED_PERCENT / FULL_PERCENT.toFloat(), range = 0f..1f),
            )
        compose.onNodeWithText(book.fileName).assertDoesNotExist()
    }

    @Test
    fun aBookWithNoReadingPositionSaysNotStartedWithoutARule() {
        compose.setContent { Library(books(book)) }

        compose.onNodeWithText("EPUB · Not started").assertIsDisplayed()
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(book.fileName).assertDoesNotExist()
    }

    @Test
    fun aBookAwaitingMeasurementShowsTheUnknownStateWithoutARule() {
        compose.setContent { Library(stateWith(BookProgress.Unmeasured)) }

        compose.onNodeWithText("EPUB · …")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reading progress is being measured.")
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    /** A state whose books are all unread, so only the title list shows. */
    private fun books(vararg books: Book): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = books.toList()))

    /** A state whose one book carries [progress], so a test can render each row state. */
    private fun stateWith(progress: BookProgress): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = listOf(book), progressByBook = mapOf(book.id to progress)))

    @Test
    fun theReadingStateChipsOpenOnUnreadAndInProgress() {
        compose.setContent { Library(books(book)) }

        compose.onNodeWithTag(libraryStateTag(ReadingState.UNREAD)).assertIsOn()
        compose.onNodeWithTag(libraryStateTag(ReadingState.IN_PROGRESS)).assertIsOn()
        compose.onNodeWithTag(libraryStateTag(ReadingState.FINISHED)).assertIsOff()
    }

    @Test
    fun tappingAChipRaisesTheToggle() {
        var toggled: ReadingState? = null
        compose.setContent { Library(books(book), onToggleState = { state -> toggled = state }) }

        compose.onNodeWithTag(libraryStateTag(ReadingState.FINISHED)).performClick()

        assertEquals(ReadingState.FINISHED, toggled)
    }

    @Test
    fun aFinishedRowIsHiddenUntilTheFinishedChipIsSelected() {
        val state = LibraryUiState(
            listing = LibraryListing(
                others = listOf(book, otherBook),
                readingStateByBook = mapOf(
                    book.id to ReadingState.FINISHED,
                    otherBook.id to ReadingState.UNREAD,
                ),
            ),
        )
        compose.setContent { Library(state) }

        compose.onNodeWithText(book.title).assertDoesNotExist()
        compose.onNodeWithText(otherBook.title).assertIsDisplayed()
    }

    @Test
    fun aFinishedRowSaysFinishedWithACheckAndNoProgressRule() {
        compose.setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(
                        others = listOf(book),
                        progressByBook = mapOf(book.id to BookProgress.Measured(MEASURED_PERCENT)),
                        readingStateByBook = mapOf(book.id to ReadingState.FINISHED),
                    ),
                    selectedStates = ReadingState.entries.toSet(),
                ),
            )
        }

        compose.onNodeWithText("EPUB · Finished").assertIsDisplayed()
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("EPUB · $MEASURED_PERCENT %").assertDoesNotExist()
    }

    @Test
    fun aShelfWhoseBooksAreAllFinishedSaysSoAndOffersShowAll() {
        var shownAll = false
        compose.setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(
                        others = listOf(book),
                        readingStateByBook = mapOf(book.id to ReadingState.FINISHED),
                    ),
                ),
                onShowAllBooks = { shownAll = true },
            )
        }

        compose.onNodeWithText("All your books are finished.").assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_SHOW_ALL_TAG).assertIsDisplayed().performClick()

        assertTrue(shownAll)
    }

    @Test
    fun aFilterThatMatchesNothingSaysSoAndOffersShowAll() {
        compose.setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(
                        others = listOf(book),
                        readingStateByBook = mapOf(book.id to ReadingState.UNREAD),
                    ),
                    selectedStates = setOf(ReadingState.FINISHED),
                ),
            )
        }

        compose.onNodeWithText("No books in this state.").assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_SHOW_ALL_TAG).assertIsDisplayed()
    }

    @Test
    fun aSearchWithNoMatchOffersClearSearchAndShowAll() {
        compose.setContent {
            Library(LibraryUiState(listing = LibraryListing(others = listOf(book)), query = "zzz"))
        }

        compose.onNodeWithText("No books match \"zzz\".").assertIsDisplayed()
        compose.onNodeWithText("Clear search").assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_SHOW_ALL_TAG).assertIsDisplayed()
    }

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
        onToggleState: (ReadingState) -> Unit = {},
        onShowAllBooks: () -> Unit = {},
    ) {
        MaterialTheme {
            LibraryScreen(
                state = state,
                actions = LibraryActions(
                    onImport = onImport,
                    onOpen = onOpen,
                    onDelete = onDelete,
                    onDismissError = onDismissError,
                    onSearch = onSearch,
                    onClearSearch = onClearSearch,
                    onToggleState = onToggleState,
                    onShowAllBooks = onShowAllBooks,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
