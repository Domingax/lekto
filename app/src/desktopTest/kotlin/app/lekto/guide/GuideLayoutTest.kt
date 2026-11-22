package app.lekto.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import app.lekto.core.CefrLevel
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.ReadingState
import app.lekto.core.book.SampleBookRow
import app.lekto.core.language.sampleRosterLanguages
import app.lekto.core.sample.Sample
import app.lekto.dictionary.LOOKUP_PANEL_TAG
import app.lekto.library.LibraryScreen
import app.lekto.library.LibraryUiState
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.guideSample
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.text.appTextsFor
import app.lekto.theme.LektoTheme
import kotlin.test.Test

/**
 * The guide at the sizes the ticket names (issue #277): a 320 dp phone, a 360 dp
 * phone and a 700 × 500 desktop window. Each screen keeps its title, its `Skip`
 * and its one action reachable, and the rows that do not fit scroll — the
 * language roster, the four notes, the six bands. This is layout alone; the copy
 * and the interactions are the semantics lanes' contract, and the planches record
 * the papers.
 */
@OptIn(ExperimentalTestApi::class)
class GuideLayoutTest {

    private val texts = appTextsFor("en")
    private val sample = guideSample()

    @Test
    fun aNarrowPhoneHoldsEveryScreen() = holdsEveryScreen(width = 320f, height = 640f)

    @Test
    fun aPhoneHoldsEveryScreen() = holdsEveryScreen(width = 360f, height = 640f)

    @Test
    fun aSmallDesktopWindowHoldsEveryScreen() = holdsEveryScreen(width = 700f, height = 500f)

    private fun holdsEveryScreen(width: Float, height: Float) {
        screen(width, height, { LanguageStage() }) {
            onNodeWithText("Which language would you like to read first?").assertIsDisplayed()
            onNodeWithTag(GUIDE_SKIP_TAG).assertIsDisplayed()
            onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG)
                .performScrollToNode(hasTestTag(guideLanguageRowTag("tr")))
            onNodeWithTag(guideLanguageRowTag("tr")).assertIsDisplayed()
            onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertCountEquals(0)
        }
        screen(width, height, { PageStage(sample) }) {
            onNodeWithText("The more you know, the less colour.").assertIsDisplayed()
            onNodeWithTag(GUIDE_NOTES_TAG).assertIsDisplayed()
            onNodeWithTag(GUIDE_CONTINUE_TAG).performScrollTo().assertIsDisplayed()
        }
        screen(width, height, { TapStage(sample) }) {
            onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
            onNodeWithTag(GUIDE_SKIP_TAG).assertIsDisplayed()
        }
        screen(width, height, { LevelStage(sample) }) {
            onNodeWithTag(guideBandTag(CefrLevel.C2)).performScrollTo().assertIsDisplayed()
            onNodeWithTag(GUIDE_START_READING_TAG).performScrollTo().assertIsDisplayed()
        }
        screen(width, height, { ArrivalStage() }) {
            onNodeWithText("A Guide Sample").performScrollTo().assertIsDisplayed()
            onNodeWithText("Installed").assertIsDisplayed()
        }
    }

    /** One size's scene, with the assertions the surface's own line makes. */
    private fun screen(
        width: Float,
        height: Float,
        content: @Composable () -> Unit,
        assertions: ComposeUiTest.() -> Unit,
    ) = runSkikoComposeUiTest(size = Size(width, height)) {
        setContent { CompositionLocalProvider(LocalAppTexts provides texts) { LektoTheme { content() } } }
        assertions()
    }

    @Composable
    private fun LanguageStage() {
        GuideScreen(
            state = GuideUiState(stage = GuideStage.LANGUAGE),
            languages = sampleRosterLanguages(),
            document = null,
        )
    }

    @Composable
    private fun PageStage(sample: Sample) {
        GuideScreen(
            state = GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            languages = sampleRosterLanguages(),
            document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, WhitespaceTextSegmenter()),
            notes = guideNotes(sample, GUIDE_DEFAULT_BAND, WhitespaceTextSegmenter(), "fr"),
            download = GuideDownloadUiState(GuideDownloadPhase.INSTALLED, "en"),
            translationLanguage = "fr",
        )
    }

    @Composable
    private fun TapStage(sample: Sample) {
        GuideScreen(
            state = GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false, lookupOpen = true),
            languages = sampleRosterLanguages(),
            document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, WhitespaceTextSegmenter()),
            notes = guideNotes(sample, GUIDE_DEFAULT_BAND, WhitespaceTextSegmenter(), "fr"),
            translationLanguage = "fr",
        )
    }

    @Composable
    private fun LevelStage(sample: Sample) {
        GuideScreen(
            state = GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
            languages = sampleRosterLanguages(),
            document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, WhitespaceTextSegmenter()),
            download = GuideDownloadUiState(GuideDownloadPhase.INSTALLED, "en"),
            translationLanguage = "fr",
        )
    }

    @Composable
    private fun ArrivalStage() {
        Column(Modifier.fillMaxSize()) {
            LibraryScreen(
                state = LibraryUiState(sample = sampleBookRow()),
                levelLine = AppMessage.of(TextKey.GUIDE_LEVEL_NOT_SET),
                modifier = Modifier.weight(1f),
            )
            GuideDownloadLine(GuideDownloadUiState(GuideDownloadPhase.INSTALLED, "en"), onStop = {})
        }
    }

    private fun sampleBookRow(): SampleBookRow = SampleBookRow(
        book = Book(
            id = Sample.identityOf("en"),
            title = "A Guide Sample",
            language = "en",
            format = BookFormat.TXT,
            fileName = "sample-en.txt",
        ),
        progress = BookProgress.NotStarted,
        state = ReadingState.UNREAD,
    )
}
