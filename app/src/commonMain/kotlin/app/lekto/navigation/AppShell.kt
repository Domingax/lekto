package app.lekto.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.icons.BookOpen
import app.lekto.ui.icons.Bookmark
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.Settings

/** The test tag on the shell's navigation, whichever shape it takes, so a test can find it in both lanes. */
internal const val SHELL_NAV_TAG: String = "shell-nav"

/** The test tag on the area the current screen fills, so a test can tell where the navigation sits. */
internal const val SHELL_SCREEN_TAG: String = "shell-screen"

/** The test tag on one section's navigation item, so a UI test can reach exactly one. */
internal fun shellSectionTag(section: ShellSection): String = "shell-section-${section.name.lowercase()}"

/**
 * The width at which the three sections stop being a bottom bar and become a
 * fixed left column (docs/design.md §6): a phone is narrower than this, a desktop
 * window is not. The same build serves both, so the shape follows the room the
 * window gives it rather than the platform it runs on.
 */
private val ShellRailBreakpoint = 600.dp

/** The left column's width: an icon and a word, with the screen's own edge padding. */
private val ShellRailWidth = 200.dp

/** The bottom bar's height: a 24 dp icon over its label, well inside the 48 dp touch target. */
private val ShellBarHeight = 64.dp

/**
 * The navigation shell (issue #185; docs/design.md §6): the three sections as a
 * bar under the screen on a phone, and as a fixed column beside it on desktop,
 * with the current one in ink and the others in the theme's muted ink.
 *
 * It is chrome only — the screen inside [content] owns its title and its actions —
 * and it is never shown over the reader, which leaves the shell and is
 * full-screen. The bar is the only way between sections, which is why importing
 * stays the Library's action rather than a fourth item.
 *
 * An item is a [selectable] with the tab role, so a screen reader announces the
 * current section and its label names the icon (docs/design.md §9).
 */
@Composable
internal fun AppShell(
    current: ShellSection,
    onSelect: (ShellSection) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val navigation = ShellNavigation(current, onSelect)
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= ShellRailBreakpoint) {
            Row(Modifier.fillMaxSize()) {
                ShellRail(navigation)
                VerticalDivider(color = MaterialTheme.colorScheme.outline)
                Box(Modifier.fillMaxHeight().weight(1f).testTag(SHELL_SCREEN_TAG)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).testTag(SHELL_SCREEN_TAG)) { content() }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                ShellBar(navigation)
            }
        }
    }
}

/**
 * The shell's live state and its one tap, bundled so both shapes and their items
 * read one value rather than a pair of parameters.
 */
private class ShellNavigation(val current: ShellSection, val onSelect: (ShellSection) -> Unit) {

    /** The section's colour: ink while it is the section the reader is in, muted ink otherwise. */
    @Composable
    fun colour(section: ShellSection): Color =
        if (section == current) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
}

/** The desktop shape: a fixed column at the left, its items stacked from the top. */
@Composable
private fun ShellRail(navigation: ShellNavigation) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(ShellRailWidth)
            .testTag(SHELL_NAV_TAG)
            .padding(vertical = LektoSpacing.ScreenEdge),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
    ) {
        ShellSection.entries.forEach { section ->
            ShellRailItem(navigation, section)
        }
    }
}

/** The phone shape: three items sharing one row at the bottom, each a finger wide. */
@Composable
private fun ShellBar(navigation: ShellNavigation) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ShellBarHeight)
            .testTag(SHELL_NAV_TAG),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShellSection.entries.forEach { section ->
            ShellBarItem(navigation, section, Modifier.weight(1f))
        }
    }
}

/** One item of the left column: its icon beside its word. */
@Composable
private fun ShellRailItem(navigation: ShellNavigation, section: ShellSection) {
    val colour = navigation.colour(section)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shellItem(navigation, section)
            .padding(horizontal = LektoSpacing.ScreenEdge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        ShellIcon(section, colour)
        ShellLabel(section, current = navigation.current == section, colour = colour)
    }
}

/** One item of the bottom bar: its icon over its word, centred. */
@Composable
private fun ShellBarItem(navigation: ShellNavigation, section: ShellSection, modifier: Modifier = Modifier) {
    val colour = navigation.colour(section)
    Column(
        modifier = modifier
            .shellItem(navigation, section)
            .padding(vertical = LektoSpacing.BetweenRelatedLines),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
    ) {
        ShellIcon(section, colour)
        ShellLabel(section, current = navigation.current == section, colour = colour)
    }
}

/** The item's shared shape: a full touch target, selectable, and findable by a test. */
private fun Modifier.shellItem(navigation: ShellNavigation, section: ShellSection): Modifier = this
    .heightIn(min = LektoSpacing.TouchTarget)
    .handCursor()
    .selectable(
        selected = navigation.current == section,
        role = Role.Tab,
        onClick = { navigation.onSelect(section) },
    )
    .testTag(shellSectionTag(section))

/** A section's icon at 24 dp, decorative: the label beside it names it (docs/design.md §9). */
@Composable
private fun ShellIcon(section: ShellSection, colour: Color) {
    Icon(imageVector = section.icon, contentDescription = null, tint = colour)
}

/** A section's word: 11 sp, medium while it is the section the reader is in (docs/design.md §3). */
@Composable
private fun ShellLabel(section: ShellSection, current: Boolean, colour: Color) {
    Text(
        text = LocalAppTexts.current[section.label],
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
        ),
        color = colour,
        textAlign = TextAlign.Center,
    )
}

/** The Lucide icon each section is known by (docs/design.md §5, §6): recognisable at 24 dp. */
private val ShellSection.icon: ImageVector
    get() = when (this) {
        ShellSection.LIBRARY -> LektoIcons.BookOpen
        ShellSection.VOCABULARY -> LektoIcons.Bookmark
        ShellSection.SETTINGS -> LektoIcons.Settings
    }
