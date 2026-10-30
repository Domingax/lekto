package app.lekto.vocabulary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import app.lekto.theme.WIDE_COLUMN_WIDTH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The level panel the Vocabulary list raises (issue #178), in both shapes: the
 * phone's sheet, and the reserved right-hand column a wide window gives the word
 * (docs/design.md §6). Both carry the saved word, the translation and the sentence
 * the row shows, and the reader's own five-level selector with the current level
 * marked — the one place a level is written outside the reader.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions") // One behaviour per test, in both shapes: splitting hides the panel.
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
    fun theColumnShowsTheSamePanelAndNotTheSheet() = runComposeUiTest {
        // A wide window reserves the word's column instead of floating a sheet
        // over the rows (issue #178, docs/design.md §6).
        setContent { Column(lantern) }

        onNodeWithTag(VOCABULARY_LEVEL_COLUMN_TAG).assertIsDisplayed()
        onNodeWithText("lantern").assertIsDisplayed()
        onNodeWithText("lanterne").assertIsDisplayed()
        onNodeWithTag(vocabularyLevelTag(MasteryLevel.LEARNED)).assertIsSelected()
        onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun theColumnSaysWhatItIsForWhileNoWordIsOpen() = runComposeUiTest {
        setContent { Column(null) }

        onNodeWithText("Choose a word to change its level.").assertIsDisplayed()
        onNodeWithTag(vocabularyLevelTag(MasteryLevel.LEARNED)).assertDoesNotExist()
    }

    @Test
    fun theColumnStacksTheLevelAboveItsChips() = runComposeUiTest {
        // 320 dp has no room for the name beside five chips, so it stands above
        // them — the same arrangement the reader's column uses (issue #199).
        setContent { Column(lantern) }

        val name = onNodeWithTag(VOCABULARY_LEVEL_NAME_TAG).getUnclippedBoundsInRoot()
        val chips = onNodeWithTag(vocabularyLevelTag(MasteryLevel.UNKNOWN)).getUnclippedBoundsInRoot()

        assertTrue(name.bottom <= chips.top, "the name sits above the chips: $name then $chips")
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

    @Composable
    private fun Column(entry: VocabularyEntry?, onSave: (MasteryLevel) -> Unit = {}, onDismiss: () -> Unit = {}) {
        MaterialTheme {
            Box(Modifier.size(width = WIDE_COLUMN_WIDTH, height = 640.dp)) {
                VocabularyLevelColumn(entry = entry, onSave = onSave, onDismiss = onDismiss)
            }
        }
    }
}
