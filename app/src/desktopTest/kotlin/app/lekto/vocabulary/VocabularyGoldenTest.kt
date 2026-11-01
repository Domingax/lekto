@file:Suppress(
    "MagicNumber", // The preview lays out the sheet's fixed bands, chips and bars as a planche does.
    "TooManyFunctions", // The planches and the previews each draws; one file, one surface.
)

package app.lekto.vocabulary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.reader.MasteryPalette
import app.lekto.reader.readerColorOr
import app.lekto.theme.Dark
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.theme.WIDE_COLUMN_WIDTH
import app.lekto.theme.contentColorOn
import app.lekto.theme.hoverWash
import app.lekto.ui.ChipFrame
import app.lekto.ui.DELETE_ICON_SIZE
import app.lekto.ui.FIRST_LINE_INSET
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.LOOKUP_SHEET_MAX_HEIGHT_FRACTION
import app.lekto.ui.SEARCH_ICON_SIZE
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Search
import app.lekto.ui.icons.Trash
import app.lekto.ui.icons.X
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Vocabulary list's visual anatomy in the screenshot lane, one sheet per
 * **App theme**, plus the delete confirmation on the light paper (issue #212;
 * docs/design.md §7.4, §8).
 *
 * It is deliberately text-free, like the other goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. Instead it records
 * the frame §7.4 gives the screen — the title and the count beside it, the search
 * surface with its hairline and icon, the filter row whose partly cut last chip
 * says it scrolls, with the current filter filled in the theme's ink and each chip
 * carrying its level's dot — then each row's word bar, dot and level name,
 * translation and muted context sentence, parted by a hairline, with the first
 * row hovered so the trash that appears in the slot reserved for it is recorded
 * too. Every frame here is redrawn, as the Library's golden redraws its own: the
 * dot is the screen's `LevelDot`, the chip's height and padding, the two icons,
 * the hairline and the mastery ramp are the values the screen itself uses, and
 * only the words are bars, because glyphs are the one thing a golden cannot pin.
 * The sheet draws the phone's own filter row (one line, the last chip cut); a
 * pointer wraps those chips instead, which is a layout no 360 dp sheet shows.
 * A hue, a radius or a spacing step that moves is caught here — the semantics
 * suite covers the text and the gestures.
 *
 * The confirmation sheet records the dialogue's anatomy as §8 prescribes it: the
 * scrim over the list, the 16 dp sheet, the title and message, and `Delete` in the
 * red the identity reserves for an action that cannot be undone.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class VocabularyGoldenTest {

    @Test
    fun lightList() = listGolden("light", Light)

    @Test
    fun darkList() = listGolden("dark", Dark)

    @Test
    fun sepiaList() = listGolden("sepia", Sepia)

    @Test
    fun lightConfirmation() = runComposeUiTest {
        setContent { LektoTheme(theme = Light) { ConfirmationPreview() } }
        onRoot().captureRoboImage("vocabulary-delete-light.png")
    }

    @Test
    fun theLevelSheetOnEveryPaper() = runComposeUiTest {
        // One planche per App theme, in one test: the sheet is the same frame,
        // repainted, and three tests would draw the same preview three times.
        var paper by mutableStateOf(Light)
        setContent { LektoTheme(theme = paper) { LevelSheetPreview() } }

        listOf("light" to Light, "dark" to Dark, "sepia" to Sepia).forEach { (name, theme) ->
            paper = theme
            onRoot().captureRoboImage("vocabulary-level-sheet-$name.png")
        }
    }

    @Test
    fun lightLevelColumn() = runComposeUiTest {
        setContent { LektoTheme(theme = Light) { LevelColumnPreview() } }
        onRoot().captureRoboImage("vocabulary-level-column-light.png")
    }

    @Test
    fun theLevelSheetMayTakeMoreOfTheScreenThanTheLookupPanels() {
        // The reader's cap keeps the page and the washed word visible; a sheet over
        // a list has nothing to keep in view (issue #178).
        assertTrue(VOCABULARY_LEVEL_SHEET_MAX_HEIGHT_FRACTION > LOOKUP_SHEET_MAX_HEIGHT_FRACTION)
    }

    private fun listGolden(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { ListPreview() } }
        onRoot().captureRoboImage("vocabulary-$name.png")
    }
}

