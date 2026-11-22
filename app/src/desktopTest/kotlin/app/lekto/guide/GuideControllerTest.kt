package app.lekto.guide

import app.lekto.LocalChanges
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.text.WordToken
import app.lekto.reader.MasteryController
import app.lekto.reader.WordTap
import app.lekto.testkit.InMemoryReaderLevels
import app.lekto.testkit.WhitespaceTextSegmenter
import app.lekto.testkit.guideSample
import app.lekto.testkit.inMemoryVocabulary
import app.lekto.testkit.testOfferedLanguage
import app.lekto.vocabulary.VocabularyController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The guide's flow (issue #277): the language answer materialises the sample and
 * starts the download; the page's two beats advance; the tap opens the real sheet
 * and a level saves the word; the band writes the **Reader level** through the
 * Languages surface's own action. A sheet closed without choosing writes nothing.
 */
@Suppress("TooManyFunctions") // One behaviour per flow step; splitting the flow would hide it.
class GuideControllerTest {

    private val vocabulary = inMemoryVocabulary()
    private val levels = InMemoryReaderLevels()
    private val mastery = MasteryController(
        VocabularyController(vocabulary, LocalChanges()),
        levels,
        { null },
        LocalChanges(),
    )
    private val materialised = mutableListOf<String>()
    private val downloads = mutableListOf<String>()
    private val languages = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))

    private fun controller(
        languages: List<app.lekto.core.language.OfferedLanguage> = this.languages,
    ): GuideController = GuideController(
        languages = languages,
        sampleOf = { language -> guideSample(language).takeIf { it.language == language } },
        materialise = { language -> materialised += language },
        startDownload = { language -> downloads += language },
        mastery = mastery,
        segmenter = WhitespaceTextSegmenter(),
        translationLanguage = { "fr" },
    )

    private fun GuideController.answer(language: String) {
        selectLanguage(language)
        advance()
    }

    /** A tap on [surface] in the sample's first paragraph, keyed as the reader's tokeniser keys it. */
    private fun tap(surface: String): WordTap {
        val paragraph = guideSample().paragraphs.first()
        val start = paragraph.indexOf(surface)
        return WordTap(
            token = WordToken(surface, start, start + surface.length, WordKey("en", surface)),
            contextSentence = paragraph,
        )
    }

    @Test
    fun twoLanguagesOpenOnTheQuestion() {
        val guide = controller()

        assertEquals(GuideStage.LANGUAGE, guide.state.stage)
        assertNull(guide.state.sample)
        assertNull(guide.state.selectedLanguage, "the question has no default answer")
    }

    @Test
    fun selectingALanguageChoosesItWithoutAdvancing() {
        val guide = controller()

        guide.selectLanguage("en")

        assertEquals(GuideStage.LANGUAGE, guide.state.stage)
        assertEquals("en", guide.state.selectedLanguage)
        assertNull(guide.state.sample, "the page waits for Continue")
        assertTrue(materialised.isEmpty(), "a selection writes no sample")
        assertEquals(listOf("en"), downloads, "the selection starts the background download")
    }

    @Test
    fun continuingFromTheQuestionMaterialisesTheChosenLanguage() {
        val guide = controller()
        guide.selectLanguage("en")

        guide.advance()

        assertEquals(listOf("en"), materialised)
        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("en", guide.state.sample?.language)
        assertTrue(guide.state.plain, "the first beat has no colour")
    }

    @Test
    fun continuingWithoutAChoiceStaysOnTheQuestion() {
        val guide = controller()

        guide.advance()

        assertEquals(GuideStage.LANGUAGE, guide.state.stage)
        assertTrue(materialised.isEmpty())
    }

    @Test
    fun theLanguageAnswerMaterialisesTheSampleAndOpensThePage() {
        val guide = controller()

        guide.answer("en")

        assertEquals(listOf("en"), materialised)
        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("en", guide.state.sample?.language)
        assertTrue(guide.state.plain, "the first beat has no colour")
    }

    @Test
    fun thePageAdvanceRevealsTheColoursThenTheTap() {
        val guide = controller()
        guide.answer("en")

        guide.advance()
        assertFalse(guide.state.plain, "the second beat is the same paragraph coloured")

        guide.advance()
        assertEquals(GuideStage.TAP, guide.state.stage)
        assertFalse(guide.state.lookupOpen, "the sheet opens on the reader's tap, not on arrival")
        assertNull(guide.state.tapped)
    }

    @Test
    fun aTapOpensTheSheetForThatWord() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()

        guide.tapWord(tap("calm"))

        assertTrue(guide.state.lookupOpen)
        assertEquals("calm", guide.state.tapped?.surface)
    }

    @Test
    fun aTapOnAWordWithNoMeaningOpensNothing() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()

        guide.tapWord(tap("his"))

        assertFalse(guide.state.lookupOpen, "a word the glossary does not answer is not a door")
        assertNull(guide.state.tapped)
    }

    @Test
    fun savingTheTappedWordRecoloursThatWord() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()
        guide.tapWord(tap("calm"))

        guide.save(MasteryLevel.LEARNED)

        assertEquals(app.lekto.core.text.WordKey("en", "calm"), guide.state.savedKey)
        assertEquals(MasteryLevel.LEARNED, guide.state.savedLevel)
        assertEquals(
            MasteryLevel.LEARNED,
            vocabulary.entryFor(app.lekto.core.text.WordKey("en", "calm"))?.mastery,
        )
    }

    @Test
    fun aLevelSavesTheWordAndStatesIt() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()

        guide.save(MasteryLevel.FAMILIAR)

        assertEquals(MasteryLevel.FAMILIAR, guide.state.savedLevel)
        assertFalse(guide.state.saveFailed)
        assertEquals(
            MasteryLevel.FAMILIAR,
            vocabulary.entryFor(app.lekto.core.text.WordKey("en", "fisherman"))?.mastery,
        )
    }

    @Test
    fun theTapStatesAWordThatWasAlreadySaved() {
        val key = app.lekto.core.text.WordKey("en", "fisherman")
        vocabulary.save(
            app.lekto.core.vocabulary.VocabularyEntry(
                key = key,
                surface = "fisherman",
                translation = "pêcheur",
                contextSentence = null,
                mastery = MasteryLevel.LEARNED,
            ),
        )
        mastery.refresh()
        val guide = controller()
        guide.answer("en")

        guide.advance()
        guide.advance()

        assertEquals(MasteryLevel.LEARNED, guide.state.savedLevel)
        assertEquals(key, guide.state.savedKey, "the sheet states the word it is showing")
    }

    @Test
    fun closingTheSheetWithoutChoosingWritesNothing() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()
        guide.tapWord(tap("calm"))

        guide.dismissLookup()

        assertFalse(guide.state.lookupOpen)
        assertNull(guide.state.savedLevel)
        assertTrue(vocabulary.all().isEmpty(), "dismissing the sheet is not a save")
    }

    @Test
    fun theBandWritesThroughTheLanguagesAction() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()
        guide.dismissLookup()
        guide.advance()

        assertEquals(GuideStage.LEVEL, guide.state.stage)
        guide.chooseBand(CefrLevel.C1)

        assertEquals(CefrLevel.C1, guide.state.band)
        assertEquals(CefrLevel.C1, levels.levelFor("en"), "the level is the stored fact")
    }

    @Test
    fun theBandIsASuggestionUntilOneIsTapped() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()
        guide.dismissLookup()
        guide.advance()

        assertEquals(GuideStage.LEVEL, guide.state.stage)
        assertEquals(GUIDE_DEFAULT_BAND, guide.state.band, "the page still colours at the default band")
        assertNull(guide.state.chosenBand, "nothing is chosen before a band is tapped")

        guide.chooseBand(CefrLevel.C1)

        assertEquals(CefrLevel.C1, guide.state.chosenBand)
    }

    @Test
    fun theLeftArrowWalksBackToTheQuestion() {
        val guide = controller()
        guide.answer("en")
        guide.advance()
        guide.advance()

        guide.back()
        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertFalse(guide.state.plain)

        guide.back()
        assertTrue(guide.state.plain)

        guide.back()
        assertEquals(GuideStage.LANGUAGE, guide.state.stage)
    }

    @Test
    fun aRelaunchMidGuideStartsOverOnTheQuestion() {
        val guide = controller()
        guide.answer("en")
        guide.advance()

        val relaunch = controller()

        assertEquals(GuideStage.LANGUAGE, relaunch.state.stage, "the document records completion only")
        assertNull(relaunch.state.selectedLanguage, "a relaunch asks the question again")
    }

    @Test
    fun aReplayAsksTheLanguageQuestionAgain() {
        val guide = controller()
        guide.answer("en")

        guide.restart()

        assertEquals(GuideStage.LANGUAGE, guide.state.stage)
        assertNull(guide.state.selectedLanguage)
        assertNull(guide.state.sample)
    }

    @Test
    fun theReplayAnswerGoesThroughTheMaterialisingSeamAgain() {
        val guide = controller()
        guide.answer("en")

        guide.restart()
        guide.answer("fr")

        assertEquals(
            listOf("en", "fr"),
            materialised,
            "the answer re-runs the materialisation that replaces the sample",
        )
    }

    @Test
    fun oneLanguageSkipsTheQuestionAndNamesItsPage() {
        val guide = controller(listOf(testOfferedLanguage("fr")))

        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("fr", guide.state.sample?.language)
        assertEquals(listOf("fr"), materialised, "a single-language build materialises its sample at once")
    }
}
