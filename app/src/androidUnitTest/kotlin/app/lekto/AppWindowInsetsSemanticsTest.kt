package app.lekto

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The same-named twin of `app/desktopTest`'s `AppWindowInsetsSemanticsTest` (the
 * parity rule, issue #73): every screen's chrome clears the window's system UI,
 * proved on the runtime Android uses. This is the lane that matters for the bug
 * the seam was added for — an edge-to-edge Android app draws the top bar under
 * the status bar, and the status bar consumes the touches in that strip.
 *
 * Robolectric's window has no insets of its own, so the test injects one through
 * [App]'s parameter, exactly as the desktop twin does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AppWindowInsetsSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theLibraryChromeClearsTheTopInset() {
        compose.setContent {
            App(environment(InMemoryLibrary()), windowInsets = WindowInsets(top = StatusBarHeight))
        }

        assertTrue(
            "the library title must sit below the status bar",
            compose.onNodeWithText("Library").getUnclippedBoundsInRoot().top >= StatusBarHeight,
        )
        assertTrue(
            "the library's header buttons must sit below the status bar",
            compose.onNodeWithText("Settings").getUnclippedBoundsInRoot().top >= StatusBarHeight,
        )
    }

    @Test
    fun theReaderChromeClearsTheTopInset() {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        compose.setContent {
            App(environment(library), windowInsets = WindowInsets(top = StatusBarHeight))
        }

        compose.onNodeWithText("The Lantern Keeper").performClick()
        compose.waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            compose.onAllNodesWithText("Page 1 of", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(
            "the reader's back button must sit below the status bar",
            compose.onNodeWithText("Library").getUnclippedBoundsInRoot().top >= StatusBarHeight,
        )
    }
}

/** Work on the injected dispatcher crosses a real thread; the Compose default is one second (issue #194). */
private const val AWAIT_DISPATCHED_WORK_MILLIS: Long = 10_000

/** A status bar's height: the inset the test injects, so the layout is deterministic. */
private val StatusBarHeight = 48.dp
