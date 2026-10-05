package app.lekto

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app launches and navigates on a **real Android runtime** (issue #71), the
 * nightly-lane smoke test that the fast Robolectric host lane cannot replace: it
 * starts the real [MainActivity] on an emulator, where the platform
 * integrations (SAF, TTS, Keystore) actually run.
 */
@RunWith(AndroidJUnit4::class)
class AppInstrumentedTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theAppOpensOnTheLibrary() {
        compose.onNodeWithText("Library").assertIsDisplayed()
    }

    @Test
    fun theLibraryNavigatesToTheVocabularyList() {
        compose.onNodeWithText("Vocabulary").performClick()

        compose.onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()
    }
}
