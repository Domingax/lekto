package app.lekto.library

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingState
import kotlin.test.Test

/** The whole percentage the measured-row tests use; [FULL_PERCENT] is its denominator. */
private const val MEASURED_PERCENT = 28
private const val FULL_PERCENT = 100

/**
 * The Library row's second line (issues #196, #200, #287): the **Author** when the
 * book names one, then the Reading state, and the state alone when it names none.
 * The four states are proved with and without an author, and the format never
 * reaches the row.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One test per behaviour; the list is the line's.
class LibraryRowSemanticsTest {

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
    fun aMeasuredReadingProgressShowsItsWholePercentageAndRule() = runComposeUiTest {
        setContent { Library(stateWith(BookProgress.Measured(MEASURED_PERCENT))) }

        onNodeWithText("$MEASURED_PERCENT %").assertIsDisplayed()
        // The rule fills to the same whole percentage the line says (issue #196).
        // It is read from the unmerged tree: the row's clickable column merges its
        // children, and the rule is a child with no text of its own.
        onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true)
            .assertRangeInfoEquals(
                ProgressBarRangeInfo(current = MEASURED_PERCENT / FULL_PERCENT.toFloat(), range = 0f..1f),
            )
        onNodeWithText(book.fileName).assertDoesNotExist()
        // The format left the row: it names the file's provenance, not the book (issue #287).
        onNodeWithText(BookFormat.EPUB.name).assertDoesNotExist()
    }

    @Test
    fun aBookWithNoReadingPositionSaysNotStartedWithoutARule() = runComposeUiTest {
        setContent { Library(books(book)) }

        onNodeWithText("Not started").assertIsDisplayed()
        onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(book.fileName).assertDoesNotExist()
    }

    @Test
    fun aBookAwaitingMeasurementShowsTheUnknownStateWithoutARule() = runComposeUiTest {
        setContent { Library(stateWith(BookProgress.Unmeasured)) }

        onNodeWithText("…")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reading progress is being measured.")
        onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aFinishedRowSaysFinishedAloneWhenTheBookNamesNoAuthor() = runComposeUiTest {
        setContent { Library(finishedState(book)) }

        // Exact, not a substring: a leading separator would pass a `contains` check (issue #287).
        onNode(
            hasText("Finished") and hasAnyAncestor(hasTestTag(libraryRowTag(book.id))),
            useUnmergedTree = true,
        ).assertTextEquals("Finished")
        onNodeWithTag(libraryProgressTag(book.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun anAuthoredRowNamesItsAuthorBeforeThePercentage() = runComposeUiTest {
        setContent { Library(authoredStateWith(BookProgress.Measured(MEASURED_PERCENT))) }

        onNodeWithText("Antoine de Saint-Exupéry · $MEASURED_PERCENT %").assertIsDisplayed()
        onNodeWithText(BookFormat.EPUB.name).assertDoesNotExist()
    }

    @Test
    fun anAuthoredUnreadRowNamesItsAuthorBeforeNotStarted() = runComposeUiTest {
        setContent { Library(books(authoredBook)) }

        onNodeWithText("Antoine de Saint-Exupéry · Not started").assertIsDisplayed()
    }

    @Test
    fun anAuthoredUnmeasuredRowNamesItsAuthorBeforeTheEllipsis() = runComposeUiTest {
        setContent { Library(authoredStateWith(BookProgress.Unmeasured)) }

        onNodeWithText("Antoine de Saint-Exupéry · …")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Reading progress is being measured.")
    }

    @Test
    fun anAuthoredFinishedRowNamesItsAuthorBeforeFinished() = runComposeUiTest {
        setContent {
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

        onNodeWithText("Antoine de Saint-Exupéry · Finished").assertIsDisplayed()
        onNodeWithTag(libraryProgressTag(authoredBook.id), useUnmergedTree = true).assertDoesNotExist()
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
