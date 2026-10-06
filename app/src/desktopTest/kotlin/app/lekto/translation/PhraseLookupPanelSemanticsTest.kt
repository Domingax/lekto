package app.lekto.translation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.TranslationShortcut
import app.lekto.core.dictionary.translationShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lookup panel in phrase mode through semantics (issue #87): a selected
 * phrase shows the zero-configuration Translation shortcut and no provider or
 * key is needed, the shortcut opens the service's prefilled page, and a phrase
 * the service cannot address still closes honestly.
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
        onNodeWithText("Close").performClick()

        assertTrue(dismissed)
    }

    @Composable
    private fun Panel(
        phrase: String = "the lantern glows",
        shortcut: TranslationShortcut? = translationShortcut(phrase, "en", "fr"),
        onOpenShortcut: (TranslationShortcut) -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        MaterialTheme {
            PhraseLookupPanel(
                phrase = phrase,
                shortcut = shortcut,
                actions = PhraseLookupActions(onOpenShortcut = onOpenShortcut, onDismiss = onDismiss),
            )
        }
    }
}
