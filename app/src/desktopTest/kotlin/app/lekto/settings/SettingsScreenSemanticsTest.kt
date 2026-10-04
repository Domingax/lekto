package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
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
 * The settings screen's behaviour through semantics: the dictionary's state and
 * an honest message for each, the download action, the inline download failure
 * with a way to dismiss it, the way to the attribution screen (issue #18), and
 * the vault export/import section (issue #20).
 */
@OptIn(ExperimentalTestApi::class)
class SettingsScreenSemanticsTest {

    @Test
    fun aMissingPackSaysSoAndOffersADownload() = runComposeUiTest {
        var downloaded = false
        settings(actions = SettingsActions(onDownload = { downloaded = true }))

        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        onNodeWithText("Download").performClick()

        assertTrue(downloaded)
    }

    @Test
    fun anInstalledPackSaysSoAndOffersADownloadAgain() = runComposeUiTest {
        settings(dictionary = DictionaryUiState(status = DictionaryPackState.Ready(testPackMetadata())))

        onNodeWithText("is installed", substring = true).assertIsDisplayed()
        onNodeWithText("Download again").assertIsDisplayed()
    }

    @Test
    fun anIncompatiblePackTellsTheUserToDownloadAgain() = runComposeUiTest {
        settings(dictionary = DictionaryUiState(status = DictionaryPackState.Incompatible(found = 9, expected = 1)))

        onNodeWithText("format 9", substring = true).assertIsDisplayed()
    }

    @Test
    fun aCorruptPackTellsTheUserToDownloadAgain() = runComposeUiTest {
        settings(dictionary = DictionaryUiState(status = DictionaryPackState.Corrupt("file is not a database")))

        onNodeWithText("damaged", substring = true).assertIsDisplayed()
    }

    @Test
    fun downloadingDisablesTheActionAndReportsProgress() = runComposeUiTest {
        settings(dictionary = DictionaryUiState(installing = true))

        onNodeWithText("Downloading", substring = true).assertIsDisplayed()
        onNodeWithText("Download").assertIsNotEnabled()
    }

    @Test
    fun aDownloadFailureShowsAndDismisses() = runComposeUiTest {
        var dismissed = false
        settings(
            dictionary = DictionaryUiState(error = "The dictionary download failed."),
            actions = SettingsActions(onDismissError = { dismissed = true }),
        )

        onNodeWithText("The dictionary download failed.").assertIsDisplayed()
        onNodeWithText("Dismiss").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun attributionIsReachable() = runComposeUiTest {
        var opened = false
        settings(actions = SettingsActions(onOpenAttribution = { opened = true }))

        onNodeWithText("Attribution").performClick()

        assertTrue(opened)
    }

    @Test
    fun theVaultSectionOffersExportAndImport() = runComposeUiTest {
        var exported = false
        var imported = false
        settings(
            vault = VaultUiState(available = true, message = "Vault exported."),
            actions = SettingsActions(onExportVault = { exported = true }, onImportVault = { imported = true }),
        )

        onNodeWithText("Vault exported.").assertIsDisplayed()
        onNodeWithText("Export vault").performClick()
        onNodeWithText("Import vault").performClick()

        assertTrue(exported)
        assertTrue(imported)
    }

    @Test
    fun anUnwiredVaultTransferSaysSoAndDisablesTheActions() = runComposeUiTest {
        settings(vault = VaultUiState(available = false))

        onNodeWithText("aren't available", substring = true).assertIsDisplayed()
        onNodeWithText("Export vault").assertIsNotEnabled()
        onNodeWithText("Import vault").assertIsNotEnabled()
    }

    @Test
    fun aVaultErrorRendersAndDismisses() = runComposeUiTest {
        var dismissed = false
        settings(
            vault = VaultUiState(available = true, error = "That file isn't a Lekto vault."),
            actions = SettingsActions(onDismissVaultMessage = { dismissed = true }),
        )

        onNodeWithText("That file isn't a Lekto vault.").assertIsDisplayed()
        onNodeWithText("Dismiss").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun exportingInProgressDisablesTheActions() = runComposeUiTest {
        settings(vault = VaultUiState(available = true, transferring = true))

        onNodeWithText("Export vault").assertIsNotEnabled()
        onNodeWithText("Import vault").assertIsNotEnabled()
    }
}

/** Renders the settings screen in the app theme, sized like a phone. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.settings(
    dictionary: DictionaryUiState = DictionaryUiState(),
    vault: VaultUiState = VaultUiState(),
    actions: SettingsActions = SettingsActions(),
) {
    setContent {
        MaterialTheme {
            SettingsScreen(
                state = SettingsUiState(dictionary, vault),
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
            )
        }
    }
}
