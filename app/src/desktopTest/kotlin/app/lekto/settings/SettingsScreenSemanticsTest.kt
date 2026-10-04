package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.dictionary.DictionaryUiState
import app.lekto.testkit.testPackMetadata
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The settings screen's behaviour through semantics (issue #18): the dictionary's
 * state and an honest message for each, the download action, the inline download
 * failure with a way to dismiss it, and the way to the attribution screen.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenSemanticsTest {

    @Test
    fun aMissingPackSaysSoAndOffersADownload() = runComposeUiTest {
        var downloaded = false
        setContent { Settings(DictionaryUiState(), onDownload = { downloaded = true }) }

        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        onNodeWithText("Download").performClick()

        assertTrue(downloaded)
    }

    @Test
    fun anInstalledPackSaysSoAndOffersADownloadAgain() = runComposeUiTest {
        val state = DictionaryUiState(status = DictionaryPackState.Ready(testPackMetadata()))

        setContent { Settings(state) }

        onNodeWithText("is installed", substring = true).assertIsDisplayed()
        onNodeWithText("Download again").assertIsDisplayed()
    }

    @Test
    fun anIncompatiblePackTellsTheUserToDownloadAgain() = runComposeUiTest {
        setContent { Settings(DictionaryUiState(status = DictionaryPackState.Incompatible(found = 9, expected = 1))) }

        onNodeWithText("format 9", substring = true).assertIsDisplayed()
    }

    @Test
    fun aCorruptPackTellsTheUserToDownloadAgain() = runComposeUiTest {
        setContent { Settings(DictionaryUiState(status = DictionaryPackState.Corrupt("file is not a database"))) }

        onNodeWithText("damaged", substring = true).assertIsDisplayed()
    }

    @Test
    fun downloadingDisablesTheActionAndReportsProgress() = runComposeUiTest {
        setContent { Settings(DictionaryUiState(installing = true)) }

        onNodeWithText("Downloading", substring = true).assertIsDisplayed()
        onNodeWithText("Download").assertIsNotEnabled()
    }

    @Test
    fun aDownloadFailureShowsAndDismisses() = runComposeUiTest {
        var dismissed = false
        setContent {
            Settings(DictionaryUiState(error = "The dictionary download failed."), onDismiss = { dismissed = true })
        }

        onNodeWithText("The dictionary download failed.").assertIsDisplayed()
        onNodeWithText("Dismiss").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun attributionIsReachable() = runComposeUiTest {
        var opened = false
        setContent { Settings(DictionaryUiState(), onOpenAttribution = { opened = true }) }

        onNodeWithText("Attribution").performClick()

        assertTrue(opened)
    }

    @Composable
    private fun Settings(
        state: DictionaryUiState,
        onDownload: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onOpenAttribution: () -> Unit = {},
    ) {
        MaterialTheme {
            SettingsScreen(
                state = state,
                actions = SettingsActions(
                    onDownload = onDownload,
                    onDismissError = onDismiss,
                    onOpenAttribution = onOpenAttribution,
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
