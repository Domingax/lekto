package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.PackMetadata
import app.lekto.testkit.testPackMetadata
import kotlin.test.Test

/**
 * The attribution screen (issue #18; ADR-0011): with a pack installed it names
 * the source, the licence and the modifications; without one it still states the
 * licence the pack carries, so the obligation is never hidden behind a download.
 */
@OptIn(ExperimentalTestApi::class)
class AttributionScreenSemanticsTest {

    @Test
    fun anInstalledPackShowsItsSourceLicenceAndModifications() = runComposeUiTest {
        setContent { Attribution(testPackMetadata()) }

        onNodeWithText("Wiktionary contributors").assertIsDisplayed()
        onNodeWithText("Wiktextract / Kaikki machine-readable extract").assertIsDisplayed()
        onNodeWithText("Extracted, trimmed", substring = true).assertExists()
    }

    @Test
    fun withoutAPackItStillStatesTheLicence() = runComposeUiTest {
        setContent { Attribution(null) }

        onNodeWithText("CC BY-SA 4.0", substring = true).assertIsDisplayed()
        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
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
