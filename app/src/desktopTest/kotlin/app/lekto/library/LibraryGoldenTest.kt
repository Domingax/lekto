@file:Suppress("MagicNumber") // The preview lays out the row's frame in design tokens and fixed heights.

package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.theme.Dark
import app.lekto.theme.LanguageHues
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.theme.contentColorOn
import app.lekto.ui.ChipFrame
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.SEARCH_ICON_SIZE
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Search
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The Library's visual anatomy in the screenshot lane, one sheet per **App
 * theme** (issue #208; docs/design.md §7.1).
 *
 * It is deliberately text-free, like the theme and mastery goldens: glyphs are
 * rendered with the host's fonts and would not verify on another machine.
 * Instead it records the frame of a shelf of four books across three **Reading
 * languages**: the search surface with its hairline and the `Search` icon, the
 * **Resume block** with its larger plate, its facts, its filled action and its
 * two snippet lines (issue #288), then each row's 44 × 60 plate — the first one a
 * solid stand-in for a **Cover**, the others in the language's own hue — its
 * title and second line as ink bars, its 3 dp progress rule, and the hairline
 * that parts two rows. The plate's frame,
 * the search surface, the spacing and the rule are the screen's own constants
 * and composables; only the words are bars, because glyphs are the one thing a
 * golden cannot pin. A hue, a radius or a spacing step that moves is caught
 * here — the semantics suite covers the text and the cover's fallback.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class LibraryGoldenTest {

    @Test
    fun lightShelf() = shelfGolden("light", Light)

    @Test
    fun darkShelf() = shelfGolden("dark", Dark)

    @Test
    fun sepiaShelf() = shelfGolden("sepia", Sepia)

    private fun shelfGolden(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { ShelfPreview() } }
        onRoot().captureRoboImage("library-$name.png")
    }
}

/** A book the preview draws: its language, its rule, whether it recently led, and whether it carries art. */
private data class PreviewBook(
    val language: String,
    val percent: Int,
    val recentlyRead: Boolean,
    val hasCover: Boolean = false,
)

/** A bar where a line of text would be: its height, and the share of the width it takes. */
private data class LineBar(val height: Dp, val share: Float)

/** Four books across three languages, two of them in the leading group; the first carries art. */
private val PREVIEW_BOOKS = listOf(
    PreviewBook(language = "en", percent = 32, recentlyRead = true, hasCover = true),
    PreviewBook(language = "fr", percent = 4, recentlyRead = true),
    PreviewBook(language = "es", percent = 0, recentlyRead = false),
    PreviewBook(language = "en", percent = 61, recentlyRead = false),
)

/** The preview's window; taller than a phone so the block and both groups fit. */
private val SHELF_WIDTH = 360.dp
private val SHELF_HEIGHT = 820.dp

/** The bars that stand in for a row's title, its second line and the chrome's words. */
private val TITLE_BAR = LineBar(height = 16.dp, share = 0.6f)
private val LINE_BAR = LineBar(height = 12.dp, share = 0.35f)
private val SCREEN_TITLE_BAR = LineBar(height = 24.dp, share = 0.4f)
private val IMPORT_WORD_BAR = LineBar(height = 14.dp, share = 0.18f)
private val SECTION_HEADING_BAR = LineBar(height = 20.dp, share = 0.3f)
private val SEARCH_PLACEHOLDER_BAR = LineBar(height = 12.dp, share = 0.35f)

/** The vignette's two-letter code, drawn as a bar. */
private val CODE_BAR = 22.dp to 10.dp

/** The resume block's cover stand-in: a solid plate where a real book's art goes. */
private val PREVIEW_COVER_COLOUR = Color(0xFF5C6B52)

/** The block's trash glyph, the filled `Resume`'s word, and its snippet's two lines (issue #288). */
private val TRASH_BAR = 16.dp to 16.dp
private val RESUME_WORD_BAR = 16.dp to 10.dp
private val SNIPPET_BAR = LineBar(height = 12.dp, share = 0.9f)

/**
 * The shelf's frame, drawn without glyphs: the screen title and the Import word,
 * the search surface, the **Resume block** (issue #288), then the rows
 * (docs/design.md §7.1).
 */
@Composable
private fun ShelfPreview() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .size(width = SHELF_WIDTH, height = SHELF_HEIGHT)
            .background(theme.background)
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = LektoSpacing.TouchTarget),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Line(theme.onBackground, SCREEN_TITLE_BAR)
            Line(theme.onBackground, IMPORT_WORD_BAR)
        }
        SearchSurface()
        FilterChips()
        ResumeBlockPreview(PREVIEW_BOOKS.first { book -> book.recentlyRead })
        SectionHeading(top = LektoSpacing.BetweenRelatedLines)
        PREVIEW_BOOKS.filter { book -> book.recentlyRead }.drop(1).forEach { book -> PreviewRow(book) }
        SectionHeading(top = LektoSpacing.BetweenBlocks)
        PREVIEW_BOOKS.filterNot { book -> book.recentlyRead }.forEach { book -> PreviewRow(book) }
    }
}

/**
 * The **Resume block**'s frame (issue #288): one card on the surface tone holding
 * the larger plate, the facts, the trash, the filled `Resume`, the hairline and
 * the two snippet lines. The plate, the radius, the rule and the spacing are the
 * screen's own constants; only the words are bars.
 */
