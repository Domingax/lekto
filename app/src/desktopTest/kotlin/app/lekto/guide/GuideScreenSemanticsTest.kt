package app.lekto.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.BookProgress
import app.lekto.core.book.ReadingState
import app.lekto.core.book.SampleBookRow
import app.lekto.core.language.SampleRoster
import app.lekto.core.language.sampleRosterLanguages
import app.lekto.core.sample.Sample
import app.lekto.dictionary.LOOKUP_CLOSE_TAG
import app.lekto.dictionary.LOOKUP_PANEL_TAG
import app.lekto.dictionary.masteryTag
import app.lekto.library.LibraryScreen
import app.lekto.library.LibraryUiState
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.guideSample
import app.lekto.testkit.testOfferedArtifact
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.text.appTextsFor
import app.lekto.theme.LektoTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first-launch guide's five screens through semantics (issue #277): the
 * language question with no preselection and its eight-row scroll, the page's
 * two beats and their four notes, the real sheet's word and meaning, the level
 * that saves and states `Saved · <level>`, the six bands, `Skip` on every screen,
 * and the arrival's sample row and download line. The copy is the contract here;
 * the goldens carry the layout.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LongParameterList") // One behaviour per screen; the render inputs are independent.
class GuideScreenSemanticsTest {

    private val segmenter = WhitespaceTextSegmenter()
    private val sample = guideSample()
    private val texts = appTextsFor("en")

