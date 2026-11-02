package app.lekto.library

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingState
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The whole percentage the block's line carries. */
private const val MEASURED_PERCENT = 28

/** The chapter the block names, and the snippet it shows. */
private const val CHAPTER = "Chapter IX"
private const val SNIPPET = "The boats came home at dusk."

/** How long the cover test waits for the decode, which runs off the main thread. */
private const val COVER_TIMEOUT_MILLIS = 5_000L

/**
 * The **Resume block**'s behaviour through semantics (issue #288): the book in
 * progress leads in it and leaves the shelf, the block obeys the search and the
 * filter, each missing piece degrades on its own, and delete still asks first.
 *
 * The block is a second face of the same shelf, so it lives beside
 * `LibraryScreenSemanticsTest` rather than inside it, and this class has the
 * same-named Android twin (issue #73).
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One test per behaviour; the list is the block's.
class LibraryResumeBlockSemanticsTest {

    private val otherBook = Book(
        id = "b2",
        title = "A Smaller Book",
        language = "en",
        format = BookFormat.TXT,
        fileName = "smaller.txt",
    )

    /** The book the block leads with: an author, a chapter and a measured position. */
    private val resumeBook = Book(
        id = "b3",
        title = "The Return",
        language = "fr",
        format = BookFormat.EPUB,
        fileName = "return.epub",
        author = "Émile Zola",
    )

    @Test
    fun theLastReadBookLeadsInAResumeBlock() = runComposeUiTest {
        setContent { Library(resumable()) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertIsDisplayed()
        // The block replaces the row: the shelf below loses exactly that book.
        onNodeWithTag(libraryRowTag(resumeBook.id)).assertDoesNotExist()
        val block = onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).getBoundsInRoot()
        val other = onNodeWithText(otherBook.title).getBoundsInRoot()
        assertTrue(block.bottom < other.top, "the block must lead the shelf: $block vs $other")
        onNodeWithText("Continue reading").assertDoesNotExist()
    }

    @Test
    fun theBlockNamesTheAuthorTheChapterAndThePercentage() = runComposeUiTest {
        setContent { Library(resumable()) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
            .assertTextContains("Émile Zola", substring = true)
            .assertTextContains("$CHAPTER · $MEASURED_PERCENT %", substring = true)
    }

    @Test
    fun theBlockShowsTheSnippetAtTheSavedPosition() = runComposeUiTest {
        setContent { Library(resumable(), snippets = { SNIPPET }) }

        onNodeWithText(SNIPPET).assertIsDisplayed()
    }

    @Test
    fun theBlockEndsAtItsButtonWithoutASnippet() = runComposeUiTest {
        setContent { Library(resumable()) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertIsDisplayed()
        onNodeWithText(SNIPPET).assertDoesNotExist()
    }

    @Test
    fun theBlockNamesThePercentageAloneWithoutAChapter() = runComposeUiTest {
        setContent { Library(resumable(chapter = null)) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
            .assertTextContains("$MEASURED_PERCENT %", substring = true)
        onNodeWithText("$CHAPTER · $MEASURED_PERCENT %").assertDoesNotExist()
    }

    @Test
    fun theBlockShowsACoverInPlaceOfThePlaque() = runComposeUiTest {
        val covers = BookCoverLoader { ImageBitmap(width = 2, height = 3) }
        setContent { Library(resumable(), covers = covers) }

        waitUntil(timeoutMillis = COVER_TIMEOUT_MILLIS) {
            onAllNodesWithTag(libraryCoverTag(resumeBook.id), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag(libraryCoverTag(resumeBook.id), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theBlockKeepsTheLanguagePlaqueWithoutACover() = runComposeUiTest {
        // A TXT never carries art, and an EPUB with no usable cover keeps its
        // language code, so the block is never a hole (issues #259, #288).
        setContent { Library(resumable()) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertTextContains("FR", substring = true)
        onNodeWithTag(libraryCoverTag(resumeBook.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theBlockIsHiddenWhileAQueryRuns() = runComposeUiTest {
        setContent { Library(resumable(query = "return")) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        onNodeWithTag(libraryRowTag(resumeBook.id)).assertIsDisplayed()
    }

    @Test
    fun theBlockIsHiddenWhenTheFilterExcludesInProgress() = runComposeUiTest {
        setContent { Library(resumable(selectedStates = setOf(ReadingState.UNREAD))) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        onNodeWithText(resumeBook.title).assertDoesNotExist()
    }

    @Test
    fun aFinishedBookNeverBecomesTheBlock() = runComposeUiTest {
        setContent {
            Library(
                LibraryUiState(
                    listing = LibraryListing(
                        others = listOf(resumeBook),
                        progressByBook = mapOf(resumeBook.id to BookProgress.Measured(MEASURED_PERCENT)),
                        readingStateByBook = mapOf(resumeBook.id to ReadingState.FINISHED),
                    ),
                    selectedStates = ReadingState.entries.toSet(),
                ),
            )
        }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        onNodeWithTag(libraryRowTag(resumeBook.id)).assertIsDisplayed()
    }

    @Test
    fun aShelfWithoutAReadingHistoryKeepsItsPlainList() = runComposeUiTest {
        setContent { Library(LibraryUiState(listing = LibraryListing(others = listOf(resumeBook)))) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        onNodeWithTag(LIBRARY_IN_PROGRESS_HEADING_TAG).assertDoesNotExist()
        onNodeWithTag(LIBRARY_OTHER_BOOKS_HEADING_TAG).assertDoesNotExist()
        onNodeWithText(resumeBook.title).assertIsDisplayed()
    }

    @Test
    fun theRemainingLastReadBooksFallUnderInProgress() = runComposeUiTest {
        // The block takes the most recent book; the others keep a named group, so
        // the shelf still says where "read recently" ends (issues #188, #288).
        val secondRead = resumeBook.copy(id = "b4", title = "An Earlier Book")
        setContent { Library(resumable(lastRead = listOf(resumeBook, secondRead))) }

        val block = onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).getBoundsInRoot()
        val recentHeading = onNodeWithTag(LIBRARY_IN_PROGRESS_HEADING_TAG).assertIsDisplayed().getBoundsInRoot()
        val recentRow = onNodeWithTag(libraryRowTag(secondRead.id)).getBoundsInRoot()
        val otherHeading = onNodeWithTag(LIBRARY_OTHER_BOOKS_HEADING_TAG).assertIsDisplayed().getBoundsInRoot()
        val other = onNodeWithText(otherBook.title).getBoundsInRoot()

        assertTrue(block.bottom < recentHeading.top, "the block leads its group: $block vs $recentHeading")
        assertTrue(recentHeading.bottom < recentRow.top, "the heading parts the groups: $recentHeading vs $recentRow")
        assertTrue(recentRow.bottom < otherHeading.top, "the rest follows: $recentRow vs $otherHeading")
        assertTrue(otherHeading.bottom < other.top, "the second group follows its heading: $otherHeading vs $other")
        onNodeWithText("Continue reading").assertDoesNotExist()
    }

    @Test
    fun theOverflowMenuAsksForConfirmationBeforeDeletingTheBlock() = runComposeUiTest {
        var deleted: Book? = null
        setContent { Library(resumable(), onDelete = { deleted = it }) }

        onNodeWithTag(libraryResumeDeleteTag(resumeBook.id)).performClick()

        onNodeWithText("Delete « The Return »?").assertIsDisplayed()
        assertNull(deleted, "the vault must be untouched until the user confirms")
        onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        assertEquals(resumeBook, deleted)
    }

    @Test
    fun theBlockIsOneItemAndResumeIsItsAction() = runComposeUiTest {
        var opened: Book? = null
        setContent { Library(resumable(), onOpen = { opened = it }) }

        // The block's own action is `Resume`, and the button is not a second stop:
        // its words belong to the block's action rather than its own node.
        onNodeWithText("Resume").assertDoesNotExist()
        onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(resumeBook, opened)
    }

    @Test
    fun theBlockSaysWhatItsLineIsWaitingForBeforeTheLengthLands() = runComposeUiTest {
        setContent { Library(resumable(measured = false)) }

        onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
            .assertTextContains("…", substring = true)
            .assertContentDescriptionEquals("Reading progress is being measured.")
    }

    /**
     * A shelf whose most recent book is positioned and **In progress**, so the
     * resume block leads it. [chapter] is the name the block's line carries;
     * `null` is a book whose chapter list names none.
     */
    @Suppress("LongParameterList") // Every argument is a default the test varies one at a time.
    private fun resumable(
        query: String = "",
        selectedStates: Set<ReadingState> = LibraryUiState.DEFAULT_STATES,
        chapter: String? = CHAPTER,
        measured: Boolean = true,
        lastRead: List<Book> = listOf(resumeBook),
        others: List<Book> = listOf(otherBook),
    ): LibraryUiState {
        val listing = LibraryListing(
            lastRead = lastRead,
            others = others,
            progressByBook = lastRead.associate { book ->
                book.id to if (measured) BookProgress.Measured(MEASURED_PERCENT) else BookProgress.Unmeasured
            },
            readingStateByBook = lastRead.associate { book -> book.id to ReadingState.IN_PROGRESS },
        ).copy(chapterTitleByBook = chapter?.let { name -> mapOf(resumeBook.id to name) }.orEmpty())
        return LibraryUiState(listing = listing, query = query, selectedStates = selectedStates)
    }

    @Composable
    @Suppress("LongParameterList") // The screen's actions, one per behaviour these tests drive.
    private fun Library(
        state: LibraryUiState,
        onOpen: (Book) -> Unit = {},
        onDelete: (Book) -> Unit = {},
        covers: BookCoverLoader = BookCoverLoader.None,
        snippets: BookSnippetLoader = BookSnippetLoader.None,
    ) {
        MaterialTheme {
            LibraryScreen(
                state = state,
                actions = LibraryActions(onOpen = onOpen, onDelete = onDelete),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                covers = covers,
                snippets = snippets,
            )
        }
    }
}
