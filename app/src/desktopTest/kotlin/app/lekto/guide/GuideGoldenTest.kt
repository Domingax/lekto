@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")
// The preview lays out each screen's frame in design tokens and fixed heights.

package app.lekto.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.reader.MasteryPalette
import app.lekto.reader.RULE_THICKNESS
import app.lekto.reader.dottedRuleEffect
import app.lekto.reader.readerColorOr
import app.lekto.reader.selectionHighlight
import app.lekto.theme.Dark
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.theme.contentColorOn
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.LOOKUP_SHEET_MAX_HEIGHT_FRACTION
import app.lekto.ui.icons.ChevronLeft
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Volume2
import app.lekto.ui.icons.X
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The first-launch guide's visual anatomy in the screenshot lane, one sheet per
 * screen per **App theme** (issue #277; docs/design.md §7.8).
 *
 * It is deliberately text-free, like every other golden: glyphs are rendered with
 * the host's fonts and would not verify on another machine. What it records is the
 * frame the field-feedback passes built — the back chevron at the head of the bar,
 * the step marks and `Skip`; the language question's selected row, its running
 * download with `Stop` and the `Continue` it reveals; the embedded page block's
 * coloured word bars over their dotted rules, with no reader chrome and no download
 * card; the real sheet raised at its cap over the washed word; the same block above
 * the six bands, A1 washed and ruled as the standing choice and `Start reading`
 * inside the one screen; and the arrival's sample row, level line and installed
 * download. The words, the levels and the meanings are the semantics suites'
 * contract; the layout, the colour ramp and the spacing are this file's.
 *
 * The page and tap beats scroll on a 640 dp phone, so the planche folds them: the
 * block is drawn shorter than the screen's `PAGE_HEIGHT` so the four notes and
 * `Continue` stay in the one frame. The level screen fits without scrolling, so its
 * planche draws every band and the filled action.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class GuideGoldenTest {

    @Test
    fun lightLanguage() = golden("language-light", Light, LANGUAGE_PROGRESS) { LanguagePreview() }

    @Test
    fun darkLanguage() = golden("language-dark", Dark, LANGUAGE_PROGRESS) { LanguagePreview() }

    @Test
    fun sepiaLanguage() = golden("language-sepia", Sepia, LANGUAGE_PROGRESS) { LanguagePreview() }

    @Test
    fun lightPage() = golden("page-light", Light, PAGE_PROGRESS) { PagePreview() }

    @Test
    fun darkPage() = golden("page-dark", Dark, PAGE_PROGRESS) { PagePreview() }

    @Test
    fun sepiaPage() = golden("page-sepia", Sepia, PAGE_PROGRESS) { PagePreview() }

    @Test
    fun lightTap() = golden("tap-light", Light, TAP_PROGRESS) { TapPreview() }

    @Test
    fun darkTap() = golden("tap-dark", Dark, TAP_PROGRESS) { TapPreview() }

    @Test
    fun sepiaTap() = golden("tap-sepia", Sepia, TAP_PROGRESS) { TapPreview() }

    @Test
    fun lightLevel() = golden("level-light", Light, LEVEL_PROGRESS) { LevelPreview() }

    @Test
    fun darkLevel() = golden("level-dark", Dark, LEVEL_PROGRESS) { LevelPreview() }

    @Test
    fun sepiaLevel() = golden("level-sepia", Sepia, LEVEL_PROGRESS) { LevelPreview() }

    @Test
    fun lightArrival() = golden("arrival-light", Light, ARRIVAL_PROGRESS) { ArrivalPreview() }

    @Test
    fun darkArrival() = golden("arrival-dark", Dark, ARRIVAL_PROGRESS) { ArrivalPreview() }

    @Test
    fun sepiaArrival() = golden("arrival-sepia", Sepia, ARRIVAL_PROGRESS) { ArrivalPreview() }

    private fun golden(
        name: String,
        theme: ColorScheme,
        progress: GuideProgress,
        screen: @Composable ColumnScope.() -> Unit,
    ) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { ScreenFrame(progress, screen) } }
        onRoot().captureRoboImage("guide-$name.png")
    }
}

