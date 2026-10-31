package app.lekto.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The budget for a wait that follows work on the app's injected dispatcher.
 * That work crosses a real thread, so the wait has to outlast a busy machine
 * rather than the one-second Compose default (issue #194).
 */
private const val AWAIT_DISPATCHED_WORK_MILLIS: Long = 10_000

/**
 * The reader screen on a **simulated Android runtime** (issue #75): the
 * same-named twin of `app/desktopTest`'s `ReaderScreenSemanticsTest`, so the
 * parity rule (issue #73) can see the two lanes together. The reader's page is a
 * `SelectionContainer` over an `AnnotatedString` whose words the page hit-tests
 * (ADR-0007) and whose pages are cut by the platform text stack, so resume,
 * paging (by the chevrons and by the horizontal swipe) and the reader's fixed
 * chrome run here on the runtime that ultimately renders them, not only on the
 * desktop JVM.
 *
 * The long-press drag that selects a phrase stays on the desktop lane: its
 * timeout is driven by the host clock, which this runtime does not advance
 * mid-gesture, so the same test would fail here for the harness's reason rather
 * than the reader's.
 *
 * A phone-sized viewport is forced, exactly as the desktop twin does. The
 * chapter is longer than the desktop twin's sample: Robolectric lays glyphs out
 * far more tightly than a device font does, so the sample chapter that spans
 * several pages on the desktop fits on one here. The longer fixture restores the
 * multi-page behaviour the assertions are about, without pinning an exact page
 * count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("MagicNumber", "TooManyFunctions") // The tap coordinates, and one test per behaviour the reader owns.
class ReaderScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private companion object {
        /**
         * The back arrow, the **Aa** control and the two page chevrons: the
         * clickable nodes the chrome adds itself, so a count above this needs at
         * least one word link.
         */
        const val CHROME_BUTTONS = 4

        /** One short line, so the page has blank space below it for a centre tap. */
        val SHORT_CHAPTER = ReaderText(
            title = "Short",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Hello.")))),
        )

        /** Sixty paragraphs of one sentence, so the chapter spans pages under Robolectric's tighter metrics. */
        val LONG_CHAPTER = ReaderText(
            title = "The Lantern Keeper",
            language = "en",
            blocks = List(60) {
                TextBlock(
                    BlockKind.PARAGRAPH,
                    listOf(TextRun("On the quiet evening the keeper lit the lantern and waited for the boats.")),
                )
            },
        )
    }

    @Test
    fun rendersWordsOnTheFirstPage() {
        compose.setContent { Reader(renderer) }

        compose.onNodeWithText("On the quiet evening", substring = true).assertIsDisplayed()
    }

    @Test
    fun aTapOnAWordAsksForThatWordLookup() {
        var tapped: WordTap? = null
        compose.setContent { Reader(renderer, onWordTap = { tap -> tapped = tap }) }

        // Words carry no link of their own (ADR-0007): the page hit-tests the tap
        // against its tokens, so a tap anywhere on a word finds it.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(compose.firstWordCentre()) }

        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) { tapped != null }
        val word = tapped
        assertNotNull("a tap on a word must ask for its lookup", word)
        assertEquals("On", word!!.token.surface)
    }

    /**
     * The reader's two bands (docs/design.md §7.2): the back arrow, the book's
     * title and the **Aa** control above; the two chevrons and the position below.
     * The chevrons are controls, disabled at a bound except the next one on the
     * last page, which raises the end of the book.
     */
    @Test
    fun theBandsCarryTheBackTitleAaAndThePosition() {
        compose.setContent { Reader(renderer) }

        compose.onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_TITLE_TAG).assertTextEquals(LONG_CHAPTER.title)
        compose.onNodeWithTag(READING_PREFERENCES_TAG).assertIsDisplayed()
        assertEquals(0, compose.progressPercent())
        compose.onNodeWithTag(READER_PREVIOUS_PAGE_TAG).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsDisplayed().assertIsEnabled()

        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).performClick()
        assertTrue("page two is further through the book than page one", compose.progressPercent() > 0)

        compose.onNodeWithTag(READER_PREVIOUS_PAGE_TAG).performClick()
        assertEquals(0, compose.progressPercent())
    }

    /**
     * The page carries the two turns as accessibility actions beside the two
     * chevrons, so a screen-reader or switch user has a way through the book
     * without hunting for the bottom band.
     */
    @Test
    fun thePageOffersTheTwoPageTurnsToAScreenReader() {
        compose.setContent { Reader(renderer) }

        val actions = compose.onNodeWithText("On the quiet evening", substring = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]

        assertEquals(
            listOf("Previous page", "Next page"),
            actions.map { action -> action.label },
        )
    }

    @Test
    fun aLeftwardSwipeTurnsThePageAndARightwardOneTurnsItBack() {
        compose.setContent { Reader(renderer) }
        assertEquals(0, compose.progressPercent())

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { swipeLeft() }
        assertTrue("a leftward swipe turns forward", compose.progressPercent() > 0)

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { swipeRight() }
        assertEquals(0, compose.progressPercent())
    }

    @Test
    fun aTapThatMissesAWordChangesNothing() {
        compose.setContent { Reader(renderer, chapter = SHORT_CHAPTER) }
        assertEquals(0, compose.progressPercent())
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()

        // The short chapter leaves the middle of the page free of glyphs. A tap
        // there used to recede the chrome or turn the page; now it does nothing.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }

        assertEquals(0, compose.progressPercent())
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun aSwipeIsIgnoredWhileTheTextPanelIsOpen() {
        compose.setContent { Reader(renderer) }
        compose.onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        val before = compose.progressPercent()

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { swipeLeft() }

        assertEquals(before, compose.progressPercent())
    }

    @Test
    fun aSwipeIsHorizontalAndCoversEnoughOfThePage() {
        val width = 400f
        assertEquals(PageSwipe.NEXT, pageSwipe(dx = -120f, dy = 8f, width = width))
        assertEquals(PageSwipe.PREVIOUS, pageSwipe(dx = 120f, dy = 8f, width = width))
        assertNull(pageSwipe(dx = -40f, dy = 8f, width = width))
        assertNull(pageSwipe(dx = -120f, dy = 200f, width = width))
    }

    @Test
    fun opensAtTheSavedPosition() {
        // A position at the far end of the text must open on the last page, not
        // the first: a resumed session is exactly where the user left it.
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        assertTrue("a session saved at the end opens near the end", compose.progressPercent() >= 90)
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_PREVIOUS_PAGE_TAG).assertIsEnabled()
        // The last page still offers a next action: it raises the end of the book.
        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsEnabled()
    }

    @Test
    fun reachingTheLastPageDoesNotOpenTheEndState() {
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        compose.onNodeWithTag(READER_END_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun nextOnTheLastPageOpensTheEndState() {
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }
        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsEnabled()

        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).performClick()

        compose.onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithText("THE END").assertIsDisplayed()
        compose.onNodeWithText(LONG_CHAPTER.title).assertIsDisplayed()
        compose.onNodeWithTag(READER_MARK_FINISHED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_BACK_TO_LIBRARY_TAG).assertIsDisplayed()
        // The state replaces the reader's chrome with its own panel.
        compose.onNodeWithTag(READER_POSITION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(READER_BACK_TAG).assertDoesNotExist()
    }

    @Test
    fun markingFinishedKeepsThePanelOpenAndUndoFlipsItBack() {
        var marked = 0
        var undone = 0
        compose.setContent {
            Reader(
                renderer,
                initialOffset = Int.MAX_VALUE,
                onMarkFinished = { marked++ },
                onMarkUnfinished = { undone++ },
            )
        }
        compose.openEndState()

        compose.onNodeWithTag(READER_MARK_FINISHED_TAG).performClick()

        assertEquals(1, marked)
        compose.onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.onNodeWithTag(READER_BACK_TO_LIBRARY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_MARK_UNFINISHED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_MARK_FINISHED_TAG).assertDoesNotExist()

        compose.onNodeWithTag(READER_MARK_UNFINISHED_TAG).performClick()

        assertEquals(1, undone)
        compose.onNodeWithTag(READER_MARK_FINISHED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_MARK_UNFINISHED_TAG).assertDoesNotExist()
    }

    @Test
    fun aFinishedSessionOpensTheEndStateAlreadyMarked() {
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE, initiallyFinished = true) }
        compose.openEndState()

        compose.onNodeWithText("Finished").assertIsDisplayed()
        compose.onNodeWithTag(READER_MARK_UNFINISHED_TAG).assertIsDisplayed()
    }

    @Test
    fun markingFinishedNeverMovesThePosition() {
        val reported = mutableListOf<Int>()
        compose.setContent {
            Reader(
                renderer,
                initialOffset = Int.MAX_VALUE,
                onPositionChange = { offset -> reported += offset },
                onMarkFinished = {},
            )
        }
        compose.openEndState()

        compose.onNodeWithTag(READER_MARK_FINISHED_TAG).performClick()

        assertTrue("declaring finished is not a page turn: $reported", reported.isEmpty())
    }

    @Test
    fun backToLibraryLeavesTheReader() {
        var left = false
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE, onBack = { left = true }) }
        compose.openEndState()

        compose.onNodeWithTag(READER_BACK_TO_LIBRARY_TAG).performClick()

        assertTrue("Back to Library leaves for the Library", left)
    }

    @Test
    fun aPageTurnReportsItsPosition() {
        var reported = -1
        compose.setContent { Reader(renderer, onPositionChange = { offset -> reported = offset }) }
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()

        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).performClick()

        assertTrue("turning to page two must report a non-zero character offset", reported > 0)
    }

    @Composable
    @Suppress("LongParameterList") // The reader's inputs are the test's knobs; a bundle would only hide them.
    private fun Reader(
        renderer: ReaderRenderer,
        chapter: ReaderText = LONG_CHAPTER,
        initialOffset: Int = 0,
        initiallyFinished: Boolean = false,
        onBack: () -> Unit = {},
        onPositionChange: (Int) -> Unit = {},
        onWordTap: (WordTap) -> Unit = {},
        onPhraseSelected: (PhraseSelection) -> Unit = {},
        onMarkFinished: () -> Unit = {},
        onMarkUnfinished: () -> Unit = {},
    ) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(chapter, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(
                    onWordTap = onWordTap,
                    onPhraseSelected = onPhraseSelected,
                    onBack = onBack,
                    onPositionChange = onPositionChange,
                    onMarkFinished = onMarkFinished,
                    onMarkUnfinished = onMarkUnfinished,
                ),
                initiallyFinished = initiallyFinished,
            )
        }
    }

    /** Taps the next zone on the last page, which is the only way the end state opens. */
    private fun ComposeTestRule.openEndState() {
        onNodeWithTag(READER_NEXT_PAGE_TAG).performClick()
        onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
    }
}

