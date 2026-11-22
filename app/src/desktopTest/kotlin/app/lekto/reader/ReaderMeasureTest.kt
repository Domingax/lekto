package app.lekto.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.reading.ReadingPreferences
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The measure (docs/design.md §3, issue #281): at a wide window the page's text
 * column stops at 34 em — about 68 characters at the default 18 sp — and the
 * width the cap takes back becomes margin, centred on the page. The cap belongs
 * to the paginator's content width, so page breaks and **Reading progress** stay
 * exact; centring only moves the column.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The viewports and the 18 sp the em is measured in are the test's parameters.
class ReaderMeasureTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    @Test
    fun theColumnIsCappedAndCentredAtElevenHundred() = cappedColumn(width = 1100.dp)

    @Test
    fun theColumnIsStillCappedAndCentredAtFourteenForty() = cappedColumn(width = 1440.dp)

    /** The page in a [width]-wide window: 34 em at the default 18 sp is 612 dp of text. */
    private fun cappedColumn(width: Dp) = runSkikoComposeUiTest(size = Size(width.value, WINDOW_HEIGHT.value)) {
        setContent {
            MaterialTheme {
                ReaderScreen(
                    document = ReaderDocument(SampleChapter.chapter, renderer),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val page = onNodeWithTag(READER_PAGE_TAG).getUnclippedBoundsInRoot()
        val text = onNodeWithTag(READER_PAGE_TEXT_TAG).getUnclippedBoundsInRoot()

        assertTrue(text.right - text.left <= MAX_MEASURE + 1.dp, "the text column never passes 34 em: ${text.right - text.left}")
        assertEquals(
            (text.left - page.left).value,
            (page.right - text.right).value,
            1f,
            "the column is centred, its margins equal",
        )
    }

    /**
     * The measure as the paginator sees it: one capped column at both wide
     * windows, so a line stays inside the comfortable band and a page breaks in
     * the same place — the **Reading progress** with it — however wide the window
     * grows (docs/design.md §3, §7.2).
     */
    @Test
    fun aWideWindowKeepsTheLineInsideSixtyEightCharacters() = runComposeUiTest {
        val widths = listOf(1100.dp, 1440.dp)
        val columns = mutableMapOf<Dp, Int>()
        val capBinds = mutableMapOf<Dp, Boolean>()
        val longestLines = mutableMapOf<Dp, Int>()
        val pagesByWidth = mutableMapOf<Dp, List<ReaderPage>>()

        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val height = with(density) { WINDOW_HEIGHT.roundToPx() }
            val text = AnnotatedString(passage)
            widths.forEach { pageWidth ->
                val column = styles.contentWidthPx(pageWidth, density)
                val uncapped = with(density) { (pageWidth - styles.horizontalMargin * 2).roundToPx() }
                val pages = paginateChapter(text, ReaderLayout(measurer, styles.body, column, height)).toList()
                val first = pages.first()
                val page = measurer.measure(
                    text = text.subSequence(first.start, first.end),
                    style = styles.body,
                    constraints = Constraints(maxWidth = column),
                )
                columns[pageWidth] = column
                capBinds[pageWidth] = column < uncapped
                pagesByWidth[pageWidth] = pages
                longestLines[pageWidth] = (0 until page.lineCount).maxOf { line -> visibleCharacters(page, line) }
            }
        }
        waitForIdle()

        widths.forEach { pageWidth ->
            assertTrue(capBinds.getValue(pageWidth), "34 em must cap the page at $pageWidth")
            assertTrue(
                longestLines.getValue(pageWidth) in COMFORTABLE_LINE..CHARACTERS_PER_LINE,
                "$pageWidth holds ${longestLines.getValue(pageWidth)} characters on a line, " +
                    "outside $COMFORTABLE_LINE..$CHARACTERS_PER_LINE",
            )
        }
        assertEquals(
            pagesByWidth.getValue(1100.dp),
            pagesByWidth.getValue(1440.dp),
            "one capped column: the window's extra width is margin, so no page break and no percentage moves",
        )
    }

    /** The characters a line shows; its trailing whitespace is the wrap, not a glyph. */
    private fun visibleCharacters(layout: TextLayoutResult, line: Int): Int =
        layout.layoutInput.text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd().length

    private val passage = buildString {
        repeat(120) { append("the quick brown fox jumps over the lazy dog and then keeps going. ") }
    }

    private val styles = ReaderStyles.of(ReadingPreferences.DEFAULT, mastery = MasteryPalette.Light)

    /** 34 em at the default 18 sp. */
    private val MAX_MEASURE: Dp = (MAX_MEASURE_EM * 18).dp

    /** Tall enough for a page and both bands at the wide shape. */
    private val WINDOW_HEIGHT = 720.dp

    private companion object {
        /** The comfortable band's floor, from the ticket's 45–75. */
        const val COMFORTABLE_LINE = 45

        /** The most characters a line may show at 34 em, the top of the comfortable band. */
        const val CHARACTERS_PER_LINE = 68
    }
}
