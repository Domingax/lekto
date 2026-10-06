package app.lekto.translation

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.lekto.core.dictionary.DictionarySource
import app.lekto.core.dictionary.TranslationShortcut
import app.lekto.core.dictionary.translationShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phrase-mode lookup panel on a **simulated Android runtime** (issue #87):
 * the same-named twin of `app/desktopTest`'s `PhraseLookupPanelSemanticsTest`,
 * so the parity rule (issue #73) sees the two lanes together. It proves the
 * zero-configuration Translation shortcut and its prefilled page under
 * Robolectric, on the runtime that ultimately renders the panel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class PhraseLookupPanelSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a selected phrase shows the translation shortcut with no provider configured`() {
        compose.setContent { Panel() }

        compose.onNodeWithText("the lantern glows").assertIsDisplayed()
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
        compose.onNodeWithText("Close").performClick()

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