/** The preview's window, the size the planche draws a phone at. */
private val LIST_WIDTH = 360.dp
private val LIST_HEIGHT = 640.dp

/** A bar where a line of text would be: its height, and the width it takes. */
private data class LineBar(val height: Dp, val width: Dp)

private val SCREEN_TITLE_BAR = LineBar(height = 24.dp, width = 150.dp)
private val COUNT_BAR = LineBar(height = 11.dp, width = 52.dp)
private val SEARCH_PLACEHOLDER_BAR = LineBar(height = 12.dp, width = 48.dp)

/** A filter chip: its level (absent for `All levels`) and the width of its label bar. */
private data class PreviewChip(val level: MasteryLevel?, val label: Dp)
private val CHIP_LABEL_BAR = 11.dp
private val PREVIEW_CHIPS = listOf(
    PreviewChip(level = null, label = 62.dp),
    PreviewChip(level = MasteryLevel.UNKNOWN, label = 52.dp),
    PreviewChip(level = MasteryLevel.RECOGNIZED, label = 66.dp),
    PreviewChip(level = MasteryLevel.FAMILIAR, label = 56.dp),
)

/** One row the preview draws: its level, its word bar and its context sentence's two line shares. */
private data class PreviewEntry(val level: MasteryLevel, val word: Dp, val sentence: Pair<Float, Float>)

private val WORD_BAR = 18.dp
private val LEVEL_NAME_BAR = 11.dp
private val TRANSLATION_BAR = 12.dp
private val SENTENCE_BAR = 10.dp
private val LEVEL_NAME_WIDTH = 56.dp
private val TRANSLATION_WIDTH = 72.dp
private val EDGE = LektoSpacing.ScreenEdge

private val PREVIEW_ENTRIES = listOf(
    PreviewEntry(MasteryLevel.UNKNOWN, word = 58.dp, sentence = 0.9f to 0.42f),
    PreviewEntry(MasteryLevel.UNKNOWN, word = 76.dp, sentence = 0.62f to 0f),
    PreviewEntry(MasteryLevel.RECOGNIZED, word = 82.dp, sentence = 0.88f to 0.5f),
    PreviewEntry(MasteryLevel.FAMILIAR, word = 68.dp, sentence = 0.84f to 0.34f),
    PreviewEntry(MasteryLevel.LEARNED, word = 46.dp, sentence = 0.66f to 0f),
)

/**
 * The list's frame, drawn without glyphs: the title and its count, the search
 * surface, the filter row and the rows (docs/design.md §7.4).
 */
@Composable
private fun ListPreview() {
    Column(
        modifier = Modifier
            .size(width = LIST_WIDTH, height = LIST_HEIGHT)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = EDGE, vertical = LektoSpacing.InsidePanel),
    ) {
        HeaderLine()
        SearchSurface()
        FilterRow()
        PREVIEW_ENTRIES.forEachIndexed { index, entry -> PreviewRow(entry, hovered = index == 0) }
    }
}

/** The search field's frame: the surface, the hairline, the icon and the muted placeholder. */
@Composable
private fun SearchSurface() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.InsidePanel)
            .heightIn(min = LektoSpacing.TouchTarget)
            .background(theme.surface, LektoRadii.Control)
            .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Control)
            .padding(start = LektoSpacing.InsidePanel, end = LektoSpacing.InsideLabelPair),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LektoIcons.Search,
            contentDescription = null,
            tint = theme.onSurfaceVariant,
            modifier = Modifier.size(SEARCH_ICON_SIZE),
        )
        Spacer(Modifier.width(LektoSpacing.BetweenRows))
        Line(theme.onSurfaceVariant, SEARCH_PLACEHOLDER_BAR)
    }
}

/** The filter row, clipped: the last chip is cut by the edge, which is what says the row scrolls. */
@Composable
private fun FilterRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.BetweenRelatedLines)
            .clipToBounds(),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        PREVIEW_CHIPS.forEachIndexed { index, chip -> Chip(chip, selected = index == 0) }
    }
}

