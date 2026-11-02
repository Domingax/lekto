package app.lekto.library

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.LibraryListing
import app.lekto.core.book.ReadingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The whole percentage the block's line carries. */
private const val MEASURED_PERCENT = 28

/** The chapter the block names, and the snippet it shows. */
private const val CHAPTER = "Chapter IX"
private const val SNIPPET = "The boats came home at dusk."

/** How long the cover test waits for the decode, which runs off the main thread. */
private const val COVER_TIMEOUT_MILLIS = 5_000L

/** The stand-in cover's size, in pixels. */
private const val COVER_WIDTH = 2
private const val COVER_HEIGHT = 3

/**
 * The **Resume block** on a **simulated Android runtime** (issue #288): the
 * same-named twin of `app/desktopTest`'s `LibraryResumeBlockSemanticsTest`, so
 * the parity rule (issue #73) sees the two lanes together. This lane adds the
 * touch idiom the desktop cannot stand in for — the block's trailing-edge swipe
 * — and scrolls to the groups the shorter Android window leaves below the fold.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // One test per behaviour; the list is the block's.
@Config(sdk = [36], qualifiers = "w360dp-h800dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class LibraryResumeBlockSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
    fun theLastReadBookLeadsInAResumeBlock() {
        compose.setContent { Library(resumable()) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertIsDisplayed()
        // The block replaces the row: the shelf below loses exactly that book.
        compose.onNodeWithTag(libraryRowTag(resumeBook.id)).assertDoesNotExist()
        val block = compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).getBoundsInRoot()
        val other = compose.onNodeWithText(otherBook.title).getBoundsInRoot()
        assertTrue("the block must lead the shelf: $block vs $other", block.bottom < other.top)
        compose.onNodeWithText("Continue reading").assertDoesNotExist()
    }

    @Test
    fun theBlockNamesTheAuthorTheChapterAndThePercentage() {
        compose.setContent { Library(resumable()) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
            .assertTextContains("Émile Zola", substring = true)
            .assertTextContains(CHAPTER, substring = true)
            .assertTextContains("· $MEASURED_PERCENT %", substring = true)
    }

    @Test
    fun theBlockShowsTheSnippetAtTheSavedPosition() {
        compose.setContent { Library(resumable(), snippets = { SNIPPET }) }

        compose.onNodeWithText(SNIPPET).assertIsDisplayed()
    }

    @Test
    fun theBlockEndsAtItsButtonWithoutASnippet() {
        compose.setContent { Library(resumable()) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertIsDisplayed()
        compose.onNodeWithText(SNIPPET).assertDoesNotExist()
    }

    @Test
    fun theBlockNamesThePercentageAloneWithoutAChapter() {
        compose.setContent { Library(resumable(chapter = null)) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
            .assertTextContains("$MEASURED_PERCENT %", substring = true)
        compose.onNodeWithText(CHAPTER).assertDoesNotExist()
    }

    @Test
    fun theBlockShowsACoverInPlaceOfThePlaque() {
        val covers = BookCoverLoader {
            Bitmap.createBitmap(COVER_WIDTH, COVER_HEIGHT, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
        compose.setContent { Library(resumable(), covers = covers) }

        compose.waitUntil(timeoutMillis = COVER_TIMEOUT_MILLIS) {
            compose.onAllNodesWithTag(libraryCoverTag(resumeBook.id), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag(libraryCoverTag(resumeBook.id), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theBlockKeepsTheLanguagePlaqueWithoutACover() {
        // A TXT never carries art, and an EPUB with no usable cover keeps its
        // language code, so the block is never a hole (issues #259, #288).
        compose.setContent { Library(resumable()) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertTextContains("FR", substring = true)
        compose.onNodeWithTag(libraryCoverTag(resumeBook.id), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theBlockIsHiddenWhileAQueryRuns() {
        compose.setContent { Library(resumable(query = "return")) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        compose.onNodeWithTag(libraryRowTag(resumeBook.id)).assertIsDisplayed()
    }

    @Test
    fun theBlockIsHiddenWhenTheFilterExcludesInProgress() {
        compose.setContent { Library(resumable(selectedStates = setOf(ReadingState.UNREAD))) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        compose.onNodeWithText(resumeBook.title).assertDoesNotExist()
    }

    @Test
    fun aFinishedBookNeverBecomesTheBlock() {
        compose.setContent {
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

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        compose.onNodeWithTag(libraryRowTag(resumeBook.id)).assertIsDisplayed()
    }

    @Test
    fun aShelfWithoutAReadingHistoryKeepsItsPlainList() {
        compose.setContent { Library(LibraryUiState(listing = LibraryListing(others = listOf(resumeBook)))) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertDoesNotExist()
        compose.onNodeWithTag(LIBRARY_IN_PROGRESS_HEADING_TAG).assertDoesNotExist()
        compose.onNodeWithTag(LIBRARY_OTHER_BOOKS_HEADING_TAG).assertDoesNotExist()
        compose.onNodeWithText(resumeBook.title).assertIsDisplayed()
    }

    @Test
    fun theRemainingLastReadBooksFallUnderInProgress() {
        // The block takes the most recent book; the others keep a named group, so
        // the shelf still says where "read recently" ends (issues #188, #288). The
        // Android window is shorter, so this lane scrolls to each group rather
        // than comparing bounds; the desktop twin pins the order itself.
        val secondRead = resumeBook.copy(id = "b4", title = "An Earlier Book")
        compose.setContent { Library(resumable(lastRead = listOf(resumeBook, secondRead))) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_LIST_TAG).performScrollToNode(hasTestTag(libraryRowTag(secondRead.id)))
        compose.onNodeWithTag(LIBRARY_IN_PROGRESS_HEADING_TAG).assertExists()
        compose.onNodeWithTag(libraryRowTag(secondRead.id)).assertIsDisplayed()
        compose.onNodeWithTag(LIBRARY_LIST_TAG).performScrollToNode(hasTestTag(libraryRowTag(otherBook.id)))
        compose.onNodeWithTag(LIBRARY_OTHER_BOOKS_HEADING_TAG).assertExists()
        compose.onNodeWithTag(libraryRowTag(otherBook.id)).assertIsDisplayed()
        compose.onNodeWithText("Continue reading").assertDoesNotExist()
    }

    @Test
    fun theSwipeOnTheBlockAsksForConfirmation() {
        // The row's trailing-edge swipe works on the block too, and only asks:
        // the vault is untouched until the dialog is confirmed (issues #208, #288).
        var deleted: Book? = null
        compose.setContent { Library(resumable(), onDelete = { deleted = it }) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id)).performTouchInput { swipeLeft() }

        compose.onNodeWithText("Delete « The Return »?").assertIsDisplayed()
        assertNull("the vault must be untouched until the user confirms", deleted)
    }

    @Test
    fun theBlockIsOneItemAndResumeIsItsAction() {
        var opened: Book? = null
        compose.setContent { Library(resumable(), onOpen = { opened = it }) }

        // The block's own action is `Resume`, and the button is not a second stop:
        // its words belong to the block's action rather than its own node.
        compose.onNodeWithText("Resume").assertDoesNotExist()
        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(resumeBook, opened)
    }

    @Test
    fun theBlockSaysWhatItsLineIsWaitingForBeforeTheLengthLands() {
        compose.setContent { Library(resumable(measured = false)) }

        compose.onNodeWithTag(libraryResumeBlockTag(resumeBook.id))
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
