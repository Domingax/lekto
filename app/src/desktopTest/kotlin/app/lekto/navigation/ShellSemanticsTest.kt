package app.lekto.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.theme.LektoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The navigation shell's shape and behaviour (issue #185; docs/design.md §6): the
 * three sections and no fourth, the current one marked, and — on this lane — the
 * desktop shape, a fixed left column beside the screen rather than a bar under
 * it. The Android twin proves the bar; the same-named twin is the parity rule
 * (issue #73).
 *
 * The reader is not here: it leaves the shell, and `AppSemanticsTest` proves it
 * covers the bar.
 */
@OptIn(ExperimentalTestApi::class)
class ShellSemanticsTest {

    @Test
    fun theThreeSectionsAndNoFourth() = runComposeUiTest {
        setContent { Shell(ShellSection.LIBRARY) }

        ShellSection.entries.forEach { section ->
            onNodeWithTag(shellSectionTag(section)).assertIsDisplayed()
        }
        // Import is the Library's action and Languages an entry inside Settings:
        // neither is a place the bar offers (docs/design.md §6).
        onNodeWithText("Import").assertDoesNotExist()
        onNodeWithText("Languages").assertDoesNotExist()
    }

    @Test
    fun theCurrentSectionIsMarkedAndTheOthersAreNot() = runComposeUiTest {
        setContent { Shell(ShellSection.VOCABULARY) }

        onNodeWithTag(shellSectionTag(ShellSection.VOCABULARY)).assertIsSelected()
        onNodeWithTag(shellSectionTag(ShellSection.LIBRARY)).assertIsNotSelected()
        onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).assertIsNotSelected()
    }

    @Test
    fun tappingASectionRaisesIt() = runComposeUiTest {
        var chosen: ShellSection? = null
        setContent { Shell(ShellSection.LIBRARY, onSelect = { section -> chosen = section }) }

        onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).performClick()

        assertEquals(ShellSection.SETTINGS, chosen)
    }

    @Test
    fun theColumnIsBesideTheScreenNotUnderIt() = runComposeUiTest {
        setContent { Shell(ShellSection.LIBRARY) }

        val root = onRoot().getUnclippedBoundsInRoot()
        val nav = onNodeWithTag(SHELL_NAV_TAG).getUnclippedBoundsInRoot()
        val screen = onNodeWithTag(SHELL_SCREEN_TAG).getUnclippedBoundsInRoot()

        // A column: full height at the window's left edge, the screen to its right.
        assertTrue(nav.left == 0.dp, "the column starts at the window's edge: ${nav.left}")
        assertTrue(
            nav.bottom - nav.top >= root.bottom - root.top,
            "the column runs the window's height: $nav vs $root",
        )
        assertTrue(screen.left >= nav.right, "the screen starts after the column: $screen vs $nav")
        // A touch target is never shorter than 48 dp (docs/design.md §9).
        assertTrue((nav.bottom - nav.top) / ShellSection.entries.size >= 48.dp, "each section is a finger high: $nav")
    }
}

/** The shell over a placeholder screen, so its shape and its items are what the test measures. */
@Composable
private fun Shell(current: ShellSection, onSelect: (ShellSection) -> Unit = {}) {
    LektoTheme {
        AppShell(current = current, onSelect = onSelect) { Text("screen") }
    }
}
