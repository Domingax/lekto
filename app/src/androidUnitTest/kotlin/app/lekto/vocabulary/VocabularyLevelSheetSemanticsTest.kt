package app.lekto.vocabulary

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The test tag on the window the wide-sheet test lays out, past the breakpoint. */
private const val WIDE_WINDOW_TAG: String = "vocabulary-level-window"

/** The phone's width, where the cap cannot bind: the sheet fills it. */
private const val NARROW_WINDOW_WIDTH: Float = 360f

/** How far a measured bound may sit from the arithmetic, in dp: a sub-pixel rounding. */
private const val TOLERANCE: Double = 1.0

/**
 * The level sheet on a **simulated Android runtime** (issue #178): the
 * same-named twin of `app/desktopTest`'s `VocabularyLevelSheetSemanticsTest`, so
 * the parity rule (issue #73) sees both lanes. It proves the sheet's selector and
 * its dismissal on the touch runtime the phone ships.
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
    fun aWideWindowCapsTheSheetAndCentresItOnAndroid() {
        // The same frame and the same cap, on the runtime the phone ships
        // (issue #178): a desktop window gets a column's width, centred.
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width = 1400.dp, height = 900.dp).testTag(WIDE_WINDOW_TAG)) {
                    VocabularyLevelSheet(entry = lantern, onSave = {}, onDismiss = {})
                }
            }
        }

        val sheet = compose.onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).getUnclippedBoundsInRoot()
        val window = compose.onNodeWithTag(WIDE_WINDOW_TAG).getUnclippedBoundsInRoot()

        assertEquals("width", WideSheetWidth.value.toDouble(), (sheet.right - sheet.left).value.toDouble(), TOLERANCE)
        // Centred in the window, not merely narrower than it: the days of the
        // window's own edges are equal on both sides.
        assertEquals(
            "the sheet is centred",
            (window.left.value + window.right.value).toDouble() / 2,
            (sheet.left.value + sheet.right.value).toDouble() / 2,
            TOLERANCE,
        )
    }

    @Test
    fun aNarrowWindowKeepsTheSheetFullWidthOnAndroid() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(width = NARROW_WINDOW_WIDTH.dp, height = 640.dp)) {
                    VocabularyLevelSheet(entry = lantern, onSave = {}, onDismiss = {})
                }
            }
        }

        val sheet = compose.onNodeWithTag(VOCABULARY_LEVEL_SHEET_TAG).getUnclippedBoundsInRoot()

        assertEquals("width", NARROW_WINDOW_WIDTH.toDouble(), (sheet.right - sheet.left).value.toDouble(), TOLERANCE)
    }

    @Composable
    private fun Sheet(entry: VocabularyEntry, onSave: (MasteryLevel) -> Unit = {}, onDismiss: () -> Unit = {}) {
        MaterialTheme {
            VocabularyLevelSheet(entry = entry, onSave = onSave, onDismiss = onDismiss)
        }
    }
}
