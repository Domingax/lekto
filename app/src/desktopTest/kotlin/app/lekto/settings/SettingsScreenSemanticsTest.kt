package app.lekto.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.theme.AppTheme
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.appTextsFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Settings index's behaviour through semantics (issue #213): what stays
 * after the two forms left for screens of their own — the **Appearance** and
 * **Languages** doorways, the **app language** picker, and the doorways to the
 * **LLM provider** and **Vault and sync**. The forms' own suites prove the
 * fields and actions they took with them.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One screen, one test per control; splitting hides the section.
class SettingsScreenSemanticsTest {

    @Test
    fun theDictionarySectionIsGone() = runComposeUiTest {
        settings()

        // The pack and its attribution left for the Languages surface (issue #149).
        onNodeWithText("Dictionary").assertDoesNotExist()
        onNodeWithText("Attribution").assertDoesNotExist()
    }

    @Test
    fun theTwoFormsAreNoLongerOnSettings() = runComposeUiTest {
        settings()

        onNodeWithTag(PROVIDER_PICKER_TAG).assertDoesNotExist()
        onNodeWithTag(SYNC_HOST_FIELD_TAG).assertDoesNotExist()
        onNodeWithText("Export vault").assertDoesNotExist()
        onNodeWithText("Import vault").assertDoesNotExist()
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
    fun theAppearanceSectionNamesTheCurrentThemeAndOpensItsScreen() = runComposeUiTest {
        var opened = false
        settings(appTheme = AppTheme.SEPIA, actions = SettingsActions(onOpenAppearance = { opened = true }))

        onNodeWithText("Sepia").performScrollTo().assertIsDisplayed()
        onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theProviderDoorwayOpensItsScreen() = runComposeUiTest {
        var opened = false
        settings(actions = SettingsActions(onOpenProvider = { opened = true }))

        onNodeWithTag(PROVIDER_LINK_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theVaultDoorwayOpensItsScreen() = runComposeUiTest {
        var opened = false
        settings(actions = SettingsActions(onOpenVault = { opened = true }))

        onNodeWithTag(VAULT_LINK_TAG).performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun theWholeScreenRendersInTheAppLanguage() = runComposeUiTest {
        // The reader's **app language** drives every text on the screen (ADR-0034):
        // with French provided, the sections and their doorways read in French.
        settings(texts = appTextsFor("fr"))

        onNodeWithText("Réglages").performScrollTo().assertIsDisplayed()
        onNodeWithTag(APPEARANCE_LINK_TAG).performScrollTo().assertIsDisplayed()
        onNodeWithTag(PROVIDER_LINK_TAG).performScrollTo().assertIsDisplayed()
        onNodeWithTag(VAULT_LINK_TAG).performScrollTo().assertIsDisplayed()
    }

    /** Renders the settings screen in the app theme, sized like a phone. */
    @Suppress("LongParameterList") // One test helper mirroring the screen's state bundles.
    private fun ComposeUiTest.settings(
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
        setContent {
            MaterialTheme {
                if (texts == null) screen() else CompositionLocalProvider(LocalAppTexts provides texts) { screen() }
            }
        }
    }
}
