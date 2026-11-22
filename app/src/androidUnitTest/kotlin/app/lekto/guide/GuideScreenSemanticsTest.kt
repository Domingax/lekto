package app.lekto.guide

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import app.lekto.testkit.testOfferedLanguage
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.text.appTextsFor
import app.lekto.theme.LektoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-launch guide on a **simulated Android runtime** (issue #277): the
 * same-named twin of `app/desktopTest`'s `GuideScreenSemanticsTest`, so the parity
 * rule (issue #73) sees the two lanes together. The five screens' contracts — the
 * language question, the page's two beats and four notes, the real sheet, the
 * level that saves, the six bands, `Skip`, and the arrival — are the same
 * behaviours on the runtime the app ships on, at a 360 dp phone width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp") // `android-compileSdk`; Robolectric 4.16 supports API 36.
@Suppress("TooManyFunctions", "LongParameterList") // One behaviour per screen; the render inputs are independent.
class GuideScreenSemanticsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val segmenter = WhitespaceTextSegmenter()
    private val sample = guideSample()
    private val texts = appTextsFor("en")

    @Test
    fun theQuestionListsEveryRosterLanguageWithNoPreselection() {
        render(GuideUiState(stage = GuideStage.LANGUAGE), sample = null)

        compose.onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG).assertIsDisplayed()
        compose.onNodeWithText("Which language would you like to read first?").assertIsDisplayed()
        SampleRoster.LANGUAGES.forEach { language ->
            compose.onNodeWithTag(guideLanguageRowTag(language)).performScrollTo().assertIsDisplayed()
        }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)).assertCountEquals(0)
    }

    @Test
    fun choosingALanguageRaisesIt() {
        var chosen: String? = null
        render(
            GuideUiState(stage = GuideStage.LANGUAGE),
            sample = null,
            actions = GuideActions(onChooseLanguage = { language -> chosen = language }),
        )

        compose.onNodeWithTag(guideLanguageRowTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theFirstBeatShowsTheParagraphWithNoColourAndNoNotes() {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true))

        compose.onNodeWithText("Here is a page of your book.").assertIsDisplayed()
        compose.onNodeWithText("Nothing is coloured yet. Look.").assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_NOTES_TAG).assertDoesNotExist()
        compose.onNodeWithText("fisherman", substring = true).assertIsDisplayed()
    }

    @Test
    fun theSecondBeatNamesFourNotesWithTheirLevelAndMeaning() {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false))

        compose.onNodeWithText("The more you know, the less colour.").assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_NOTES_TAG).assertIsDisplayed()
        listOf(
            MasteryLevel.UNKNOWN to "Unknown",
            MasteryLevel.RECOGNIZED to "Recognized",
            MasteryLevel.FAMILIAR to "Familiar",
            MasteryLevel.LEARNED to "Learned",
        ).forEach { (level, name) ->
            compose.onNodeWithTag(guideNoteTag(level)).assertExists()
            compose.onNodeWithText(name.uppercase()).assertExists()
        }
        compose.onNodeWithText("fisherman").assertExists()
        compose.onNodeWithText("pêcheur").assertExists()
        compose.onNodeWithText("calme").assertExists()
    }

    @Test
    fun theTapOpensTheRealSheetAnsweredFromTheSample() {
        render(GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false, lookupOpen = true))

        compose.onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithText("fisherman").assertIsDisplayed()
        compose.onNodeWithText("→ pêcheur", substring = true).assertExists()
        compose.onAllNodesWithText("The old fisherman rowed his small boat across the calm water.")
            .assertCountEquals(2)
        compose.onNodeWithText("Not saved").assertExists()
    }

    @Test
    fun tappingALevelSavesTheWordAndStatesIt() {
        val saved = mutableStateOf<MasteryLevel?>(null)
        render(
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false, lookupOpen = true),
            actions = GuideActions(onSaveWord = { level -> saved.value = level }),
            savedLevel = saved,
        )

        compose.onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        compose.onNodeWithText("Saved · Familiar").assertExists()
    }

    @Test
    fun closingTheSheetWithoutChoosingWritesNothing() {
        var saved: MasteryLevel? = null
        var closed = false
        render(
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false, lookupOpen = true),
            actions = GuideActions(
                onSaveWord = { level -> saved = level },
                onDismissLookup = { closed = true },
            ),
        )

        compose.onNodeWithTag(LOOKUP_CLOSE_TAG).performClick()

        assertTrue("the sheet closes", closed)
        assertNull("closing is not a save", saved)
    }

    @Test
    fun theLevelScreenCarriesSixBandsAndTheirOneLine() {
        var chosen: CefrLevel? = null
        render(
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false, band = CefrLevel.B1),
            actions = GuideActions(onChooseBand = { level -> chosen = level }),
        )

        compose.onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        CefrLevel.entries.forEach { level ->
            compose.onNodeWithTag(guideBandTag(level)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(texts.bandName(level)).assertExists()
            compose.onNodeWithText(texts.bandLine(level)).assertExists()
        }
        compose.onNodeWithText("What is your level in English?").assertIsDisplayed()

        compose.onNodeWithTag(guideBandTag(CefrLevel.C1)).performScrollTo().performClick()

        assertEquals(CefrLevel.C1, chosen)
    }

    @Test
    fun theDefaultBandIsASuggestionAndNoBandIsChosen() {
        render(GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false))

        compose.onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        CefrLevel.entries.forEach { level -> compose.onNodeWithTag(guideBandTag(level)).assertIsNotSelected() }
    }

    @Test
    fun aTappedBandIsTheOnlyOneChosen() {
        render(
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false, chosenBand = CefrLevel.C1),
        )

        compose.onNodeWithTag(guideBandTag(CefrLevel.C1)).assertIsSelected()
        CefrLevel.entries.filter { level -> level != CefrLevel.C1 }.forEach { level ->
            compose.onNodeWithTag(guideBandTag(level)).assertIsNotSelected()
        }
    }

    @Test
    fun skipSitsOnEveryScreen() {
        var skipped = 0
        val stages = listOf(
            GuideUiState(stage = GuideStage.LANGUAGE),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        compose.setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme { GuideUnderTest(current.value, GuideActions(onSkip = { skipped++ })) }
            }
        }

        stages.forEach { stage ->
            current.value = stage
            compose.waitForIdle()
            compose.onNodeWithTag(GUIDE_SKIP_TAG).performClick()
        }

        assertEquals(stages.size, skipped)
    }

    @Test
    fun oneLanguageOpensOnThePageNamingIt() {
        render(
            GuideUiState(stage = GuideStage.PAGE, sample = sample),
            languages = listOf(testOfferedLanguage("en")),
        )

        compose.onNodeWithText("Reading in English").assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG).assertDoesNotExist()
    }

    @Test
    fun theDownloadLineAnnouncesItsSizeAndFollowsTheStop() {
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

        compose.onNodeWithText("Table and dictionary · English").assertIsDisplayed()
        compose.onNodeWithText("36 MB in the background").assertIsDisplayed()

        compose.onNodeWithTag(GUIDE_DOWNLOAD_STOP_TAG).performClick()

        compose.onNodeWithText("Stopped").assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_DOWNLOAD_STOP_TAG).assertDoesNotExist()
    }

    @Test
    fun theArrivalIsTheShelfWithTheSampleAndTheDownloadLine() {
        compose.setContent {
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

        compose.onNodeWithText("A Guide Sample").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your reading level is not set yet. Settings ▸ Languages.").assertIsDisplayed()
        compose.onNodeWithText("Installed").assertIsDisplayed()
    }

    /** Renders the guide over [state], with the page and notes its sample produces. */
    private fun render(
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
        compose.setContent {
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
