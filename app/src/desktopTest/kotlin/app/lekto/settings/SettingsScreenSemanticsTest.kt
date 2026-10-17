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
import app.lekto.core.llm.LlmModelsResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.sync.SyncSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The settings screen's behaviour through semantics: the way to the **Languages**
 * surface (issues #146, #149), the vault export/import section (issue #20), and
 * the LLM provider section (issue #88) — choosing a provider, the custom base URL
 * field, the masked key field, save, the inline connection result and key
 * removal. The dictionary pack and the way to attribution left for the
 * **Languages** surface (issue #149), so Settings holds only the app's own
 * configuration.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per control; splitting the class hides the section.
class SettingsScreenSemanticsTest {

    @Test
    fun theDictionarySectionIsGone() = runComposeUiTest {
        settings()

        // The pack and its attribution left for the Languages surface (issue #149).
        onNodeWithText("Dictionary").assertDoesNotExist()
        onNodeWithText("Attribution").assertDoesNotExist()
    }

    @Test
    fun theAppLanguagePickerOffersTheLanguagesTheAppShips() = runComposeUiTest {
        settings(appLanguage = "en")

        onNodeWithTag(APP_LANGUAGE_PICKER_TAG).performScrollTo().performClick()

        onNodeWithTag(appLanguageOptionTag("en")).assertIsDisplayed()
        onNodeWithTag(appLanguageOptionTag("fr")).assertIsDisplayed()
    }

    @Test
    fun theCurrentAppLanguageIsShownByName() = runComposeUiTest {
        settings(appLanguage = "fr")

        onNodeWithText("French").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingAnAppLanguageRaisesTheAction() = runComposeUiTest {
        var chosen: String? = null
        settings(appLanguage = "en", actions = SettingsActions(onSelectAppLanguage = { code -> chosen = code }))

        onNodeWithTag(APP_LANGUAGE_PICKER_TAG).performScrollTo().performClick()
        onNodeWithTag(appLanguageOptionTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theVaultSectionOffersExportAndImport() = runComposeUiTest {
        var exported = false
        var imported = false
        settings(
            vault = VaultUiState(available = true, message = "Vault exported."),
            actions = SettingsActions(onExportVault = { exported = true }, onImportVault = { imported = true }),
        )

        onNodeWithText("Vault exported.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Export vault").performScrollTo().performClick()
        onNodeWithText("Import vault").performScrollTo().performClick()

        assertTrue(exported)
        assertTrue(imported)
    }

    @Test
    fun anUnwiredVaultTransferSaysSoAndDisablesTheActions() = runComposeUiTest {
        settings(vault = VaultUiState(available = false))

        onNodeWithText("Vault export and import aren't available", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aVaultErrorRendersAndDismisses() = runComposeUiTest {
        var dismissed = false
        settings(
            vault = VaultUiState(available = true, error = "That file isn't a Lekto vault."),
            actions = SettingsActions(onDismissVaultMessage = { dismissed = true }),
        )

        onNodeWithText("That file isn't a Lekto vault.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Dismiss").performScrollTo().performClick()

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
        settings(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.CUSTOM, "my-model"),
            ),
        )

        onNodeWithTag(BASE_URL_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aNamedProviderHidesTheBaseUrlField() = runComposeUiTest {
        settings(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"),
            ),
        )

        onNodeWithTag(BASE_URL_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun savingPassesTheTypedKeyToTheController() = runComposeUiTest {
        var saved: String? = null
        settings(
            provider = ProviderUiState(available = true, providerChosen = true),
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
            provider = ProviderUiState(available = true, providerChosen = true),
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
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Success("The provider answered."),
            ),
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
                providerChosen = true,
                result = ProviderResult.Failure("The provider rejected the API key."),
            ),
        )

        onNodeWithText("The provider rejected the API key.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aStoredKeyOffersRemoval() = runComposeUiTest {
        var removed = false
        settings(
            provider = ProviderUiState(available = true, providerChosen = true, hasStoredKey = true),
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
    fun anUnchosenProviderShowsThePlaceholderAndHidesItsControls() = runComposeUiTest {
        settings(provider = ProviderUiState(available = true))

        onNodeWithText("Choose a provider").performScrollTo().assertIsDisplayed()
        onNodeWithTag(MODEL_FIELD_TAG).assertDoesNotExist()
        onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aKeylessProviderOffersNoApiKeyField() = runComposeUiTest {
        settings(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OLLAMA, "llama3"),
            ),
        )

        onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun testingInProgressDisablesTheActions() = runComposeUiTest {
        settings(provider = ProviderUiState(available = true, providerChosen = true, testing = true))

        onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun openingTheModelFieldAsksToLoadTheModelsWithTheTypedKey() = runComposeUiTest {
        var loaded: String? = null
        settings(
            provider = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onLoadModels = { key -> loaded = key }),
        )

        onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performClick()

        assertEquals("sk-live-123", loaded)
    }

    @Test
    fun aLoadedModelListIsOfferedFromTheModelField() = runComposeUiTest {
        var chosen: String? = null
        settings(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"),
                models = listOf("gpt-4o-mini", "gpt-4o"),
            ),
            actions = SettingsActions(onModelChange = { model -> chosen = model }),
        )

        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performClick()
        onNodeWithText("gpt-4o").performClick()

        assertEquals("gpt-4o", chosen)
    }

    @Test
    fun anUnsupportedModelListIsReportedInlineAndLeavesManualEntry() = runComposeUiTest {
        settings(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Failure(LlmModelsResult.UNSUPPORTED),
            ),
        )

        onNodeWithText("doesn't list its models", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingKeyForTheModelListIsShownDiscreetlyBesideTheField() = runComposeUiTest {
        settings(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                modelsHint = LlmModelsResult.MISSING_KEY,
            ),
        )

        onNodeWithText("Enter your API key", substring = true).performScrollTo().assertIsDisplayed()
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
    appLanguage: String = "en",
    vault: VaultUiState = VaultUiState(),
    provider: ProviderUiState = ProviderUiState(),
    sync: SyncUiState = SyncUiState(),
    actions: SettingsActions = SettingsActions(),
) {
    setContent {
        MaterialTheme {
            SettingsScreen(
                state = SettingsUiState(appLanguage = appLanguage, vault = vault, provider = provider, sync = sync),
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
            )
        }
    }
}