/**
 * One filter, drawn with the screen's own frame (the **Aa** panel's chip, issue
 * #212): its level's dot and its name, the paper lifted against the screen when it
 * is not the filter and the ink when it is.
 */
@Composable
private fun Chip(chip: PreviewChip, selected: Boolean) {
    val theme = MaterialTheme.colorScheme
    Box(Modifier.heightIn(min = LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
        ChipFrame(selected = selected) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chip.level?.let { level -> Dot(level) }
                Line(if (selected) theme.onPrimary else theme.onSurface, LineBar(CHIP_LABEL_BAR, chip.label))
            }
        }
    }
}

/**
 * One row's frame: the word with its level, the translation and the muted
 * sentence, parted from the next row by a hairline. The wash and the trash that a
 * hover adds stop at the hairline, as the screen's own frame does.
 */
@Composable
private fun PreviewRow(entry: PreviewEntry, hovered: Boolean) {
    val theme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (hovered) hoverWash(theme.onBackground) else Color.Transparent)
                .padding(vertical = LektoSpacing.BetweenRows),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Line(theme.onBackground, LineBar(WORD_BAR, entry.word))
                    Dot(entry.level)
                    Line(theme.onBackground, LineBar(LEVEL_NAME_BAR, LEVEL_NAME_WIDTH))
                }
                Line(theme.onSurface, LineBar(TRANSLATION_BAR, TRANSLATION_WIDTH))
                SentenceLine(theme, entry.sentence.first)
                if (entry.sentence.second > 0f) SentenceLine(theme, entry.sentence.second)
            }
            // The action's place is reserved on every row; the trash only shows
            // while the pointer is on it, so revealing it never shifts the row. It
            // sits on the word's line, as the screen places it (issue #212).
            Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.TopCenter) {
                if (hovered) {
                    Icon(
                        imageVector = LektoIcons.Trash,
                        contentDescription = null,
                        tint = theme.onSurfaceVariant,
                        modifier = Modifier.padding(top = FIRST_LINE_INSET).size(DELETE_ICON_SIZE),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(HAIRLINE_THICKNESS).background(theme.outlineVariant))
    }
}

/** One line of the context sentence, in the muted ink §7.4 gives it. */
@Composable
private fun SentenceLine(theme: ColorScheme, share: Float) {
    Box(Modifier.fillMaxWidth(share).height(SENTENCE_BAR).background(theme.onSurfaceVariant, LektoRadii.Control))
}

/** The level's colour, drawn by the screen's own dot so the golden cannot drift from it. */
@Composable
private fun Dot(level: MasteryLevel) {
    val theme = MaterialTheme.colorScheme
    LevelDot(level.readerColorOr(theme.outline, MasteryPalette.of(theme)))
}

/** A bar where a line of text would be. */
@Composable
private fun Line(colour: Color, bar: LineBar) {
    Box(Modifier.width(bar.width).height(bar.height).background(colour, LektoRadii.Control))
}

/** The confirmation's frame: the scrim over the list, the sheet, its title and message, and its two actions. */
@Composable
private fun ConfirmationPreview() {
    val theme = MaterialTheme.colorScheme
    Box(Modifier.size(width = LIST_WIDTH, height = LIST_HEIGHT)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(theme.background)
                .padding(horizontal = EDGE, vertical = LektoSpacing.InsidePanel),
        ) {
            HeaderLine()
            SearchSurface()
            FilterRow()
            PREVIEW_ENTRIES.take(2).forEach { entry -> PreviewRow(entry, hovered = false) }
        }
        // The scrim Material draws behind a dialogue, over the list it protects.
        Box(Modifier.fillMaxSize().background(theme.scrim.copy(alpha = SCRIM_ALPHA)))
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ConfirmationSheet() }
    }
}

/** The title and its count, as the list's header draws them. */
@Composable
private fun HeaderLine() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Line(theme.onBackground, SCREEN_TITLE_BAR)
        Line(theme.onSurfaceVariant, COUNT_BAR)
    }
}