/** The wait for the page's text is the test's parameter. */
@Suppress("MagicNumber")
private fun ComposeTestRule.firstWordCentre(): Offset {
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodesWithTag(READER_PAGE_TEXT_TAG).fetchSemanticsNodes().isNotEmpty()
    }
    // The text node arrives before its layout callback has run; the tap needs the
    // layout to find the word.
    waitForIdle()
    return firstWordPoint()
}

/** The centre of the page's first word, in the page's coordinates, from the text's own layout. */
@Suppress("MagicNumber") // The small inset keeps the point off the glyph's edge.
private fun ComposeTestRule.firstWordPoint(): Offset {
    val text = onNodeWithTag(READER_PAGE_TEXT_TAG).fetchSemanticsNode()
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val results = mutableListOf<TextLayoutResult>()
    text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
    val layout = results.first()
    val shown = text.config[SemanticsProperties.Text].joinToString("") { it.text }
    // The first word: a page opens with a block separator, so its first
    // characters belong to no word at all.
    val from = 0
    val anchorChar = (from until shown.length).firstOrNull { index -> !shown[index].isWhitespace() }
        ?: shown.indexOfFirst { character -> !character.isWhitespace() }.coerceAtLeast(0)
    val word = layout.getWordBoundary(anchorChar)
    val start = layout.getBoundingBox(word.start)
    val last = layout.getBoundingBox((word.end - 1).coerceAtLeast(word.start))
    val origin = text.boundsInRoot.topLeft
    return Offset(
        origin.x - page.left + (start.left + last.right) / 2f,
        origin.y - page.top + start.top + start.height / 2f,
    )
}

/** The **Reading progress** the position line shows, as a whole percentage. */
private fun ComposeTestRule.progressPercent(): Int {
    val text = onNodeWithTag(READER_POSITION_TAG).fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(" ") { line -> line.text }
    return Regex("(\\d+)\\s*%").find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: error("no percentage in the position: $text")
}
