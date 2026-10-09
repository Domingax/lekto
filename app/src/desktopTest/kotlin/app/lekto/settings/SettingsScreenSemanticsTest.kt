package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.sync.SyncSettings
import app.lekto.dictionary.DictionaryUiState
import app.lekto.testkit.testPackMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings screen's behaviour through semantics: the dictionary's state and
 * an honest message for each, the download action, the inline download failure
 * with a way to dismiss it, the way to the attribution screen (issue #18), the
 * vault export/import section (issue #20), and the LLM provider section
 * (issue #88) — choosing a provider, the custom base URL field, the masked key
 * field, save, the inline connection result and key removal.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One screen, one test per control; splitting the class hides the section.
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

    @Test
    fun choosingAProviderRaisesIt() = runComposeUiTest {
        var chosen: LlmProvider? = null
        settings(
            provider = ProviderUiState(available = true),
            actions = SettingsActions(onSelectProvider = { provider -> chosen = provider }),
        )

        onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        onNodeWithText("Anthropic").performClick()

        assertEquals(LlmProvider.ANTHROPIC, chosen)
    }

    @Test
    fun aCustomProviderOffersABaseUrlField() = runComposeUiTest {
        settings(provider = ProviderUiState(true, config = LlmProviderConfig(LlmProvider.CUSTOM, "my-model")))

        onNodeWithTag(BASE_URL_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aNamedProviderHidesTheBaseUrlField() = runComposeUiTest {
        settings(provider = ProviderUiState(true, config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini")))

        onNodeWithTag(BASE_URL_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun savingPassesTheTypedKeyToTheController() = runComposeUiTest {
        var saved: String? = null
        settings(
            provider = ProviderUiState(available = true),
            actions = SettingsActions(onSaveProvider = { key -> saved = key }),
        )

        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performTextInput("gpt-4o-mini")
        onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        onNodeWithText("Save").performScrollTo().performClick()

        assertEquals("sk-live-123", saved)
    }

    @Test
    fun testingPassesTheTypedKeyToTheController() = runComposeUiTest {
        var tested: String? = null
        settings(
            provider = ProviderUiState(available = true),
            actions = SettingsActions(onTestProvider = { key -> tested = key }),
        )

        onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().performClick()

        assertEquals("sk-live-123", tested)
    }

    @Test
    fun aSuccessfulConnectionIsReportedAndDismissed() = runComposeUiTest {
        var dismissed = false
        settings(
            provider = ProviderUiState(available = true, result = ProviderResult.Success("The provider answered.")),
            actions = SettingsActions(onDismissProviderResult = { dismissed = true }),
        )

        onNodeWithText("The provider answered.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(PROVIDER_DISMISS_TAG).performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun aFailedConnectionIsReportedInline() = runComposeUiTest {
        settings(
            provider = ProviderUiState(
                available = true,
                result = ProviderResult.Failure("The provider rejected the API key."),
            ),
        )

        onNodeWithText("The provider rejected the API key.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aStoredKeyOffersRemoval() = runComposeUiTest {
        var removed = false
        settings(
            provider = ProviderUiState(available = true, hasStoredKey = true),
            actions = SettingsActions(onRemoveProviderKey = { removed = true }),
        )

        onNodeWithText("A key is stored.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Remove key").performScrollTo().performClick()

        assertTrue(removed)
    }

    @Test
    fun anUnwiredProviderSaysSoAndHidesItsControls() = runComposeUiTest {
        settings(provider = ProviderUiState(available = false))

        onNodeWithText("Connecting an LLM provider", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun aKeylessProviderOffersNoApiKeyField() = runComposeUiTest {
        settings(provider = ProviderUiState(true, config = LlmProviderConfig(LlmProvider.OLLAMA, "llama3")))

        onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun testingInProgressDisablesTheActions() = runComposeUiTest {
        settings(provider = ProviderUiState(available = true, testing = true))

        onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun syncAlwaysWarnsAgainstFolderSyncTools() = runComposeUiTest {
        settings()

        onNodeWithText("folder-sync tool", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithText("corrupt", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnwiredSyncSaysSoAndHidesItsControls() = runComposeUiTest {
        settings(sync = SyncUiState(available = false))

        onNodeWithText("Sync isn't available", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aConfiguredSyncShowsItsStatusAndEndpoint() = runComposeUiTest {
        settings(
            sync = SyncUiState(
                available = true,
                config = SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            ),
        )

        onNodeWithText("Sync is off.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEnabledSyncSaysSyncIsOn() = runComposeUiTest {
        settings(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                hasStoredPassword = true,
            ),
        )

        onNodeWithText("Sync is on.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(SYNC_DISABLE_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingSyncPassesTheTypedPasswordToTheController() = runComposeUiTest {
        var saved: String? = null
        settings(
            sync = SyncUiState(available = true),
            actions = SettingsActions(onSaveSync = { password -> saved = password }),
        )

        onNodeWithTag(SYNC_PASSWORD_FIELD_TAG).performScrollTo().performTextInput("app-password")
        onNodeWithTag(SYNC_SAVE_TAG).performScrollTo().performClick()

        assertEquals("app-password", saved)
    }

    @Test
    fun testingSyncPassesTheTypedPasswordToTheController() = runComposeUiTest {
        var tested: String? = null
        settings(
            sync = SyncUiState(available = true),
            actions = SettingsActions(onTestSync = { password -> tested = password }),
        )

        onNodeWithTag(SYNC_PASSWORD_FIELD_TAG).performScrollTo().performTextInput("app-password")
        onNodeWithTag(SYNC_TEST_TAG).performScrollTo().performClick()

        assertEquals("app-password", tested)
    }

    @Test
    fun enablingSyncRaisesTheAction() = runComposeUiTest {
        var enabled = false
        settings(
            sync = SyncUiState(available = true, config = configured()),
            actions = SettingsActions(onEnableSync = { enabled = true }),
        )

        onNodeWithTag(SYNC_ENABLE_TAG).performScrollTo().performClick()

        assertTrue(enabled)
    }

    @Test
    fun disablingSyncRaisesTheAction() = runComposeUiTest {
        var disabled = false
        settings(
            sync = SyncUiState(available = true, config = configured(enabled = true)),
            actions = SettingsActions(onDisableSync = { disabled = true }),
        )

        onNodeWithTag(SYNC_DISABLE_TAG).performScrollTo().performClick()

        assertTrue(disabled)
    }

    @Test
    fun syncNowIsOfferedOnlyWhenSyncIsOn() = runComposeUiTest {
        settings(sync = SyncUiState(available = true, config = configured()))

        onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun syncNowRaisesTheActionWhenSyncIsOn() = runComposeUiTest {
        var synced = false
        settings(
            sync = SyncUiState(available = true, config = configured(enabled = true)),
            actions = SettingsActions(onSyncNow = { synced = true }),
        )

        onNodeWithTag(SYNC_NOW_TAG).performScrollTo().performClick()

        assertTrue(synced)
    }

    @Test
    fun theLastSyncOutcomeIsShown() = runComposeUiTest {
        settings(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Success("Synced: uploaded 1."),
            ),
        )

        onNodeWithText("Synced: uploaded 1.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFailedSyncIsShownInPlainLanguage() = runComposeUiTest {
        settings(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Failure("The last sync didn't finish: the server is unreachable"),
            ),
        )

        onNodeWithText("The last sync didn't finish", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSyncResultRendersAndDismisses() = runComposeUiTest {
        var dismissed = false
        settings(
            sync = SyncUiState(available = true, result = SyncResult.Success("Saved.")),
            actions = SettingsActions(onDismissSyncResult = { dismissed = true }),
        )

        onNodeWithText("Saved.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(SYNC_DISMISS_TAG).performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun disconnectingRaisesTheAction() = runComposeUiTest {
        var disconnected = false
        settings(
            sync = SyncUiState(available = true, config = configured()),
            actions = SettingsActions(onDisconnectSync = { disconnected = true }),
        )

        onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().performClick()

        assertTrue(disconnected)
    }

    @Test
    fun syncingInProgressDisablesTheControls() = runComposeUiTest {
        settings(sync = SyncUiState(available = true, config = configured(enabled = true), syncing = true))

        onNodeWithTag(SYNC_SAVE_TAG).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(SYNC_TEST_TAG).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
        onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().assertIsNotEnabled()
    }
}

/** A usable endpoint with the password stored, for the sync section's tests. */
private fun configured(enabled: Boolean = false): SyncSettings =
    SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = enabled)

/** Renders the settings screen in the app theme, sized like a phone. */
@Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.settings(
    dictionary: DictionaryUiState = DictionaryUiState(),
    vault: VaultUiState = VaultUiState(),
    provider: ProviderUiState = ProviderUiState(),
    sync: SyncUiState = SyncUiState(),
    actions: SettingsActions = SettingsActions(),
) {
    setContent {
        MaterialTheme {
            SettingsScreen(
                state = SettingsUiState(dictionary, vault, provider, sync),
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
            )
        }
    }
}
