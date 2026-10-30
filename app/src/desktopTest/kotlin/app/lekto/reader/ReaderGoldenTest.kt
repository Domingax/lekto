@file:Suppress("MagicNumber") // The preview lays out fixed bands and bars, as a planche does.

package app.lekto.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.theme.Dark
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.theme.WIDE_COLUMN_WIDTH
import app.lekto.theme.WIDE_WINDOW_BREAKPOINT
import app.lekto.ui.icons.ALargeSmall
import app.lekto.ui.icons.ArrowLeft
import app.lekto.ui.icons.ChevronLeft
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The reader's chrome in the screenshot lane (issue #209; docs/design.md §6,
 * §7.2): the top band (back, book title, **Aa**) and the bottom band (the two
 * tap-zone chevrons and the position), one sheet per **App theme**, plus the
 * **Aa** panel open on the light paper.
 *
 * It is deliberately text-free, like the other goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. The bands, their
 * heights, their padding, the four real icons, the panel's square edge and its chip
 * frames are drawn by this preview; only the words are bars. The semantics suite
 * proves the text and the interactions. Record with
 * `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderGoldenTest {

    @Test
    fun lightPage() = pageGolden("light", Light)

    @Test
    fun darkPage() = pageGolden("dark", Dark)

    @Test
    fun sepiaPage() = pageGolden("sepia", Sepia)

    @Test
    fun lightTextPanel() = runComposeUiTest {
        setContent { LektoTheme(theme = Light) { ReaderPreview(panelOpen = true) } }
        onRoot().captureRoboImage("reader-text-panel-light.png")
    }

    /**
     * The desktop shape (issue #199; docs/design.md §6): the page and its two
     * bands, and the reserved context column beside them with the chapter list —
     * the heading, the hierarchy and the current row in ink.
     */
    @Test
    fun lightContextColumn() = runSkikoComposeUiTest(
        size = Size(WIDE_WINDOW_BREAKPOINT.value, CONTEXT_COLUMN_WINDOW_HEIGHT.value),
    ) {
        setContent { LektoTheme(theme = Light) { ContextColumnPreview() } }
        onRoot().captureRoboImage("reader-context-column-light.png")
    }

    private fun pageGolden(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { ReaderPreview(panelOpen = false) } }
        onRoot().captureRoboImage("reader-$name.png")
    }
}

/** The preview's window, the size the planche draws a phone at. */
private val READER_WIDTH = 360.dp
private val READER_HEIGHT = 640.dp

/** The desktop preview's height; its width is the context column's real breakpoint. */
private val CONTEXT_COLUMN_WINDOW_HEIGHT = 720.dp

/** A bar where a line of text would be: its height and the share of the width it takes. */
private data class LineBar(val height: Dp, val share: Float)

private val TITLE_BAR = LineBar(height = 12.dp, share = 0.35f)
private val POSITION_BAR = LineBar(height = 10.dp, share = 0.25f)
private val PANEL_TITLE_BAR = LineBar(height = 16.dp, share = 0.16f)
private val CLOSE_BAR = LineBar(height = 11.dp, share = 0.1f)
private val ROW_LABEL_BAR = LineBar(height = 9.dp, share = 0.22f)
private val CHIP_LABEL_BAR = LineBar(height = 9.dp, share = 0.45f)
private val PAGE_LINE_BAR = LineBar(height = 10.dp, share = 0.94f)

/** The page's lines, with a shorter one where a paragraph ends. */
private val PAGE_LINE_SHARES = listOf(
    0.96f, 0.92f, 0.95f, 0.9f, 0.93f, 0.6f,
    0.94f, 0.88f, 0.52f, 0.95f, 0.9f, 0.93f,
    0.91f, 0.62f, 0.96f, 0.9f, 0.94f, 0.89f,
    0.93f, 0.48f,
)

/** A chip's visible height inside its 48 dp touch target. */
private val CHIP_HEIGHT = 28.dp

/** The three chip frames the size row draws: the step down, the value and the step up. */
private val STEP_CHIP_WIDTH = 32.dp

/** The text face and margin chips, at the width each label needs. */
private val FACE_CHIP_WIDTH = 56.dp
private val MARGIN_CHIP_WIDTHS = listOf(58.dp, 92.dp, 48.dp)

/**
 * The reader's frame: the top band, the page and the bottom band; the **Aa**
 * panel joins the column when [panelOpen] is set, so it pushes the page and the
 * bottom band up instead of covering them (docs/design.md §7.2).
 */
@Composable
private fun ReaderPreview(panelOpen: Boolean) {
    val theme = MaterialTheme.colorScheme
    Column(Modifier.size(width = READER_WIDTH, height = READER_HEIGHT).background(theme.background)) {
        ChromeBand()
        PageLines()
        PageBarBand()
        if (panelOpen) TextPanel()
    }
}

/** The 48 dp top band: the way back, the book's title in muted ink, and the **Aa** icon. */
@Composable
private fun ChromeBand() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHROME_HEIGHT)
            .padding(horizontal = LektoSpacing.ScreenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BandIcon(LektoIcons.ArrowLeft, theme.onBackground)
        Spacer(Modifier.width(LektoSpacing.BetweenRelatedLines))
        Box(Modifier.weight(1f)) { Line(theme.onSurfaceVariant, TITLE_BAR) }
        BandIcon(LektoIcons.ALargeSmall, theme.onBackground)
    }
}

/**
 * The book's page as bars: the reading margin on each side and the paragraph
 * rhythm. The page keeps its full pagination; the panel only clips the visible
 * part, as the reader does.
 */
@Composable
private fun ColumnScope.PageLines() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clipToBounds()
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        PAGE_LINE_SHARES.forEach { share ->
            Line(theme.onBackground, PAGE_LINE_BAR.copy(share = share))
        }
    }
}

/**
 * The 64 dp bottom band: the two chevrons marking the thirds and turning the
 * page, and the position in muted ink. The preview opens on page one, so the left
 * chevron is faded as its disabled control would be.
 */
@Composable
private fun PageBarBand() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(PAGE_BAR_HEIGHT)
            .padding(horizontal = LektoSpacing.ScreenEdge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BandIcon(LektoIcons.ChevronLeft, theme.onSurfaceVariant.copy(alpha = 0.38f))
        Line(theme.onSurfaceVariant, POSITION_BAR)
        BandIcon(LektoIcons.ChevronRight, theme.onSurfaceVariant)
    }
}

/** An icon in its 48 dp control, centred as the band's icon buttons centre theirs. */
@Composable
private fun BandIcon(imageVector: ImageVector, tint: Color) {
    Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
        Icon(imageVector = imageVector, contentDescription = null, tint = tint)
    }
}

/**
 * The **Aa** panel: the surface tone and the hairline, square because it meets
 * the bottom edge, then a name and **Close**, and the three printed-form rows of
 * chips.
 */
@Composable
private fun TextPanel() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface)
            .border(1.dp, theme.outline)
            .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
    ) {
        PanelHeader()
        PanelRow {
            Chip(FACE_CHIP_WIDTH, selected = true)
            Chip(FACE_CHIP_WIDTH, selected = false)
        }
        PanelRow {
            Chip(STEP_CHIP_WIDTH, selected = false)
            Line(theme.onSurfaceVariant, LineBar(height = 10.dp, share = 0.5f), Modifier.width(16.dp))
            Chip(STEP_CHIP_WIDTH, selected = false)
        }
        PanelRow {
            MARGIN_CHIP_WIDTHS.forEachIndexed { index, width -> Chip(width, selected = index == 1) }
        }
    }
}

/** The panel's name and its way out. */
@Composable
private fun PanelHeader() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = LektoSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { Line(theme.onBackground, PANEL_TITLE_BAR) }
        Line(theme.onBackground, CLOSE_BAR)
    }
}

/** One printed-form row: its label on the left and its chips after it. */
@Composable
private fun PanelRow(chips: @Composable () -> Unit) {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { Line(theme.onSurfaceVariant, ROW_LABEL_BAR) }
        chips()
    }
}

/** A chip's visible frame: filled with ink when selected, paper against the panel otherwise. */
@Composable
private fun Chip(width: Dp, selected: Boolean) {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .height(CHIP_HEIGHT)
            .width(width)
            .background(if (selected) theme.primary else theme.background, LektoRadii.Control)
            .border(1.dp, if (selected) theme.primary else theme.outline, LektoRadii.Control),
        contentAlignment = Alignment.Center,
    ) {
        Line(if (selected) theme.onPrimary else theme.onSurface, CHIP_LABEL_BAR)
    }
}

/**
 * The desktop reader's frame (issue #199; docs/design.md §6): the same page and
 * bands as the phone, with the reserved context column beside them — parted by a
 * 1 dp rule and filled with the surface tone. The column carries the **Chapters**
 * heading and the book's hierarchy, the current row in ink and the others in the
 * body ink, exactly where the live list puts them.
 */
@Composable
private fun ContextColumnPreview() {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier
            .size(width = WIDE_WINDOW_BREAKPOINT, height = CONTEXT_COLUMN_WINDOW_HEIGHT)
            .background(theme.background),
    ) {
        Column(Modifier.weight(1f)) {
            ChromeBand()
            PageLines()
            PageBarBand()
        }
        ContextColumn()
    }
}

/** The reserved column: a hairline rule, the surface tone, and the chapter list it shows when idle. */
@Composable
private fun ContextColumn() {
    val theme = MaterialTheme.colorScheme
    Row(Modifier.width(WIDE_COLUMN_WIDTH).fillMaxHeight()) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(theme.outline))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.surface)
                .padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.InsidePanel),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
        ) {
            ChapterRowBar(depth = 0, current = false, heading = true)
            ChapterRowBar(depth = 0, current = false)
            ChapterRowBar(depth = 1, current = false)
            ChapterRowBar(depth = 1, current = true)
            ChapterRowBar(depth = 0, current = false)
        }
    }
}

/** One row of the chapter list as a bar: indented by its depth, in ink while it is the current chapter. */
@Composable
private fun ChapterRowBar(depth: Int, current: Boolean, heading: Boolean = false) {
    val theme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .padding(start = CHAPTER_INDENT * depth),
        contentAlignment = Alignment.CenterStart,
    ) {
        val colour = when {
            heading -> theme.onBackground
            current -> theme.primary
            else -> theme.onSurface
        }
        Line(colour, if (heading) PANEL_TITLE_BAR else ROW_LABEL_BAR)
    }
}

/** A bar where a line of text would be. */
@Composable
private fun Line(colour: Color, bar: LineBar, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(bar.share).height(bar.height).background(colour, LektoRadii.Control))
}
