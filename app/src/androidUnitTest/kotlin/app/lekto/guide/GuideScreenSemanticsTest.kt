package app.lekto.guide

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
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
 * language question and its select-then-Continue, the page's two beats and four
 * notes, the tap-opened sheet, the level that saves and recolours, the six bands,
 * the back affordance, `Skip`, and the arrival — are the same behaviours on the
 * runtime the app ships on, at a 360 dp phone width.
 */
// One behaviour per screen; the render inputs are independent.
@RunWith(RobolectricTestRunner::class)
// `android-compileSdk`; Robolectric 4.16 supports API 36.
@Config(sdk = [36], qualifiers = "w360dp-h800dp")
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
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
            compose.onNodeWithTag(guideLanguageRowTag(language)).assertIsNotSelected()
        }
        compose.onNodeWithTag(GUIDE_CONTINUE_TAG).assertDoesNotExist()
    }

    @Test
    fun selectingALanguageRaisesIt() {
        var chosen: String? = null
        render(
            GuideUiState(stage = GuideStage.LANGUAGE),
            sample = null,
            actions = GuideActions(onSelectLanguage = { language -> chosen = language }),
        )

        compose.onNodeWithTag(guideLanguageRowTag("fr")).performClick()

        assertEquals("fr", chosen)
    }

    @Test
    fun theSelectedLanguageCarriesTheSelectedTreatmentAndRevealsContinue() {
        var continued = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "fr"),
            sample = null,
            actions = GuideActions(onContinue = { continued++ }),
        )

        compose.onNodeWithTag(guideLanguageRowTag("fr")).assertIsSelected()
        SampleRoster.LANGUAGES.filter { language -> language != "fr" }.forEach { language ->
            compose.onNodeWithTag(guideLanguageRowTag(language)).performScrollTo().assertIsNotSelected()
        }

        compose.onNodeWithTag(GUIDE_CONTINUE_TAG).performClick()

        assertEquals(1, continued)
    }

    @Test
    fun everyScreenAfterTheFirstCarriesTheBackAffordance() {
        var backed = 0
        val stages = listOf(
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        compose.setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme { GuideUnderTest(current.value, GuideActions(onBack = { backed++ })) }
            }
        }

        stages.forEach { stage ->
            current.value = stage
            compose.waitForIdle()
            compose.onNodeWithTag(GUIDE_BACK_TAG).performClick()
        }

        assertEquals(stages.size, backed)
    }

    @Test
    fun theFirstScreenCarriesNoBackAffordance() {
        render(GuideUiState(stage = GuideStage.LANGUAGE), sample = null)

        compose.onNodeWithTag(GUIDE_BACK_TAG).assertDoesNotExist()
    }

    @Test
    fun theBackChevronHeadsTheBarAfterTheFirstScreen() {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true))

        val back = compose.onNodeWithTag(GUIDE_BACK_TAG).fetchSemanticsNode().boundsInRoot
        val steps = compose.onNodeWithTag(GUIDE_STEPS_TAG).fetchSemanticsNode().boundsInRoot
        val skip = compose.onNodeWithTag(GUIDE_SKIP_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue("the chevron heads the row: $back / $steps", back.right <= steps.left)
        assertTrue("Skip keeps the far right: $steps / $skip", skip.left >= steps.right)
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
    fun theColouredBeatKeepsItsHeadingAndDropsTheSecondLine() {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false))

        compose.onNodeWithText("The more you know, the less colour.").assertIsDisplayed()
        compose.onNodeWithText("Four words on this page, four levels.").assertDoesNotExist()
    }

    @Test
    fun theTapScreenArrivesWithoutTheSheetOpen() {
        render(GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false))

        compose.onNodeWithTag(LOOKUP_PANEL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(GUIDE_CONTINUE_TAG).assertIsDisplayed()
    }

    @Test
    fun theSheetHidesTheWayForwardAndClosingBringsItBack() {
        val lookupOpen = mutableStateOf(true)
        compose.setContent {
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
        compose.onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_CONTINUE_TAG).assertDoesNotExist()

        lookupOpen.value = false
        compose.waitForIdle()

        compose.onNodeWithTag(LOOKUP_PANEL_TAG).assertDoesNotExist()
        compose.onNodeWithTag(GUIDE_CONTINUE_TAG).assertIsDisplayed()
    }

    @Test
    fun aTapOnAWordAsksForThatWord() {
        var tapped: app.lekto.reader.WordTap? = null
        render(
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            actions = GuideActions(onTapWord = { tap -> tapped = tap }),
        )

        // The page hit-tests the tap against the reader's own word layer; a few
        // points along the line are tried because a paragraph has spaces between
        // its words, and a tap on a space belongs to no word (issue #277).
        tapGuidePageUntilTapped { tapped != null }
        val word = tapped ?: error("no word opened from a tap on the embedded page")
        assertTrue(
            "the tapped word comes from the sample: ${word.token.surface}",
            sample.paragraphs.first().contains(word.token.surface),
        )
    }

    @Test
    fun theTapOpensTheRealSheetForTheTappedWord() {
        render(
            GuideUiState(
                stage = GuideStage.TAP,
                sample = sample,
                plain = false,
                lookupOpen = true,
                tapped = note("calm"),
            ),
        )

        compose.onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithText("calm").assertIsDisplayed()
        compose.onNodeWithText("→ calme", substring = true).assertExists()
        compose.onNodeWithText("Not saved").assertExists()
    }

    @Test
    fun theTapSheetOffersNoInstallAction() {
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

        compose.onNodeWithTag(LOOKUP_PANEL_TAG).assertIsDisplayed()
        compose.onNodeWithTag(LOOKUP_INSTALL_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingALevelSavesTheWordAndStatesIt() {
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

        compose.onNodeWithTag(masteryTag(MasteryLevel.FAMILIAR)).performClick()

        compose.onNodeWithText("Saved · Familiar").assertExists()
    }

    @Test
    fun closingTheSheetWithoutChoosingWritesNothing() {
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

        compose.onNodeWithTag(LOOKUP_CLOSE_TAG).performClick()

        assertTrue("the sheet closes", closed)
        assertNull("closing is not a save", saved)
    }

    @Test
    fun thePageScreensHostTheEmbeddedBlockAndNoReaderChrome() {
        val stages = listOf(
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        compose.setContent {
            CompositionLocalProvider(LocalAppTexts provides texts) {
                LektoTheme { GuideUnderTest(current.value, GuideActions()) }
            }
        }

        stages.forEach { stage ->
            current.value = stage
            compose.waitForIdle()
            compose.onNodeWithTag(GUIDE_PAGE_TAG).assertIsDisplayed()
            compose.onNodeWithTag(app.lekto.reader.READER_PAGE_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun aHighlightedWordsRuleStartsAtItsWordsMeasuredLeftEdge() {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false))

        val document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, segmenter)
        val text = compose.onNodeWithTag(GUIDE_PAGE_TEXT_TAG).fetchSemanticsNode()
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

        assertTrue("the demonstrated paragraph colours at least one word", highlighted.isNotEmpty())
        highlighted.forEach { word ->
            val rule = ruleUnder(layout, word).first()
            assertEquals(
                "the rule under ${word.surface} starts at the word's measured left edge",
                layout.getBoundingBox(word.start).left,
                rule.left,
                0.01f,
            )
        }
    }

    @Test
    fun aHighlightedWordsRuleStartsAtItsWordsMeasuredLeftEdge() {
        render(GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false))

        val document = guideDocument(sample, GUIDE_DEFAULT_BAND, plain = false, segmenter)
        val text = compose.onNodeWithTag(GUIDE_PAGE_TEXT_TAG).fetchSemanticsNode()
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

        assertTrue("the demonstrated paragraph colours at least one word", highlighted.isNotEmpty())
        highlighted.forEach { word ->
            val rule = ruleUnder(layout, word).first()
            assertEquals(
                "the rule under ${word.surface} starts at the word's measured left edge",
                layout.getBoundingBox(word.start).left,
                rule.left,
                0.01f,
            )
        }
    }

    @Test
    fun theLevelScreenCarriesSixBandsAndTheirOneLine() {
        var chosen: CefrLevel? = null
        render(
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false, band = CefrLevel.B1),
            actions = GuideActions(onChooseBand = { level -> chosen = level }),
        )

        compose.onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithText("What is your level in English?").assertIsDisplayed()
        CefrLevel.entries.forEach { level ->
            compose.onNodeWithTag(guideBandTag(level)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(texts.bandName(level)).assertExists()
            compose.onNodeWithText(texts.bandLine(level)).assertExists()
        }

        compose.onNodeWithTag(guideBandTag(CefrLevel.C1)).performScrollTo().performClick()

        assertEquals(CefrLevel.C1, chosen)
    }

    @Test
    fun theLevelPreviewCarriesNoLabel() {
        render(GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false))

        compose.onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithText("Preview · the sample page").assertDoesNotExist()
    }

    @Test
    fun a1IsTheStandingChoiceFromEntry() {
        render(GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false))

        compose.onNodeWithTag(GUIDE_LEVEL_PREVIEW_TAG).assertIsDisplayed()
        compose.onNodeWithTag(guideBandTag(CefrLevel.A1)).assertIsSelected()
        CefrLevel.entries.filter { level -> level != CefrLevel.A1 }.forEach { level ->
            compose.onNodeWithTag(guideBandTag(level)).assertIsNotSelected()
        }
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
    fun oneLanguageOpensOnThePageWithoutTheQuestionOrTheLabel() {
        render(
            GuideUiState(stage = GuideStage.PAGE, sample = sample),
            languages = listOf(testOfferedLanguage("en")),
        )

        compose.onNodeWithTag(GUIDE_LANGUAGE_LIST_TAG).assertDoesNotExist()
        compose.onNodeWithText("Reading in English").assertDoesNotExist()
    }

    @Test
    fun theTapScreenNamesNoReadingLanguage() {
        render(GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false))

        // The mirror of the desktop lane: the last screen that named the reading
        // language no longer does.
        compose.onNodeWithText("Reading in English").assertDoesNotExist()
        compose.onNodeWithText("Lecture en anglais").assertDoesNotExist()
    }

    @Test
    fun theDownloadCardSitsOnTheFirstScreenAndOffersStop() {
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

        compose.onNodeWithText("Table and dictionary · English").assertIsDisplayed()
        compose.onNodeWithText("36 MB in the background").assertIsDisplayed()

        compose.onNodeWithTag(GUIDE_DOWNLOAD_STOP_TAG).performClick()

        compose.onNodeWithText("Stopped").assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_DOWNLOAD_STOP_TAG).assertDoesNotExist()
        compose.onNodeWithTag(GUIDE_DOWNLOAD_RESUME_TAG).assertIsDisplayed()
    }

    @Test
    fun theMiddleScreensShowNoDownloadCard() {
        val download = GuideDownloadUiState(GuideDownloadPhase.DOWNLOADING, "en")
        val stages = listOf(
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = true),
            GuideUiState(stage = GuideStage.PAGE, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.TAP, sample = sample, plain = false),
            GuideUiState(stage = GuideStage.LEVEL, sample = sample, plain = false),
        )
        val current = mutableStateOf(stages.first())
        compose.setContent {
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
            compose.waitForIdle()
            compose.onNodeWithTag(GUIDE_DOWNLOAD_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun aFailedDownloadSaysSoAndOffersRetry() {
        var retried = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.FAILED, "en")),
            actions = GuideActions(onRetryDownload = { retried++ }),
        )

        compose.onNodeWithText("The download did not finish. Check your connection.").assertIsDisplayed()
        val message = compose.onNodeWithText("The download did not finish. Check your connection.")
            .fetchSemanticsNode().boundsInRoot
        val retry = compose.onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("Retry stands below the message: $message / $retry", retry.top >= message.bottom)
        compose.onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).performClick()

        assertEquals(1, retried)
    }

    @Test
    fun anUnreachableManifestSaysSoAndOffersRetry() {
        var retried = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.UNAVAILABLE, "en")),
            actions = GuideActions(onRetryDownload = { retried++ }),
        )

        compose.onNodeWithText("The language data could not be reached. Check your connection.")
            .assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).performClick()

        assertEquals(1, retried)
    }

    @Test
    fun aRefusedManifestSaysTheBuildCannotReadItAndOffersNoRetry() {
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.REFUSED, "en")),
            actions = GuideActions(onRetryDownload = {}),
        )

        compose.onNodeWithText("This build does not understand the published language data. Update the app.")
            .assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).assertDoesNotExist()
    }

    @Test
    fun aLanguageTheManifestDoesNotPublishSaysSoAndOffersNoRetry() {
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.LANGUAGE_NOT_OFFERED, "en")),
            actions = GuideActions(onRetryDownload = {}),
        )

        compose.onNodeWithText("This language is not published yet.").assertIsDisplayed()
        compose.onNodeWithTag(GUIDE_DOWNLOAD_RETRY_TAG).assertDoesNotExist()
    }

    @Test
    fun aStoppedDownloadOffersToResume() {
        var resumed = 0
        render(
            GuideUiState(stage = GuideStage.LANGUAGE, selectedLanguage = "en"),
            downloadState = mutableStateOf(GuideDownloadUiState(GuideDownloadPhase.STOPPED, "en")),
            actions = GuideActions(onResumeDownload = { resumed++ }),
        )

        compose.onNodeWithText("Stopped").assertIsDisplayed()
        val message = compose.onNodeWithText("Stopped").fetchSemanticsNode().boundsInRoot
        val resume = compose.onNodeWithTag(GUIDE_DOWNLOAD_RESUME_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("Resume stands below the message: $message / $resume", resume.top >= message.bottom)
        compose.onNodeWithTag(GUIDE_DOWNLOAD_RESUME_TAG).performClick()

        assertEquals(1, resumed)
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
                            GuideDownloadActions(onStop = {}),
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

    /** The note the covered word would produce, for the sheet and its save. */
    private fun note(surface: String): GuideNote = guideNotes(sample, GUIDE_DEFAULT_BAND, segmenter, "fr")
        .first { note -> note.surface == surface }

    /**
     * Taps a few points along the embedded page's first line until [landed] says a
     * word opened. Robolectric's semantics layout and the rendered layout disagree
     * on glyph widths, so a probe is honest where a fixed coordinate is not.
     */
    private fun tapGuidePageUntilTapped(landed: () -> Boolean) {
        val page = compose.onNodeWithTag(GUIDE_PAGE_TAG).fetchSemanticsNode().boundsInRoot
        val text = compose.onNodeWithTag(GUIDE_PAGE_TEXT_TAG).fetchSemanticsNode()
        val shown = text.config[SemanticsProperties.Text].joinToString("") { line -> line.text }
        val results = mutableListOf<TextLayoutResult>()
        text.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.first()
        val origin = text.boundsInRoot.topLeft
        for (fraction in listOf(0.1f, 0.25f, 0.4f, 0.55f, 0.7f, 0.85f)) {
            val at = (shown.length * fraction).toInt().coerceIn(0, (shown.length - 1).coerceAtLeast(0))
            val box = layout.getBoundingBox(at)
            val point = Offset(
                origin.x - page.left + box.left + 1f,
                origin.y - page.top + box.top + box.height / 2f,
            )
            compose.onNodeWithTag(GUIDE_PAGE_TAG).performTouchInput { click(point) }
            compose.waitForIdle()
            if (landed()) return
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
