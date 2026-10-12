package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.PackMetadata
import app.lekto.core.difficulty.DifficultyMetadata
import app.lekto.testkit.testDifficultyMetadata
import app.lekto.testkit.testPackMetadata
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The attribution screen on a **simulated Android runtime** (issues #78 and #64):
 * the same-named twin of `app/desktopTest`'s `AttributionScreenSemanticsTest`, so
 * the parity rule (issue #73) sees the two lanes together. With a pack or a
 * difficulty table installed the screen names the source, the data, the licence
 * and the modifications; without one it still states the licence it carries, so
 * ADR-0011's and ADR-0029's obligations are never hidden behind a download.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AttributionScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `an installed pack shows its source licence and modifications on Android`() {
        compose.setContent { Attribution(testPackMetadata()) }

        compose.onNodeWithText("Wiktionary contributors").assertIsDisplayed()
        compose.onNodeWithText("Wiktextract / Kaikki machine-readable extract").assertIsDisplayed()
        compose.onNodeWithText("Extracted, trimmed", substring = true).assertExists()
    }

    @Test
    fun `an installed difficulty table shows its wordfreq attribution on Android`() {
        compose.setContent { Attribution(null, testDifficultyMetadata()) }

        compose.onNodeWithText("wordfreq by Robyn Speer").assertIsDisplayed()
    }

    @Test
    fun `without a pack it still states the licence on Android`() {
        compose.setContent { Attribution(null) }

        compose.onNodeWithText("Dictionary data is derived from Wiktionary contributors", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("Word-difficulty data is derived from wordfreq", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The offline dictionary isn't installed yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The word-difficulty data isn't installed yet", substring = true).assertIsDisplayed()
    }

    @Composable
    private fun Attribution(metadata: PackMetadata?, difficulty: DifficultyMetadata? = null) {
        MaterialTheme {
            AttributionScreen(
                metadata = metadata,
                difficulty = difficulty,
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