    @Test
    fun theQuestionListsEveryRosterLanguageWithNoPreselection() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.LANGUAGE), sample = null)

        onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG).assertIsDisplayed()
        onNodeWithText("Which language would you like to read first?").assertIsDisplayed()
        SampleRoster.LANGUAGES.forEach { language ->
            onNodeWithTag(guideLanguageRowTag(language)).performScrollTo().assertIsDisplayed()
        }
        // No row carries a selection: the question has no default answer.
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertCountEquals(0)
    }

    @Test
    fun choosingALanguageRaisesIt() = runComposeUiTest {
        var chosen: String? = null
        render(
            GuideUiState(stage = GuideStage.LANGUAGE),
            sample = null,
            actions = GuideActions(onChooseLanguage = { language -> chosen = language }),
        )

        onNodeWithTag(guideLanguageRowTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theFirstBeatShowsTheParagraphWithNoColourAndNoNotes() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true))

        onNodeWithText("Here is a page of your book.").assertIsDisplayed()
        onNodeWithText("Nothing is coloured yet. Look.").assertIsDisplayed()
        onNodeWithTag(GUIDE_NOTES_TAG).assertDoesNotExist()
        onNodeWithText("fisherman", substring = true).assertIsDisplayed()
    }

    @Test
    fun theSecondBeatNamesFourNotesWithTheirLevelAndMeaning() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false))

        onNodeWithText("The more you know, the less colour.").assertIsDisplayed()
        onNodeWithTag(GUIDE_NOTES_TAG).assertIsDisplayed()
        listOf(
            MasteryLevel.UNKNOWN to "Unknown",
            MasteryLevel.RECOGNIZED to "Recognized",
            MasteryLevel.FAMILIAR to "Familiar",
            MasteryLevel.LEARNED to "Learned",
        ).forEach { (level, name) ->
            onNodeWithTag(guideNoteTag(level)).assertExists()
            onNodeWithText(name.uppercase()).assertExists()
        }
        onNodeWithText("fisherman").assertExists()
        onNodeWithText("pêcheur").assertExists()
        onNodeWithText("calme").assertExists()
    }

    @Test
    fun theTapOpensTheRealSheetAnsweredFromTheSample() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false, lookupOpen = true))

        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        onNodeWithText("fisherman").assertIsDisplayed()
        onNodeWithText("→ pêcheur", substring = true).assertExists()
        onAllNodesWithText("The old fisherman rowed his small boat across the calm water.")
            .assertCountEquals(2)
        onNodeWithText("Not saved").assertExists()
    }

    @Test
    fun tappingALevelSavesTheWordAndStatesIt() = runComposeUiTest {
        val saved = mutableStateOf<MasteryLevel?>(null)
        render(
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false, lookupOpen = true),
            actions = GuideActions(onSaveWord = { level -> saved.value = level }),
            savedLevel = saved,
        )

        onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        onNodeWithText("Saved · Familiar").assertExists()
    }

    @Test
    fun closingTheSheetWithoutChoosingWritesNothing() = runComposeUiTest {
        var saved: MasteryLevel? = null
        var closed = false
        render(
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false, lookupOpen = true),
            actions = GuideActions(
                onSaveWord = { level -> saved = level },
                onDismissLookup = { closed = true },
            ),
        )

        onNodeWithTag(LOOKUP_CLOSE_TAG).performClick()

        assertTrue(closed, "the sheet closes")
        assertEquals(null, saved, "closing is not a save")
    }

    @Test
    fun theLevelScreenCarriesSixBandsAndTheirOneLine() = runComposeUiTest {
        var chosen: CefrLevel? = null
        render(
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false, band = CefrLevel.B1),
            actions = GuideActions(onChooseBand = { level -> chosen = level }),
        )

        onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        CefrLevel.entries.forEach { level ->
            onNodeWithTag(guideBandTag(level)).performScrollTo().assertIsDisplayed()
            onNodeWithText(texts.bandName(level)).assertIsDisplayed()
            onNodeWithText(texts.bandLine(level)).assertIsDisplayed()
        }
        onNodeWithText("What is your level in English?").assertIsDisplayed()

        onNodeWithTag(guideBandTag(CefrLevel.C1)).performScrollTo().performClick()

        assertEquals(CefrLevel.C1, chosen)
    }

    @Test
    fun skipSitsOnEveryScreen() = runComposeUiTest {
        var skipped = 0
        val stages = listOf(
            GuideUiState(stage = GuideStage.LANGUAGE),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme { GuideUnderTest(current.value, stageActions { skipped++ }) }
            }
        }

        stages.forEach { stage ->
            current.value = stage
            waitForIdle()
            onNodeWithTag(GUIDE_SKIP_TAG).performClick()
        }

        assertEquals(stages.size, skipped)
    }

    @Test
    fun oneLanguageOpensOnThePageNamingIt() = runComposeUiTest {
        render(
            GuideUiState(stage = GuideStage.PAGE, sample = sample),
            languages = listOf(app.lekto.testkit.testOfferedLanguage("en")),
        )

        onNodeWithText("Reading in English").assertIsDisplayed()
        onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun theDownloadLineAnnouncesItsSizeAndFollowsTheStop() = runComposeUiTest {
        val artifacts = listOf(
            testOfferedArtifact("lekto-difficulty-en.sqlite.gz", sizeBytes = 12_000_000),
            testOfferedArtifact("lekto-dictionary-en-fr.sqlite.gz", sizeBytes = 24_000_000),
        )
        val download = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.DOWNLOADING, "en", artifacts))
        render(
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true),
            downloadState = download,
            actions = GuideActions(
                onStopDownload = { download.value = download.value.copy(phase = GuideDownloadPhase.STOPPED) },
            ),
        )

        onNodeWithText("Table and dictionary · English").assertIsDisplayed()
        onNodeWithText("36 MB in the background").assertIsDisplayed()

        onNodeWithTag(GUIDE_DOWNLOAD_STOP_TAG).performClick()

        onNodeWithText("Stopped").assertIsDisplayed()
        onNodeWithTag(GUIDE_DOWNLOAD_STOP_TAG).assertDoesNotExist()
    }

    @Test
    fun theArrivalIsTheShelfWithTheSampleAndTheDownloadLine() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme {
                    Column(Modifier.fillMaxSize()) {
                        LibraryScreen(
                            state = LibraryUiState(sample = sampleBookRow()),
                            levelLine = AppMessage.of(TextKey.GUIDE_LEVEL_NOT_SET),
                            modifier = Modifier.weight(1f),
                        )
                        GuideDownloadLine(
                            GuideDownloadUiState(GuideDownloadPhase.INSTALLED, "en"),
                            onStop = {},
                        )
                    }
                }
            }
        }

        onNodeWithText("A Guide Sample").performScrollTo().assertIsDisplayed()
        onNodeWithText("Your reading level is not set yet. Settings ▸ Languages.").assertIsDisplayed()
        onNodeWithText("Installed").assertIsDisplayed()
    }

    /** Renders the guide over [state], with the page and notes its sample produces. */
    private fun ComposeUiTest.render(
        state: GuideUiState,
        sample: Sample? = guideSample(),
        languages: List<app.lekto.core.language.OfferedLanguage> = sampleRosterLanguages(),
        actions: GuideActions = GuideActions(),
        download: GuideDownloadUiState = GuideDownloadUiState(),
        downloadState: androidx.compose.runtime.MutableState<GuideDownloadUiState> = mutableStateOf(download),
        savedLevel: androidx.compose.runtime.MutableState<MasteryLevel?> = mutableStateOf(state.savedLevel),
    ) {
        val document = sample?.let { chosen -> guideDocument(chosen, state.band, state.plain, segmenter) }
        val notes = sample?.let { chosen -> guideNotes(chosen, state.band, segmenter, "fr") }.orEmpty()
        setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme {
                    GuideScreen(
                        state = state.copy(sample = sample, savedLevel = savedLevel.value),
                        languages = languages,
                        document = document,
                        notes = notes,
                        download = downloadState.value,
                        translationLanguage = "fr",
                        actions = actions,
                    )
                }
            }
        }
    }

    private fun stageActions(onSkip: () -> Unit): GuideActions = GuideActions(onSkip = onSkip, onFinish = onSkip)

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

/** The guide inside the test's text and theme providers, so a stage can be swapped without re-setting content. */
@androidx.compose.runtime.Composable
private fun GuideUnderTest(state: GuideUiState, actions: GuideActions) {
    val sample = state.sample
    val document = sample?.let { chosen -> guideDocument(chosen, state.band, state.plain, WhitespaceTextSegmenter()) }
    val notes = sample?.let { chosen ->
        guideNotes(chosen, state.band, WhitespaceTextSegmenter(), "fr")
    }.orEmpty()
    GuideScreen(
        state = state,
        languages = sampleRosterLanguages(),
        document = document,
        notes = notes,
        translationLanguage = "fr",
        actions = actions,
    )
}
