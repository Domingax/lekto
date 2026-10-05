package app.lekto.vocabulary

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.lekto.testkit.testVocabularyEntry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The vocabulary list on a **simulated Android runtime** (issue #71): the
 * same-named twin of `app/desktopTest`'s `VocabularyScreenSemanticsTest`, so the
 * parity rule (issue #73) can see the two lanes together. The list's `LazyColumn`
 * keyed its rows by a `WordKey` (a data class), which Android rejects because a
 * lazy item's key must be Bundle-saveable; the desktop lane never enforced that,
 * so the crash (issue #69) shipped green. Rendering the list here runs the same
 * code on the runtime that broke, so the constraint is tested where it applies.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class VocabularyScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the list renders saved entries on Android`() {
        val entry = testVocabularyEntry()

        compose.setContent {
            MaterialTheme {
                VocabularyScreen(state = VocabularyUiState.Results(listOf(entry), ""))
            }
        }

        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("lanterne").assertIsDisplayed()
    }
}
