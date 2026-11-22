@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")
// The preview lays out each screen's frame in design tokens and fixed heights.

package app.lekto.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.sample.Sample
import app.lekto.reader.MasteryPalette
import app.lekto.reader.readerColor
import app.lekto.testkit.guideSample
import app.lekto.theme.Dark
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The first-launch guide's visual anatomy in the screenshot lane, one sheet per
 * screen per **App theme** (issue #277; docs/design.md §7.8).
 *
 * It is deliberately text-free, like every other golden: glyphs are rendered with
 * the host's fonts and would not verify on another machine. What it records is the
 * frame — the five step marks and `Skip`, each screen's title and supporting line,
 * the eight language rows, the page's coloured word bars and the four notes, the
 * real sheet's cap and handle, the six band rows with their chosen wash, and the
 * arrival's sample row and download line. The words, the levels and the meanings
 * are the semantics suites' contract; the layout, the colour ramp and the spacing
 * are this file's.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class GuideGoldenTest {

    @Test
    fun lightLanguage() = golden("language-light", Light) { LanguagePreview() }

    @Test
    fun darkLanguage() = golden("language-dark", Dark) { LanguagePreview() }

    @Test
    fun sepiaLanguage() = golden("language-sepia", Sepia) { LanguagePreview() }

    @Test
    fun lightPage() = golden("page-light", Light) { PagePreview(coloured = true) }

    @Test
    fun darkPage() = golden("page-dark", Dark) { PagePreview(coloured = true) }

    @Test
    fun sepiaPage() = golden("page-sepia", Sepia) { PagePreview(coloured = true) }

    @Test
    fun lightTap() = golden("tap-light", Light) { TapPreview() }

    @Test
    fun darkTap() = golden("tap-dark", Dark) { TapPreview() }

    @Test
    fun sepiaTap() = golden("tap-sepia", Sepia) { TapPreview() }

    @Test
    fun lightLevel() = golden("level-light", Light) { LevelPreview() }

    @Test
    fun darkLevel() = golden("level-dark", Dark) { LevelPreview() }

    @Test
    fun sepiaLevel() = golden("level-sepia", Sepia) { LevelPreview() }

    @Test
    fun lightArrival() = golden("arrival-light", Light) { ArrivalPreview() }

    @Test
    fun darkArrival() = golden("arrival-dark", Dark) { ArrivalPreview() }

    @Test
    fun sepiaArrival() = golden("arrival-sepia", Sepia) { ArrivalPreview() }

    private fun golden(name: String, theme: ColorScheme, screen: @Composable () -> Unit) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { ScreenFrame(screen) } }
        onRoot().captureRoboImage("guide-$name.png")
    }
}

private val SCREEN_WIDTH = 360.dp
private val SCREEN_HEIGHT = 640.dp

/** The phone frame every sheet draws. */
@Composable
private fun ScreenFrame(screen: @Composable () -> Unit) {
    Column(
        Modifier
            .width(SCREEN_WIDTH)
            .height(SCREEN_HEIGHT)
            .background(MaterialTheme.colorScheme.background),
    ) {
        GuideBarPreview()
        screen()
    }
}

/** The five step marks and `Skip` at the trailing edge. */
@Composable
private fun GuideBarPreview() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.ScreenEdge, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            Spacer(
                Modifier
                    .padding(end = LektoSpacing.InsideLabelPair)
                    .width(18.dp)
                    .height(3.dp)
                    .background(
                        if (index < 2) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
        Bar(MaterialTheme.colorScheme.onBackground, 32.dp, 10.dp)
    }
}

/** Screen 1: the title, its supporting lines, the eight languages and the footnote. */
@Composable
private fun LanguagePreview() {
    Column(Modifier.padding(horizontal = LektoSpacing.ScreenEdge)) {
        Heading(112.dp, 188.dp, 156.dp)
        Spacer(Modifier.height(LektoSpacing.BetweenBlocks))
        repeat(8) { index ->
            LanguageRowPreview(index == 0)
            if (index != 7) Hairline()
        }
        Spacer(Modifier.height(LektoSpacing.InsidePanel))
        Bar(MaterialTheme.colorScheme.onSurfaceVariant, 176.dp, 9.dp)
    }
}

