package app.lekto.vocabulary

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.testkit.testVocabularyEntry
import app.lekto.ui.CONFIRM_DIALOG_CANCEL_TAG
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How far the desktop drag test pulls a row, in pixels: most of a phone-wide row. */
private const val DRAG_DISTANCE = 200f

/**
 * The vocabulary list through semantics (issue #23; its anatomy and delete from
 * issue #212): the two distinct empty states, the count beside the title, each
 * saved entry's word, level, translation and context sentence, the search field
 * raising the query, a search that matches nothing naming itself and offering to
 * clear, the mastery filter, and a delete that is a row gesture behind a
 * confirmation.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One list, one test per behaviour; splitting hides the screen's surface.
class VocabularyScreenSemanticsTest {

    private val lantern = testVocabularyEntry()
    private val harbour = testVocabularyEntry(surface = "harbour", mastery = MasteryLevel.RECOGNIZED)

    @Test
    fun anEmptyVocabularyShowsTheInstructionalEmptyState() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Empty) }

        onNodeWithText("No words saved yet").assertIsDisplayed()
        onNodeWithText("Tap a word while reading", substring = true).assertIsDisplayed()
        onNodeWithTag(VOCABULARY_SEARCH_TAG).assertDoesNotExist()
    }

    @Test
    fun anEmptyVocabularyCountsNoWords() = runComposeUiTest {
        // The count sits beside the title in every state (docs/design.md §7.4), so
        // the header does not jump when the first word lands (issue #212).
        setContent { Vocabulary(VocabularyUiState.Empty) }

        onNodeWithTag(VOCABULARY_COUNT_TAG).assertTextEquals("0 words")
    }

    @Test
    fun theTitleCarriesTheCountOfSavedWords() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern, harbour), "")) }

        onNodeWithTag(VOCABULARY_COUNT_TAG).assertTextEquals("2 words")
    }

    @Test
    fun aSingleSavedWordIsCountedInTheSingular() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), "")) }

        onNodeWithTag(VOCABULARY_COUNT_TAG).assertTextEquals("1 word")
    }

    @Test
    fun theCountCountsTheVocabularyRatherThanTheFilteredList() = runComposeUiTest {
        // The count sits beside the title, so it counts what the reader has saved
        // — not the rows a search or a level narrowed the list to (issue #212).
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), "zzzz", total = 12)) }

        onNodeWithTag(VOCABULARY_COUNT_TAG).assertTextEquals("12 words")
    }

    @Test
    fun listsEachEntryWithItsTranslationContextSentenceAndMastery() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), "")) }

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertIsDisplayed()
        onNodeWithText("The lantern burned all night.").assertIsDisplayed()
        onNodeWithTag(vocabularyMasteryTag(lantern.key), useUnmergedTree = true).assertTextEquals("Learned")
    }

    @Test
    fun anEntryWithNoTranslationShowsNoTranslationLine() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern.copy(translation = null)), "")) }

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertDoesNotExist()
    }

    @Test
    fun typingInTheSearchFieldRaisesTheQuery() = runComposeUiTest {
        var query: String? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onSearch = { query = it }) }

        onNodeWithTag(VOCABULARY_SEARCH_TAG).performTextInput("lant")

        assertEquals("lant", query)
    }

    @Test
    fun aSearchThatMatchesNothingSaysSoAndOffersToClearIt() = runComposeUiTest {
        var cleared = false
        setContent {
            Vocabulary(VocabularyUiState.Results(emptyList(), "zzzz"), onClearSearch = { cleared = true })
        }

        onNodeWithText("No words match \"zzzz\".").assertIsDisplayed()
        onNodeWithText("The search covers the word, its translation and its sentence.").assertIsDisplayed()
        onNodeWithTag(VOCABULARY_CLEAR_SEARCH_TAG).performClick()

        assertTrue(cleared, "clearing the search is one tap from the no-match message")
    }

    @Test
    fun aSearchThatMatchesNothingIsNotTheEmptyVocabulary() = runComposeUiTest {
        // §8's two states: the empty one says how words arrive, the no-match one
        // names the query back — neither can stand in for the other (issue #212).
        setContent { Vocabulary(VocabularyUiState.Results(emptyList(), "zzzz")) }

        onNodeWithText("No words saved yet").assertDoesNotExist()
        onNodeWithText("No words match", substring = true).assertIsDisplayed()
    }

    @Test
    fun anEmptyVocabularyIsNotTheNoMatchState() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Empty) }

        onNodeWithText("No words match", substring = true).assertDoesNotExist()
        onNodeWithTag(VOCABULARY_CLEAR_SEARCH_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingAMasteryFilterRaisesThatLevel() = runComposeUiTest {
        var filtered: MasteryLevel? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onFilter = { filtered = it }) }

        // A pointer's chips wrap, so the level is on screen without scrolling; the
        // Android twin still scrolls the one touch row (issue #212).
        onNodeWithTag(vocabularyFilterTag(MasteryLevel.LEARNED)).performClick()

        assertEquals(MasteryLevel.LEARNED, filtered)
    }

    @Test
    fun theAllChipClearsTheMasteryFilter() = runComposeUiTest {
        var filtered: MasteryLevel? = MasteryLevel.LEARNED
        setContent {
            Vocabulary(
                VocabularyUiState.Results(listOf(lantern), "", MasteryLevel.LEARNED),
                onFilter = { filtered = it },
            )
        }

        onNodeWithTag(vocabularyFilterTag(null)).performClick()

        assertEquals(null, filtered)
    }

    @Test
    fun theCurrentFilterIsAnnouncedAsSelected() = runComposeUiTest {
        // A filter is announced, not only filled: the ink is the sighted cue, the
        // selected state is the one a screen reader hears (docs/design.md §9).
        setContent {
            Vocabulary(VocabularyUiState.Results(listOf(lantern), "", MasteryLevel.LEARNED))
        }

        onNodeWithTag(vocabularyFilterTag(MasteryLevel.LEARNED)).assertIsSelected()
        onNodeWithTag(vocabularyFilterTag(null)).assertIsNotSelected()
    }

    @Test
    fun aFilterThatMatchesNothingSaysSo() = runComposeUiTest {
        setContent { Vocabulary(VocabularyUiState.Results(emptyList(), "", MasteryLevel.KNOWN)) }

        onNodeWithText("No words at the Known level", substring = true).assertIsDisplayed()
    }

    @Test
    fun tappingARowRaisesTheWordToOpen() = runComposeUiTest {
        // The row's one tap is the level sheet (issue #178): it raises the word,
        // and the composition root resolves the live entry from it.
        var opened: VocabularyEntry? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onOpen = { opened = it }) }

        onNodeWithTag(vocabularyRowTag(lantern.key)).performClick()

        assertEquals(lantern, opened)
    }

    @Test
    fun aRowTapDeletesNothing() = runComposeUiTest {
        // Opening the word panel and deleting the word are two gestures, and the
        // tap must never reach the delete (issue #178; docs/design.md §7.4).
        var deleted: VocabularyEntry? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onDelete = { deleted = it }) }

        onNodeWithTag(vocabularyRowTag(lantern.key)).performClick()
        onNodeWithText("Delete « lantern »?").assertDoesNotExist()
        assertNull(deleted)
    }

    @Test
    fun theRowNamesTheLevelActionToAccessibility() = runComposeUiTest {
        // A screen reader hears what the tap does, in the reader's language: the
        // word names the row and the action's label names the change (issue #178).
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), "")) }

        val onClick = onNodeWithTag(vocabularyRowTag(lantern.key))
            .fetchSemanticsNode().config[SemanticsActions.OnClick]

        assertEquals("Change level", onClick.label)
    }

    @Test
    fun theHoverWashIsOneBandAcrossTheWholeRow() = runComposeUiTest {
        // The row's wash is `DeletingRow`'s, one 5 % band over the whole row. A
        // Material click on the row would paint its own 8 % hover layer over the
        // content only, so the text would sit in a darker patch than the delete's
        // slot (issue #178) — a colour a hand-drawn golden cannot catch.
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), "")) }
        onNodeWithTag(vocabularyRowTag(lantern.key)).performMouseInput { moveTo(center) }
        waitForIdle()

        val row = onNodeWithTag(vocabularyRowTag(lantern.key)).getUnclippedBoundsInRoot()
        val delete = onNodeWithTag(vocabularyDeleteTag(lantern.key)).getUnclippedBoundsInRoot()
        val root = onRoot().getUnclippedBoundsInRoot()
        val image = onRoot().captureToImage().toPixelMap()
        val scale = image.width / (root.right - root.left).value
        // Two blank points on the same line: the content's trailing edge, and the
        // delete's slot under its icon. Both are the row's wash, or one is darker.
        val y = ((delete.bottom - 4.dp).value * scale).toInt()
        val inContent = image[((row.right - 40.dp).value * scale).toInt(), y]
        val inDeleteSlot = image[((delete.left + 24.dp).value * scale).toInt(), y]

        assertEquals(inDeleteSlot, inContent, "one wash spans the row, delete slot included")
        assertTrue(inContent != Color.Transparent, "the row is hovered, so a wash is drawn")
    }

    @Test
    fun theDeleteSitsOnTheWordsLine() = runComposeUiTest {
        // The trash belongs to the row's first line, not to the gap between the
        // word and its translation: it is the word's action (issue #212).
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), "")) }

        onNodeWithTag(vocabularyRowTag(lantern.key)).performMouseInput { moveTo(center) }
        // Read from the unmerged tree: the trash's clickable target is 48 dp, and
        // it is the icon's own 18 dp the eye aligns with the word.
        val word = onNodeWithText("lantern", useUnmergedTree = true).getBoundsInRoot()
        val trash = onNodeWithContentDescription("Delete", useUnmergedTree = true).getBoundsInRoot()

        assertTrue(trash.top >= word.top, "the trash sits within the word's line: $trash vs $word")
        assertTrue(trash.bottom <= word.bottom, "the trash sits within the word's line: $trash vs $word")
    }

    @Test
    fun theDeleteAsksForConfirmationBeforeDeleting() = runComposeUiTest {
        // The mouse idiom: the trash is not on the row until the pointer is on it,
        // and it only ever asks — nothing is removed until the dialog is confirmed
        // (issue #212, docs/design.md §8: there is no undo).
        var deleted: VocabularyEntry? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onDelete = { deleted = it }) }

        onNodeWithTag(vocabularyDeleteTag(lantern.key)).assertDoesNotExist()
        onNodeWithTag(vocabularyRowTag(lantern.key)).performMouseInput { moveTo(center) }
        onNodeWithTag(vocabularyDeleteTag(lantern.key)).assertIsDisplayed().performClick()

        onNodeWithText("Delete « lantern »?").assertIsDisplayed()
        onNodeWithText("This removes the entry from your vocabulary.", substring = true).assertIsDisplayed()
        assertNull(deleted, "the vault must be untouched until the reader confirms")
    }

    @Test
    fun aDragDoesNotDeleteOnDesktop() = runComposeUiTest {
        // The swipe is the touch idiom and only that: on a pointer platform a
        // dragged row must not open the confirmation (issue #208, #212).
        var deleted: VocabularyEntry? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onDelete = { deleted = it }) }

        onNodeWithTag(vocabularyRowTag(lantern.key)).performMouseInput {
            press()
            moveBy(Offset(x = -DRAG_DISTANCE, y = 0f))
            release()
        }

        onNodeWithText("Delete « lantern »?").assertDoesNotExist()
        assertNull(deleted)
    }

    @Test
    fun confirmingTheDialogDeletesTheEntry() = runComposeUiTest {
        var deleted: VocabularyEntry? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onDelete = { deleted = it }) }

        onNodeWithTag(vocabularyRowTag(lantern.key)).performMouseInput { moveTo(center) }
        onNodeWithTag(vocabularyDeleteTag(lantern.key)).performClick()
        onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        assertEquals(lantern, deleted)
    }

    @Test
    fun cancellingTheDialogLeavesTheEntry() = runComposeUiTest {
        var deleted: VocabularyEntry? = null
        setContent { Vocabulary(VocabularyUiState.Results(listOf(lantern), ""), onDelete = { deleted = it }) }

        onNodeWithTag(vocabularyRowTag(lantern.key)).performMouseInput { moveTo(center) }
        onNodeWithTag(vocabularyDeleteTag(lantern.key)).performClick()
        onNodeWithTag(CONFIRM_DIALOG_CANCEL_TAG).performClick()

        onNodeWithText("Delete « lantern »?").assertDoesNotExist()
        assertNull(deleted)
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

    @Suppress("LongParameterList") // The screen's actions are independent; a bundle would only hide that.
    @Composable
    private fun Vocabulary(
        state: VocabularyUiState,
        onSearch: (String) -> Unit = {},
        onFilter: (MasteryLevel?) -> Unit = {},
        onClearSearch: () -> Unit = {},
        onDelete: (VocabularyEntry) -> Unit = {},
        onOpen: (VocabularyEntry) -> Unit = {},
    ) {
        MaterialTheme {
            VocabularyScreen(
                state = state,
                actions = VocabularyActions(
                    onSearch = onSearch,
                    onFilter = onFilter,
                    onClearSearch = onClearSearch,
                    onDelete = onDelete,
                    onOpen = onOpen,
                ),
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