/** The sheet itself: 16 dp, the identity's radius for a dialogue (§4), with its two actions. */
@Composable
private fun ConfirmationSheet() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(CONFIRM_WIDTH)
            .background(theme.surface, LektoRadii.Sheet)
            .padding(LektoSpacing.ScreenEdge),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsidePanel),
    ) {
        Line(theme.onSurface, CONFIRM_TITLE_BAR)
        Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
            Line(theme.onSurface, CONFIRM_MESSAGE_FIRST_BAR)
            Line(theme.onSurface, CONFIRM_MESSAGE_SECOND_BAR)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LektoSpacing.InsidePanel, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Line(theme.onSurface, ACTION_BAR)
            Line(theme.error, ACTION_BAR)
        }
    }
}

/** The dialogue's width and the bars that stand in for its words. */
private val CONFIRM_WIDTH = 300.dp
private val CONFIRM_TITLE_BAR = LineBar(height = 20.dp, width = 150.dp)
private val CONFIRM_MESSAGE_FIRST_BAR = LineBar(height = 11.dp, width = 252.dp)
private val CONFIRM_MESSAGE_SECOND_BAR = LineBar(height = 11.dp, width = 180.dp)
private val ACTION_BAR = LineBar(height = 13.dp, width = 46.dp)

/** The scrim's opacity, as Material's dialogue draws it. */
private const val SCRIM_ALPHA = 0.32f

/** The drag handle's bar, as the sheet shell draws it. */
private val LEVEL_HANDLE_WIDTH = 32.dp
private val LEVEL_HANDLE_HEIGHT = 4.dp

/** A level chip's visible frame inside its 48 dp target, and the ink ring of the current one. */
private val LEVEL_CHIP_WIDTH = 32.dp
private val LEVEL_CHIP_HEIGHT = 32.dp
private val LEVEL_CHIP_RING = 2.dp

/** The bars that stand in for the level sheet's words. */
private val LEVEL_WORD_BAR = LineBar(height = 22.dp, width = 96.dp)
private val LEVEL_TRANSLATION_BAR = LineBar(height = 12.dp, width = 88.dp)
private val LEVEL_SENTENCE_BAR = LineBar(height = 10.dp, width = 220.dp)
private val LEVEL_LABEL_BAR = LineBar(height = 8.dp, width = 40.dp)
private val LEVEL_SHEET_NAME_BAR = LineBar(height = 12.dp, width = 56.dp)
private val LEVEL_CHIP_LABEL_BAR = LineBar(height = 9.dp, width = 8.dp)

/**
 * The level sheet's frame over the list it was raised from (issue #178): the
 * scrim, then the shared sheet — its drag handle, the word in the title's size,
 * the close icon, the translation and the muted sentence, a hairline, and the
 * reader's five chips with the current level ringed and named.
 *
 * Every frame here is redrawn, as the Library's golden redraws its own: the
 * handle, the title band and the chip row copy the sheet's own values, and only
 * the words are bars, because glyphs are the one thing a golden cannot pin.
 */
@Composable
private fun LevelSheetPreview() {
    val theme = MaterialTheme.colorScheme
    Box(Modifier.size(width = LIST_WIDTH, height = LIST_HEIGHT).background(theme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = EDGE, vertical = LektoSpacing.InsidePanel),
        ) {
            HeaderLine()
            SearchSurface()
            FilterRow()
            PREVIEW_ENTRIES.take(2).forEach { entry -> PreviewRow(entry, hovered = false) }
        }
        Box(Modifier.fillMaxSize().background(theme.scrim.copy(alpha = SCRIM_ALPHA)))
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = theme.surface,
            border = BorderStroke(HAIRLINE_THICKNESS, theme.outline),
        ) {
            Column(Modifier.fillMaxWidth()) {
                LevelSheetHandle()
                LevelSheetHeader()
                LevelSheetMeaning()
                LevelSheetDivider()
                LevelSheetSelector()
            }
        }
    }
}

