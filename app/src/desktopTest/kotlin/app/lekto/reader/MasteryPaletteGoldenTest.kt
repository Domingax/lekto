@file:Suppress("MagicNumber") // The preview's layout is a grid of fixed dimensions.

package app.lekto.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordToken
import app.lekto.testkit.WhitespaceTextSegmenter
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The reader's visual anchor in the screenshot lane.
 *
 * It is deliberately text-free, like the theme golden: glyphs are rendered with
 * the host's fonts and would not verify on another machine. Instead it records
 * the mastery palette and a page's word layer as coloured blocks: the palette row
 * is the levels in order, and the rows below are the **real** words of
 * [SampleChapter], coloured by the real lookup — so a change to tokenisation or
 * mastery colouring moves this golden, and the page's block layout catches a
 * layout regression the palette alone would miss. Record with
 * `./gradlew :app:recordRoborazziDesktop`, verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class MasteryPaletteGoldenTest {

    @Test
    fun masteryPaletteAndWordLayer() = runComposeUiTest {
        setContent { PalettePreview() }
        onRoot().captureRoboImage("mastery-palette.png")
    }
}

private const val WORDS_PER_ROW = 8

@Composable
private fun PalettePreview() {
    val tokens = remember {
        buildReaderTokens(
            chapter = SampleChapter.chapter,
            renderer = ReaderRenderer(WhitespaceTextSegmenter(), SampleChapter.mastery),
        )
    }
    val words = tokens.words.take(WORDS_PER_ROW * 3)

    Column(modifier = Modifier.size(width = 280.dp, height = 200.dp).background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            MasteryLevel.entries.forEach { level ->
                Box(modifier = Modifier.fillMaxHeight().weight(1f).background(level.swatch()))
            }
        }
        words.chunked(WORDS_PER_ROW).forEach { row ->
            WordRow(row)
        }
    }
}

@Composable
private fun WordRow(words: List<WordToken>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        words.forEach { word ->
            val level = SampleChapter.mastery.levelOf(word.surface, SampleChapter.LANGUAGE)
            Box(modifier = Modifier.width(wordWidth(word.surface)).height(14.dp).background(level.swatch()))
            Box(modifier = Modifier.width(5.dp).height(14.dp))
        }
    }
}

/** A level's colour, with a known word drawn in normal ink rather than transparent. */
private fun MasteryLevel.swatch(): Color = readerColorOr(ReaderStyles.Reading.body.color)

/** A box roughly proportional to the word, clamped so a long word still fits. */
private fun wordWidth(word: String): Dp = (word.length * 5).coerceIn(10, 46).dp
