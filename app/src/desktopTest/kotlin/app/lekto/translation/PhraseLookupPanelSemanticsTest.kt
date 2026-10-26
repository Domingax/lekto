package app.lekto.translation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.TranslationShortcut
import app.lekto.core.dictionary.translationShortcut
import app.lekto.text.AppMessage
import app.lekto.ui.LOOKUP_SHEET_MAX_HEIGHT_FRACTION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lookup panel in phrase mode through semantics (issues #87, #89): a selected
 * phrase shows the zero-configuration Translation shortcut and no provider or key
 * is needed; a connected provider's translation streams into the panel and it
 * discloses what leaves the device; without a provider it shows a non-blocking
 * prompt beside the shortcut; and a provider failure is reported inline.
 */
@OptIn(ExperimentalTestApi::class)
class PhraseLookupPanelSemanticsTest {

    @Test
    fun aSelectedPhraseShowsTheTranslationShortcutWithNoProviderConfigured() = runComposeUiTest {
        setContent { Panel() }

        onNodeWithText("the lantern glows").assertIsDisplayed()
        onNodeWithText("Translate online").assertIsDisplayed()
        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
        onNodeWithText(DictionarySource.GOOGLE_TRANSLATE.label).assertIsDisplayed()
    }

    @Test
    fun theShortcutOpensTheServicePageWithThePhrasePrefilled() = runComposeUiTest {
        var opened: String? = null
        setContent { Panel(onOpenShortcut = { chosen -> opened = chosen.url }) }

        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).performClick()

        assertEquals(
            "https://translate.google.com/?sl=en&tl=fr&text=the+lantern+glows&op=translate",
            opened,
        )
    }

    @Test
    fun aPhraseWithNoServiceStillCloses() = runComposeUiTest {
        var dismissed = false
        setContent { Panel(shortcut = null, onDismiss = { dismissed = true }) }

        onNodeWithText("No translation service is available", substring = true).assertIsDisplayed()
        onNodeWithTag(PHRASE_CLOSE_TAG).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun withoutAProviderThePanelShowsANonBlockingPromptBesideTheShortcut() = runComposeUiTest {
        var openedSettings = false
        setContent { Panel(onOpenSettings = { openedSettings = true }) }

        onNodeWithText("Connect an LLM provider", substring = true).assertIsDisplayed()
        onNodeWithTag(TRANSLATION_SETTINGS_TAG).performClick()
        assertTrue(openedSettings)

        // The unconnected state says what a phrase needs and does not pretend to
        // have a translation; the shortcut still works with no provider and no key.
        onNodeWithTag(TRANSLATION_RESULT_TAG).assertDoesNotExist()
        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
    }

    @Test
    fun aStreamingTranslationShowsTheTextAndDisclosesWhatLeavesTheDevice() = runComposeUiTest {
        setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    context = "Containing sentence: The lantern glows softly.",
                    state = PhraseTranslationState.Streaming("La lanterne"),
                ),
            )
        }

        onNodeWithTag(TRANSLATION_RESULT_TAG).assertTextEquals("La lanterne")
        // The connected state shows a translation, not the no-provider prompt, and
        // the disclosure is collapsed so the translation stays the focus.
        onNodeWithText("What's sent?").assertIsDisplayed()
        onNodeWithText("The lantern glows softly.", substring = true).assertDoesNotExist()

        onNodeWithTag(TRANSLATION_DISCLOSURE_TAG).performClick()

        onNodeWithText("Sends your selection and its sentence to OpenAI.", substring = true).assertIsDisplayed()
        onNodeWithText("The lantern glows softly.", substring = true).assertIsDisplayed()
    }

    @Test
    fun aProviderFailureIsReportedInline() = runComposeUiTest {
        setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    state = PhraseTranslationState.Failed(AppMessage.core("The provider rejected the API key.")),
                ),
            )
        }

        onNodeWithText("The provider rejected the API key.").assertIsDisplayed()
        // The failure is its own state: no prompt, and the shortcut still stands.
        onNodeWithText("Connect an LLM provider", substring = true).assertDoesNotExist()
        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
        // The phrase was still sent, so the disclosure is still reachable.
        onNodeWithTag(TRANSLATION_DISCLOSURE_TAG).performClick()
        onNodeWithText("Sends your selection and its sentence to OpenAI.", substring = true).assertIsDisplayed()
    }

    @Test
    fun anEmptyProviderAnswerSaysSo() = runComposeUiTest {
        setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    state = PhraseTranslationState.Done(""),
                ),
            )
        }

        onNodeWithTag(TRANSLATION_RESULT_TAG).assertTextEquals("The provider returned no translation.")
    }

    @Test
    fun aLongTranslationScrollsRatherThanClipping() = runComposeUiTest {
        val long = (1..40).joinToString(" ") { "Ligne $it de la traduction." }
        setContent {
            Panel(
                translation = PhraseTranslationUiState(
                    provider = "OpenAI",
                    state = PhraseTranslationState.Done(long),
                ),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }

        val panel = onNodeWithTag(PHRASE_PANEL_TAG).getUnclippedBoundsInRoot()
        val ceiling = 640f * LOOKUP_SHEET_MAX_HEIGHT_FRACTION
        assertTrue(
            panel.bottom.value - panel.top.value <= ceiling + 1f,
            "the sheet is ${panel.bottom.value - panel.top.value} dp tall, above the $ceiling dp ceiling",
        )

        // The shortcut needs nothing configured, so it stays pinned while the long
        // translation scrolls to its disclosure.
        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()
        onNodeWithTag(TRANSLATION_DISCLOSURE_TAG).performScrollTo().assertIsDisplayed()
        onNodeWithTag(TRANSLATION_SHORTCUT_TAG).assertIsDisplayed()

        val shortcut = onNodeWithTag(TRANSLATION_SHORTCUT_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            shortcut.bottom.value <= panel.bottom.value + 1f,
            "the shortcut ends at ${shortcut.bottom} dp, past the sheet's ${panel.bottom} dp",
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