/** The grabber that says the sheet was raised from the bottom edge. */
@Composable
private fun LevelSheetHandle() {
    Box(
        Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(LEVEL_HANDLE_WIDTH)
                .height(LEVEL_HANDLE_HEIGHT)
                .background(MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}

/** The word in the serif and the close action, on the sheet's own header band. */
@Composable
private fun LevelSheetHeader() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = LektoSpacing.InsidePanel,
            end = LektoSpacing.InsideLabelPair,
            top = LektoSpacing.InsideLabelPair,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Line(theme.onBackground, LEVEL_WORD_BAR)
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = LektoIcons.X,
            contentDescription = null,
            tint = theme.onBackground,
            modifier = Modifier.padding(horizontal = LektoSpacing.BetweenRows).size(24.dp),
        )
    }
}

/** What the row says about the word: its translation, then the sentence in the muted ink. */
@Composable
private fun LevelSheetMeaning() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = LektoSpacing.InsidePanel).padding(
            bottom = LektoSpacing.BetweenRows,
        ),
    ) {
        Line(theme.onSurface, LEVEL_TRANSLATION_BAR)
        Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
        Line(theme.onSurfaceVariant, LEVEL_SENTENCE_BAR)
    }
}

/** The hairline that parts the word from its levels, inset to the content. */
@Composable
private fun LevelSheetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = LektoSpacing.InsidePanel),
        color = MaterialTheme.colorScheme.outline,
    )
}

/** The level named beside the five chips, as the reader's footer draws them. */
@Composable
private fun LevelSheetSelector() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            horizontal = LektoSpacing.InsidePanel,
            vertical = LektoSpacing.BetweenRows,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).heightIn(min = LektoSpacing.TouchTarget),
            verticalArrangement = Arrangement.Center,
        ) {
            Line(theme.onSurfaceVariant, LEVEL_LABEL_BAR)
            Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
            Line(theme.onBackground, LEVEL_SHEET_NAME_BAR)
        }
        LevelSheetChips()
    }
}

/**
 * The word's reserved column (issue #178; docs/design.md §6): the reader's column
 * shape — a surface beside the content, parted by a hairline — with the word,
 * what the row says about it, and the level stacked above the chips, because
 * 320 dp has no room for both on one line.
 */
@Composable
private fun LevelColumnPreview() {
    val theme = MaterialTheme.colorScheme
    Row(Modifier.size(width = WIDE_COLUMN_WIDTH, height = LIST_HEIGHT).background(theme.surface)) {
        Box(Modifier.width(HAIRLINE_THICKNESS).fillMaxHeight().background(theme.outline))
        Column(Modifier.fillMaxWidth()) {
            LevelSheetHeader()
            LevelSheetMeaning()
            LevelSheetDivider()
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = LektoSpacing.InsidePanel,
                    vertical = LektoSpacing.BetweenRows,
                ),
            ) {
                Line(theme.onSurfaceVariant, LEVEL_LABEL_BAR)
                Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
                Line(theme.onBackground, LEVEL_SHEET_NAME_BAR)
                Spacer(Modifier.height(LektoSpacing.BetweenRelatedLines))
                LevelSheetChips()
            }
        }
    }
}

/** The five chip frames, filled with each level's colour; the current one keeps an ink ring. */
@Composable
private fun LevelSheetChips() {
    val theme = MaterialTheme.colorScheme
    val ramp = MasteryPalette.of(theme)
    val levels = listOf(ramp.unknown, ramp.recognized, ramp.familiar, ramp.learned, null)
    Row {
        levels.forEachIndexed { index, colour ->
            // The sheet opens on the saved level, so the planche rings that one.
            val selected = index == MasteryLevel.LEARNED.ordinal
            val fill = colour ?: theme.outline
            Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(LEVEL_CHIP_WIDTH)
                        .height(LEVEL_CHIP_HEIGHT)
                        .background(fill, LektoRadii.Control)
                        .then(
                            if (selected) {
                                Modifier.border(LEVEL_CHIP_RING, theme.onBackground, LektoRadii.Control)
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Line(contentColorOn(fill), LEVEL_CHIP_LABEL_BAR)
                }
            }
        }
    }
}
