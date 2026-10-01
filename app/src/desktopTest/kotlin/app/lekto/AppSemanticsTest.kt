package app.lekto

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test

/**
 * The UI-semantics suite.
 *
 * It exercises the shared Compose UI the way a reader's assistive technology
 * would — through semantics, not pixels — so it is fast and stable enough for
 * the inner loop. Screenshot goldens are a separate, slower lane. The suite runs
 * on the JVM via `:app:desktopTest`.
 */
@OptIn(ExperimentalTestApi::class)
class AppSemanticsTest {

    @Test
    fun showsTheReader() = runComposeUiTest {
        setContent { App(segmenter = WhitespaceTextSegmenter()) }

        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        onNodeWithText("Next page").assertIsDisplayed()
    }
}
