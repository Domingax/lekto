@file:Suppress("MagicNumber") // The preview lays out a phone's frame in design tokens and fixed heights.

package app.lekto.vocabulary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import app.lekto.theme.hoverWash
import app.lekto.ui.DELETE_ICON_SIZE
import app.lekto.ui.FIRST_LINE_INSET
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.SEARCH_ICON_SIZE
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Search
import app.lekto.ui.icons.Trash
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

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

/** A filter chip: its label bar's width, and the width of the pill it sits in. */
private data class PreviewChip(val level: MasteryLevel?, val label: Dp, val pill: Dp)
private val CHIP_LABEL_BAR = 11.dp
private val PREVIEW_CHIPS = listOf(
    PreviewChip(level = null, label = 62.dp, pill = 86.dp),
    PreviewChip(level = MasteryLevel.UNKNOWN, label = 52.dp, pill = 92.dp),
    PreviewChip(level = MasteryLevel.RECOGNIZED, label = 66.dp, pill = 106.dp),
    PreviewChip(level = MasteryLevel.FAMILIAR, label = 56.dp, pill = 96.dp),
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

/** One filter: a hairline pill with its level's dot and its name, filled with ink when it is the filter. */
@Composable
private fun Chip(chip: PreviewChip, selected: Boolean) {
    val theme = MaterialTheme.colorScheme
    val fill = if (selected) theme.primary else Color.Transparent
    Row(
        modifier = Modifier
            .width(chip.pill)
            .height(ButtonDefaults.MinHeight)
            .background(fill, LektoRadii.Control)
            // Only the unselected chip carries the hairline: the current filter is
            // the filled control, as the screen's own Button draws it.
            .then(
                if (selected) Modifier else Modifier.border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Control),
            )
            .padding(FILTER_CHIP_PADDING),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chip.level?.let { level -> Dot(level) }
        Line(if (selected) theme.onPrimary else theme.onSurface, LineBar(CHIP_LABEL_BAR, chip.label))
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
