package app.lekto.vocabulary

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.testkit.testVocabularyEntry
import app.lekto.theme.LektoSpacing
import app.lekto.theme.WIDE_COLUMN_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The level panel on a **simulated Android runtime** (issue #178): the
 * same-named twin of `app/desktopTest`'s `VocabularyLevelSheetSemanticsTest`, so
 * the parity rule (issue #73) sees both lanes. It proves the sheet's selector and
 * its dismissal, and the column a wide window would show, on the touch runtime
 * the phone ships.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class VocabularyLevelSheetSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val lantern = testVocabularyEntry()

    @Test
    fun theSheetShowsTheWordItsTranslationAndItsSentenceOnAndroid() {
        compose.setContent { Sheet(lantern) }

        compose.onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).assertIsDisplayed()
        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithText("lanterne").assertIsDisplayed()
        compose.onNodeWithText("The lantern burned all night.").assertIsDisplayed()
    }

    @Test
    fun theSavedLevelIsTheOnlyOneMarkedOnAndroid() {
        compose.setContent { Sheet(lantern) }

        compose.onNodeWithTag(vocabularyLevelTag(MasteryLevel.LEARNED)).assertIsSelected()
        compose.onNodeWithTag(vocabularyLevelTag(MasteryLevel.KNOWN)).assertIsNotSelected()
        compose.onNodeWithTag(VOCABULARY_LEVEL_NAME_TAG).assertIsDisplayed()
        compose.onNodeWithText("Learned").assertIsDisplayed()
    }

    @Test
    fun aLevelTapRaisesItOnAndroid() {
        var saved: MasteryLevel? = null
        compose.setContent { Sheet(lantern, onSave = { saved = it }) }

        compose.onNodeWithTag(vocabularyLevelTag(MasteryLevel.KNOWN)).performClick()

        assertEquals(MasteryLevel.KNOWN, saved)
    }

    @Test
    fun theCloseActionDismissesOnAndroid() {
        var dismissed = false
        compose.setContent { Sheet(lantern, onDismiss = { dismissed = true }) }

        compose.onNodeWithTag(VOCABULARY_LEVEL_CLOSE_TAG).performClick()

        assertTrue(dismissed)
    }

    @Test
    fun everyLevelChipClearsTheTouchTargetOnAndroid() {
        compose.setContent { Sheet(lantern) }

        MasteryLevel.entries.forEach { level ->
            val bounds = compose.onNodeWithTag(vocabularyLevelTag(level)).getBoundsInRoot()
            assertTrue(
                "$level is shorter than a touch target: $bounds",
                bounds.bottom - bounds.top >= LektoSpacing.TouchTarget,
            )
        }
    }

    @Test
    fun theColumnShowsTheSamePanelOnAndroid() {
        compose.setContent { Column(lantern) }

        compose.onNodeWithTag(VOCABULARY_LEVEL_COLUMN_TAG).assertIsDisplayed()
        compose.onNodeWithText("lantern").assertIsDisplayed()
        compose.onNodeWithTag(vocabularyLevelTag(MasteryLevel.LEARNED)).assertIsSelected()
        compose.onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).assertDoesNotExist()
    }

    @Test
    fun theColumnStacksTheLevelAboveItsChipsOnAndroid() {
        compose.setContent { Column(lantern) }

        val name = compose.onNodeWithTag(VOCABULARY_LEVEL_NAME_TAG).getUnclippedBoundsInRoot()
        val chips = compose.onNodeWithTag(vocabularyLevelTag(MasteryLevel.UNKNOWN)).getUnclippedBoundsInRoot()

        assertTrue("the name sits above the chips: $name then $chips", name.bottom <= chips.top)
    }

    @Composable
    private fun Sheet(entry: VocabularyEntry, onSave: (MasteryLevel) -> Unit = {}, onDismiss: () -> Unit = {}) {
        MaterialTheme {
            VocabularyLevelSheet(entry = entry, onSave = onSave, onDismiss = onDismiss)
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
