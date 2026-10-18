package app.lekto.reader

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingMargin
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.testkit.WhitespaceTextSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reader's **Aa** panel on a **simulated Android runtime** (ADR-0035): the
 * same-named twin of `app/desktopTest`'s `ReadingPreferencesPanelSemanticsTest`, so
 * the parity rule (issue #73) can see the two lanes together. Robolectric lays
 * glyphs out far more tightly than a device font, so the repagination case uses a
 * chapter long enough to span pages under its metrics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("MagicNumber") // The five size steps and the viewport are the test's parameters.
class ReadingPreferencesPanelSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    @Test
    fun thePanelShowsTheCurrentChoiceAndRaisesANewFace() {
        var changed: ReadingPreferences? = null
        compose.setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences.DEFAULT,
                    onChange = { next -> changed = next },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(readerFaceTag(ReadingFace.SERIF)).assertIsSelected()
        compose.onNodeWithTag(readerFaceTag(ReadingFace.SANS_SERIF)).performClick()

        assertEquals(ReadingFace.SANS_SERIF, changed?.face)
    }

    @Test
    fun thePanelRaisesANewMargin() {
        var changed: ReadingPreferences? = null
        compose.setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences.DEFAULT,
                    onChange = { next -> changed = next },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(readerMarginTag(ReadingMargin.COMFORTABLE)).assertIsSelected()
        compose.onNodeWithTag(readerMarginTag(ReadingMargin.WIDE)).performClick()

        assertEquals(ReadingMargin.WIDE, changed?.margin)
    }

    @Test
    fun thePanelRaisesADismiss() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences.DEFAULT,
                    onChange = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        compose.onNodeWithTag(READING_PREFERENCES_CLOSE_TAG).performClick()

        assertTrue("the panel's Close must dismiss it", dismissed)
    }

    @Test
    fun theSizeStepsAndIsDisabledAtItsBounds() {
        var changed: ReadingPreferences? = null
        compose.setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences(size = ReadingPreferences.MAX_SIZE),
                    onChange = { next -> changed = next },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithTag(READING_LARGER_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(READING_SMALLER_TAG).performClick()

        assertEquals(ReadingPreferences.MAX_SIZE - ReadingPreferences.SIZE_STEP, changed?.size)
    }

    @Test
    fun theAaControlRevealsThePanel() {
        compose.setContent { PreferencesReader(renderer) }

        compose.onNodeWithText("Text face").assertDoesNotExist()

        compose.onNodeWithTag(READING_PREFERENCES_TAG).performClick()

        compose.onNodeWithText("Text face").assertIsDisplayed()
    }

    @Test
    fun tappingOutsideThePanelDismissesItWithoutTappingAWord() {
        var tappedWord = false
        compose.setContent { PreferencesReader(renderer, onWordTap = { tappedWord = true }) }

        compose.onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        compose.onNodeWithText("Text face").assertIsDisplayed()

        // A tap on the page, well above the panel, is outside it: it dismisses
        // rather than opening the lookup on a word or turning the page.
        compose.onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(center.x, 4f)) }

        compose.onNodeWithText("Text face").assertDoesNotExist()
        assertTrue("a tap that dismisses the panel must not tap a word", !tappedWord)
    }

    @Test
    fun aSizeChangeRepaginatesTheOpenBook() {
        var changed: ReadingPreferences? = null
        compose.setContent { PreferencesReader(renderer) { next -> changed = next } }

        compose.onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        val before = compose.pageBarText()

        compose.onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        repeat(5) { compose.onNodeWithTag(READING_LARGER_TAG).performClick() }

        val chosen = changed
        assertNotNull("the panel must raise a change", chosen)
        assertEquals(ReadingPreferences.MAX_SIZE, chosen!!.size)
        assertNotEquals(before, compose.pageBarText(), "a bigger text must repaginate the open book")
    }
}

/** Sixty paragraphs of one sentence, so the chapter spans pages under Robolectric's tighter metrics. */
private val LONG_CHAPTER = ReaderChapter(
    title = "The Lantern Keeper",
    language = "en",
    blocks = List(60) {
        TextBlock(
            BlockKind.PARAGRAPH,
            listOf(TextRun("On the quiet evening the keeper lit the lantern and waited for the boats.")),
        )
    },
)

/**
 * The reader over [renderer], with the preference held as state the panel changes,
 * exactly as the app wires it (ADR-0035).
 */
@Composable
private fun PreferencesReader(
    renderer: ReaderRenderer,
    onWordTap: (WordTap) -> Unit = {},
    onChange: (ReadingPreferences) -> Unit = {},
) {
    var preferences by remember { mutableStateOf(ReadingPreferences.DEFAULT) }
    MaterialTheme {
        ReaderScreen(
            document = ReaderDocument(LONG_CHAPTER, renderer, preferences = preferences),
            modifier = Modifier.size(width = 360.dp, height = 640.dp),
            actions = ReaderActions(
                onWordTap = onWordTap,
                onBack = {},
                onReadingPreferencesChange = { next ->
                    preferences = next
                    onChange(next)
                },
            ),
        )
    }
}

/** The page bar's text, so a test can tell one pagination from another. */
private fun ComposeTestRule.pageBarText(): String = onNodeWithText("Page 1 of", substring = true).fetchSemanticsNode()
    .config[SemanticsProperties.Text]
    .joinToString(" ") { text -> text.text }
