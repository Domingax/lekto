package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The **LLM provider** screen's behaviour through semantics (issue #213;
 * docs/design.md §7.5, §7.6): the provider preset, the custom base URL, the
 * masked key, the model list, save, the inline connection result and key removal.
 * The screen was extracted from Settings, so these are the section's own tests
 * moved with it, still driven through the existing actions.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per control; splitting hides the section.
class ProviderSettingsScreenSemanticsTest {

    @Test
    fun theScreenNamesItselfAndGoesBack() = runComposeUiTest {
        var wentBack = false
        provider(onBack = { wentBack = true })

        onNodeWithTag(PROVIDER_SETTINGS_TITLE_TAG).assertIsDisplayed()
        onNodeWithTag(PROVIDER_BACK_TAG).performClick()

        assertTrue(wentBack)
    }

    @Test
    fun choosingAProviderRaisesIt() = runComposeUiTest {
        var chosen: LlmProvider? = null
        provider(actions = SettingsActions(onSelectProvider = { provider -> chosen = provider }))

        onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        onNodeWithText("Anthropic").performClick()

        assertEquals(LlmProvider.ANTHROPIC, chosen)
    }

    @Test
    fun aCustomProviderOffersABaseUrlFieldThatWritesOnChange() = runComposeUiTest {
        var baseUrl: String? = null
        provider(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.CUSTOM, "my-model"),
            ),
            actions = SettingsActions(onBaseUrlChange = { typed -> baseUrl = typed }),
        )

        onNodeWithTag(BASE_URL_FIELD_TAG).performScrollTo().performTextInput("https://example.test/v1")

        assertEquals("https://example.test/v1", baseUrl)
    }

    @Test
    fun aTypedModelIsReportedOnChange() = runComposeUiTest {
        var model: String? = null
        provider(
            state = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onModelChange = { typed -> model = typed }),
        )

        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performTextInput("gpt-4o-mini")

        assertEquals("gpt-4o-mini", model)
    }

    @Test
    fun aNamedProviderHidesTheBaseUrlField() = runComposeUiTest {
        provider(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"),
            ),
        )

        onNodeWithTag(BASE_URL_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun connectingPassesTheTypedKeyToTheController() = runComposeUiTest {
        var tested: String? = null
        provider(
            state = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onConnectProvider = { key -> tested = key }),
        )

        onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        onNodeWithTag(PROVIDER_CONNECT_TAG).performScrollTo().performClick()

        assertEquals("sk-live-123", tested)
    }

    @Test
    fun aStoredKeyShowsAMaskedPlaceholder() = runComposeUiTest {
        provider(
            state = ProviderUiState(available = true, providerChosen = true, hasStoredKey = true),
        )

        onNodeWithText(SECRET_PLACEHOLDER).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSuccessfulConnectionIsReportedAndDismissed() = runComposeUiTest {
        var dismissed = false
        provider(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Success(AppMessage.core("The provider answered.")),
            ),
            actions = SettingsActions(onDismissProviderResult = { dismissed = true }),
        )

        onNodeWithText("The provider answered.").performScrollTo().assertIsDisplayed()
        onNodeWithTag(PROVIDER_DISMISS_TAG).performScrollTo().performClick()

        assertTrue(dismissed)
    }

    @Test
    fun aFailedConnectionIsReportedInline() = runComposeUiTest {
        provider(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Failure(AppMessage.core("The provider rejected the API key.")),
            ),
        )

        onNodeWithText("The provider rejected the API key.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aStoredKeyOffersRemoval() = runComposeUiTest {
        var removed = false
        provider(
            state = ProviderUiState(available = true, providerChosen = true, hasStoredKey = true),
            actions = SettingsActions(onRemoveProviderKey = { removed = true }),
        )

        onNodeWithText("A key is stored.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Remove key").performScrollTo().performClick()

        assertTrue(removed)
    }

    @Test
    fun anUnwiredProviderSaysSoAndHidesItsControls() = runComposeUiTest {
        provider(state = ProviderUiState(available = false))

        onNodeWithText("Connecting an LLM provider", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun anUnchosenProviderShowsThePlaceholderAndHidesItsControls() = runComposeUiTest {
        provider(state = ProviderUiState(available = true))

        onNodeWithText("Choose a provider").performScrollTo().assertIsDisplayed()
        onNodeWithTag(MODEL_FIELD_TAG).assertDoesNotExist()
        onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aKeylessProviderOffersNoApiKeyField() = runComposeUiTest {
        provider(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OLLAMA, "llama3"),
            ),
        )

        onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun testingInProgressDisablesTheAction() = runComposeUiTest {
        provider(state = ProviderUiState(available = true, providerChosen = true, testing = true))

        onNodeWithTag(PROVIDER_CONNECT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun openingTheModelFieldAsksToLoadTheModelsWithTheTypedKey() = runComposeUiTest {
        var loaded: String? = null
        provider(
            state = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onLoadModels = { key -> loaded = key }),
        )

        onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performClick()

        assertEquals("sk-live-123", loaded)
    }

    @Test
    fun aLoadedModelListIsOfferedFromTheModelField() = runComposeUiTest {
        var chosen: String? = null
        provider(
            state = ProviderUiState(
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
        provider(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Failure(AppMessage.core(LlmModelsResult.UNSUPPORTED)),
            ),
        )

        onNodeWithText("doesn't list its models", substring = true).performScrollTo().assertIsDisplayed()
        onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aMissingKeyForTheModelListIsShownDiscreetlyBesideTheField() = runComposeUiTest {
        provider(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                modelsHint = AppMessage.core(LlmModelsResult.MISSING_KEY),
            ),
        )

        onNodeWithText("Enter your API key", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theScreenRendersInTheAppLanguage() = runComposeUiTest {
        provider(texts = appTextsFor("fr"))

        onNodeWithText("Fournisseur LLM").assertIsDisplayed()
        onNodeWithText("Choisir un fournisseur").performScrollTo().assertIsDisplayed()
    }

    /** Renders the provider screen in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun ComposeUiTest.provider(
        state: ProviderUiState = ProviderUiState(available = true),
        actions: SettingsActions = SettingsActions(),
        onBack: () -> Unit = {},
        texts: AppTexts? = null,
    ) {
        val screen: @Composable () -> Unit = {
            ProviderSettingsScreen(
                state = state,
                actions = actions,
                onBack = onBack,
                modifier = Modifier.size(width = 360.dp, height = 720.dp),
            )
        }
        setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}
