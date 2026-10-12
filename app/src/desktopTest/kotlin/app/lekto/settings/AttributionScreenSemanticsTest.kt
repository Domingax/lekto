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
import app.lekto.core.difficulty.DifficultyMetadata
import app.lekto.testkit.testDifficultyMetadata
import app.lekto.testkit.testPackMetadata
import kotlin.test.Test

/**
 * The attribution screen (issues #18 and #64; ADR-0011, ADR-0029): with a pack or
 * a difficulty table installed it names the source, the licence and the
 * modifications; without one it still states the licence it carries, so the
 * obligation is never hidden behind a download.
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
    fun anInstalledDifficultyTableShowsItsWordfreqAttribution() = runComposeUiTest {
        setContent { Attribution(null, testDifficultyMetadata()) }

        onNodeWithText("wordfreq by Robyn Speer").assertIsDisplayed()
    }

    @Test
    fun withoutAPackItStillStatesTheLicence() = runComposeUiTest {
        setContent { Attribution(null) }

        onNodeWithText("Dictionary data is derived from Wiktionary contributors", substring = true)
            .assertIsDisplayed()
        onNodeWithText("Word-difficulty data is derived from wordfreq", substring = true).assertIsDisplayed()
        onNodeWithText("The offline dictionary isn't installed yet", substring = true).assertIsDisplayed()
        onNodeWithText("The word-difficulty data isn't installed yet", substring = true).assertIsDisplayed()
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
