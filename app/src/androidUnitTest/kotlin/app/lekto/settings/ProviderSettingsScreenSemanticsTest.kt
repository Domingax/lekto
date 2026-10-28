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
 * The **LLM provider** screen on a **simulated Android runtime** (issue #213):
 * the same-named twin of `app/desktopTest`'s
 * `ProviderSettingsScreenSemanticsTest`, so the parity rule (issue #73) sees the
 * two lanes together. The provider preset, the custom base URL, the masked key,
 * the model list, save, the inline connection result and key removal are the same
 * behaviours on the runtime the app ships on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per control; splitting hides the section.
class ProviderSettingsScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theScreenNamesItselfAndGoesBack() {
        var wentBack = false
        render(onBack = { wentBack = true })

        compose.onNodeWithTag(PROVIDER_SETTINGS_TITLE_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_BACK_TAG).performClick()

        assertTrue(wentBack)
    }

    @Test
    fun choosingAProviderRaisesIt() {
        var chosen: LlmProvider? = null
        render(actions = SettingsActions(onSelectProvider = { provider -> chosen = provider }))

        compose.onNodeWithTag(PROVIDER_PICKER_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Anthropic").performClick()

        assertEquals(LlmProvider.ANTHROPIC, chosen)
    }

    @Test
    fun aCustomProviderOffersABaseUrlFieldThatWritesOnChange() {
        var baseUrl: String? = null
        render(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.CUSTOM, "my-model"),
            ),
            actions = SettingsActions(onBaseUrlChange = { typed -> baseUrl = typed }),
        )

        compose.onNodeWithTag(BASE_URL_FIELD_TAG).performScrollTo().performTextInput("https://example.test/v1")

        assertEquals("https://example.test/v1", baseUrl)
    }

    @Test
    fun aTypedModelIsReportedOnChange() {
        var model: String? = null
        render(
            state = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onModelChange = { typed -> model = typed }),
        )

        compose.onNodeWithTag(MODEL_FIELD_TAG).performScrollTo().performTextInput("gpt-4o-mini")

        assertEquals("gpt-4o-mini", model)
    }

    @Test
    fun aNamedProviderHidesTheBaseUrlField() {
        render(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OPENAI, "gpt-4o-mini"),
            ),
        )

        compose.onNodeWithTag(BASE_URL_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun connectingPassesTheTypedKeyToTheController() {
        var tested: String? = null
        render(
            state = ProviderUiState(available = true, providerChosen = true),
            actions = SettingsActions(onConnectProvider = { key -> tested = key }),
        )

        compose.onNodeWithTag(API_KEY_FIELD_TAG).performScrollTo().performTextInput("sk-live-123")
        compose.onNodeWithTag(PROVIDER_CONNECT_TAG).performScrollTo().performClick()

        assertEquals("sk-live-123", tested)
    }

    @Test
    fun aStoredKeyShowsAMaskedPlaceholder() {
        render(
            state = ProviderUiState(available = true, providerChosen = true, hasStoredKey = true),
        )

        compose.onNodeWithText(SECRET_PLACEHOLDER).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSuccessfulConnectionIsReported() {
        render(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                result = ProviderResult.Success(AppMessage.core("The provider answered.")),
            ),
        )

        compose.onNodeWithText("The provider answered.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aConnectionTestShowsTheLoader() {
        // An indeterminate indicator never reaches idle, so the test clock must not
        // advance while the node is asserted.
        compose.mainClock.autoAdvance = false
        render(state = ProviderUiState(available = true, providerChosen = true, testing = true))
        compose.mainClock.advanceTimeByFrame()

        compose.onNodeWithTag(PROVIDER_PROGRESS_TAG).assertExists()
    }

    @Test
    fun aFailedConnectionIsReportedInline() {
        render(
            state = ProviderUiState(
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
            state = ProviderUiState(available = true, providerChosen = true, hasStoredKey = true),
            actions = SettingsActions(onRemoveProviderKey = { removed = true }),
        )

        compose.onNodeWithText("A key is stored.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remove key").performScrollTo().performClick()

        assertTrue(removed)
    }

    @Test
    fun anUnwiredProviderSaysSoAndHidesItsControls() {
        render(state = ProviderUiState(available = false))

        compose.onNodeWithText("Connecting an LLM provider", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
    }

    @Test
    fun anUnchosenProviderShowsThePlaceholderAndHidesItsControls() {
        render(state = ProviderUiState(available = true))

        compose.onNodeWithText("Choose a provider").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(MODEL_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun aKeylessProviderOffersNoApiKeyField() {
        render(
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                config = LlmProviderConfig(LlmProvider.OLLAMA, "llama3"),
            ),
        )

        compose.onNodeWithTag(API_KEY_FIELD_TAG).assertDoesNotExist()
    }

    @Test
    fun testingInProgressDisablesTheAction() {
        render(state = ProviderUiState(available = true, providerChosen = true, testing = true))

        compose.onNodeWithTag(PROVIDER_CONNECT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun openingTheModelFieldAsksToLoadTheModelsWithTheTypedKey() {
        var loaded: String? = null
        render(
            state = ProviderUiState(available = true, providerChosen = true),
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
            state = ProviderUiState(
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
            state = ProviderUiState(
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
            state = ProviderUiState(
                available = true,
                providerChosen = true,
                modelsHint = AppMessage.core(LlmModelsResult.MISSING_KEY),
            ),
        )

        compose.onNodeWithText("Enter your API key", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theScreenRendersInTheAppLanguage() {
        render(texts = appTextsFor("fr"))

        compose.onNodeWithText("Fournisseur LLM").assertIsDisplayed()
        compose.onNodeWithText("Choisir un fournisseur").performScrollTo().assertIsDisplayed()
    }

    /** Renders the provider screen in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun render(
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
        compose.setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}
