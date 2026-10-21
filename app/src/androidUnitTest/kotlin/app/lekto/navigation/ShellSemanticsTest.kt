package app.lekto.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lekto.theme.LektoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The same-named twin of `app/desktopTest`'s `ShellSemanticsTest` (the parity
 * rule, issue #73): the shell's three sections, the current one marked, and — on
 * the runtime Android uses, a phone — the shape a phone gets, a bar under the
 * screen rather than a column beside it (issue #185; docs/design.md §6).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class ShellSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theThreeSectionsAndNoFourth() {
        compose.setContent { Shell(ShellSection.LIBRARY) }

        ShellSection.entries.forEach { section ->
            compose.onNodeWithTag(shellSectionTag(section)).assertIsDisplayed()
        }
        // Import is the Library's action and Languages an entry inside Settings:
        // neither is a place the bar offers (docs/design.md §6).
        compose.onNodeWithText("Import").assertDoesNotExist()
        compose.onNodeWithText("Languages").assertDoesNotExist()
    }

    @Test
    fun theCurrentSectionIsMarkedAndTheOthersAreNot() {
        compose.setContent { Shell(ShellSection.VOCABULARY) }

        compose.onNodeWithTag(shellSectionTag(ShellSection.VOCABULARY)).assertIsSelected()
        compose.onNodeWithTag(shellSectionTag(ShellSection.LIBRARY)).assertIsNotSelected()
        compose.onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).assertIsNotSelected()
    }

    @Test
    fun tappingASectionRaisesIt() {
        var chosen: ShellSection? = null
        compose.setContent { Shell(ShellSection.LIBRARY, onSelect = { section -> chosen = section }) }

        compose.onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).performClick()

        assertEquals(ShellSection.SETTINGS, chosen)
    }

    @Test
    fun theBarIsUnderTheScreenNotBesideIt() {
        compose.setContent { Shell(ShellSection.LIBRARY) }

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val nav = compose.onNodeWithTag(SHELL_NAV_TAG).getUnclippedBoundsInRoot()
        val screen = compose.onNodeWithTag(SHELL_SCREEN_TAG).getUnclippedBoundsInRoot()

        // A bar: the window's full width at its bottom edge, the screen above it.
        assertTrue(
            "the bar runs the window's width: $nav vs $root",
            nav.right - nav.left >= root.right - root.left,
        )
        assertTrue("the screen ends where the bar starts: $screen vs $nav", screen.bottom <= nav.top)
        // A touch target is never shorter than 48 dp (docs/design.md §9), measured
        // on an item rather than on the bar that holds it.
        val item = compose.onNodeWithTag(shellSectionTag(ShellSection.SETTINGS)).getUnclippedBoundsInRoot()
        assertTrue("a section is a finger high: $item", item.bottom - item.top >= 48.dp)
        assertTrue("a section is a finger wide: $item", item.right - item.left >= 48.dp)
    }
}

/** The shell over a placeholder screen, so its shape and its items are what the test measures. */
@Composable
private fun Shell(current: ShellSection, onSelect: (ShellSection) -> Unit = {}) {
    LektoTheme {
        AppShell(current = current, onSelect = onSelect) { Text("screen") }
    }
}