private val SCREEN_WIDTH = 360.dp
private val SCREEN_HEIGHT = 640.dp

/** The five screens the progress row marks: the four stages and the arrival. */
private const val GUIDE_STEPS = 5

/** What the step bar records on one planche: how many of the five are reached, and whether back is offered. */
private data class GuideProgress(val stepsReached: Int, val back: Boolean)

private val LANGUAGE_PROGRESS = GuideProgress(GuideStage.LANGUAGE.position, back = false)
private val PAGE_PROGRESS = GuideProgress(GuideStage.PAGE.position, back = true)
private val TAP_PROGRESS = GuideProgress(GuideStage.TAP.position, back = true)
private val LEVEL_PROGRESS = GuideProgress(GuideStage.LEVEL.position, back = true)

/** The arrival is the fifth mark: the shelf the guide hands over to, with no way back into the guide. */
private val ARRIVAL_PROGRESS = GuideProgress(GUIDE_STEPS, back = false)

/**
 * The level preview's block height: the compact card's page, three lines, above
 * the six bands. It mirrors the level screen's compacted card, so the planche
 * records the screen that fits a 640 dp phone rather than a taller one.
 */
private val PREVIEW_BLOCK_HEIGHT = 56.dp

/** The block's own inset: the reader's capped measure inside the guide's frame. */
private val PAGE_INSET = 24.dp

/** The measure the page block's words may fill: the screen's width less its edges and the block's inset. */
private val PAGE_LINE_WIDTH = SCREEN_WIDTH - LektoSpacing.ScreenEdge * 2 - PAGE_INSET * 2

/** The narrower measure inside the level screen's preview card. */
private val PREVIEW_LINE_WIDTH =
    SCREEN_WIDTH - LektoSpacing.ScreenEdge * 2 - LektoSpacing.InsidePanel * 2 - PAGE_INSET * 2

/** The gap between two words on a line. */
private val WORD_GAP = 5.dp

/** The sheet's maximum height, which is what the tap planche raises it to. */
private val SHEET_HEIGHT = SCREEN_HEIGHT * LOOKUP_SHEET_MAX_HEIGHT_FRACTION

/** The phone frame every sheet draws. */
@Composable
private fun ScreenFrame(progress: GuideProgress, screen: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .width(SCREEN_WIDTH)
            .height(SCREEN_HEIGHT)
            .background(MaterialTheme.colorScheme.background),
    ) {
        GuideBarPreview(progress)
        screen()
    }
}

/** The back chevron at the head of the bar after the first screen, the step marks, and `Skip` at the edge. */
@Composable
private fun GuideBarPreview(progress: GuideProgress) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsideLabelPair)
            .heightIn(min = LektoSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (progress.back) {
            Icon(
                LektoIcons.ChevronLeft,
                null,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(LektoSpacing.InsidePanel))
        }
        repeat(GUIDE_STEPS) { index ->
            Spacer(
                Modifier
                    .padding(end = LektoSpacing.InsideLabelPair)
                    .width(18.dp)
                    .height(3.dp)
                    .background(
                        if (index < progress.stepsReached) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
            )
        }
        Spacer(Modifier.weight(1f))
        Bar(MaterialTheme.colorScheme.onBackground, 32.dp, 10.dp)
    }
}

/**
 * Screen 1: the question, the roster with one row selected, the running download
 * with its size and `Stop`, the footnote, and the `Continue` the answer reveals.
 */
@Composable
private fun ColumnScope.LanguagePreview() {
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = LektoSpacing.ScreenEdge)) {
        Heading(112.dp, 188.dp, 156.dp)
        Column(
            Modifier.fillMaxWidth().weight(1f).clipToBounds().padding(top = LektoSpacing.BetweenBlocks),
        ) {
            repeat(8) { index ->
                LanguageRowPreview(selected = index == 0)
                Hairline()
            }
        }
        DownloadLine(Modifier.padding(top = LektoSpacing.BetweenRows))
        Footnote()
        ContinueButton()
    }
}

