package app.lekto.guide

import app.lekto.LocalChanges
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.reader.MasteryController
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
    private val languages = listOf(testOfferedLanguage("en"), testOfferedLanguage("fr"))

    private fun controller(
        languages: List<app.lekto.core.language.OfferedLanguage> = this.languages,
        existingSample: () -> String? = { null },
        readingLanguage: () -> String? = { null },
    ): GuideController = GuideController(
        languages = languages,
        sampleOf = { language -> guideSample(language).takeIf { it.language == language } },
        materialise = { language -> materialised += language },
        download = null,
        mastery = mastery,
        segmenter = WhitespaceTextSegmenter(),
        translationLanguage = { "fr" },
        existingSample = existingSample,
        readingLanguage = readingLanguage,
    )

    @Test
    fun twoLanguagesOpenOnTheQuestion() {
        val guide = controller()

        assertEquals(GuideStage.LANGUAGE, guide.state.stage)
        assertNull(guide.state.sample)
    }

    @Test
    fun theLanguageAnswerMaterialisesTheSampleAndOpensThePage() {
        val guide = controller()

        guide.chooseLanguage("en")

        assertEquals(listOf("en"), materialised)
        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("en", guide.state.sample?.language)
        assertTrue(guide.state.plain, "the first beat has no colour")
    }

    @Test
    fun thePageAdvanceRevealsTheColoursThenTheTap() {
        val guide = controller()
        guide.chooseLanguage("en")

        guide.advance()
        assertFalse(guide.state.plain, "the second beat is the same paragraph coloured")

        guide.advance()
        assertEquals(GuideStage.TAP, guide.state.stage)
        assertTrue(guide.state.lookupOpen, "the tap screen opens the real sheet")
    }

    @Test
    fun aLevelSavesTheWordAndStatesIt() {
        val guide = controller()
        guide.chooseLanguage("en")
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
        guide.chooseLanguage("en")

        guide.advance()
        guide.advance()

        assertEquals(MasteryLevel.LEARNED, guide.state.savedLevel)
    }

    @Test
    fun closingTheSheetWithoutChoosingWritesNothing() {
        val guide = controller()
        guide.chooseLanguage("en")
        guide.advance()
        guide.advance()

        guide.dismissLookup()

        assertFalse(guide.state.lookupOpen)
        assertNull(guide.state.savedLevel)
        assertTrue(vocabulary.all().isEmpty(), "dismissing the sheet is not a save")
    }

    @Test
    fun theBandWritesThroughTheLanguagesAction() {
        val guide = controller()
        guide.chooseLanguage("en")
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
        guide.chooseLanguage("en")
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
        guide.chooseLanguage("en")
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
    fun aDeviceThatAlreadyHoldsASampleOpensOnItsPageAndWritesNothing() {
        val guide = controller(existingSample = { "en" })

        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("en", guide.state.sample?.language)
        assertTrue(materialised.isEmpty(), "a replay never materialises a second sample")
    }

    @Test
    fun aReplayWithBooksDemonstratesTheirFirstReadingLanguage() {
        val guide = controller(readingLanguage = { "fr" })

        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("fr", guide.state.sample?.language)
        assertTrue(materialised.isEmpty(), "the page demonstrates a book's language without writing a sample")
    }

    @Test
    fun aReplayNeverReplacesTheSampleItAlreadyHolds() {
        val guide = controller(existingSample = { "en" }, readingLanguage = { "fr" })

        assertEquals("en", guide.state.sample?.language)
        guide.restart()

        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("en", guide.state.sample?.language)
        assertTrue(materialised.isEmpty())
    }

    @Test
    fun aReplayDoesNotAskTheLanguageAgain() {
        val guide = controller(existingSample = { "en" })

        guide.back()

        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertTrue(guide.state.plain, "the page stays on its first beat")
    }

    @Test
    fun oneLanguageSkipsTheQuestionAndNamesItsPage() {
        val guide = controller(listOf(testOfferedLanguage("fr")))

        assertEquals(GuideStage.PAGE, guide.state.stage)
        assertEquals("fr", guide.state.sample?.language)
        assertEquals(listOf("fr"), materialised, "a single-language build materialises its sample at once")
    }
}
