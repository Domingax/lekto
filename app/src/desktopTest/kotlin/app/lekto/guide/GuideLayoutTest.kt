package app.lekto.guide

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.lekto.core.CefrLevel
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.ReadingState
import app.lekto.core.book.SampleBookRow
import app.lekto.core.language.sampleRosterLanguages
import app.lekto.core.sample.Sample
import app.lekto.dictionary.LOOKUP_PANEL_TAG
import app.lekto.library.LibraryScreen
import app.lekto.library.LibraryUiState
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.guideSample
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.text.appTextsFor
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Half a pixel of tolerance for the rounding a layout does at the screen's edge. */
private const val FIT_SLACK = 0.5f

/**
 * The guide at the sizes the ticket names (issue #277): a 320 dp phone, a 360 dp
 * phone and a 700 × 500 desktop window. Each screen keeps its title, its `Skip`
 * and its one action reachable, and the rows that do not fit scroll — the
 * language roster, the four notes, the six bands. This is layout alone; the copy
 * and the interactions are the semantics lanes' contract, and the planches record
 * the papers.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One scene per size and stage; the file is the guide's layout matrix.
class GuideLayoutTest {

    private val texts = appTextsFor("en")
    private val sample = guideSample()

    @Test
    fun aNarrowPhoneHoldsEveryScreen() = holdsEveryScreen(width = 320f, height = 640f)

    @Test
    fun aPhoneHoldsEveryScreen() = holdsEveryScreen(width = 360f, height = 640f)

    @Test
    fun aSmallDesktopWindowHoldsEveryScreen() = holdsEveryScreen(width = 700f, height = 500f)

    @Test
    fun theLevelScreenFitsAPhoneWithoutScrolling() = theLevelScreenFits(width = 360f, height = 640f)

    @Test
    fun theLevelScreenFitsANarrowPhoneWithoutScrolling() = theLevelScreenFits(width = 320f, height = 640f)

    @Test
    fun thePageBlockTakesTheFlexibleHeightAndTheActionSitsUnderTheNotes() = thePageRhythm(width = 360f, height = 640f)

    @Test
    fun theNarrowPhonePageBlockTakesTheFlexibleHeight() = thePageRhythm(width = 320f, height = 640f)

    @Test
    fun theLevelScreenBreathesWithoutScrolling() = theLevelRhythm(width = 360f, height = 640f)

    @Test
    fun theNarrowPhoneLevelScreenBreathesWithoutScrolling() = theLevelRhythm(width = 320f, height = 640f)

    @Test
    fun theArrivalSitsTheDownloadLineInsideTheShelfFrame() = theArrival(frameWidth = 360f, height = 640f)

    @Test
    fun theNarrowPhoneArrivalSitsTheDownloadLineInsideTheShelfFrame() = theArrival(frameWidth = 320f, height = 640f)

    @Test
    fun everyWaysForwardIsAWideButtonInsideTheScreenEdges() = wideWayForwardButton(width = 360f)

    /** The one filled action fills the screen's width inside the edge insets. */
    private fun wideWayForwardButton(width: Float) = screen(width = width, height = 640f, { LanguageStage() }) {
        val button = onNodeWithTag(GUIDE_CONTINUE_TAG).fetchSemanticsNode().boundsInRoot
        assertEquals(LektoSpacing.ScreenEdge.value, button.left, FIT_SLACK, "the button starts at the screen edge")
        assertEquals(
            width - LektoSpacing.ScreenEdge.value,
            button.right,
            FIT_SLACK,
            "the button fills the width inside the screen edge",
        )
    }

    /** Every part of the level screen — heading, preview, six bands and the action — is on screen. */
    private fun theLevelScreenFits(width: Float, height: Float) {
        screen(width, height, { LevelStage(sample) }) {
            val tags = listOf(GUIDE_TITLE_TAG, GUIDE_LEVEL_PREVIEW_TAG, GUIDE_START_READING_TAG) +
                CefrLevel.entries.map(::guideBandTag)
            tags.forEach { tag -> assertWithin(height, tag) }
        }
    }

    /**
     * The page beat's rhythm (issue #277's third review): the block takes the
     * flexible middle and is taller than the four notes under it, the notes sit one
     * row unit under it and the way forward one panel unit under the notes, all of
     * it inside the screen without a scroll.
     */
    private fun thePageRhythm(width: Float, height: Float) {
        screen(width, height, { PageStage(sample) }) {
            val block = onNodeWithTag(GUIDE_PAGE_TAG).fetchSemanticsNode().boundsInRoot
            val notes = onNodeWithTag(GUIDE_NOTES_TAG).fetchSemanticsNode().boundsInRoot
            val action = onNodeWithTag(GUIDE_CONTINUE_TAG).fetchSemanticsNode().boundsInRoot

            assertTrue(block.height > notes.height, "the page is the star: $block / $notes")
            assertEquals(
                LektoSpacing.BetweenRows.value,
                notes.top - block.bottom,
                FIT_SLACK,
                "the notes stay compact",
            )
            // The filled button carries Material's touch inset inside its node, so
            // the layout gap is at least the panel unit the screen asks for.
            assertTrue(
                action.top - notes.bottom >= LektoSpacing.InsidePanel.value - FIT_SLACK,
                "one panel under the notes: $notes / $action",
            )
            assertTrue(action.bottom <= height + FIT_SLACK, "the action anchors the screen: $action")
        }
    }

    /**
     * The level screen's rhythm (issue #277's third review): the preview, the six
     * bands and the action are separated by units of the scale, and the action still
     * ends inside a 640 dp screen — compact and breathing are not enemies.
     */
    private fun theLevelRhythm(width: Float, height: Float) {
        screen(width, height, { LevelStage(sample) }) {
            val preview = onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).fetchSemanticsNode().boundsInRoot
            val bands = onNodeWithTag(guideBandTag(CefrLevel.A1)).fetchSemanticsNode().boundsInRoot
            val lastBand = onNodeWithTag(guideBandTag(CefrLevel.C2)).fetchSemanticsNode().boundsInRoot
            val action = onNodeWithTag(GUIDE_START_READING_TAG).fetchSemanticsNode().boundsInRoot

            assertEquals(LektoSpacing.BetweenRows.value, bands.top - preview.bottom, FIT_SLACK, "the bands breathe")
            // The filled button carries Material's touch inset inside its node, so the
            // layout gap is at least the panel unit the screen asks for.
            assertTrue(
                action.top - lastBand.bottom >= LektoSpacing.InsidePanel.value - FIT_SLACK,
                "the action is a panel unit under the bands: $lastBand / $action",
            )
            assertTrue(
                action.bottom + LektoSpacing.InsideLabelPair.value <= height + FIT_SLACK,
                "the action keeps its foot off the edge: $action",
            )
        }
    }

    /**
     * The arrival (issue #277's third review): the download line is the shelf's own
     * last item, under the sample's lines, sharing the shelf's edges and one row
     * unit below them — not a strip pinned to the foot of the screen.
     */
    private fun theArrival(frameWidth: Float, height: Float) {
        screen(frameWidth, height, { ArrivalStage() }) {
            val line = onNodeWithTag(GUIDE_DOWNLOAD_TAG).fetchSemanticsNode().boundsInRoot
            val level = onNodeWithText("Your reading level is not set yet. Settings ▸ Languages.")
                .fetchSemanticsNode().boundsInRoot

            assertEquals(
                LektoSpacing.ScreenEdge.value,
                line.left,
                FIT_SLACK,
                "the line keeps the shelf's left edge",
            )
            assertEquals(
                frameWidth - LektoSpacing.ScreenEdge.value,
                line.right,
                FIT_SLACK,
                "and its right edge",
            )
            assertEquals(
                LektoSpacing.BetweenRows.value,
                line.top - level.bottom,
                FIT_SLACK,
                "one row unit under the sample",
            )
            assertTrue(line.bottom < height, "the line is part of the shelf, not pinned to its foot: $line")
        }
    }

    /** Asserts [tag] is displayed and its whole bounds lie inside a [height]-tall screen. */
    private fun ComposeUiTest.assertWithin(height: Float, tag: String) {
        onNodeWithTag(tag).assertIsDisplayed()
        val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= -FIT_SLACK && bounds.bottom <= height + FIT_SLACK, "$tag fits the screen: $bounds")
    }

    private fun holdsEveryScreen(width: Float, height: Float) {
        screen(width, height, { LanguageStage() }) {
            onNodeWithText("Which language would you like to read first?").assertIsDisplayed()
            onNodeWithTag(GUIDE_SKIP_TAG).assertIsDisplayed()
            onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG)
                .performScrollToNode(hasTestTag(guideLanguageRowTag("tr")))
            onNodeWithTag(guideLanguageRowTag("tr")).assertIsDisplayed()
            sampleRosterLanguages().forEach { offered ->
                onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG)
                    .performScrollToNode(hasTestTag(guideLanguageRowTag(offered.language)))
                onNodeWithTag(guideLanguageRowTag(offered.language)).assertIsNotSelected()
            }
        }
        screen(width, height, { PageStage(sample) }) {
            onNodeWithText("The more you know, the less colour.").assertIsDisplayed()
            onNodeWithTag(GUIDE_NOTES_TAG).assertIsDisplayed()
            onNodeWithTag(GUIDE_CONTINUE_TAG).assertIsDisplayed()
        }
        screen(width, height, { TapStage(sample) }) {
            onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
            onNodeWithTag(GUIDE_SKIP_TAG).assertIsDisplayed()
        }
        screen(width, height, { LevelStage(sample) }) {
            onNodeWithTag(guideBandTag(CefrLevel.C2)).performScrollTo().assertIsDisplayed()
            onNodeWithTag(GUIDE_START_READING_TAG).performScrollTo().assertIsDisplayed()
        }
        screen(width, height, { ArrivalStage() }) {
            onNodeWithText("A Guide Sample").performScrollTo().assertIsDisplayed()
            onNodeWithText("Installed").assertIsDisplayed()
        }
    }

    /** One size's scene, with the assertions the surface's own line makes. */
    private fun screen(
        width: Float,
        height: Float,
        content: @Composable () -> Unit,
        assertions: ComposeUiTest.() -> Unit,
    ) = runSkikoComposeUiTest(size = Size(width, height)) {
        setContent { CompositionLocalProvider(LocalAppTexts provides texts) { LektoTheme { content() } } }
        assertions()
    }

    @Composable
    private fun LanguageStage() {
        GuideScreen(
            state = GuideUiState(stage = GuideStage.LANGUAGE),
            languages = sampleRosterLanguages(),
            document = null,
        )
    }

    @Composable
    private fun PageStage(sample: Sample) {
        GuideScreen(
            state = GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            languages = sampleRosterLanguages(),
            document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, WhitespaceTextSegmenter()),
            notes = guideNotes(sample, GUIDE_DEFAULT_BAND, WhitespaceTextSegmenter(), "fr"),
            download = GuideDownloadUiState(GuideDownloadPhase.INSTALLED, "en"),
            translationLanguage = "fr",
        )
    }

    @Composable
    private fun TapStage(sample: Sample) {
        GuideScreen(
            state = GuideUiState(
                stage = GuideStage.TAP,
                sample = sample,
                plain = false,
                lookupOpen = true,
                tapped = guideNotes(sample, GUIDE_DEFAULT_BAND, WhitespaceTextSegmenter(), "fr").first(),
            ),
            languages = sampleRosterLanguages(),
            document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, WhitespaceTextSegmenter()),
            notes = guideNotes(sample, GUIDE_DEFAULT_BAND, WhitespaceTextSegmenter(), "fr"),
            translationLanguage = "fr",
        )
    }

    @Composable
    private fun LevelStage(sample: Sample) {
        GuideScreen(
            state = GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
            languages = sampleRosterLanguages(),
            document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, WhitespaceTextSegmenter()),
            download = GuideDownloadUiState(GuideDownloadPhase.INSTALLED, "en"),
            translationLanguage = "fr",
        )
    }

    @Composable
    private fun ArrivalStage() {
        LibraryScreen(
            state = LibraryUiState(sample = sampleBookRow()),
            levelLine = AppMessage.of(TextKey.GUIDE_LEVEL_NOT_SET),
            footer = {
                GuideShelfFooter(
                    GuideDownloadUiState(GuideDownloadPhase.INSTALLED, "en"),
                    GuideDownloadActions(onStop = {}),
                )
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun sampleBookRow(): SampleBookRow = SampleBookRow(
        book = Book(
            id = Sample.identityOf("en"),
            title = "A Guide Sample",
            language = "en",
            format = BookFormat.TXT,
            fileName = "sample-en.txt",
        ),
        progress = BookProgress.NotStarted,
        state = ReadingState.UNREAD,
    )
}
