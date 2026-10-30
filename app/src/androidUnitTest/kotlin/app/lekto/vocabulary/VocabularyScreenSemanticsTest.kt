package app.lekto.vocabulary

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import app.lekto.core.MasteryLevel
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.testkit.testVocabularyEntry
import app.lekto.ui.CONFIRM_DIALOG_CANCEL_TAG
import app.lekto.ui.CONFIRM_DIALOG_CONFIRM_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The vocabulary list on a **simulated Android runtime** (issue #71): the
 * same-named twin of `app/desktopTest`'s `VocabularyScreenSemanticsTest`, so the
 * parity rule (issue #73) can see the two lanes together. The list's `LazyColumn`
 * keyed its rows by a `WordKey` (a data class), which Android rejects because a
 * lazy item's key must be Bundle-saveable; the desktop lane never enforced that,
 * so the crash (issue #69) shipped green. Rendering the list here runs the same
 * code on the runtime that broke, so the constraint is tested where it applies.
 *
 * The delete is proved here through the touch idiom the desktop lane cannot
 * reach: a swipe from the trailing edge opens the same confirmation (issue #212).
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // One list, one test per behaviour; splitting hides the screen's surface.
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class VocabularyScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val lantern = testVocabularyEntry()

    @Test
    fun theListRendersSavedEntriesOnAndroid() {
        compose.setContent {
            MaterialTheme {
                VocabularyScreen(state = VocabularyUiState.Results(listOf(lantern), ""))
            }
        }

        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("lanterne").assertIsDisplayed()
        compose.onNodeWithTag(VOCABULARY_COUNT_TAG).assertTextEquals("1 word")
    }

    @Test
    fun anEntryWithNoTranslationShowsNoTranslationLineOnAndroid() {
        val entry = testVocabularyEntry().copy(translation = null)

        compose.setContent {
            MaterialTheme {
                VocabularyScreen(state = VocabularyUiState.Results(listOf(entry), ""))
            }
        }

        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("lanterne").assertDoesNotExist()
    }

    @Test
    fun theTwoEmptyStatesDifferOnAndroid() {
        compose.setContent {
            MaterialTheme {
                VocabularyScreen(state = VocabularyUiState.Results(emptyList(), "zzzz"))
            }
        }

        compose.onNodeWithText("No words match", substring = true).assertIsDisplayed()
        compose.onNodeWithText("No words saved yet").assertDoesNotExist()
        compose.onNodeWithTag(VOCABULARY_CLEAR_SEARCH_TAG).assertIsDisplayed().performClick()
    }

    @Test
    fun aSwipeAsksForConfirmationBeforeDeleting() {
        // The touch idiom: a swipe uncovers the action, and the gesture only ever
        // asks — nothing is removed until the dialog is confirmed (issue #212).
        var deleted: VocabularyEntry? = null
        compose.setContent {
            MaterialTheme {
                VocabularyScreen(
                    state = VocabularyUiState.Results(listOf(lantern), ""),
                    actions = VocabularyActions(onDelete = { deleted = it }),
                )
            }
        }

        compose.onNodeWithTag(vocabularyRowTag(lantern.key)).performTouchInput { swipeLeft() }

        compose.onNodeWithText("Delete « lantern »?").assertIsDisplayed()
        assertNull("the vault must be untouched until the reader confirms", deleted)
    }

    @Test
    fun confirmingTheDialogDeletesTheEntryOnAndroid() {
        var deleted: VocabularyEntry? = null
        compose.setContent {
            MaterialTheme {
                VocabularyScreen(
                    state = VocabularyUiState.Results(listOf(lantern), ""),
                    actions = VocabularyActions(onDelete = { deleted = it }),
                )
            }
        }

        compose.onNodeWithTag(vocabularyRowTag(lantern.key)).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(CONFIRM_DIALOG_CONFIRM_TAG).performClick()

        assertEquals(lantern, deleted)
    }

    @Test
    fun cancellingTheDialogLeavesTheEntryOnAndroid() {
        var deleted: VocabularyEntry? = null
        compose.setContent {
            MaterialTheme {
                VocabularyScreen(
                    state = VocabularyUiState.Results(listOf(lantern), ""),
                    actions = VocabularyActions(onDelete = { deleted = it }),
                )
            }
        }

        compose.onNodeWithTag(vocabularyRowTag(lantern.key)).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(CONFIRM_DIALOG_CANCEL_TAG).performClick()

        compose.onNodeWithText("Delete « lantern »?").assertDoesNotExist()
        assertNull(deleted)
    }

    @Test
    fun aFilterChipRaisesItsLevelOnAndroid() {
        var filtered: MasteryLevel? = null
        compose.setContent {
            MaterialTheme {
                VocabularyScreen(
                    state = VocabularyUiState.Results(listOf(lantern), ""),
                    actions = VocabularyActions(onFilter = { filtered = it }),
                )
            }
        }

        compose.onNodeWithTag(vocabularyFilterTag(MasteryLevel.LEARNED))
            .performScrollTo()
            .performClick()

        assertEquals(MasteryLevel.LEARNED, filtered)
    }

    @Test
    fun aRowTapRaisesTheWordAndNotTheSwipeOnAndroid() {
        // The touch lane's two gestures: a tap opens the word's level sheet, a
        // swipe still only asks to delete (issue #178; docs/design.md §7.4).
        var opened: VocabularyEntry? = null
        var deleted: VocabularyEntry? = null
        compose.setContent {
            MaterialTheme {
                VocabularyScreen(
                    state = VocabularyUiState.Results(listOf(lantern), ""),
                    actions = VocabularyActions(onDelete = { deleted = it }, onOpen = { opened = it }),
                )
            }
        }

        compose.onNodeWithTag(vocabularyRowTag(lantern.key)).performClick()

        assertEquals(lantern, opened)
        compose.onNodeWithText("Delete « lantern »?").assertDoesNotExist()
        assertNull(deleted)
    }

    @Test
    fun theCountPluralisesOnAndroid() {
        val harbour = testVocabularyEntry(surface = "harbour")

        compose.setContent {
            MaterialTheme {
                VocabularyScreen(state = VocabularyUiState.Results(listOf(lantern, harbour), "", total = 12))
            }
        }

        compose.onNodeWithTag(VOCABULARY_COUNT_TAG).assertTextEquals("12 words")
    }
}
