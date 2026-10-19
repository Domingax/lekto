package app.lekto

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every screen's chrome clears the window's system UI. Android 15 draws an app
 * that targets SDK 35+ edge to edge, so without this the top bar sits under the
 * status bar — and the system, which owns that strip, consumes its touches: on
 * the library the header buttons answer only a tap below them, and in the reader
 * the *Library* button does not answer at all.
 *
 * The inset is injected rather than read from the platform, because a test
 * window reports none: [App] takes it as a parameter, the deterministic seam
 * (docs/testing.md, "Deterministic seams"). Its same-named Android twin proves
 * the same layout on the runtime Android uses.
 */
@OptIn(ExperimentalTestApi::class)
class AppWindowInsetsSemanticsTest {

    @Test
    fun theLibraryChromeClearsTheTopInset() = runComposeUiTest {
        setContent {
            App(environment(InMemoryLibrary()), windowInsets = WindowInsets(top = StatusBarHeight))
        }

        assertTrue(
            onNodeWithText("Library").getUnclippedBoundsInRoot().top >= StatusBarHeight,
            "the library title must sit below the status bar",
        )
        assertTrue(
            onNodeWithText("Settings").getUnclippedBoundsInRoot().top >= StatusBarHeight,
            "the library's header buttons must sit below the status bar",
        )
    }

    @Test
    fun theReaderChromeClearsTheTopInset() = runComposeUiTest {
        val library = InMemoryLibrary().apply { import("lantern.epub", byteArrayOf(1)) }
        setContent {
            App(environment(library), windowInsets = WindowInsets(top = StatusBarHeight))
        }

        onNodeWithText("The Lantern Keeper").performClick()
        waitUntil(timeoutMillis = AWAIT_DISPATCHED_WORK_MILLIS) {
            onAllNodesWithText("Page 1 of", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(
            onNodeWithText("Library").getUnclippedBoundsInRoot().top >= StatusBarHeight,
            "the reader's back button must sit below the status bar",
        )
    }
}

/** Work on the injected dispatcher crosses a real thread; the Compose default is one second (issue #194). */
private const val AWAIT_DISPATCHED_WORK_MILLIS: Long = 10_000

/** A status bar's height: the inset the test injects, so the layout is deterministic. */
private val StatusBarHeight = 48.dp
