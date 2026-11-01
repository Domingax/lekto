package app.lekto.reader

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.lekto.core.reading.ReadingPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lift under the lookup sheet (issue #22, docs/design.md §7.2): the page rises
 * by just enough for the touched word to clear the sheet, and not at all when the
 * word already sits above it. A word low on the page used to be found by its link
 * node; words carry no link now (ADR-0007), so the rule is asserted directly on
 * the geometry it uses.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The viewport, the line index and the sheet's height are the experiment's parameters.
class PageLiftTest {

    private val styles = ReaderStyles.of(ReadingPreferences.DEFAULT, mastery = MasteryPalette.Light)

    private val passage = buildString {
        repeat(40) { append("the quick brown fox jumps over the lazy dog and then keeps going. ") }
    }

    @Test
    fun aWordUnderTheSheetLiftsThePageAndAWordAboveItDoesNot() = runComposeUiTest {
        lateinit var layout: TextLayoutResult
        var measuredWidth = 0

        setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            measuredWidth = with(density) { 320.dp.roundToPx() }
            layout = measurer.measure(
                text = AnnotatedString(passage),
                style = styles.body,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = measuredWidth),
            )
        }
        waitForIdle()

        val page = ReaderPage(0, passage.length)
        val gap = 12f
        // The sheet's top edge sits just above the last line, so a word there is
        // under it and a word on the first line is not.
        val lastLine = layout.lineCount - 1
        val lastChar = layout.getLineStart(lastLine)
        val sheetTop = layout.getLineTop(lastLine) - gap / 2f

        val lowLift = pageLift(layout, page, lastChar..lastChar + 1, sheetTop, gap)
        val highLift = pageLift(layout, page, 0..1, sheetTop, gap)

        assertTrue(lowLift > 0f, "a word under the sheet must lift the page")
        val liftedBottom = layout.getBoundingBox(lastChar).bottom - lowLift
        assertEquals(
            sheetTop - gap,
            liftedBottom,
            0.5f,
            "the lifted word ends one gap above the sheet's edge, and no higher",
        )
        assertEquals(0f, highLift, "a word above the sheet must not move the page")
    }

    @Test
    fun aPageWithNoSelectionDoesNotMove() = runComposeUiTest {
        lateinit var layout: TextLayoutResult
        setContent {
            val measurer = rememberTextMeasurer()
            layout = measurer.measure(AnnotatedString(passage), style = styles.body)
        }
        waitForIdle()

        assertEquals(0f, pageLift(layout, ReaderPage(0, passage.length), null, visibleBottomPx = 100f, gapPx = 8f))
    }
}
