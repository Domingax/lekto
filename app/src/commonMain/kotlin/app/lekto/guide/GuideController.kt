package app.lekto.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.sample.Sample
import app.lekto.core.text.TextSegmenter
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasteryController

/** The guide's screens, in the order a first launch walks them (issue #277). */
@Suppress("MagicNumber") // The positions are the step numbers the five marks show.
enum class GuideStage(val position: Int) {
    /** The language question; skipped entirely when the build ships one sample. */
    LANGUAGE(1),

    /** The sample's page, in two beats: plain, then coloured. */
    PAGE(2),

    /** One word to tap, with the real lookup sheet open. */
    TAP(3),

    /** The page preview and the six CEFR bands. */
    LEVEL(4),
}

/**
 * What the guide's screens render (issue #277): the stage, the sample once a
 * language is answered, the band the page paints at, the band the reader actually
 * tapped, the beat of the page, whether the real sheet is open, the level the
 * word was saved at, and an honest failure when the write was refused. The
 * arrival is the shell's own shelf, so it is not a stage here.
 *
 * [band] is the suggestion the page demonstrates before any choice; [chosenBand]
 * is the **Reader level** the reader tapped, so a suggestion is never drawn as a
 * selection (issue #279's rule, applied to the bands).
 */
data class GuideUiState(
    val stage: GuideStage = GuideStage.LANGUAGE,
    val sample: Sample? = null,
    val band: CefrLevel = GUIDE_DEFAULT_BAND,
    val chosenBand: CefrLevel? = null,
    val plain: Boolean = true,
    val lookupOpen: Boolean = false,
    val savedLevel: MasteryLevel? = null,
    val saveFailed: Boolean = false,
)

/**
 * The first launch's flow (issue #277), from the language question to the level
 * that colours the shelf: it materialises the chosen sample through the
 * **Sample book** seam (#278), starts the background download, advances the
 * page's two beats, opens the real sheet over one word, saves that word to the
 * real **Vocabulary** at the level tapped, and writes the chosen band through the
 * **Languages** surface's own action (`MasteryController.setLevel`).
 *
 * The screens own nothing: this holder decides what each one shows, so the flow
 * is proved once and the composables stay render-only. The sample's own colour
 * maps and glossary are the demonstration's source, so every screen works before,
 * during and after the download.
 */