/** One language row: its code, its name and the chevron. */
@Composable
private fun LanguageRowPreview(first: Boolean) {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = LektoSpacing.BetweenRows),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Bar(theme.onSurfaceVariant, 22.dp, 9.dp)
        Bar(theme.onBackground, if (first) 68.dp else 58.dp, 13.dp)
        Spacer(Modifier.weight(1f))
        Icon(LektoIcons.ChevronRight, null, tint = theme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/** Screen 2: the page in two beats; the coloured one carries the four notes. */
@Composable
private fun PagePreview(coloured: Boolean) {
    val theme = MaterialTheme.colorScheme
    val sample = guideSample()
    Column(Modifier.padding(horizontal = LektoSpacing.ScreenEdge)) {
        Heading(188.dp, 148.dp, 118.dp)
        Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
        Bar(theme.onSurfaceVariant, 84.dp, 9.dp)
        Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
        DownloadLine(installed = false)
        Spacer(Modifier.height(LektoSpacing.BetweenRows))
        PageCard(sample, coloured)
        if (coloured) {
            Spacer(Modifier.height(LektoSpacing.BetweenRows))
            NoteRows(sample)
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Bar(theme.onBackground, 54.dp, 11.dp)
        }
    }
}

/** The page's frame as coloured word bars: a heading bar and four lines of words. */
@Composable
private fun PageCard(sample: Sample, coloured: Boolean) {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    Column(Modifier.fillMaxWidth().height(220.dp).padding(vertical = LektoSpacing.InsidePanel)) {
        Bar(theme.onSurfaceVariant, 72.dp, 9.dp)
        Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
        repeat(4) { line ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(if (line == 3) 3 else 6) { word ->
                    val level = if (coloured) colourAt(line, word) else null
                    val colour = level?.readerColor(ramp) ?: theme.onBackground
                    Bar(colour, if (word % 4 == 0) 34.dp else 26.dp, 12.dp)
                }
            }
        }
    }
}

/** The four notes: the level's coloured frame, the word, the level's name and the meaning. */
@Composable
private fun NoteRows(sample: Sample) {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    val levels = listOf(
        MasteryLevel.UNKNOWN,
        MasteryLevel.RECOGNIZED,
        MasteryLevel.FAMILIAR,
        MasteryLevel.LEARNED,
    )
    Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows)) {
        levels.forEach { level ->
            Row(horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
                Box(
                    Modifier.size(28.dp).background(level.readerColor(ramp), LektoRadii.Control),
                    contentAlignment = Alignment.Center,
                ) { Bar(theme.background, 6.dp, 8.dp) }
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
                        Bar(theme.onBackground, 52.dp, 11.dp)
                        Bar(theme.onSurfaceVariant, 34.dp, 8.dp)
                    }
                    Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
                    Bar(theme.onSurfaceVariant, 74.dp, 9.dp)
                }
            }
        }
    }
}

/** Screen 3: the page, with the real sheet raised at its cap over it. */
@Composable
private fun TapPreview() {
    val theme = MaterialTheme.colorScheme
    val sample = guideSample()
    Box(Modifier.fillMaxWidth().height(560.dp)) {
        Column(Modifier.padding(horizontal = LektoSpacing.ScreenEdge)) {
            Heading(148.dp, 208.dp, 160.dp)
            Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
            PageCard(sample, coloured = true)
        }
        SheetPreview()
    }
}

/** The bottom sheet's frame: the handle, the headword, the context, the senses and the footer. */
@Composable
private fun BoxScope.SheetPreview() {
    val theme = MaterialTheme.colorScheme
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(336.dp)
            .background(theme.surface, LektoRadii.Sheet),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Spacer(Modifier.width(32.dp).height(4.dp).background(theme.outline, LektoRadii.Control))
        }
        Column(Modifier.padding(horizontal = LektoSpacing.InsidePanel)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bar(theme.onSurface, 96.dp, 24.dp)
                Spacer(Modifier.weight(1f))
                Bar(theme.onSurfaceVariant, 84.dp, 10.dp)
            }
            Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
            Bar(theme.onSurfaceVariant, 140.dp, 10.dp)
            Spacer(Modifier.height(LektoSpacing.BetweenRows))
            Hairline()
            Spacer(Modifier.height(LektoSpacing.BetweenRows))
            Bar(theme.onSurface, 228.dp, 14.dp)
            Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
            Bar(theme.onSurfaceVariant, 64.dp, 10.dp)
            Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
            Bar(theme.onSurfaceVariant, 220.dp, 10.dp)
        }
        Spacer(Modifier.weight(1f))
        Hairline()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.BetweenRows),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bar(theme.onSurfaceVariant, 64.dp, 10.dp)
            Spacer(Modifier.weight(1f))
            repeat(5) { index -> ChipPreview(filled = index == 0, ringed = index == 2) }
        }
    }
}

/** Screen 4: the preview card and the six bands, the chosen one washed and ruled. */
@Composable
private fun LevelPreview() {
    val theme = MaterialTheme.colorScheme
    val sample = guideSample()
    Column(Modifier.padding(horizontal = LektoSpacing.ScreenEdge)) {
        Heading(176.dp, 128.dp, 108.dp)
        Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
        Bar(theme.onSurfaceVariant, 84.dp, 9.dp)
        Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(LektoSpacing.InsidePanel)
                .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Surface),
        ) {
            Bar(theme.onSurfaceVariant, 118.dp, 9.dp)
            Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
            PageCard(sample, coloured = true)
        }
        Spacer(Modifier.height(LektoSpacing.BetweenRows))
        repeat(6) { index ->
            BandRowPreview(index == 2)
            if (index != 5) Hairline()
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth().height(48.dp).background(theme.primary, LektoRadii.Control),
            contentAlignment = Alignment.Center,
        ) { Bar(theme.onPrimary, 88.dp, 11.dp) }
        Spacer(Modifier.height(LektoSpacing.InsidePanel))
    }
}

