package app.lekto.dictionary

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.WordLookup
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The minimal lookup card through semantics (issue #18): a known word shows its
 * pronunciation, definitions and translations; an unknown word and an offline
 * lookup each show their honest message.
 */
@OptIn(ExperimentalTestApi::class)
class WordLookupCardSemanticsTest {

    @Test
    fun aFoundWordShowsItsPronunciationDefinitionsAndTranslations() = runComposeUiTest {
        val entry = DictionaryEntry(
            lemma = "blorple",
            language = "en",
            partOfSpeech = "verb",
            pronunciation = "/ˈblɔːpəl/",
            audioUrl = null,
            senses = listOf(DictionarySense("To move swiftly.", listOf("blorper"))),
        )

        setContent { Card(WordLookup.Found("blorple", "en", "blorple", listOf(entry))) }

        onNodeWithText("blorple").assertIsDisplayed()
        onNodeWithText("/ˈblɔːpəl/").assertIsDisplayed()
        onNodeWithText("To move swiftly.", substring = true).assertIsDisplayed()
        onNodeWithText("→ blorper", substring = true).assertIsDisplayed()
    }

    @Test
    fun anUnknownWordSaysSoAndCanBeDismissed() = runComposeUiTest {
        var dismissed = false
        setContent { Card(WordLookup.NotInDictionary("zzzz", "en"), onDismiss = { dismissed = true }) }

        onNodeWithText("isn't in the offline dictionary", substring = true).assertIsDisplayed()
        onNodeWithText("Close").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun anUnavailableLookupShowsItsMessage() = runComposeUiTest {
        setContent { Card(WordLookup.Unavailable("The offline dictionary isn't installed yet.")) }

        onNodeWithText("isn't installed yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun anEntryWithoutPronunciationOrTranslationsStillShowsItsDefinition() = runComposeUiTest {
        val entry = DictionaryEntry(
            lemma = "vrok",
            language = "en",
            partOfSpeech = null,
            pronunciation = null,
            audioUrl = null,
            senses = listOf(DictionarySense("A word.", emptyList())),
        )

        setContent { Card(WordLookup.Found("vrok", "en", "vrok", listOf(entry))) }

        onNodeWithText("A word.", substring = true).assertIsDisplayed()
    }

    @Composable
    private fun Card(result: WordLookup, onDismiss: () -> Unit = {}) {
        MaterialTheme {
            WordLookupCard(result, onDismiss, Modifier.size(width = 360.dp, height = 480.dp))
        }
    }
}