/** One language row: its ink rule, its code, its name and the chevron; the chosen one washed. */
@Composable
private fun LanguageRowPreview(selected: Boolean) {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .background(if (selected) theme.surfaceVariant else Color.Transparent)
            .padding(vertical = LektoSpacing.BetweenRows),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Spacer(
            Modifier.width(2.dp).height(24.dp).background(if (selected) theme.onBackground else Color.Transparent),
        )
        Bar(theme.onSurfaceVariant, 22.dp, 9.dp)
        Bar(theme.onBackground, if (selected) 68.dp else 58.dp, 13.dp)
        Spacer(Modifier.weight(1f))
        Icon(LektoIcons.ChevronRight, null, tint = theme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/** The download line under the list: the assets, the published size and, while it runs, `Stop`. */
@Composable
private fun DownloadLine(modifier: Modifier = Modifier) {
    val theme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Bar(theme.onSurface, 128.dp, 10.dp)
            Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
            Bar(theme.onSurfaceVariant, 116.dp, 9.dp)
        }
        Bar(theme.onSurface, 30.dp, 10.dp)
    }
}

/** The line under the list: other languages can be added later. */
@Composable
private fun Footnote() {
    Column(Modifier.padding(vertical = LektoSpacing.InsidePanel)) {
        Bar(MaterialTheme.colorScheme.onSurfaceVariant, 176.dp, 9.dp)
    }
}

/** The filled `Continue` the language answer reveals, at the trailing edge. */
@Composable
private fun ContinueButton() {
    val theme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .width(96.dp)
                .height(40.dp)
                .background(theme.primary, LektoRadii.Control),
            contentAlignment = Alignment.Center,
        ) { Bar(theme.onPrimary, 44.dp, 11.dp) }
    }
}

/** Screen 2: the embedded block, coloured at A1, and the four notes under it. No download card. */
@Composable
private fun ColumnScope.PagePreview() {
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = LektoSpacing.ScreenEdge)) {
        Heading(188.dp, 148.dp, 118.dp)
        PageBlock(Modifier.fillMaxWidth().weight(1f).padding(top = LektoSpacing.InsidePanel), lines = 9)
        NoteRows(Modifier.padding(top = LektoSpacing.BetweenRows))
        Row(Modifier.fillMaxWidth().padding(top = LektoSpacing.InsidePanel)) {
            Spacer(Modifier.weight(1f))
            Bar(MaterialTheme.colorScheme.onBackground, 54.dp, 11.dp)
        }
    }
}

/**
 * The embedded page block: the sample's first paragraph as word bars, the coloured
 * ones carrying their level's hue and the reader's own 1 dp dotted rule (issue
 * #281). It has no reader chrome: no page bar, no **Aa**, no page turns.
 */
@Composable
private fun PageBlock(
    modifier: Modifier = Modifier,
    lines: Int,
    lineWidth: Dp = PAGE_LINE_WIDTH,
    washed: Pair<Int, Int>? = null,
) {
    Column(
        modifier.clipToBounds().padding(horizontal = PAGE_INSET, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        repeat(lines) { line ->
            PageLine(
                widths = wordsFitting(PAGE_LINES[line % PAGE_LINES.size], lineWidth),
                line = line,
                washedWord = if (washed?.first == line) washed.second else null,
            )
        }
    }
}

/**
 * The words of one line that fit [lineWidth]. The preview card is narrower than the
 * page beat, so the same paragraph sets fewer words on a line there — a wrap, not
 * a narrower word.
 */
private fun wordsFitting(widths: List<Dp>, lineWidth: Dp): List<Dp> {
    val fitted = mutableListOf<Dp>()
    var used = 0.dp
    for (width in widths) {
        val next = if (fitted.isEmpty()) width else used + WORD_GAP + width
        if (next > lineWidth) break
        fitted += width
        used = next
    }
    return fitted
}

/** One line of the page: word-width bars, four carrying a level's colour and its rule. */
@Composable
private fun PageLine(widths: List<Dp>, line: Int, washedWord: Int?) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(WORD_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        widths.forEachIndexed { word, width ->
            WordBar(width, colourAt(line, word), washed = word == washedWord)
        }
    }
}

