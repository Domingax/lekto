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
import androidx.compose.material3.ColorScheme
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
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.WordToken
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.theme.Dark
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The reader's visual anchor in the screenshot lane, one sheet per **App
 * theme** (issue #183).
 *
 * It is deliberately text-free, like the theme goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. Instead it records
 * each paper's mastery ramp and a page's word layer as coloured blocks: the
 * palette row is the levels in order, and the rows below are the **real** words
 * of [SampleChapter], coloured by the real lookup — so a change to tokenisation
 * or mastery colouring moves this golden, and the page's block layout catches a
 * layout regression the palette alone would miss. Record with
 * `./gradlew :app:recordRoborazziDesktop`, verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class MasteryPaletteGoldenTest {

    @Test
    fun lightPaletteAndWordLayer() = paletteGolden("light", Light)

    @Test
    fun darkPaletteAndWordLayer() = paletteGolden("dark", Dark)

    @Test
    fun sepiaPaletteAndWordLayer() = paletteGolden("sepia", Sepia)

    private fun paletteGolden(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent { PalettePreview(theme) }
        onRoot().captureRoboImage("mastery-palette-$name.png")
    }
}

private const val WORDS_PER_ROW = 8

@Composable
private fun PalettePreview(theme: ColorScheme) {
    val styles = remember(theme) {
        ReaderStyles.of(ReadingPreferences.DEFAULT, MasteryPalette.of(theme), theme.onBackground)
    }
    val tokens = remember(styles) {
        buildReaderTokens(
            chapter = SampleChapter.chapter,
            renderer = ReaderRenderer(WhitespaceTextSegmenter(), SampleChapter.mastery),
            styles = styles,
        )
    }
    val words = tokens.words.take(WORDS_PER_ROW * 3)

    Column(modifier = Modifier.size(width = 280.dp, height = 200.dp).background(theme.background)) {
        Row(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            MasteryLevel.entries.forEach { level ->
                Box(modifier = Modifier.fillMaxHeight().weight(1f).background(level.swatch(styles)))
            }
        }
        words.chunked(WORDS_PER_ROW).forEach { row ->
            WordRow(row, styles)
        }
    }
}

@Composable
private fun WordRow(words: List<WordToken>, styles: ReaderStyles) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        words.forEach { word ->
            val level = SampleChapter.mastery.levelOf(word.key)
            Box(modifier = Modifier.width(wordWidth(word.surface)).height(14.dp).background(level.swatch(styles)))
            Box(modifier = Modifier.width(5.dp).height(14.dp))
        }
    }
}

/** A level's colour, with a known word drawn in the paper's normal ink rather than transparent. */
private fun MasteryLevel.swatch(styles: ReaderStyles): Color = readerColorOr(styles.body.color, styles.mastery)

/** A box roughly proportional to the word, clamped so a long word still fits. */
private fun wordWidth(word: String): Dp = (word.length * 5).coerceIn(10, 46).dp
