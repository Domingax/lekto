package app.lekto.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
@Suppress("MagicNumber") // The tap coordinates and the thirds of the width are the test's parameters.
class ReaderScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    private companion object {
        /**
         * Library, Previous page and Next page: the clickable nodes the chrome
         * adds itself, so a count above this needs at least one word link.
         */
        const val CHROME_BUTTONS = 3

        /** One short line, so the page has blank space below it for a centre tap. */
        val SHORT_CHAPTER = ReaderChapter(
            title = "Short",
            language = "en",
            blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun("Hello.")))),
        )

        /** Sixty paragraphs of one sentence, so the chapter spans pages under Robolectric's tighter metrics. */
        val LONG_CHAPTER = ReaderChapter(
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

        // The chrome contributes Library, Previous page and Next page; every word
        // adds another clickable node, so a count above the chrome proves the word
        // layer is tappable and reachable on Android, not just painted.
        val clickables = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()

        assertTrue("expected word links beyond the chrome, found ${clickables.size}", clickables.size > CHROME_BUTTONS)
    }

    @Test
    fun navigatesForwardAndBackWithTheButtons() {
        compose.setContent { Reader(renderer) }

        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Previous page").assertIsNotEnabled()

        compose.onNodeWithText("Next page").performClick()
        compose.onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Previous page").assertIsEnabled()

        compose.onNodeWithText("Previous page").performClick()
        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Previous page").assertIsNotEnabled()
    }

    @Test
    fun theRightZoneTurnsThePageAndTheLeftZoneTurnsItBack() {
        compose.setContent { Reader(renderer) }
        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()

        // A tap in the right third advances; in the left third it goes back.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput {
            click(Offset(visibleSize.width - 4f, center.y))
        }
        compose.onNodeWithText("Page 2 of", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(4f, center.y)) }
        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingTheCentreRecedesAndReturnsTheChrome() {
        compose.setContent { Reader(renderer, chapter = SHORT_CHAPTER) }
        compose.onNodeWithText("Hello.").assertIsDisplayed()
        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()

        // The short chapter leaves the middle of the page free of glyphs, so the
        // centre tap is a chrome toggle rather than a word lookup.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        compose.onNodeWithText("Page 1 of", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Library").assertDoesNotExist()
        compose.onNodeWithText("Hello.").assertIsDisplayed()

        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(center) }
        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun opensAtTheSavedPosition() {
        // A position at the far end of the text must open on the last page, not
        // the first: a resumed session is exactly where the user left it.
        compose.setContent { Reader(renderer, initialOffset = Int.MAX_VALUE) }

        compose.onNodeWithText("Page 1 of", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Previous page").assertIsEnabled()
        compose.onNodeWithText("Next page").assertIsNotEnabled()
    }

    @Test
    fun aPageTurnReportsItsPosition() {
        var reported = -1
        compose.setContent { Reader(renderer, onPositionChange = { offset -> reported = offset }) }

        compose.onNodeWithText("Next page").performClick()

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
        chapter: ReaderChapter = LONG_CHAPTER,
        initialOffset: Int = 0,
        onPositionChange: (Int) -> Unit = {},
        onPhraseSelected: (PhraseSelection) -> Unit = {},
    ) {
        MaterialTheme {
            ReaderScreen(
                document = ReaderDocument(chapter, renderer, initialOffset),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
                actions = ReaderActions(
                    onPhraseSelected = onPhraseSelected,
                    onBack = {},
                    onPositionChange = onPositionChange,
                ),
            )
        }
    }
}

/** The first word link's centre, in the reader page's coordinates. */
@Suppress("MagicNumber") // The wait for the word layer is the test's parameter.
private fun ComposeTestRule.firstWordCentre(): Offset {
    waitUntil(timeoutMillis = 5_000) {
        onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .isNotEmpty()
    }
    val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
    val word = onAllNodes(hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        .onFirst()
        .fetchSemanticsNode()
        .boundsInRoot
    return word.center - page.topLeft
}
