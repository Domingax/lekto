package app.lekto.vocabulary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.testkit.testVocabularyEntry
import app.lekto.theme.LektoSpacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The test tag on the window the wide-sheet test lays out, past the breakpoint. */
private const val WIDE_WINDOW_TAG: String = "vocabulary-level-window"

/** The phone's width, where the cap cannot bind: the sheet fills it. */
private const val NARROW_WINDOW_WIDTH: Float = 360f

/**
 * The level sheet the Vocabulary list raises (issue #178): the saved word, the
 * translation and the sentence the row shows, and the reader's own five-level
 * selector with the current level marked — the one place a level is written
 * outside the reader.
 */
@OptIn(ExperimentalTestApi::class)
class VocabularyLevelSheetSemanticsTest {

    private val lantern = testVocabularyEntry()

    @Test
    fun showsTheWordItsTranslationAndItsSentence() = runComposeUiTest {
        setContent { Sheet(lantern) }

        onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).assertIsDisplayed()
        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertIsDisplayed()
        onNodeWithText("The lantern burned all night.").assertIsDisplayed()
    }

    @Test
    fun marksTheSavedLevelAndNoOther() = runComposeUiTest {
        // The selector opens on the level the row shows, so the reader can see
        // what the word is before changing it.
        setContent { Sheet(lantern) }

        MasteryLevel.entries.forEach { level ->
            val chip = onNodeWithTag(vocabularyLevelTag(level))
            if (level == lantern.mastery) chip.assertIsSelected() else chip.assertIsNotSelected()
        }
        onNodeWithTag(VOCABULARY_LEVEL_NAME_TAG).assertIsDisplayed()
        onNodeWithText("Learned").assertIsDisplayed()
    }

    @Test
    fun tappingALevelRaisesIt() = runComposeUiTest {
        var saved: MasteryLevel? = null
        setContent { Sheet(lantern, onSave = { saved = it }) }

        onNodeWithTag(vocabularyLevelTag(MasteryLevel.KNOWN)).performClick()

        assertEquals(MasteryLevel.KNOWN, saved)
    }

    @Test
    fun theCloseActionDismisses() = runComposeUiTest {
        var dismissed = false
        setContent { Sheet(lantern, onDismiss = { dismissed = true }) }

        onNodeWithTag(VOCABULARY_LEVEL_CLOSE_TAG).performClick()

        assertTrue(dismissed, "the sheet's close is one tap, like the lookup panel's")
    }

    @Test
    fun tappingOutsideDismisses() = runComposeUiTest {
        // The scrim, as the reader's panels have: a tap outside the sheet closes
        // it instead of reaching the list behind (docs/design.md §7.3).
        var dismissed = false
        setContent { Sheet(lantern, onDismiss = { dismissed = true }) }

        onNodeWithTag(VOCABULARY_LEVEL_SCRIM_TAG).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun anEntryWithNoTranslationOrSentenceShowsOnlyItsWord() = runComposeUiTest {
        setContent { Sheet(lantern.copy(translation = null, contextSentence = null)) }

        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertDoesNotExist()
        onNodeWithText("The lantern burned all night.").assertDoesNotExist()
    }

    @Test
    fun everyLevelChipIsAtLeastTheMinimumTouchTarget() = runComposeUiTest {
        // The chips are compact, so the target is what makes them reachable
        // (docs/design.md §9).
        setContent { Sheet(lantern) }

        MasteryLevel.entries.forEach { level ->
            val bounds = onNodeWithTag(vocabularyLevelTag(level)).getBoundsInRoot()
            assertTrue(
                bounds.bottom - bounds.top >= LektoSpacing.TouchTarget,
                "$level is shorter than a touch target: $bounds",
            )
        }
    }

    @Test
    fun aWideWindowCapsTheSheetAndCentresIt() = runComposeUiTest {
        // A sheet stretching the whole width of a desktop window reads as a bar,
        // not as something raised for one word (docs/design.md §6, issue #178).
        setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width = 1400.dp, height = 900.dp).testTag(WIDE_WINDOW_TAG)) {
                    VocabularyLevelSheet(entry = lantern, onSave = {}, onDismiss = {})
                }
            }
        }

        val sheet = onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).getUnclippedBoundsInRoot()
        val window = onNodeWithTag(WIDE_WINDOW_TAG).getUnclippedBoundsInRoot()

        assertEquals(WideSheetWidth.value, (sheet.right - sheet.left).value, 1f)
        // Centred in the window, not merely narrower than it: the days of the
        // window's own edges are equal on both sides.
        assertEquals(
            (window.left.value + window.right.value) / 2,
            (sheet.left.value + sheet.right.value) / 2,
            1f,
            "the sheet is centred",
        )
    }

    @Test
    fun aNarrowWindowKeepsTheSheetFullWidth() = runComposeUiTest {
        setContent { Sheet(lantern) }

        val sheet = onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).getUnclippedBoundsInRoot()

        assertEquals(NARROW_WINDOW_WIDTH, (sheet.right - sheet.left).value, 1f)
    }

    @Composable
    private fun Sheet(entry: VocabularyEntry, onSave: (MasteryLevel) -> Unit = {}, onDismiss: () -> Unit = {}) {
        MaterialTheme {
            VocabularyLevelSheet(
                entry = entry,
                onSave = onSave,
                onDismiss = onDismiss,
                modifier = Modifier.size(width = 360.dp, height = 640.dp),
            )
        }
    }
}
