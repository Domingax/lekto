package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lekto.core.llm.LlmModelsResult
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.core.sync.SyncSettings
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The settings screen on a **simulated Android runtime** (issue #77): the
 * same-named twin of `app/desktopTest`'s `SettingsScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. The way to the
 * **Languages** surface (issue #149), the vault section's export/import controls,
 * and the LLM provider section (issue #88) are the same behaviours the desktop
 * lane proves, run here on the runtime the app ships on.
 *
 * It is a deliberate mirror, not a shared body: the desktop lane drives
 * `runComposeUiTest` and this one a Robolectric `createAndroidComposeRule`, and
 * `app` has no test source set both lanes compile, so the two files are kept in
 * step by hand (issue #77).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per control; splitting hides the section.
class SettingsScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theDictionarySectionIsGone() {
        render()

        // The pack and its attribution left for the Languages surface (issue #149).
        compose.onNodeWithText("Dictionary").assertDoesNotExist()
        compose.onNodeWithText("Attribution").assertDoesNotExist()
    }

    @Test
    fun theAppLanguagePickerOffersTheLanguagesTheAppShips() {
        render(appLanguage = "en")

        compose.onNodeWithTag(APP_LANGUAGE_PICKER_TAG).performScrollTo().performClick()

        compose.onNodeWithTag(appLanguageOptionTag("en")).assertIsDisplayed()
        compose.onNodeWithTag(appLanguageOptionTag("fr")).assertIsDisplayed()
    }

    @Test
    fun theCurrentAppLanguageIsShownByName() {
        render(appLanguage = "fr")

        compose.onNodeWithText("French").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun choosingAnAppLanguageRaisesTheAction() {
        var chosen: String? = null
        render(appLanguage = "en", actions = SettingsActions(onSelectAppLanguage = { code -> chosen = code }))

        compose.onNodeWithTag(APP_LANGUAGE_PICKER_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(appLanguageOptionTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theVaultSectionOffersExportAndImport() {
        var exported = false
        var imported = false
        render(
            vault = VaultUiState(available = true, message = AppMessage.core("Vault exported.")),
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

        compose.onNodeWithText("Vault export and import aren't available", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Export vault").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Import vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aVaultErrorRendersAndDismisses() {
        var dismissed = false
        render(
            vault = VaultUiState(available = true, error = AppMessage.core("That file isn't a Lekto vault.")),
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

    @Test
    fun choosingAProviderRaisesIt() {
        var chosen: LlmProvider? = null
        render(
            provider = ProviderUiState(available = true),
            actions = SettingsActions(onSelectProvider = { provider -> chosen = provider }),
        )

        compose.onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Anthropic").performClick()

        assertEquals(LlmProvider.ANTHROPIC, chosen)
    }

    @Test
    fun aCustomProviderOffersABaseUrlField() {
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.CUSTOM, "my-model"),
            ),
        )

        compose.onNodeWithTag(BASE_URL_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aNamedProviderHidesTheBaseUrlField() {
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"),
            ),
        )

        compose.onNodeWithTag(BASE_URL_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun savingPassesTheTypedKeyToTheController() {
        var saved: String? = null
        render(
            provider = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onSaveProvider = { key -> saved = key }),
        )

        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performTextInput("gpt-4o-mini")
        compose.onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals("sk-live-123", saved)
    }

    @Test
    fun testingPassesTheTypedKeyToTheController() {
        var tested: String? = null
        render(
            provider = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onTestProvider = { key -> tested = key }),
        )

        compose.onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        compose.onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().performClick()

        assertEquals("sk-live-123", tested)
    }

    @Test
    fun aSuccessfulConnectionIsReportedAndDismissed() {
        var dismissed = false
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Success(AppMessage.core("The provider answered.")),
            ),
            actions = SettingsActions(onDismissProviderResult = { dismissed = true }),
        )

        compose.onNodeWithText("The provider answered.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_DISMISS_TAG).performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun aFailedConnectionIsReportedInline() {
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Failure(AppMessage.core("The provider rejected the API key.")),
            ),
        )

        compose.onNodeWithText("The provider rejected the API key.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aStoredKeyOffersRemoval() {
        var removed = false
        render(
            provider = ProviderUiState(available = true, providerChosen = true, hasStoredKey = true),
            actions = SettingsActions(onRemoveProviderKey = { removed = true }),
        )

        compose.onNodeWithText("A key is stored.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove key").performScrollTo().performClick()

        assertTrue(removed)
    }

    @Test
    fun anUnwiredProviderSaysSoAndHidesItsControls() {
        render(provider = ProviderUiState(available = false))

        compose.onNodeWithText("Connecting an LLM provider", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun anUnchosenProviderShowsThePlaceholderAndHidesItsControls() {
        render(provider = ProviderUiState(available = true))

        compose.onNodeWithText("Choose a provider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(MODEL_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aKeylessProviderOffersNoApiKeyField() {
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OLLAMA, "llama3"),
            ),
        )

        compose.onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun testingInProgressDisablesTheActions() {
        render(provider = ProviderUiState(available = true, providerChosen = true, testing = true))

        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun openingTheModelFieldAsksToLoadTheModelsWithTheTypedKey() {
        var loaded: String? = null
        render(
            provider = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onLoadModels = { key -> loaded = key }),
        )

        compose.onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performClick()

        assertEquals("sk-live-123", loaded)
    }

    @Test
    fun aLoadedModelListIsOfferedFromTheModelField() {
        var chosen: String? = null
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"),
                models = listOf("gpt-4o-mini", "gpt-4o"),
            ),
            actions = SettingsActions(onModelChange = { model -> chosen = model }),
        )

        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performClick()
        compose.onNodeWithText("gpt-4o").performClick()

        assertEquals("gpt-4o", chosen)
    }

    @Test
    fun anUnsupportedModelListIsReportedInlineAndLeavesManualEntry() {
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Failure(AppMessage.core(LlmModelsResult.UNSUPPORTED)),
            ),
        )

        compose.onNodeWithText("doesn't list its models", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingKeyForTheModelListIsShownDiscreetlyBesideTheField() {
        render(
            provider = ProviderUiState(
                available = true,
                providerChosen = true,
                modelsHint = AppMessage.core(LlmModelsResult.MISSING_KEY),
            ),
        )

        compose.onNodeWithText("Enter your API key", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun syncAlwaysWarnsAgainstFolderSyncTools() {
        render()

        compose.onNodeWithText("folder-sync tool", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("corrupt", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anUnwiredSyncSaysSoAndHidesItsControls() {
        render(sync = SyncUiState(available = false))

        compose.onNodeWithText("Sync isn't available", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aConfiguredSyncShowsItsStatusAndEndpoint() {
        render(
            sync = SyncUiState(
                available = true,
                config = SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            ),
        )

        compose.onNodeWithText("Sync is off.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anEnabledSyncSaysSyncIsOn() {
        render(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                hasStoredPassword = true,
            ),
        )

        compose.onNodeWithText("Sync is on.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_DISABLE_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun savingSyncPassesTheTypedPasswordToTheController() {
        var saved: String? = null
        render(
            sync = SyncUiState(available = true),
            actions = SettingsActions(onSaveSync = { password -> saved = password }),
        )

        compose.onNodeWithTag(SYNC_PASSWORD_FIELD_TAG).performScrollTo().performTextInput("app-password")
        compose.onNodeWithTag(SYNC_SAVE_TAG).performScrollTo().performClick()

        assertEquals("app-password", saved)
    }

    @Test
    fun testingSyncPassesTheTypedPasswordToTheController() {
        var tested: String? = null
        render(
            sync = SyncUiState(available = true),
            actions = SettingsActions(onTestSync = { password -> tested = password }),
        )

        compose.onNodeWithTag(SYNC_PASSWORD_FIELD_TAG).performScrollTo().performTextInput("app-password")
        compose.onNodeWithTag(SYNC_TEST_TAG).performScrollTo().performClick()

        assertEquals("app-password", tested)
    }

    @Test
    fun enablingSyncRaisesTheAction() {
        var enabled = false
        render(
            sync = SyncUiState(available = true, config = configured()),
            actions = SettingsActions(onEnableSync = { enabled = true }),
        )

        compose.onNodeWithTag(SYNC_ENABLE_TAG).performScrollTo().performClick()

        assertTrue(enabled)
    }

    @Test
    fun disablingSyncRaisesTheAction() {
        var disabled = false
        render(
            sync = SyncUiState(available = true, config = configured(enabled = true)),
            actions = SettingsActions(onDisableSync = { disabled = true }),
        )

        compose.onNodeWithTag(SYNC_DISABLE_TAG).performScrollTo().performClick()

        assertTrue(disabled)
    }

    @Test
    fun syncNowIsOfferedOnlyWhenSyncIsOn() {
        render(sync = SyncUiState(available = true, config = configured()))

        compose.onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun syncNowRaisesTheActionWhenSyncIsOn() {
        var synced = false
        render(
            sync = SyncUiState(available = true, config = configured(enabled = true)),
            actions = SettingsActions(onSyncNow = { synced = true }),
        )

        compose.onNodeWithTag(SYNC_NOW_TAG).performScrollTo().performClick()

        assertTrue(synced)
    }

    @Test
    fun theLastSyncOutcomeIsShown() {
        render(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Success(AppMessage.core("Synced: uploaded 1.")),
            ),
        )

        compose.onNodeWithText("Synced: uploaded 1.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aFailedSyncIsShownInPlainLanguage() {
        render(
            sync = SyncUiState(
                available = true,
                config = configured(enabled = true),
                lastSync = SyncResult.Failure(
                    AppMessage.core("The last sync didn't finish: the server is unreachable"),
                ),
            ),
        )

        compose.onNodeWithText("The last sync didn't finish", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSyncResultRendersAndDismisses() {
        var dismissed = false
        render(
            sync = SyncUiState(available = true, result = SyncResult.Success(AppMessage.core("Saved."))),
            actions = SettingsActions(onDismissSyncResult = { dismissed = true }),
        )

        compose.onNodeWithText("Saved.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(SYNC_DISMISS_TAG).performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun disconnectingRaisesTheAction() {
        var disconnected = false
        render(
            sync = SyncUiState(available = true, config = configured()),
            actions = SettingsActions(onDisconnectSync = { disconnected = true }),
        )

        compose.onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().performClick()

        assertTrue(disconnected)
    }

    @Test
    fun syncingInProgressDisablesTheControls() {
        render(sync = SyncUiState(available = true, config = configured(enabled = true), syncing = true))

        compose.onNodeWithTag(SYNC_SAVE_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SYNC_TEST_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SYNC_NOW_TAG).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(SYNC_DISCONNECT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() {
        // The reader's **app language** drives every text on the screen (ADR-0034):
        // with French provided, the sections and their controls read in French.
        render(texts = appTextsFor("fr"))

        compose.onNodeWithText("Réglages").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Coffre").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Exporter le coffre").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Importer un coffre").performScrollTo().assertIsDisplayed()
    }

    /** Renders the settings screen in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun render(
        appLanguage: String = "en",
        vault: VaultUiState = VaultUiState(),
        provider: ProviderUiState = ProviderUiState(),
        sync: SyncUiState = SyncUiState(),
        actions: SettingsActions = SettingsActions(),
        texts: AppTexts? = null,
    ) {
        val screen: @Composable () -> Unit = {
            SettingsScreen(
                state = SettingsUiState(appLanguage = appLanguage, vault = vault, provider = provider, sync = sync),
                actions = actions,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
            )
        }
        compose.setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}

/** A usable endpoint with the password stored, for the sync section's tests. */
private fun configured(enabled: Boolean = false): SyncSettings =
    SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = enabled)