@Composable
private fun ResumeBlockPreview(book: PreviewBook) {
    val theme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = LektoSpacing.BetweenRows)
            .background(theme.surface, LektoRadii.Surface)
            .border(HAIRLINE_THICKNESS, theme.outline, LektoRadii.Surface)
            .padding(LektoSpacing.InsidePanel),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Block(
                colour = if (book.hasCover) PREVIEW_COVER_COLOUR else LanguageHues.fill(book.language, theme),
                size = RESUME_COVER_WIDTH to RESUME_COVER_HEIGHT,
            )
            ResumeFactsPreview(book)
            Block(theme.onSurfaceVariant, TRASH_BAR)
        }
        ResumeActionPreview()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = LektoSpacing.InsidePanel)
                .height(HAIRLINE_THICKNESS)
                .background(theme.outline),
        )
        Column(Modifier.padding(top = LektoSpacing.BetweenRelatedLines)) {
            Line(theme.onSurfaceVariant, SNIPPET_BAR)
            Line(theme.onSurfaceVariant, SNIPPET_BAR)
        }
    }
}

/** The block's filled `Resume` control, as bars. */
@Composable
private fun ResumeActionPreview() {
    val theme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.InsidePanel)
            .height(LektoSpacing.TouchTarget)
            .background(theme.primary, LektoRadii.Control),
        contentAlignment = Alignment.Center,
    ) {
        Block(contentColorOn(theme.primary), RESUME_WORD_BAR)
    }
}

/** The block's title, author and line, as bars. */
@Composable
private fun RowScope.ResumeFactsPreview(book: PreviewBook) {
    val theme = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .padding(start = LektoSpacing.BetweenRows, end = LektoSpacing.TouchTarget),
    ) {
        Line(theme.onBackground, TITLE_BAR)
        Line(theme.onSurfaceVariant, LINE_BAR, top = LektoSpacing.InsideLabelPair)
        Line(theme.onSurfaceVariant, LINE_BAR, top = LektoSpacing.BetweenRelatedLines)
        ProgressRule(bookId = "preview-block", percent = book.percent)
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

/**
 * The **Reading state** filter row (issue #200; docs/design.md §7.1): the same
 * [ChipFrame] the rest of the app uses, two chips inked and one on the paper,
 * with a bar where each label would be. The row is what the golden records; the
 * words are the semantics suite's.
 */
@Composable
private fun FilterChips() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        listOf(true, true, false).forEach { selected ->
            ChipFrame(selected = selected) {
                Box(
                    Modifier
                        .width(CHIP_BAR_WIDTH)
                        .height(CHIP_BAR_HEIGHT)
                        .background(if (selected) theme.onPrimary else theme.onSurfaceVariant, LektoRadii.Control),
                )
            }
        }
    }
}

/** The bars that stand in for a filter chip's word and its box. */
private val CHIP_BAR_WIDTH = 28.dp
private val CHIP_BAR_HEIGHT = 10.dp

/** A group's heading: the serif section title, drawn as a bar, with the gap that parts the groups. */
@Composable
private fun SectionHeading(top: Dp) {
    Column(Modifier.padding(top = top, bottom = LektoSpacing.BetweenRelatedLines)) {
        Line(colour = MaterialTheme.colorScheme.onBackground, bar = SECTION_HEADING_BAR)
    }
}

/** One row's frame: the plate, the title and second line, and the rule. Rows part by rhythm alone. */
@Composable
private fun PreviewRow(book: PreviewBook) {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LektoSpacing.BetweenRows),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (book.hasCover) {
            BookPlate(language = book.language, cover = PREVIEW_COVER)
        } else {
            LanguageVignette(book.language)
        }
        Spacer(Modifier.width(LektoSpacing.BetweenRows))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
            Line(theme.onBackground, TITLE_BAR)
            Line(theme.onSurfaceVariant, LINE_BAR)
            ProgressRule(bookId = "preview", percent = book.percent)
        }
    }
}

/**
 * A solid stand-in for a cover: the golden pins the plate's frame and radius,
 * never the pixels of a real book's art, which no two machines share.
 */
private val PREVIEW_COVER: ImageBitmap = ImageBitmap(
    width = LANGUAGE_VIGNETTE_WIDTH.value.toInt(),
    height = LANGUAGE_VIGNETTE_HEIGHT.value.toInt(),
).also { bitmap ->
    Canvas(bitmap).drawRect(
        Rect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
        Paint().apply { color = Color(0xFF5C6B52) },
    )
}

/** The vignette: the language's hue, and a bar where its two-letter code sits. */
@Composable
private fun LanguageVignette(language: String) {
    val fill = LanguageHues.fill(language, MaterialTheme.colorScheme)
    Box(
        modifier = Modifier
            .width(LANGUAGE_VIGNETTE_WIDTH)
            .height(LANGUAGE_VIGNETTE_HEIGHT)
            .background(fill, LektoRadii.Control),
        contentAlignment = Alignment.Center,
    ) {
        Block(contentColorOn(fill), CODE_BAR)
    }
}

/** A bar where a line of text would be, with an optional gap above it. */
@Composable
private fun Line(colour: Color, bar: LineBar, top: Dp = 0.dp) {
    Box(
        Modifier
            .padding(top = top)
            .fillMaxWidth(bar.share)
            .height(bar.height)
            .background(colour, LektoRadii.Control),
    )
}

/** A fixed-size block where the vignette's code would be. */
@Composable
private fun Block(colour: Color, size: Pair<Dp, Dp>) {
    val (width, height) = size
    Box(Modifier.width(width).height(height).background(colour, LektoRadii.Control))
}
