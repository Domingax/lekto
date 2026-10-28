@file:Suppress("MagicNumber") // The preview lays out the index's frame in design tokens and fixed heights.

package app.lekto.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.theme.Dark
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
 * The Settings index's visual anatomy in the screenshot lane, one sheet per
 * **App theme** (issue #214; docs/design.md §7.5).
 *
 * It is deliberately text-free, like the other goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. Instead it records
 * the frame the index gives the reader — the serif title, the three small muted
 * group labels **Reading**, **Data** and **Application**, and each row's title and
 * current value as ink bars with the chevron that says a screen opens, parted by
 * hairlines that never close a group. The spacing, the hairline and the chevron
 * are the screen's own tokens and icon; only the words are bars. A spacing step
 * or a divider that moves is caught here — the semantics suites cover the text.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsIndexGoldenTest {

    @Test
    fun lightIndex() = indexGolden("light", Light)

    @Test
    fun darkIndex() = indexGolden("dark", Dark)

    @Test
    fun sepiaIndex() = indexGolden("sepia", Sepia)

    private fun indexGolden(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { IndexPreview() } }
        onRoot().captureRoboImage("settings-index-$name.png")
    }
}

/** One row the preview draws: the share of its title bar, and whether it carries a value. */
private data class PreviewRow(val title: Float, val value: Float?)

/** The three groups, in the order the screen renders them. */
private val PREVIEW_GROUPS = listOf(
    listOf(
        PreviewRow(title = 0.44f, value = 0.16f),
        PreviewRow(title = 0.44f, value = 0.18f),
        PreviewRow(title = 0.52f, value = 0.32f),
    ),
    listOf(PreviewRow(title = 0.42f, value = 0.30f), PreviewRow(title = 0.44f, value = 0.34f)),
    listOf(PreviewRow(title = 0.40f, value = null), PreviewRow(title = 0.22f, value = 0.20f)),
)

/** The phone frame: the title, then one block per group. */
@Composable
private fun IndexPreview() {
    Column(
        modifier = Modifier
            .width(SCREEN_WIDTH)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenBlocks),
    ) {
        Bar(width = 0.4f, height = TITLE_HEIGHT)
        PREVIEW_GROUPS.forEach { rows -> GroupPreview(rows) }
    }
}

/** One group: its small muted label, then its rows and the hairlines that part them. */
@Composable
private fun GroupPreview(rows: List<PreviewRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
        Bar(width = GROUP_LABEL_WIDTH, height = LABEL_HEIGHT, muted = true)
        Column {
            rows.forEachIndexed { index, row ->
                RowPreview(row)
                if (index != rows.lastIndex) Hairline()
            }
        }
    }
}

/** One row: its title, its value under it when it has one, and the chevron at the edge. */
@Composable
private fun RowPreview(row: PreviewRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LektoSpacing.TouchTarget)
            .padding(vertical = LektoSpacing.BetweenRows),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            Bar(width = row.title, height = ROW_TITLE_HEIGHT)
            if (row.value != null) Bar(width = row.value, height = ROW_VALUE_HEIGHT, muted = true)
        }
        ChevronPreview()
    }
}

/** The `chevron-right` the row ends with, drawn at its real size and muted tint. */
@Composable
private fun ChevronPreview() {
    Icon(
        imageVector = LektoIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(CHEVRON_SIZE).height(CHEVRON_SIZE),
    )
}

/** A bar where a line of text would be: its share of the width, its height and whether it is muted. */
@Composable
private fun Bar(width: Float, height: Dp, muted: Boolean = false) {
    val colour = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Box(Modifier.fillMaxWidth(width).height(height).background(colour))
}

/** The hairline that parts two rows of one group, never drawn after the last. */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(HAIRLINE_THICKNESS).background(MaterialTheme.colorScheme.outline))
}

/** The preview's phone width: the frame the design's planche gives each screen. */
private val SCREEN_WIDTH = 360.dp

/** The heights that stand in for the title and the two lines of a row. */
private val TITLE_HEIGHT = 22.dp
private const val GROUP_LABEL_WIDTH = 0.14f
private val LABEL_HEIGHT = 11.dp
private val ROW_TITLE_HEIGHT = 16.dp
private val ROW_VALUE_HEIGHT = 12.dp

/** The chevron's frame, the size the icon draws at. */
private val CHEVRON_SIZE = 24.dp
