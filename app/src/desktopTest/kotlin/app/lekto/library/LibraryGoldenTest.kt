@file:Suppress("MagicNumber") // The preview lays out the row's frame in design tokens and fixed heights.

package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
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
 * languages**: the search surface with its hairline and the `Search` icon, then
 * each row's 44 × 60 vignette in the language's own hue, its title and second
 * line as ink bars, its 3 dp progress rule, and the hairline that parts two
 * rows. The vignette's frame, the search surface, the spacing and the rule are
 * the screen's own constants and composables; only the words are bars, because
 * glyphs are the one thing a golden cannot pin. A hue, a radius or a spacing
 * step that moves is caught here — the semantics suite covers the text.
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

/** A book the preview draws: its language, the share of its rule that is filled, and whether it recently led. */
private data class PreviewBook(val language: String, val percent: Int, val recentlyRead: Boolean)

/** A bar where a line of text would be: its height, and the share of the width it takes. */
private data class LineBar(val height: Dp, val share: Float)

/** Four books across three languages, two of them in the leading group. */
private val PREVIEW_BOOKS = listOf(
    PreviewBook(language = "en", percent = 32, recentlyRead = true),
    PreviewBook(language = "fr", percent = 4, recentlyRead = true),
    PreviewBook(language = "es", percent = 0, recentlyRead = false),
    PreviewBook(language = "en", percent = 61, recentlyRead = false),
)

/** The preview's window, the size the planche draws a phone at. */
private val SHELF_WIDTH = 360.dp
private val SHELF_HEIGHT = 640.dp

/** The bars that stand in for a row's title, its second line and the chrome's words. */
private val TITLE_BAR = LineBar(height = 16.dp, share = 0.6f)
private val LINE_BAR = LineBar(height = 12.dp, share = 0.35f)
private val SCREEN_TITLE_BAR = LineBar(height = 24.dp, share = 0.4f)
private val IMPORT_WORD_BAR = LineBar(height = 14.dp, share = 0.18f)
private val SECTION_HEADING_BAR = LineBar(height = 20.dp, share = 0.3f)
private val SEARCH_PLACEHOLDER_BAR = LineBar(height = 12.dp, share = 0.35f)

/** The vignette's two-letter code, drawn as a bar. */
private val CODE_BAR = 22.dp to 10.dp

/**
 * The shelf's frame, drawn without glyphs: the screen title and the Import word,
 * the search surface, then the rows (docs/design.md §7.1).
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
        SectionHeading(top = LektoSpacing.BetweenRelatedLines)
        PREVIEW_BOOKS.filter { book -> book.recentlyRead }.forEach { book -> PreviewRow(book) }
        SectionHeading(top = LektoSpacing.BetweenBlocks)
        PREVIEW_BOOKS.filterNot { book -> book.recentlyRead }.forEach { book -> PreviewRow(book) }
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

/** A group's heading: the serif section title, drawn as a bar, with the gap that parts the groups. */
@Composable
private fun SectionHeading(top: Dp) {
    Column(Modifier.padding(top = top, bottom = LektoSpacing.BetweenRelatedLines)) {
        Line(colour = MaterialTheme.colorScheme.onBackground, bar = SECTION_HEADING_BAR)
    }
}

/** One row's frame: the vignette, the title and second line, and the rule. Rows part by rhythm alone. */
@Composable
private fun PreviewRow(book: PreviewBook) {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LektoSpacing.BetweenRows),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageVignette(book.language)
        Spacer(Modifier.width(LektoSpacing.BetweenRows))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair)) {
            Line(theme.onBackground, TITLE_BAR)
            Line(theme.onSurfaceVariant, LINE_BAR)
            ProgressRule(bookId = "preview", percent = book.percent)
        }
    }
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

/** A bar where a line of text would be. */
@Composable
private fun Line(colour: Color, bar: LineBar) {
    Box(Modifier.fillMaxWidth(bar.share).height(bar.height).background(colour, LektoRadii.Control))
}

/** A fixed-size block where the vignette's code would be. */
@Composable
private fun Block(colour: Color, size: Pair<Dp, Dp>) {
    val (width, height) = size
    Box(Modifier.width(width).height(height).background(colour, LektoRadii.Control))
}