/** One word: its bar and, when it is highlighted, the dotted rule under it. */
@Composable
private fun WordBar(width: Dp, level: MasteryLevel?, washed: Boolean) {
    val theme = MaterialTheme.colorScheme
    val colour = level?.readerColorOr(theme.onBackground, MasteryPalette.of(theme)) ?: theme.onBackground
    if (washed) {
        Box(
            Modifier
                .background(selectionHighlight(theme.onBackground), LektoRadii.Control)
                .padding(2.dp),
        ) { WordBarColumn(width, colour, level != null) }
    } else {
        WordBarColumn(width, colour, level != null)
    }
}

/** A word's bar and its rule slot, so every word on a line shares one baseline. */
@Composable
private fun WordBarColumn(width: Dp, colour: Color, ruled: Boolean) {
    Column {
        Bar(colour, width, 10.dp)
        Spacer(Modifier.height(2.dp))
        if (ruled) {
            DottedRule(colour, width)
        } else {
            Spacer(Modifier.width(width).height(1.dp))
        }
    }
}

/** The 1 dp dotted rule under a highlighted word, drawn with the reader's own stroke (issue #281). */
@Composable
private fun DottedRule(colour: Color, width: Dp) {
    val density = LocalDensity.current
    Box(
        Modifier
            .width(width)
            .height(RULE_THICKNESS)
            .drawBehind {
                drawLine(
                    color = colour,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = size.height,
                    pathEffect = dottedRuleEffect(density),
                )
            },
    )
}

/**
 * The four notes: the level's coloured chip and number, the word, the level's name
 * and the sample's meaning, one per highlighted level in acquisition order.
 */
@Composable
private fun NoteRows(modifier: Modifier = Modifier) {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    val levels = listOf(
        MasteryLevel.UNKNOWN,
        MasteryLevel.RECOGNIZED,
        MasteryLevel.FAMILIAR,
        MasteryLevel.LEARNED,
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows)) {
        levels.forEach { level ->
            val colour = level.readerColorOr(theme.outline, ramp)
            Row(horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
                Box(
                    Modifier.size(28.dp).background(colour, LektoRadii.Control),
                    contentAlignment = Alignment.Center,
                ) { Bar(contentColorOn(colour), 6.dp, 8.dp) }
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

/** Screen 3: the block with the emphasised word washed, and the real sheet raised over it at its cap. */
@Composable
private fun ColumnScope.TapPreview() {
    Box(Modifier.fillMaxWidth().weight(1f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.ScreenEdge)) {
            Heading(148.dp, 208.dp, 160.dp)
            PageBlock(
                Modifier.fillMaxWidth().weight(1f).padding(top = LektoSpacing.InsidePanel),
                lines = 9,
                washed = 0 to 1,
            )
        }
        SheetPreview()
    }
}

/**
 * The lookup sheet at the 60 % cap: the handle, the word and its meta beside the
 * speaker and close, the sentence it was tapped in, the one sense, the references
 * and the footer's state beside the five chips.
 */
@Composable
private fun BoxScope.SheetPreview() {
    val theme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(SHEET_HEIGHT),
        color = theme.surface,
        border = BorderStroke(HAIRLINE_THICKNESS, theme.outline),
    ) {
        Column(Modifier.fillMaxSize()) {
            SheetHandle()
            SheetHeader()
            ContextLine()
            SheetDivider()
            SheetAnswer()
            Spacer(Modifier.weight(1f))
            SheetDivider()
            ReferenceLine()
            SheetDivider()
            SheetFooter()
        }
    }
}

/** The grabber that says the sheet was raised from the bottom edge. */
@Composable
private fun SheetHandle() {
    Box(
        Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(32.dp).height(4.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
    }
}

/** The word, its meta line, and the speaker and close in their 48 dp controls. */
@Composable
private fun SheetHeader() {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(
            start = LektoSpacing.InsidePanel,
            end = LektoSpacing.InsideLabelPair,
            top = LektoSpacing.InsideLabelPair,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(bottom = LektoSpacing.BetweenRelatedLines)) {
            Bar(theme.onBackground, 96.dp, 24.dp)
            Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
            Bar(theme.onSurfaceVariant, 84.dp, 10.dp)
        }
        BandIcon(LektoIcons.Volume2, theme.onBackground)
        BandIcon(LektoIcons.X, theme.onBackground)
    }
}

/** The sentence the word was tapped in, muted under the headword. */
@Composable
private fun ContextLine() {
    Column(Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.InsidePanel)) {
        Bar(MaterialTheme.colorScheme.onSurfaceVariant, 160.dp, 9.dp)
    }
}