/** One band row: its code, its name, its one line and the ink rule when chosen. */
@Composable
private fun BandRowPreview(chosen: Boolean) {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (chosen) theme.surfaceVariant else Color.Transparent)
            .padding(vertical = LektoSpacing.BetweenRelatedLines),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier.width(2.dp).height(24.dp).background(if (chosen) theme.onBackground else Color.Transparent),
        )
        Bar(theme.onSurfaceVariant, 20.dp, 9.dp, Modifier.padding(start = LektoSpacing.BetweenRelatedLines))
        Spacer(Modifier.width(LektoSpacing.BetweenRelatedLines))
        Bar(theme.onSurface, 86.dp, 13.dp)
        Spacer(Modifier.weight(1f))
        Bar(theme.onSurfaceVariant, 62.dp, 9.dp)
    }
}

/** Screen 5: the shelf with the sample as its first row, the level line and the download line. */
@Composable
private fun ArrivalPreview() {
    val theme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = LektoSpacing.ScreenEdge)) {
        Row(Modifier.fillMaxWidth().padding(vertical = LektoSpacing.InsidePanel), verticalAlignment = Alignment.CenterVertically) {
            Bar(theme.onBackground, 112.dp, 22.dp)
            Spacer(Modifier.weight(1f))
            Bar(theme.onBackground, 48.dp, 11.dp)
        }
        Bar(theme.onSurfaceVariant, 196.dp, 11.dp)
        Spacer(Modifier.height(LektoSpacing.BetweenBlocks))
        Row(horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
            Box(
                Modifier.width(44.dp).height(60.dp).border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Control),
                contentAlignment = Alignment.Center,
            ) { Bar(theme.onSurfaceVariant, 18.dp, 8.dp) }
            Column(Modifier.padding(top = 2.dp)) {
                Bar(theme.onBackground, 112.dp, 14.dp)
                Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
                Bar(theme.onSurfaceVariant, 120.dp, 10.dp)
                Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
                Box(
                    Modifier
                        .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Control)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) { Bar(theme.onSurfaceVariant, 40.dp, 8.dp) }
            }
        }
        Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
        Bar(theme.onSurfaceVariant, 212.dp, 9.dp)
        Spacer(Modifier.weight(1f))
        Hairline()
        Row(Modifier.fillMaxWidth().padding(vertical = LektoSpacing.InsidePanel), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Bar(theme.onSurface, 132.dp, 10.dp)
                Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
                Bar(theme.onSurfaceVariant, 52.dp, 9.dp)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The download line: its two bars and, while running, the `Stop` word at the edge. */
@Composable
private fun DownloadLine(installed: Boolean) {
    val theme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Bar(theme.onSurface, 128.dp, 10.dp)
            Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
            Bar(theme.onSurfaceVariant, if (installed) 52.dp else 116.dp, 9.dp)
        }
        Spacer(Modifier.weight(1f))
        if (!installed) Bar(theme.onSurface, 30.dp, 10.dp)
    }
}

/** A heading: two title lines and one supporting line, as bars. */
@Composable
private fun Heading(first: Dp, second: Dp, intro: Dp) {
    val theme = MaterialTheme.colorScheme
    Column(Modifier.padding(top = LektoSpacing.InsidePanel)) {
        Bar(theme.onBackground, first, 22.dp)
        Spacer(Modifier.height(6.dp))
        Bar(theme.onBackground, second, 22.dp)
        Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
        Bar(theme.onSurfaceVariant, intro, 11.dp)
    }
}

/** The one mastery chip frame: filled in a level's colour, ringed when selected. */
@Composable
private fun ChipPreview(filled: Boolean, ringed: Boolean) {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    val colour = if (filled) MasteryLevel.FAMILIAR.readerColor(ramp) else theme.outline
    Box(
        Modifier
            .size(32.dp)
            .background(if (filled) colour else Color.Transparent, LektoRadii.Control)
            .border(if (filled) 0.dp else 2.dp, colour, LektoRadii.Control),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) Bar(theme.background, 6.dp, 8.dp)
        if (ringed) Box(Modifier.size(24.dp).border(1.dp, theme.onBackground, LektoRadii.Control))
    }
}

/** The four words the page colours, one per level, in reading order. */
private fun colourAt(line: Int, word: Int): MasteryLevel? = when {
    line == 0 && word == 1 -> MasteryLevel.RECOGNIZED
    line == 1 && word == 3 -> MasteryLevel.UNKNOWN
    line == 2 && word == 0 -> MasteryLevel.LEARNED
    line == 2 && word == 4 -> MasteryLevel.FAMILIAR
    else -> null
}

/** A bar where a line of text would be. */
@Composable
private fun Bar(colour: Color, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).background(colour))
}

/** The 1 dp hairline the screens draw between rows. */
@Composable
private fun Hairline() {
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}
