package app.lekto.vocabulary

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The vocabulary list through semantics (issue #23): an empty vocabulary shows
 * the instructional empty state, each saved entry shows its word, translation,
 * context sentence and mastery, the search field raises the query, a search that
 * matches nothing says so, and Delete raises the entry it belongs to.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One list, one test per behaviour; splitting hides the screen's surface.
class VocabularyScreenSemanticsTest {

    private val lantern = VocabularyEntry(
        key = WordKey("en", "lantern"),
        surface = "lantern",
        translation = "lanterne",
        contextSentence = "The lantern burned all night.",
        mastery = MasteryLevel.MASTERED,
    )

    @Test
    fun anEmptyVocabularyShowsTheInstructionalEmptyState() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Empty) }

        onNodeWithText("No words saved yet", substring = true).assertIsDisplayed()
        onNodeWithTag(VOCABULARY_SEARCH_TAG).assertDoesNotExist()
    }

    @Test
    fun listsEachEntryWithItsTranslationContextSentenceAndMastery() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), "")) }

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertIsDisplayed()
        onNodeWithText("The lantern burned all night.").assertIsDisplayed()
        onNodeWithText("Mastered").assertIsDisplayed()
    }

    @Test
    fun typingInTheSearchFieldRaisesTheQuery() = runComposeUiTest {
        var query: String? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onSearch = { query = it }) }

        onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("lant")

        assertEquals("lant", query)
    }

    @Test
    fun aSearchThatMatchesNothingSaysSo() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Results(emptyList(), "zzzz")) }

        onNodeWithText("No words match", substring = true).assertIsDisplayed()
    }

    @Test
    fun deletingAnEntryRaisesThatEntry() = runComposeUiTest {
        var deleted: VocabularyEntry? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onDelete = { deleted = it }) }

        onNodeWithTag(vocabularyDeleteTag(lantern.key)).performClick()

        assertEquals(lantern, deleted)
    }

    @Test
    fun theBackActionReturnsToTheLibrary() = runComposeUiTest {
        var backed = false
        setContent { Vocabulary(VocabularyUiState.Empty, onBack = { backed = true }) }

        onNodeWithText("Library").performClick()

        assertTrue(backed)
    }

    @Test
    fun theListIsKeyedByABundleSaveableValue() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides BundleStrictRegistry) {
                Vocabulary(VocabularyUiState.Results(listOf(lantern), ""))
            }
        }

        onNodeWithText("lantern").assertIsDisplayed()
    }

    @Composable
    private fun Vocabulary(
        state: VocabularyUiState,
        onSearch: (String) -> Unit = {},
        onDelete: (VocabularyEntry) -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        MaterialTheme {
            VocabularyScreen(
                state = state,
                actions = VocabularyActions(onSearch = onSearch, onDelete = onDelete, onBack = onBack),
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}

/**
 * A saveable-state registry as strict as Android's: it accepts only types the
 * platform can put in a `Bundle` — here, anything `Serializable`, and a [WordKey]
 * is a Kotlin data class and is not one. Rendering the list under it reproduces
 * the Android-only crash a non-Bundle key causes, so the list's `LazyColumn` key
 * is guarded in the JVM lane (issue #69).
 */
private val BundleStrictRegistry = object : SaveableStateRegistry {
    override fun canBeSaved(value: Any): Boolean = value is java.io.Serializable

    override fun consumeRestored(key: String): Any? = null

    override fun registerProvider(key: String, valueProvider: () -> Any?): SaveableStateRegistry.Entry =
        object : SaveableStateRegistry.Entry {
            override fun unregister() = Unit
        }

    override fun performSave(): Map<String, List<Any?>> = emptyMap()
}
