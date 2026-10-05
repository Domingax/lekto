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
import app.lekto.testkit.testPackMetadata
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The attribution screen on a **simulated Android runtime** (issue #78): the
 * same-named twin of `app/desktopTest`'s `AttributionScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. With a pack installed the
 * screen names the source, the data, the licence and the modifications; without
 * one it still states the licence the pack carries, so ADR-0011's obligation is
 * never hidden behind a download. Rendering it here runs the same composable on
 * the runtime Android uses, so an Android-only constraint fails here rather than
 * on a device.
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
    fun `without a pack it still states the licence on Android`() {
        compose.setContent { Attribution(null) }

        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertIsDisplayed()
        compose.onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
    }

    @Composable
    private fun Attribution(metadata: PackMetadata?) {
        MaterialTheme {
            AttributionScreen(
                metadata = metadata,
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
