package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.dictionary.DictionaryUiState
import app.lekto.testkit.testPackMetadata
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The settings screen on a **simulated Android runtime** (issue #77): the
 * same-named twin of `app/desktopTest`'s `SettingsScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The dictionary section's
 * status and download, the way to attribution, and the vault section's
 * export/import controls are the same behaviours the desktop lane proves, run
 * here on the runtime the app ships on.
 *
 * It is a deliberate mirror, not a shared body: the desktop lane drives
 * `runComposeUiTest` and this one a Robolectric `createAndroidComposeRule`, and
 * `app` has no test source set both lanes compile, so the two files are kept in
 * step by hand (issue #77; cf. the thinner vocabulary twin, which only guards an
 * Android-only constraint).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class SettingsScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun aMissingPackSaysSoAndOffersADownload() {
        var downloaded = false
        render(actions = SettingsActions(onDownload = { downloaded = true }))

        compose.onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download").performClick()

        assertTrue(downloaded)
    }

    @Test
    fun anInstalledPackSaysSoAndOffersADownloadAgain() {
        render(dictionary = DictionaryUiState(status = DictionaryPackState.Ready(testPackMetadata())))

        compose.onNodeWithText("is installed", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download again").assertIsDisplayed()
    }

    @Test
    fun anIncompatiblePackTellsTheUserToDownloadAgain() {
        render(dictionary = DictionaryUiState(status = DictionaryPackState.Incompatible(found = 9, expected = 1)))

        compose.onNodeWithText("format 9", substring = true).assertIsDisplayed()
    }

    @Test
    fun aCorruptPackTellsTheUserToDownloadAgain() {
        render(dictionary = DictionaryUiState(status = DictionaryPackState.Corrupt("file is not a database")))

        compose.onNodeWithText("damaged", substring = true).assertIsDisplayed()
    }

    @Test
    fun downloadingDisablesTheActionAndReportsProgress() {
        render(dictionary = DictionaryUiState(installing = true))

        compose.onNodeWithText("Downloading", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Download").assertIsNotEnabled()
    }

    @Test
    fun aDownloadFailureShowsAndDismisses() {
        var dismissed = false
        render(
            dictionary = DictionaryUiState(error = "The dictionary download failed."),
            actions = SettingsActions(onDismissError = { dismissed = true }),
        )

        compose.onNodeWithText("The dictionary download failed.").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun attributionIsReachable() {
        var opened = false
        render(actions = SettingsActions(onOpenAttribution = { opened = true }))

        compose.onNodeWithText("Attribution").performClick()

        assertTrue(opened)
    }

    @Test
    fun theVaultSectionOffersExportAndImport() {
        var exported = false
        var imported = false
        render(
            vault = VaultUiState(available = true, message = "Vault exported."),
            actions = SettingsActions(onExportVault = { exported = true }, onImportVault = { imported = true }),
        )

        compose.onNodeWithText("Vault exported.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export vault").performScrollTo().performClick()
        compose.onNodeWithText("Import vault").performScrollTo().performClick()

        assertTrue(exported)
        assertTrue(imported)
    }

    @Test
    fun anUnwiredVaultTransferSaysSoAndDisablesTheActions() {
        render(vault = VaultUiState(available = false))

        compose.onNodeWithText("aren't available", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aVaultErrorRendersAndDismisses() {
        var dismissed = false
        render(
            vault = VaultUiState(available = true, error = "That file isn't a Lekto vault."),
            actions = SettingsActions(onDismissVaultMessage = { dismissed = true }),
        )

        compose.onNodeWithText("That file isn't a Lekto vault.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun exportingInProgressDisablesTheActions() {
        render(vault = VaultUiState(available = true, transferring = true))

        compose.onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    /** Renders the settings screen in the app theme, sized like a phone. */
    private fun render(
        dictionary: DictionaryUiState = DictionaryUiState(),
        vault: VaultUiState = VaultUiState(),
        actions: SettingsActions = SettingsActions(),
    ) {
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState(dictionary, vault),
                    actions = actions,
                    modifier = Modifier.size(width = 360.dp, height = 720.dp),
                )
            }
        }
    }
}