/** The answer's one numbered sense: its definition and the pack's translation under it. */
@Composable
private fun SheetAnswer() {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.InsidePanel, vertical = LektoSpacing.BetweenRows),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Bar(theme.onSurfaceVariant, 6.dp, 14.dp)
        Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
            Bar(theme.onSurface, 228.dp, 12.dp)
            Bar(theme.onSurfaceVariant, 64.dp, 9.dp)
        }
    }
}

/** The external references as one muted line, closing the body above the footer. */
@Composable
private fun ReferenceLine() {
    val theme = MaterialTheme.colorScheme
    val widths = listOf(58.dp, 30.dp, 30.dp, 76.dp)
    Row(
        Modifier.fillMaxWidth().height(LektoSpacing.TouchTarget).padding(horizontal = LektoSpacing.InsidePanel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        widths.forEachIndexed { index, width ->
            if (index > 0) {
                Box(Modifier.padding(horizontal = 4.dp).size(3.dp).background(theme.onSurfaceVariant, CircleShape))
            }
            Bar(theme.onSurfaceVariant, width, 9.dp)
        }
    }
}

/** The pinned footer: `Not saved` beside the five filled chip frames, none ringed before a save. */
@Composable
private fun SheetFooter() {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.InsidePanel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { Bar(theme.onSurfaceVariant, 64.dp, 12.dp) }
        Row {
            MasteryLevel.entries.forEach { level ->
                val colour = level.readerColorOr(theme.outline, ramp)
                Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.width(32.dp).height(32.dp).background(colour, LektoRadii.Control),
                        contentAlignment = Alignment.Center,
                    ) { Bar(contentColorOn(colour), 6.dp, 8.dp) }
                }
            }
        }
    }
}

/** An icon in its 48 dp control, centred as the sheet's icon buttons centre theirs. */
@Composable
private fun BandIcon(imageVector: ImageVector, tint: Color) {
    Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
        Icon(imageVector = imageVector, contentDescription = null, tint = tint)
    }
}

/** A hairline between two blocks, inset to the content as the sheet draws it. */
@Composable
private fun SheetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = LektoSpacing.InsidePanel),
        color = MaterialTheme.colorScheme.outline,
    )
}

/** Screen 4: the same block in its compact preview card, the six bands with A1 chosen, and `Start reading`. */
@Composable
private fun ColumnScope.LevelPreview() {
    Column(
        Modifier.fillMaxWidth().weight(1f).clipToBounds().padding(horizontal = LektoSpacing.ScreenEdge),
    ) {
        Heading(176.dp, 128.dp, 108.dp)
        PreviewCard()
        BandList()
        StartReadingButton()
    }
}

/** The preview card: the embedded page at A1, with no label of its own. */
@Composable
private fun PreviewCard() {
    val theme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRows),
        shape = LektoRadii.Surface,
        color = theme.background,
        border = BorderStroke(HAIRLINE_THICKNESS, theme.outline),
    ) {
        Column(Modifier.padding(LektoSpacing.BetweenRelatedLines)) {
            PageBlock(
                Modifier.fillMaxWidth().height(PREVIEW_BLOCK_HEIGHT),
                lines = 3,
                lineWidth = PREVIEW_LINE_WIDTH,
            )
        }
    }
}

/**
 * The six bands, code, name and one line each. A1 is washed and ruled as the
 * standing choice: the page above paints at A1 from entry, so the planche shows
 * the selection the screen shows.
 */
