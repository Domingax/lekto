package app.lekto.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
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
import app.lekto.dictionary.LOOKUP_INSTALL_TAG
import app.lekto.dictionary.LOOKUP_PANEL_TAG
import app.lekto.dictionary.masteryTag
import app.lekto.library.LibraryScreen
import app.lekto.reader.MasteryPalette
import app.lekto.reader.ReaderPage
import app.lekto.reader.ReaderStyles
import app.lekto.reader.buildChapterText
import app.lekto.reader.buildPageTokens
import app.lekto.reader.ruleUnder
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
// One behaviour per screen; the render inputs are independent.
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
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
        // No row carries a selection and no Continue is offered: the question has no default answer.
        SampleRoster.LANGUAGES.forEach { language ->
            onNodeWithTag(guideLanguageRowTag(language)).performScrollTo().assertIsNotSelected()
        }
        onNodeWithTag(GUIDE_CONTINUE_TAG).assertDoesNotExist()
    }

    @Test
    fun selectingALanguageRaisesIt() = runComposeUiTest {
        var chosen: String? = null
        render(
            GuideUiState(stage = GuideStage.LANGUAGE),
            sample = null,
            actions = GuideActions(onSelectLanguage = { language -> chosen = language }),
        )

        onNodeWithTag(guideLanguageRowTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theSelectedLanguageCarriesTheSelectedTreatmentAndRevealsContinue() = runComposeUiTest {
        var continued = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "fr"),
            sample = null,
            actions = GuideActions(onContinue = { continued++ }),
        )

        onNodeWithTag(guideLanguageRowTag("fr")).assertIsSelected()
        SampleRoster.LANGUAGES.filter { language -> language != "fr" }.forEach { language ->
            onNodeWithTag(guideLanguageRowTag(language)).performScrollTo().assertIsNotSelected()
        }

        onNodeWithTag(GUIDE_CONTINUE_TAG).performClick()

        assertEquals(1, continued)
    }

    @Test
    fun everyScreenAfterTheFirstCarriesTheBackAffordance() = runComposeUiTest {
        var backed = 0
        val stages = listOf(
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme { GuideUnderTest(current.value, GuideActions(onBack = { backed++ })) }
            }
        }

        stages.forEach { stage ->
            current.value = stage
            waitForIdle()
            onNodeWithTag(GUIDE_BACK_TAG).performClick()
        }

        assertEquals(stages.size, backed)
    }

    @Test
    fun theFirstScreenCarriesNoBackAffordance() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.LANGUAGE), sample = null)

        onNodeWithTag(GUIDE_BACK_TAG).assertDoesNotExist()
    }

    @Test
    fun theBackChevronHeadsTheBarAfterTheFirstScreen() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true))

        val back = onNodeWithTag(GUIDE_BACK_TAG).fetchSemanticsNode().boundsInRoot
        val steps = onNodeWithTag(GUIDE_STEPS_TAG).fetchSemanticsNode().boundsInRoot
        val skip = onNodeWithTag(GUIDE_SKIP_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(back.right <= steps.left, "the chevron heads the row: $back / $steps")
        assertTrue(skip.left >= steps.right, "Skip keeps the far right: $steps / $skip")
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
    fun theColouredBeatKeepsItsHeadingAndDropsTheSecondLine() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false))

        onNodeWithText("The more you know, the less colour.").assertIsDisplayed()
        onNodeWithText("Four words on this page, four levels.").assertDoesNotExist()
    }

    @Test
    fun theTapScreenArrivesWithoutTheSheetOpen() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false))

        onNodeWithTag(LOOKUP_PANEL_TAG).assertDoesNotExist()
        onNodeWithTag(GUIDE_CONTINUE_TAG).assertIsDisplayed()
    }

    @Test
    fun theSheetHidesTheWayForwardAndClosingBringsItBack() = runComposeUiTest {
        val lookupOpen = mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme {
                    GuideScreen(
                        state = GuideUiState(
                            stage = GuideStage.TAP,
                            sample = sample,
                            plain = false,
                            lookupOpen = lookupOpen.value,
                            tapped = note("calm"),
                        ),
                        languages = sampleRosterLanguages(),
                        document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, segmenter),
                        notes = guideNotes(sample, GUIDE_DEFAULT_BAND, segmenter, "fr"),
                        translationLanguage = "fr",
                    )
                }
            }
        }

        // The sheet is the interaction: while it is open the guide offers nothing under it.
        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        onNodeWithTag(GUIDE_CONTINUE_TAG).assertDoesNotExist()

        lookupOpen.value = false
        waitForIdle()

        onNodeWithTag(LOOKUP_PANEL_TAG).assertDoesNotExist()
        onNodeWithTag(GUIDE_CONTINUE_TAG).assertIsDisplayed()
    }

    @Test
    fun aTapOnAWordAsksForThatWord() = runComposeUiTest {
        var tapped: app.lekto.reader.WordTap? = null
        render(
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            actions = GuideActions(onTapWord = { tap -> tapped = tap }),
        )

        onNodeWithTag(GUIDE_PAGE_TAG).performTouchInput { click(guideWordTapPoint("calm")) }

        assertEquals("calm", tapped?.token?.surface, "the embedded page hit-tests the reader's own word layer")
    }

    @Test
    fun theTapOpensTheRealSheetForTheTappedWord() = runComposeUiTest {
        render(
            GuideUiState(
                stage = GuideStage.TAP,
                sample = sample,
                plain = false,
                lookupOpen = true,
                tapped = note("calm"),
            ),
        )

        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        onNodeWithText("calm").assertIsDisplayed()
        onNodeWithText("→ calme", substring = true).assertExists()
        onNodeWithText("Not saved").assertExists()
    }

    @Test
    fun theTapSheetOffersNoInstallAction() = runComposeUiTest {
        // The guide answers from the sample's glossary, never from the offline
        // pack, so the offline door has nothing to open here (issue #282's seam).
        render(
            GuideUiState(
                stage = GuideStage.TAP,
                sample = sample,
                plain = false,
                lookupOpen = true,
                tapped = note("calm"),
            ),
        )

        onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        onNodeWithTag(LOOKUP_INSTALL_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingALevelSavesTheWordAndStatesIt() = runComposeUiTest {
        val saved = mutableStateOf<MasteryLevel?>(null)
        render(
            GuideUiState(
                stage = GuideStage.TAP,
                sample = sample,
                plain = false,
                lookupOpen = true,
                tapped = note("calm"),
                savedKey = note("calm").key,
            ),
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
            GuideUiState(
                stage = GuideStage.TAP,
                sample = sample,
                plain = false,
                lookupOpen = true,
                tapped = note("calm"),
            ),
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
    fun thePageScreensHostTheEmbeddedBlockAndNoReaderChrome() = runComposeUiTest {
        val stages = listOf(
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme { GuideUnderTest(current.value, GuideActions()) }
            }
        }

        stages.forEach { stage ->
            current.value = stage
            waitForIdle()
            onNodeWithTag(GUIDE_PAGE_TAG).assertIsDisplayed()
            onNodeWithTag(app.lekto.reader.READER_PAGE_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun aHighlightedWordsRuleStartsAtItsWordsMeasuredLeftEdge() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false))

        val document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, segmenter)
        val text = onNodeWithTag(GUIDE_PAGE_TEXT_TAG).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.first()
        val styles = ReaderStyles.of(document.preferences, MasteryPalette.Light)
        val chapter = buildChapterText(document.text, styles)
        val tokens = buildPageTokens(
            chapterText = chapter,
            page = ReaderPage(0, chapter.length),
            renderer = document.renderer,
            chapter = document.text,
            styles = styles,
        )
        val highlighted = tokens.words.filter { word ->
            document.renderer.mastery.levelOf(word.key).highlighted
        }

        assertTrue(highlighted.isNotEmpty(), "the demonstrated paragraph colours at least one word")
        highlighted.forEach { word ->
            val rule = ruleUnder(layout, word).first()
            assertEquals(
                layout.getBoundingBox(word.start).left,
                rule.left,
                0.01f,
                "the rule under ${word.surface} starts at the word's measured left edge",
            )
        }
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
    fun theLevelPreviewCarriesNoLabel() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false))

        onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        onNodeWithText("Preview · the sample page").assertDoesNotExist()
    }

    @Test
    fun a1IsTheStandingChoiceFromEntry() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false))

        onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        onNodeWithTag(guideBandTag(CefrLevel.A1)).assertIsSelected()
        CefrLevel.entries.filter { level -> level != CefrLevel.A1 }.forEach { level ->
            onNodeWithTag(guideBandTag(level)).assertIsNotSelected()
        }
    }

    @Test
    fun aTappedBandIsTheOnlyOneChosen() = runComposeUiTest {
        render(
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false, chosenBand = CefrLevel.C1),
        )

        onNodeWithTag(guideBandTag(CefrLevel.C1)).assertIsSelected()
        CefrLevel.entries.filter { level -> level != CefrLevel.C1 }.forEach { level ->
            onNodeWithTag(guideBandTag(level)).assertIsNotSelected()
        }
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
    fun oneLanguageOpensOnThePageWithoutTheQuestionOrTheLabel() = runComposeUiTest {
        render(
            GuideUiState(stage = GuideStage.PAGE, sample = sample),
            languages = listOf(app.lekto.testkit.testOfferedLanguage("en")),
        )

        onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG).assertDoesNotExist()
        onNodeWithText("Reading in English").assertDoesNotExist()
    }

    @Test
    fun theTapScreenNamesNoReadingLanguage() = runComposeUiTest {
        render(GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false))

        // The owner's third review removed the label from the last screen that
        // carried it: the tapped word is the subject, and no beat names the language.
        onNodeWithText("Reading in English").assertDoesNotExist()
        onNodeWithText("Lecture en anglais").assertDoesNotExist()
    }

    @Test
    fun theDownloadCardSitsOnTheFirstScreenAndOffersStop() = runComposeUiTest {
        val artifacts = listOf(
            testOfferedArtifact("lekto-difficulty-en.sqlite.gz", sizeBytes = 12_000_000),
            testOfferedArtifact("lekto-dictionary-en-fr.sqlite.gz", sizeBytes = 24_000_000),
        )
        val download = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.DOWNLOADING, "en", artifacts))
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
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
        onNodeWithTag(GUIDE_DOWNLOAD_RESUME_TAG).assertIsDisplayed()
    }

    @Test
    fun theMiddleScreensShowNoDownloadCard() = runComposeUiTest {
        val download = GuideDownloadUiState(GuideDownloadPhase.DOWNLOADING, "en")
        val stages = listOf(
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme {
                    GuideScreen(
                        state = current.value,
                        languages = sampleRosterLanguages(),
                        document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, segmenter),
                        notes = guideNotes(sample, GUIDE_DEFAULT_BAND, segmenter, "fr"),
                        download = download,
                        translationLanguage = "fr",
                    )
                }
            }
        }

        stages.forEach { stage ->
            current.value = stage
            waitForIdle()
            onNodeWithTag(GUIDE_DOWNLOAD_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun aFailedDownloadSaysSoAndOffersRetry() = runComposeUiTest {
        var retried = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.FAILED, "en")),
            actions = GuideActions(onRetryDownload = { retried++ }),
        )

        onNodeWithText("The download did not finish. Check your connection.").assertIsDisplayed()
        val message = onNodeWithText("The download did not finish. Check your connection.")
            .fetchSemanticsNode().boundsInRoot
        val retry = onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(retry.top >= message.bottom, "Retry stands below the message: $message / $retry")
        onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).performClick()

        assertEquals(1, retried)
    }

    @Test
    fun anUnreachableManifestSaysSoAndOffersRetry() = runComposeUiTest {
        var retried = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.UNAVAILABLE, "en")),
            actions = GuideActions(onRetryDownload = { retried++ }),
        )

        onNodeWithText("The language data could not be reached. Check your connection.").assertIsDisplayed()
        onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).performClick()

        assertEquals(1, retried)
    }

    @Test
    fun aRefusedManifestSaysTheBuildCannotReadItAndOffersNoRetry() = runComposeUiTest {
        var retried = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.REFUSED, "en")),
            actions = GuideActions(onRetryDownload = { retried++ }),
        )

        onNodeWithText("This build does not understand the published language data. Update the app.")
            .assertIsDisplayed()
        onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).assertDoesNotExist()
    }

    @Test
    fun aLanguageTheManifestDoesNotPublishSaysSoAndOffersNoRetry() = runComposeUiTest {
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.LANGUAGE_NOT_OFFERED, "en")),
            actions = GuideActions(onRetryDownload = {}),
        )

        onNodeWithText("This language is not published yet.").assertIsDisplayed()
        onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).assertDoesNotExist()
    }

    @Test
    fun aStoppedDownloadOffersToResume() = runComposeUiTest {
        var resumed = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.STOPPED, "en")),
            actions = GuideActions(onResumeDownload = { resumed++ }),
        )

        onNodeWithText("Stopped").assertIsDisplayed()
        val message = onNodeWithText("Stopped").fetchSemanticsNode().boundsInRoot
        val resume = onNodeWithTag(GUIDE_DOWNLOAD_RESUME_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(resume.top >= message.bottom, "Resume stands below the message: $message / $resume")
        onNodeWithTag(GUIDE_DOWNLOAD_RESUME_TAG).performClick()

        assertEquals(1, resumed)
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
                            GuideDownloadActions(onStop = {}),
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

    /** The note the covered word would produce, for the sheet and its save. */
    private fun note(surface: String): GuideNote = guideNotes(sample, GUIDE_DEFAULT_BAND, segmenter, "fr")
        .first { note -> note.surface == surface }

    /** The point at the centre of [word] on the embedded page, in the page block's own coordinates. */
    private fun ComposeUiTest.guideWordTapPoint(word: String): Offset {
        val page = onNodeWithTag(GUIDE_PAGE_TAG).fetchSemanticsNode().boundsInRoot
        val text = onNodeWithTag(GUIDE_PAGE_TEXT_TAG).fetchSemanticsNode()
        val shown = text.config[SemanticsProperties.Text].joinToString("") { line -> line.text }
        val results = mutableListOf<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.first()
        val at = shown.indexOf(word).takeIf { index -> index >= 0 }
            ?: error("the page does not show $word: $shown")
        val box = layout.getBoundingBox(at)
        val origin = text.boundsInRoot.topLeft
        return Offset(origin.x - page.left + box.left + box.width / 2f, origin.y - page.top + box.top + box.height / 2f)
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
