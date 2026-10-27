package app.lekto.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.lekto.core.theme.AppTheme
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
 * The Settings index on a **simulated Android runtime** (issue #213): the
 * same-named twin of `app/desktopTest`'s `SettingsScreenSemanticsTest`, so the
 * parity rule (issue #73) sees the two lanes together. What stays after the two
 * forms left for screens of their own — the **Appearance** and **Languages**
 * doorways, the **app language** picker, and the doorways to the **LLM provider**
 * and **Vault and sync** — is proved here on the runtime the app ships on.
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
    fun theTwoFormsAreNoLongerOnSettings() {
        render()

        compose.onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
        compose.onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithText("Export vault").assertDoesNotExist()
        compose.onNodeWithText("Import vault").assertDoesNotExist()
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
    fun theAppearanceSectionNamesTheCurrentThemeAndOpensItsScreen() {
        var opened = false
        render(appTheme = AppTheme.SEPIA, actions = SettingsActions(onOpenAppearance = { opened = true }))

        compose.onNodeWithText("Sepia").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theProviderDoorwayOpensItsScreen() {
        var opened = false
        render(actions = SettingsActions(onOpenProvider = { opened = true }))

        compose.onNodeWithTag(PROVIDER_LINK_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theVaultDoorwayOpensItsScreen() {
        var opened = false
        render(actions = SettingsActions(onOpenVault = { opened = true }))

        compose.onNodeWithTag(VAULT_LINK_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() {
        // The reader's **app language** drives every text on the screen (ADR-0034):
        // with French provided, the sections and their doorways read in French.
        render(texts = appTextsFor("fr"))

        compose.onNodeWithText("Réglages").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_LINK_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(VAULT_LINK_TAG).performScrollTo().assertIsDisplayed()
    }

    /** Renders the settings screen in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun render(
        appLanguage: String = "en",
        appTheme: AppTheme = AppTheme.SYSTEM,
        actions: SettingsActions = SettingsActions(),
        texts: AppTexts? = null,
    ) {
        val screen: @Composable () -> Unit = {
            SettingsScreen(
                state = SettingsUiState(appLanguage = appLanguage, appTheme = appTheme),
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
