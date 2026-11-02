package app.lekto.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The whole percentage the measured-row tests use; [FULL_PERCENT] is its denominator. */
private const val MEASURED_PERCENT = 28
private const val FULL_PERCENT = 100

/**
 * The Library row's second line on a **simulated Android runtime** (issues #196,
 * #200, #287): the same-named twin of `app/desktopTest`'s
 * `LibraryRowSemanticsTest`. The **Author** leads when a book names one, then the
 * Reading state; a book that names none reads the state alone. The four states
 * are proved with and without an author, and the format never reaches the row.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // One test per behaviour; the list is the line's.
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class LibraryRowSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val book = Book(
        id = "b1",
        title = "The Lantern Keeper",
        language = "en",
        format = BookFormat.EPUB,
        fileName = "lantern.epub",
    )

    private val authoredBook = Book(
        id = "b4",
        title = "Le Petit Prince",
        language = "fr",
        format = BookFormat.EPUB,
        fileName = "petit-prince.epub",
        author = "Antoine de Saint-Exupéry",
    )

    @Test
    fun aMeasuredReadingProgressShowsItsWholePercentageAndRule() {
        compose.setContent { Library(stateWith(BookProgress.Measured(MEASURED_PERCENT))) }

        compose.onNodeWithText("$MEASURED_PERCENT %").assertIsDisplayed()
        // The rule fills to the same whole percentage the line says (issue #196).
        // It is read from the unmerged tree: the row's clickable column merges its
        // children, and the rule is a child with no text of its own.
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true)
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(current = MEASURED_PERCENT / FULL_PERCENT.toFloat(), range = 0f..1f),
            )
        compose.onNodeWithText(book.fileName).assertDoesNotExist()
        // The format left the row: it names the file's provenance, not the book (issue #287).
        compose.onNodeWithText(BookFormat.EPUB.name).assertDoesNotExist()
    }

    @Test
    fun aBookWithNoReadingPositionSaysNotStartedWithoutARule() {
        compose.setContent { Library(books(book)) }

        compose.onNodeWithText("Not started").assertIsDisplayed()
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(book.fileName).assertDoesNotExist()
    }

    @Test
    fun aBookAwaitingMeasurementShowsTheUnknownStateWithoutARule() {
        compose.setContent { Library(stateWith(BookProgress.Unmeasured)) }

        compose.onNodeWithText("…")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reading progress is being measured.")
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aFinishedRowSaysFinishedAloneWhenTheBookNamesNoAuthor() {
        compose.setContent { Library(finishedState(book)) }

        // Exact, not a substring: a leading separator would pass a `contains` check (issue #287).
        compose.onNode(
            hasText("Finished") and hasAnyAncestor(hasTestTag(libraryRowTag(book.id))),
            useUnmergedTree = true,
        ).assertTextEquals("Finished")
        compose.onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anAuthoredRowNamesItsAuthorBeforeThePercentage() {
        compose.setContent { Library(authoredStateWith(BookProgress.Measured(MEASURED_PERCENT))) }

        compose.onNodeWithText("Antoine de Saint-Exupéry · $MEASURED_PERCENT %").assertIsDisplayed()
        compose.onNodeWithText(BookFormat.EPUB.name).assertDoesNotExist()
    }

    @Test
    fun anAuthoredUnreadRowNamesItsAuthorBeforeNotStarted() {
        compose.setContent { Library(books(authoredBook)) }

        compose.onNodeWithText("Antoine de Saint-Exupéry · Not started").assertIsDisplayed()
    }

    @Test
    fun anAuthoredUnmeasuredRowNamesItsAuthorBeforeTheEllipsis() {
        compose.setContent { Library(authoredStateWith(BookProgress.Unmeasured)) }

        compose.onNodeWithText("Antoine de Saint-Exupéry · …")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reading progress is being measured.")
    }

    @Test
    fun anAuthoredFinishedRowNamesItsAuthorBeforeFinished() {
        compose.setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(
                        others = listOf(authoredBook),
                        readingStateByBook = mapOf(authoredBook.id to ReadingState.FINISHED),
                    ),
                    selectedStates = ReadingState.entries.toSet(),
                ),
            )
        }

        compose.onNodeWithText("Antoine de Saint-Exupéry · Finished").assertIsDisplayed()
        compose.onNodeWithTag(libraryProgressTag(authoredBook.id), useUnmergedTree = true).assertDoesNotExist()
    }

    /** A state whose books are all unread, so only the title list shows. */
    private fun books(vararg books: Book): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = books.toList()))

    /** A state whose one book carries [progress], so a test can render each row state. */
    private fun stateWith(progress: BookProgress): LibraryUiState =
        LibraryUiState(listing = LibraryListing(others = listOf(book), progressByBook = mapOf(book.id to progress)))

    /** A state whose one authored book carries [progress]. */
    private fun authoredStateWith(progress: BookProgress): LibraryUiState = LibraryUiState(
        listing = LibraryListing(
            others = listOf(authoredBook),
            progressByBook = mapOf(authoredBook.id to progress),
        ),
    )

    /** A state whose one book is declared finished, with its own book so the chips stay on. */
    private fun finishedState(book: Book): LibraryUiState = LibraryUiState(
        listing = LibraryListing(
            others = listOf(book),
            readingStateByBook = mapOf(book.id to ReadingState.FINISHED),
        ),
        selectedStates = ReadingState.entries.toSet(),
    )

    @Composable
    private fun Library(state: LibraryUiState) {
        MaterialTheme {
            LibraryScreen(
                state = state,
                actions = LibraryActions(),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
