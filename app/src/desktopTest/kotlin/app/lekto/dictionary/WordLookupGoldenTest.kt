@file:Suppress("MagicNumber") // The preview lays out the sheet's fixed bands, chips and bars as a planche does.

package app.lekto.dictionary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.reader.MasteryPalette
import app.lekto.reader.selectionHighlight
import app.lekto.theme.Dark
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.theme.contentColorOn
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Volume2
import app.lekto.ui.icons.X
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The lookup sheet in the screenshot lane (issue #210; docs/design.md §7.3): the
 * sheet raised over a page, one sheet per **App theme**, plus the offline state
 * with no pack installed.
 *
 * It is deliberately text-free, like the other goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. Instead it records
 * the sheet's frame — the top corners at the sheet radius, the surface tone and
 * the hairline, the drag handle, the word and its muted meta line, the two real
 * icons, the numbered senses with their translations, the level named beside the
 * five chips, and the references as one muted line — over a page that stays
 * visible above the 45 % cap. The page's washed word shows why the reader keeps
 * their place. The semantics suite proves the text and the interactions.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class WordLookupGoldenTest {

    @Test
    fun lightSheet() = sheetGolden("light", Light)

    @Test
    fun darkSheet() = sheetGolden("dark", Dark)

    @Test
    fun sepiaSheet() = sheetGolden("sepia", Sepia)

    @Test
    fun offlineSheet() = runComposeUiTest {
        setContent { LektoTheme(theme = Light) { LookupPreview(offline = true) } }
        onRoot().captureRoboImage("word-lookup-offline-light.png")
    }

    private fun sheetGolden(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { LookupPreview(offline = false) } }
        onRoot().captureRoboImage("word-lookup-$name.png")
    }
}

/** The preview's window, the size the planche draws a phone at. */
private val SHEET_WIDTH = 360.dp
private val SHEET_HEIGHT = 640.dp

/** The most of the screen the sheet may cover, as the panel enforces it. */
private val SHEET_MAX_HEIGHT = SHEET_HEIGHT * SHEET_MAX_HEIGHT_FRACTION

/** The drag handle's bar. */
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp

/** A chip's visible frame inside its touch target. */
private val CHIP_WIDTH = 32.dp
private val CHIP_HEIGHT = 28.dp

/** A bar where a line of text would be: its height and the share of the width it takes. */
private data class LineBar(val height: Dp, val share: Float)

private val WORD_BAR = LineBar(height = 24.dp, share = 0.2f)
private val META_BAR = LineBar(height = 10.dp, share = 0.24f)
private val SENSE_NUMBER_BAR = LineBar(height = 9.dp, share = 0.03f)
private val SENSE_BAR = LineBar(height = 10.dp, share = 0.82f)
private val TRANSLATION_BAR = LineBar(height = 9.dp, share = 0.36f)
private val LEVEL_LABEL_BAR = LineBar(height = 8.dp, share = 0.1f)
private val LEVEL_NAME_BAR = LineBar(height = 12.dp, share = 0.18f)
private val CHIP_LABEL_BAR = LineBar(height = 9.dp, share = 0.4f)
private val REFERENCE_BARS = listOf(58.dp, 30.dp, 30.dp, 76.dp)

/** The page's lines above the sheet, with a shorter one where a paragraph ends. */
private val PAGE_LINE_SHARES = listOf(0.94f, 0.9f, 0.52f, 0.96f, 0.88f, 0.94f, 0.62f, 0.9f, 0.48f)

/**
 * The page, the washed word and the sheet raised over it. The page keeps its
 * full height behind the sheet; the sheet covers at most [SHEET_MAX_HEIGHT].
 */
@Composable
private fun LookupPreview(offline: Boolean) {
    val theme = MaterialTheme.colorScheme
    Box(Modifier.size(width = SHEET_WIDTH, height = SHEET_HEIGHT).background(theme.background)) {
        PageBehind()
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(SHEET_MAX_HEIGHT),
            shape = LektoRadii.SheetTop,
            color = theme.surface,
            border = BorderStroke(1.dp, theme.outline),
        ) {
            Column(Modifier.fillMaxWidth()) {
                DragHandle()
                SheetHeader()
                HorizontalDivider(color = theme.outline)
                if (offline) OfflineBody() else SensesBody()
                SheetFooter()
            }
        }
    }
}

/** The page above the sheet: the reading margin, the line rhythm and one washed word. */
@Composable
private fun PageBehind() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = LektoSpacing.ScreenEdge,
            vertical = LektoSpacing.InsidePanel,
        ),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        PAGE_LINE_SHARES.forEachIndexed { index, share ->
            if (index == 2) WashedLine(share) else Line(theme.onBackground, LineBar(height = 10.dp, share = share))
        }
    }
}

