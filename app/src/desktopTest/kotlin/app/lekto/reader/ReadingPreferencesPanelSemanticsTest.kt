package app.lekto.reader

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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.reading.ReadingFace
import app.lekto.core.reading.ReadingMargin
import app.lekto.core.reading.ReadingPreferences
import app.lekto.testkit.WhitespaceTextSegmenter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The reader's **Aa** panel (ADR-0035): it shows the current face, size and margin,
 * it raises the value the reader picked, the size stops at its bounds, and a change
 * repaginates the open book at once. The panel is the reader's own chrome, so its
 * same-named `app/androidUnitTest` twin re-proves it on the runtime Android uses.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("MagicNumber") // The five size steps and the viewport are the test's parameters.
class ReadingPreferencesPanelSemanticsTest {

    private val renderer = ReaderRenderer(
        segmenter = WhitespaceTextSegmenter(),
        mastery = MasteryLookup { MasteryLevel.KNOWN },
    )

    @Test
    fun thePanelShowsTheCurrentChoiceAndRaisesANewFace() = runComposeUiTest {
        var changed: ReadingPreferences? = null
        setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences.DEFAULT,
                    onChange = { next -> changed = next },
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag(readerFaceTag(ReadingFace.SERIF)).assertIsSelected()
        onNodeWithTag(readerFaceTag(ReadingFace.SANS_SERIF)).performClick()

        assertEquals(ReadingFace.SANS_SERIF, changed?.face)
    }

    @Test
    fun thePanelRaisesANewMargin() = runComposeUiTest {
        var changed: ReadingPreferences? = null
        setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences.DEFAULT,
                    onChange = { next -> changed = next },
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag(readerMarginTag(ReadingMargin.COMFORTABLE)).assertIsSelected()
        onNodeWithTag(readerMarginTag(ReadingMargin.WIDE)).performClick()

        assertEquals(ReadingMargin.WIDE, changed?.margin)
    }

    @Test
    fun thePanelRaisesADismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences.DEFAULT,
                    onChange = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        onNodeWithTag(READING_PREFERENCES_CLOSE_TAG).performClick()

        assertTrue(dismissed, "the panel's Close must dismiss it")
    }

    @Test
    fun theSizeStepsAndIsDisabledAtItsBounds() = runComposeUiTest {
        var changed: ReadingPreferences? = null
        setContent {
            MaterialTheme {
                ReadingPreferencesPanel(
                    ReadingPreferences(size = ReadingPreferences.MAX_SIZE),
                    onChange = { next -> changed = next },
                    onDismiss = {},
                )
            }
        }

        onNodeWithTag(READING_LARGER_TAG).assertIsNotEnabled()
        onNodeWithTag(READING_SMALLER_TAG).performClick()

        assertEquals(ReadingPreferences.MAX_SIZE - ReadingPreferences.SIZE_STEP, changed?.size)
    }

    @Test
    fun theAaControlRevealsThePanel() = runComposeUiTest {
        setContent { PreferencesReader(renderer) }

        onNodeWithText("Text face").assertDoesNotExist()

        onNodeWithTag(READING_PREFERENCES_TAG).performClick()

        onNodeWithText("Text face").assertIsDisplayed()
    }

    /**
     * The panel joins the column under the bottom band, so it pushes the page and
     * the position line up rather than covering them (docs/design.md §7.2).
     */
    @Test
    fun thePanelPushesTheBandUpInsteadOfCoveringThePage() = runComposeUiTest {
        setContent { PreferencesReader(renderer) }
        onNodeWithTag(READING_PREFERENCES_TAG).performClick()

        val page = onNodeWithTag(READER_PAGE_TAG).fetchSemanticsNode().boundsInRoot
        val position = onNodeWithTag(READER_POSITION_TAG).fetchSemanticsNode().boundsInRoot
        val panel = onNodeWithTag(READING_PREFERENCES_PANEL_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(position.bottom <= panel.top, "the position must stay above the panel")
        assertTrue(page.bottom <= position.top, "the page must end above the position band")
    }

    @Test
    fun tappingOutsideThePanelDismissesItWithoutTappingAWord() = runComposeUiTest {
        var tappedWord = false
        setContent { PreferencesReader(renderer, onWordTap = { tappedWord = true }) }

        onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        onNodeWithText("Text face").assertIsDisplayed()

        // A tap on the page, well above the panel, is outside it: it dismisses
        // rather than opening the lookup on a word or turning the page.
        onNodeWithTag(READER_PAGE_TAG).performTouchInput { click(Offset(center.x, 4f)) }

        onNodeWithText("Text face").assertDoesNotExist()
        assertTrue(!tappedWord, "a tap that dismisses the panel must not tap a word")
        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
    }

    /**
     * The page above the sheet is the preview (ADR-0035): a face or margin choice
     * reaches the reader the moment it is tapped, the sheet stays open, and there
     * is no separate step to confirm it.
     */
    @Test
    fun aFaceOrMarginChangeAppliesAtOnceWithNoConfirmationStep() = runComposeUiTest {
        var changed: ReadingPreferences? = null
        setContent { PreferencesReader(renderer) { next -> changed = next } }

        onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        onNodeWithTag(readerFaceTag(ReadingFace.SANS_SERIF)).performClick()

        assertEquals(ReadingFace.SANS_SERIF, assertNotNull(changed).face)
        onNodeWithTag(readerFaceTag(ReadingFace.SANS_SERIF)).assertIsSelected()

        onNodeWithTag(readerMarginTag(ReadingMargin.WIDE)).performClick()

        assertEquals(ReadingMargin.WIDE, assertNotNull(changed).margin)
        onNodeWithTag(readerMarginTag(ReadingMargin.WIDE)).assertIsSelected()

        onNodeWithTag(READING_SMALLER_TAG).performClick()

        assertEquals(ReadingPreferences.DEFAULT_SIZE - ReadingPreferences.SIZE_STEP, assertNotNull(changed).size)
        // The sheet is still the one open surface: the change applied above it.
        onNodeWithText("Text face").assertIsDisplayed()
    }

    @Test
    fun aSizeChangeRepaginatesTheOpenBook() = runComposeUiTest {
        var changed: ReadingPreferences? = null
        setContent { PreferencesReader(renderer) { next -> changed = next } }

        onNodeWithText("Page 1 of", substring = true).assertIsDisplayed()
        val before = pageBarText()

        onNodeWithTag(READING_PREFERENCES_TAG).performClick()
        repeat(5) { onNodeWithTag(READING_LARGER_TAG).performClick() }

        assertEquals(ReadingPreferences.MAX_SIZE, assertNotNull(changed).size)
        assertNotEquals(before, pageBarText(), "a bigger text must repaginate the open book")
    }
}

/**
 * The reader over [renderer], with the preference held as state the panel changes,
 * exactly as the app wires it (ADR-0035).
 */
@Composable
@Suppress("LongParameterList") // The reader's inputs are the test's knobs; a bundle would only hide them.
private fun PreferencesReader(
    renderer: ReaderRenderer,
    chapter: ReaderChapter = SampleChapter.chapter,
    onWordTap: (WordTap) -> Unit = {},
    onChange: (ReadingPreferences) -> Unit = {},
) {
    var preferences by remember { mutableStateOf(ReadingPreferences.DEFAULT) }
    MaterialTheme {
        ReaderScreen(
            document = ReaderDocument(chapter, renderer, preferences = preferences),
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
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.pageBarText(): String = onNodeWithText("Page 1 of", substring = true).fetchSemanticsNode()
    .config[SemanticsProperties.Text]
    .joinToString(" ") { text -> text.text }