@Composable
private fun BandList() {
    Column(Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRows)) {
        repeat(6) { index ->
            BandRowPreview(selected = index == 0)
            if (index != 5) Hairline()
        }
    }
}

/** One band row: its code, its name, its one line, and the ink rule A1 fills from entry. */
@Composable
private fun BandRowPreview(selected: Boolean) {
    val theme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .background(if (selected) theme.surfaceVariant else Color.Transparent)
            .padding(vertical = LektoSpacing.BetweenRelatedLines),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier.width(2.dp).height(24.dp).background(if (selected) theme.onBackground else Color.Transparent),
        )
        Bar(
            theme.onSurfaceVariant,
            20.dp,
            9.dp,
            Modifier.padding(start = LektoSpacing.BetweenRelatedLines),
        )
        Spacer(Modifier.width(LektoSpacing.BetweenRelatedLines))
        Bar(theme.onSurface, 86.dp, 13.dp)
        Spacer(Modifier.weight(1f))
        Bar(theme.onSurfaceVariant, 62.dp, 9.dp)
    }
}

/** The filled `Start reading` the level screen keeps inside the one frame. */
@Composable
private fun StartReadingButton() {
    val theme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.InsidePanel, bottom = LektoSpacing.BetweenRelatedLines)
            .height(40.dp)
            .background(theme.primary, LektoRadii.Control),
        contentAlignment = Alignment.Center,
    ) { Bar(theme.onPrimary, 92.dp, 11.dp) }
}

/** Screen 5: the shelf with the sample as its first row, the level line and the installed download. */
@Composable
private fun ColumnScope.ArrivalPreview() {
    val theme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = LektoSpacing.ScreenEdge)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = LektoSpacing.InsidePanel),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
        InstalledLine(Modifier.padding(top = LektoSpacing.BetweenRows, bottom = LektoSpacing.InsidePanel))
    }
}

/** The arrival's download line once the table and the pack have landed: the assets and `Installed`. */
@Composable
private fun InstalledLine(modifier: Modifier = Modifier) {
    val theme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Bar(theme.onSurface, 132.dp, 10.dp)
            Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
            Bar(theme.onSurfaceVariant, 52.dp, 9.dp)
        }
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

/**
 * The four words the page colours, one per level, in reading order: A1's page
 * carries all four highlighted levels on the sample's first paragraph.
 */
private fun colourAt(line: Int, word: Int): MasteryLevel? = when {
    line == 0 && word == 1 -> MasteryLevel.RECOGNIZED
    line == 2 && word == 2 -> MasteryLevel.UNKNOWN
    line == 4 && word == 1 -> MasteryLevel.LEARNED
    line == 5 && word == 3 -> MasteryLevel.FAMILIAR
    else -> null
}

/**
 * The paragraph's lines as word widths. The sample's first paragraph sets about
 * nine lines in the guide's measure; only the word counts and the rhythm matter,
 * because the words themselves are the semantics suites' contract.
 */
private val PAGE_LINES: List<List<Dp>> = listOf(
    listOf(32.dp, 24.dp, 40.dp, 28.dp, 36.dp, 20.dp, 44.dp),
    listOf(26.dp, 42.dp, 30.dp, 24.dp, 38.dp, 20.dp, 34.dp),
    listOf(30.dp, 22.dp, 46.dp, 26.dp, 34.dp, 40.dp, 18.dp),
    listOf(36.dp, 24.dp, 28.dp, 44.dp, 20.dp, 32.dp, 26.dp),
    listOf(22.dp, 38.dp, 30.dp, 26.dp, 42.dp, 24.dp, 34.dp),
    listOf(40.dp, 26.dp, 20.dp, 36.dp, 28.dp, 44.dp, 22.dp),
    listOf(28.dp, 34.dp, 24.dp, 40.dp, 26.dp, 20.dp, 38.dp),
    listOf(32.dp, 22.dp, 42.dp, 26.dp, 36.dp, 24.dp, 44.dp),
    listOf(24.dp, 30.dp, 26.dp, 20.dp),
)

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