@Suppress("LongParameterList") // The flow's independent inputs.
class GuideController(
    languages: List<OfferedLanguage>,
    private val sampleOf: (String) -> Sample?,
    private val materialise: (String) -> Unit = {},
    private val download: GuideDownloadController? = null,
    private val mastery: MasteryController? = null,
    private val segmenter: TextSegmenter,
    private val translationLanguage: () -> String? = { null },
    private val existingSample: () -> String? = { null },
    private val readingLanguage: () -> String? = { null },
) {

    private val singleLanguage: String? = languages.singleOrNull()?.language

    /**
     * The screens' state. It opens on the page when the shelf already implies a
     * language — a materialised sample, or the reader's first **Reading language**
     * — and on the question only on a true first launch (issue #277's review).
     */
    var state by mutableStateOf(GuideUiState())
        private set

    private var chosenLanguage: String? = null

    /** Whether this run asked the language question, so `Back` may return to it. */
    private var asksLanguage: Boolean = false

    init {
        val opening = openingLanguage()
        asksLanguage = opening == null
        show(opening)
        // A build that ships one sample has nothing to ask; the first launch
        // materialises it at once. A replay never does (see restart).
        if (opening != null && opening == singleLanguage && existingSample() == null && readingLanguage() == null) {
            begin(opening)
        }
    }

    /** The language answer: materialise its sample and start the download, then show the page. */
    fun chooseLanguage(language: String) {
        if (state.stage != GuideStage.LANGUAGE) return
        begin(language)
        show(language)
    }

    /**
     * The page's next beat: plain to coloured, coloured to the tap, and the tap's
     * own dismissal to the level. It does nothing on the question, which the rows
     * answer, and while the sheet covers the tap screen's continue.
     */
    fun advance() {
        state = when {
            state.stage == GuideStage.PAGE && state.plain -> state.copy(plain = false)

            state.stage == GuideStage.PAGE -> state.copy(
                stage = GuideStage.TAP,
                lookupOpen = true,
                savedLevel = existingLevel(state.sample),
            )

            state.stage == GuideStage.TAP && !state.lookupOpen -> state.copy(stage = GuideStage.LEVEL)

            else -> state
        }
    }

    /** Reopens the real sheet on the tap screen, after the reader closed it once. */
    fun tapWord() {
        if (state.stage == GuideStage.TAP) state = state.copy(lookupOpen = true)
    }

    /** The desktop's left arrow: back one screen, never before the question or the single page. */
    fun back() {
        state = when {
            state.stage == GuideStage.LEVEL -> state.copy(stage = GuideStage.TAP, lookupOpen = false)
            state.stage == GuideStage.TAP -> state.copy(stage = GuideStage.PAGE, plain = false)
            state.stage == GuideStage.PAGE && !state.plain -> state.copy(plain = true)
            state.stage == GuideStage.PAGE && asksLanguage -> state.copy(stage = GuideStage.LANGUAGE)
            else -> state
        }
    }

    /** Closes the sheet without writing anything. */
    fun dismissLookup() {
        state = state.copy(lookupOpen = false)
    }

    /**
     * Saves the demonstrated word at [level] to the real **Vocabulary**, with the
     * sample as the book it was saved from, and states it. A refused write says so
     * and leaves the word unsaved.
     */
    @Suppress("ReturnCount") // Three guards name the three ways there is no word to save; one happy path follows.
    fun save(level: MasteryLevel) {
        val sample = state.sample ?: return
        val note = guideNotes(sample, state.band, segmenter, translationLanguage()).firstOrNull() ?: return
        val writer = mastery ?: run {
            state = state.copy(saveFailed = true)
            return
        }
        val entry = VocabularyEntry.saved(
            key = note.key,
            surface = note.surface,
            translation = note.meaning,
            contextSentence = note.context,
            mastery = level,
        )
        val landed = writer.save(entry, sample.identity)
        state = state.copy(savedLevel = if (landed) level else state.savedLevel, saveFailed = !landed)
    }

    /** Writes [band] as the reader's level for the chosen language and repaints the page. */
    fun chooseBand(band: CefrLevel) {
        state = state.copy(band = band, chosenBand = band)
        chosenLanguage?.let { language -> mastery?.setLevel(language, band) }
    }

    /**
     * Puts the flow back at its opening screen, so the Settings ▸ Application row
     * can replay it over an existing shelf. It demonstrates the sample the device
     * already holds, or the first language of the reader's books, and never
     * materialises a second sample or replaces the first. The download is not
     * restarted: an installed table stays installed and a stopped one stays
     * stopped.
     */
    fun restart() {
        val opening = openingLanguage()
        asksLanguage = opening == null
        show(opening)
    }

    /** The language a replay demonstrates: the stored sample, then the books, then the single shipped sample. */
    private fun openingLanguage(): String? = existingSample() ?: readingLanguage() ?: singleLanguage

    /** Shows the page in [language], or the question when there is no language to demonstrate. */
    private fun show(language: String?) {
        chosenLanguage = language
        state = if (language == null) {
            GuideUiState(stage = GuideStage.LANGUAGE)
        } else {
            GuideUiState(stage = GuideStage.PAGE, sample = sampleOf(language), plain = true)
        }
    }

    /** Materialises [language]'s sample and asks for its download, once per answer. */
    private fun begin(language: String) {
        materialise(language)
        download?.start(language)
    }

    /** The level the demonstrated word already carries in the vault, so a replay states a fact. */
    private fun existingLevel(sample: Sample?): MasteryLevel? {
        val note = sample?.let { chosen ->
            guideNotes(chosen, state.band, segmenter, translationLanguage()).firstOrNull()
        }
        return note?.let { chosen -> mastery?.entryFor(chosen.key)?.mastery }
    }
}
