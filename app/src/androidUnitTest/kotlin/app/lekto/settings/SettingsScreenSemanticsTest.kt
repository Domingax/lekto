package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.llm.LlmProvider
import app.lekto.core.llm.LlmProviderConfig
import app.lekto.dictionary.DictionaryUiState
import app.lekto.testkit.testPackMetadata
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
 * parity rule (issue #73) sees the two lanes together. The dictionary section's
 * status and download, the way to attribution, the vault section's
 * export/import controls, and the LLM provider section (issue #88) are the
 * same behaviours the desktop lane proves, run here on the runtime the app ships
 * on.
 *
 * It is a deliberate mirror, not a shared body: the desktop lane drives
 * `runComposeUiTest` and this one a Robolectric `createAndroidComposeRule`, and
 * `app` has no test source set both lanes compile, so the two files are kept in
 * step by hand (issue #77).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("TooManyFunctions") // One screen, one test per control; splitting the class hides the section.
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
        render(provider = ProviderUiState(true, config = LlmProviderConfig(LlmProvider.CUSTOM, "my-model")))

        compose.onNodeWithTag(BASE_URL_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aNamedProviderHidesTheBaseUrlField() {
        render(provider = ProviderUiState(true, config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini")))

        compose.onNodeWithTag(BASE_URL_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun savingPassesTheTypedKeyToTheController() {
        var saved: String? = null
        render(
            provider = ProviderUiState(available = true),
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
            provider = ProviderUiState(available = true),
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
            provider = ProviderUiState(available = true, result = ProviderResult.Success("The provider answered.")),
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
                result = ProviderResult.Failure("The provider rejected the API key."),
            ),
        )

        compose.onNodeWithText("The provider rejected the API key.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aStoredKeyOffersRemoval() {
        var removed = false
        render(
            provider = ProviderUiState(available = true, hasStoredKey = true),
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
    fun aKeylessProviderOffersNoApiKeyField() {
        render(provider = ProviderUiState(true, config = LlmProviderConfig(LlmProvider.OLLAMA, "llama3")))

        compose.onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun testingInProgressDisablesTheActions() {
        render(provider = ProviderUiState(available = true, testing = true))

        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag(TEST_CONNECTION_TAG).performScrollTo().assertIsNotEnabled()
    }

    /** Renders the settings screen in the app theme, sized like a phone. */
    private fun render(
        dictionary: DictionaryUiState = DictionaryUiState(),
        vault: VaultUiState = VaultUiState(),
        provider: ProviderUiState = ProviderUiState(),
        actions: SettingsActions = SettingsActions(),
    ) {
        compose.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState(dictionary, vault, provider),
                    actions = actions,
                    modifier = Modifier.size(width = 360.dp, height = 720.dp),
                )
            }
        }
    }
}
