package app.lekto.translation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.TranslationShortcut
import app.lekto.core.dictionary.translationShortcut
import app.lekto.text.AppMessage
import app.lekto.ui.LOOKUP_SHEET_MAX_HEIGHT_FRACTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phrase-mode lookup panel on a **simulated Android runtime** (issues #87,
 * #89): the same-named twin of `app/desktopTest`'s
 * `PhraseLookupPanelSemanticsTest`, so the parity rule (issue #73) sees the two
 * lanes together. It proves the zero-configuration Translation shortcut, the
 * streamed translation with its disclosure, the non-blocking no-provider prompt
 * and the inline failure under Robolectric, on the runtime that ultimately
 * renders the panel.
 */
@RunWith(RobolectricTestRunner::class)
// `android-compileSdk`; Robolectric 4.16 supports API 36. The qualifiers give the
// sheet a phone-sized window (360 × 800 dp), so its 45 % cap leaves room for the
// prompt and the shortcut, and the test reads them where a reader would.
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
class PhraseLookupPanelSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a selected phrase shows the translation shortcut with no provider configured`() {
        compose.setContent { Panel() }

        compose.onNodeWithText("“the lantern glows”").assertIsDisplayed()
        compose.onNodeWithText("Translate online").assertIsDisplayed()
        compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
        compose.onNodeWithText(DictionarySource.GOOGLE_TRANSLATE.label).assertIsDisplayed()
    }

    @Test
    fun `the shortcut opens the service page with the phrase prefilled`() {
        var opened: String? = null
        compose.setContent { Panel(onOpenShortcut = { chosen -> opened = chosen.url }) }

        compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).performClick()

        assertEquals(
            "https://translate.google.com/?sl=en&tl=fr&text=the+lantern+glows&op=translate",
            opened,
        )
    }

    @Test
    fun `a phrase with no service still closes`() {
        var dismissed = false
        compose.setContent { Panel(shortcut = null, onDismiss = { dismissed = true }) }

        compose.onNodeWithText("No translation service is available", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(PHRASE_CLOSE_TAG).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `without a provider the panel shows a non-blocking prompt beside the shortcut`() {
        var openedSettings = false
        compose.setContent { Panel(onOpenSettings = { openedSettings = true }) }

        compose.onNodeWithText("A phrase needs an AI", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Set up a provider").assertIsDisplayed()
        compose.onNodeWithTag(TRANSLATION_SETTINGS_TAG).performClick()
        assertTrue(openedSettings)

        // The unconnected state says what a phrase needs and does not pretend to
        // have a translation; the shortcut still works with no provider and no key.
        compose.onNodeWithTag(TRANSLATION_RESULT_TAG).assertDoesNotExist()
        compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
    }

    @Test
    fun `a streaming translation shows the text and discloses what leaves the device`() {
        compose.setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    context = "Containing sentence: The lantern glows softly.",
                    state = PhraseTranslationState.Streaming("La lanterne"),
                ),
            )
        }

        compose.onNodeWithTag(TRANSLATION_RESULT_TAG).assertTextEquals("La lanterne")
        // The connected state shows a translation, not the no-provider prompt, and
        // the disclosure is collapsed so the translation stays the focus.
        compose.onNodeWithText("What's sent?").assertIsDisplayed()
        compose.onNodeWithText("The lantern glows softly.", substring = true).assertDoesNotExist()

        compose.onNodeWithTag(TRANSLATION_DISCLOSURE_TAG).performClick()

        compose.onNodeWithText("Sends your selection and its sentence to OpenAI.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The lantern glows softly.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a provider failure is reported inline`() {
        compose.setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    state = PhraseTranslationState.Failed(AppMessage.core("The provider rejected the API key.")),
                ),
            )
        }

        compose.onNodeWithText("The provider rejected the API key.").assertIsDisplayed()
        // The failure is its own state: no prompt, and the shortcut still stands.
        compose.onNodeWithText("A phrase needs an AI", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
        // The phrase was still sent, so the disclosure is still reachable.
        compose.onNodeWithTag(TRANSLATION_DISCLOSURE_TAG).performClick()
        compose.onNodeWithText("Sends your selection and its sentence to OpenAI.", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an empty provider answer says so`() {
        compose.setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    state = PhraseTranslationState.Done(""),
                ),
            )
        }

        compose.onNodeWithTag(TRANSLATION_RESULT_TAG).assertTextEquals("The provider returned no translation.")
    }

    @Test
    fun `a long translation scrolls rather than clipping`() {
        val long = (1..40).joinToString(" ") { "Ligne $it de la traduction." }
        compose.setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    state = PhraseTranslationState.Done(long),
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }

        val panel = compose.onNodeWithTag(PHRASE_PANEL_TAG).getUnclippedBoundsInRoot()
        val ceiling = 640f * LOOKUP_SHEET_MAX_HEIGHT_FRACTION
        assertTrue(
            "the sheet is ${panel.bottom.value - panel.top.value} dp tall, above the $ceiling dp ceiling",
            panel.bottom.value - panel.top.value <= ceiling + 1f,
        )

        // The shortcut needs nothing configured, so it stays pinned while the long
        // translation scrolls to its disclosure.
        compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
        compose.onNodeWithTag(TRANSLATION_DISCLOSURE_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()

        val shortcut = compose.onNodeWithTag(TRANSLATION_SHORTCUT_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            "the shortcut ends at ${shortcut.bottom} dp, past the sheet's ${panel.bottom} dp",
            shortcut.bottom.value <= panel.bottom.value + 1f,
        )
    }

    @Suppress("LongParameterList") // The panel's inputs are independent; a bundle would only hide them.
    @Composable
    private fun Panel(
        phrase: String = "the lantern glows",
        shortcut: TranslationShortcut? = translationShortcut(phrase, "en", "fr"),
        translation: PhraseTranslationUiState = PhraseTranslationUiState(),
        onOpenShortcut: (TranslationShortcut) -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onDismiss: () -> Unit = {},
        modifier: Modifier = Modifier,
    ) {
        MaterialTheme {
            PhraseLookupPanel(
                phrase = phrase,
                shortcut = shortcut,
                actions = PhraseLookupActions(onOpenShortcut = onOpenShortcut, onDismiss = onDismiss),
                translation = translation,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )
        }
    }
}
