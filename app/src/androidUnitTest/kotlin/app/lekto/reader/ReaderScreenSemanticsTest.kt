package app.lekto.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * `SelectionContainer` over an `AnnotatedString` whose words are clickable
 * `LinkAnnotation`s and whose pages are cut by the platform text stack, so
 * resume, paging (by button and by tap zone) and the receding chrome run here on
 * the runtime that ultimately renders them, not only on the desktop JVM.
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
    fun eachWordIsAFocusableLink() {
        compose.setContent { Reader(renderer) }

        // The chrome contributes the back arrow, the **Aa** control and the two
        // page chevrons; every word adds another clickable node, so a count above
        // the chrome proves the word layer is tappable and reachable on Android,
        // not just painted.
        val clickables = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()

        assertTrue("expected word links beyond the chrome, found ${clickables.size}", clickables.size > CHROME_BUTTONS)
    }

    /**
     * The reader's two bands (docs/design.md §7.2): the back arrow, the book's
     * title and the **Aa** control above; the two chevrons and the position below.
     * The chevrons mark the tap zones and are controls too, disabled at a bound,
     * so a tap turns the page like the zone under them.
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
    fun theRightZoneTurnsThePageAndTheLeftZoneTurnsItBack() {
        compose.setContent { Reader(renderer) }
        assertEquals(0, compose.progressPercent())

        // A tap in the right third advances; in the left third it goes back.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput {
            click(Offset(visibleSize.width - 4f, center.y))
        }
        assertTrue("the right zone turns forward", compose.progressPercent() > 0)

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(4f, center.y)) }
        assertEquals(0, compose.progressPercent())
    }

    @Test
    fun tappingTheCentreRecedesAndReturnsTheChrome() {
        compose.setContent { Reader(renderer, chapter = SHORT_CHAPTER) }
        compose.onNodeWithText("Hello.").assertIsDisplayed()
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()

        // The short chapter leaves the middle of the page free of glyphs, so the
        // centre tap is a chrome toggle rather than a word lookup.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        compose.onNodeWithTag(READER_POSITION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(READER_BACK_TAG).assertDoesNotExist()
        compose.onNodeWithText("Hello.").assertIsDisplayed()

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_BACK_TAG).assertIsDisplayed()
    }

    @Test
    fun opensAtTheSavedPosition() {
        // A position at the far end of the text must open on the last page, not
        // the first: a resumed session is exactly where the user left it.
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        assertTrue("a session saved at the end opens near the end", compose.progressPercent() >= 90)
        compose.onNodeWithTag(READER_POSITION_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_PREVIOUS_PAGE_TAG).assertIsEnabled()
        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsNotEnabled()
    }

    @Test
    fun reachingTheLastPageDoesNotOpenTheEndState() {
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        compose.onNodeWithTag(READER_END_PANEL_TAG).assertDoesNotExist()
    }

    @Test
    fun nextOnTheLastPageOpensTheEndState() {
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }
        compose.onNodeWithTag(READER_NEXT_PAGE_TAG).assertIsNotEnabled()

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput {
            click(Offset(visibleSize.width - 4f, center.y))
        }

        compose.onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithText("THE END").assertIsDisplayed()
        compose.onNodeWithText(LONG_CHAPTER.title).assertIsDisplayed()
        compose.onNodeWithTag(READER_MARK_FINISHED_TAG).assertIsDisplayed()
        compose.onNodeWithTag(READER_BACK_TO_LIBRARY_TAG).assertIsDisplayed()
        // The state hides the reader's chrome.
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

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput {
            click(Offset(visibleSize.width - 4f, center.y))
        }

        assertTrue("turning to page two must report a non-zero character offset", reported > 0)
    }

    @Test
    fun selectingAPhraseByLongPressDragReportsIt() {
        var selected: PhraseSelection? = null
        compose.setContent { Reader(renderer, onPhraseSelected = { chosen -> selected = chosen }) }

        compose.onNodeWithText("On the quiet evening", substring = true).assertIsDisplayed()
        val start = compose.firstWordCentre()
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput {
            down(start)
            advanceEventTime(1000)
            moveTo(Offset(start.x + 80f, start.y))
            moveTo(Offset(start.x + 160f, start.y))
            up()
        }

        assertNotNull("a long-press drag must select a phrase", selected)
        val phrase = selected!!
        val chapter = LONG_CHAPTER.blocks.joinToString("\n\n") { block -> block.text }
        // Robolectric's text metrics are sub-pixel, so the drag does not extend
        // reliably here; the desktop lane proves a multi-word range and
        // `PhraseSelectionTest` pins it. This lane proves the gesture selects a
        // phrase from the chapter and opens the panel.
        assertTrue("the selected phrase must not be blank", phrase.text.isNotBlank())
        assertTrue("the phrase must come from the chapter: ${phrase.text}", chapter.contains(phrase.text))
    }

    @Test
    fun theTapZonesAreThirdsOfTheWidth() {
        val width = 300
        assertEquals(ReaderTapZone.PREVIOUS, readerTapZone(0f, width))
        assertEquals(ReaderTapZone.PREVIOUS, readerTapZone(99f, width))
        assertEquals(ReaderTapZone.CHROME, readerTapZone(150f, width))
        assertEquals(ReaderTapZone.NEXT, readerTapZone(201f, width))
        assertEquals(ReaderTapZone.NEXT, readerTapZone(300f, width))
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
        onPhraseSelected: (PhraseSelection) -> Unit = {},
        onMarkFinished: () -> Unit = {},
        onMarkUnfinished: () -> Unit = {},
    ) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(chapter, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(
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
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(visibleSize.width - 4f, center.y)) }
        onNodeWithTag(READER_END_PANEL_TAG).assertIsDisplayed()
    }
}

/** The first word link's centre, in the reader page's coordinates. */
@Suppress("MagicNumber") // The wait for the word layer is the test's parameter.
private fun ComposeTestRule.firstWordCentre(): Offset {
    waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
        onAllNodes(WORD_LINK)
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val word = onAllNodes(WORD_LINK)
        .onFirst()
        .fetchSemanticsNode()
        .boundsInRoot
    return word.center - page.topLeft
}

/**
 * A word link on the page: a clickable node that carries neither text nor a
 * description. The reader's chrome has an icon since the navigation shell
 * (issue #185) — clickable, described, and not a word — so a word is the one
 * clickable node with nothing to read on it.
 */
private val WORD_LINK = hasClickAction() and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.Text) and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)

/** The **Reading progress** the position line shows, as a whole percentage. */
private fun ComposeTestRule.progressPercent(): Int {
    val text = onNodeWithTag(READER_POSITION_TAG).fetchSemanticsNode()
        .config[SemanticsProperties.Text]
        .joinToString(" ") { line -> line.text }
    return Regex("(\\d+)\\s*%").find(text)?.groupValues?.get(1)?.toIntOrNull()
        ?: error("no percentage in the position: $text")
}