/** The tapped word's line: the bar sits inside the ink wash that keeps it found on the page. */
@Composable
private fun WashedLine(share: Float) {
    val theme = MaterialTheme.colorScheme
    Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Line(theme.onBackground, LineBar(height = 10.dp, share = share))
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.width(62.dp).height(18.dp).background(selectionHighlight(theme.onBackground), LektoRadii.Control),
        ) {
            Box(Modifier.padding(3.dp).fillMaxWidth().height(10.dp).background(theme.onBackground, LektoRadii.Control))
        }
    }
}

/** The grabber that says the sheet was raised from the bottom edge. */
@Composable
private fun DragHandle() {
    val theme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines), contentAlignment = Alignment.Center) {
        Box(Modifier.width(HANDLE_WIDTH).height(HANDLE_HEIGHT).background(theme.outline, CircleShape))
    }
}

/** The word, its meta line in the muted ink, and the speaker and close in their 48 dp controls. */
@Composable
private fun SheetHeader() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = LektoSpacing.InsidePanel,
            top = LektoSpacing.InsideLabelPair,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(bottom = LektoSpacing.BetweenRelatedLines)) {
            Line(theme.onBackground, WORD_BAR)
            Spacer(Modifier.height(6.dp))
            Line(theme.onSurfaceVariant, META_BAR)
        }
        BandIcon(imageVector = LektoIcons.Volume2, tint = theme.onBackground)
        BandIcon(imageVector = LektoIcons.X, tint = theme.onBackground)
    }
}

/** An icon in its 48 dp control, centred as the sheet's icon buttons centre theirs. */
@Composable
private fun BandIcon(imageVector: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
        Icon(imageVector = imageVector, contentDescription = null, tint = tint)
    }
}

/** Three numbered senses, each with its translation under it in the muted ink. */
@Composable
private fun ColumnScope.SensesBody() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.BetweenRows),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        repeat(3) { SenseRow() }
        Line(theme.onSurfaceVariant, LineBar(height = 9.dp, share = 0.08f))
    }
}

/** One sense: its number, its definition and the translation under it. */
@Composable
private fun SenseRow() {
    val theme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
        Line(theme.onSurfaceVariant, SENSE_NUMBER_BAR)
        Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
            Line(theme.onBackground, SENSE_BAR)
            Line(theme.onSurfaceVariant, TRANSLATION_BAR)
        }
    }
}

/** The no-pack state: one honest sentence bar where the senses would be. */
@Composable
private fun ColumnScope.OfflineBody() {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.BetweenRows),
    ) {
        Line(theme.onBackground, LineBar(height = 10.dp, share = 0.62f))
    }
}

/** The level named beside the five chips, then the references as one muted line. */
@Composable
private fun SheetFooter() {
    val theme = MaterialTheme.colorScheme
    HorizontalDivider(color = theme.outline)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.InsideLabelPair),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Line(theme.onSurfaceVariant, LEVEL_LABEL_BAR)
            Spacer(Modifier.height(4.dp))
            Line(theme.onBackground, LEVEL_NAME_BAR)
        }
        MasteryChips()
    }
    HorizontalDivider(color = theme.outline)
    ReferenceLine()
}

/** The five chip frames, the selected one filled with its level's colour. */
@Composable
private fun MasteryChips() {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    val levels = listOf(ramp.unknown, ramp.recognized, ramp.familiar, ramp.learned, null)
    Row {
        levels.forEachIndexed { index, colour ->
            val selected = index == 1
            val fill = colour ?: theme.outline
            Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(CHIP_WIDTH)
                        .height(CHIP_HEIGHT)
                        .background(if (selected) fill else theme.background, LektoRadii.Control)
                        .border(2.dp, fill, LektoRadii.Control),
                    contentAlignment = Alignment.Center,
                ) {
                    Line(if (selected) contentColorOn(fill) else theme.onSurface, CHIP_LABEL_BAR)
                }
            }
        }
    }
}

/** The external references as one muted line: four names and the dots between them. */
@Composable
private fun ReferenceLine() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().height(
            LektoSpacing.TouchTarget,
        ).padding(horizontal = LektoSpacing.InsideLabelPair),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        REFERENCE_BARS.forEachIndexed { index, width ->
            if (index > 0) {
                Box(Modifier.padding(horizontal = 4.dp).size(3.dp).background(theme.onSurfaceVariant, CircleShape))
            }
            Line(theme.onSurfaceVariant, LineBar(height = 9.dp, share = 1f), Modifier.width(width))
        }
    }
}

/** A bar where a line of text would be. */
@Composable
private fun Line(colour: Color, bar: LineBar, modifier: Modifier = Modifier) {
    Box((modifier.then(Modifier.fillMaxWidth(bar.share))).height(bar.height).background(colour, LektoRadii.Control))
}
