package app.lekto.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.sample.Sample
import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.WordKey
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.reader.MasteryController
import app.lekto.reader.WordTap

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
 * What the guide's screens render (issue #277): the stage, the language the
 * reader selected, the sample once `Continue` answers, the band the page paints
 * at, the band the reader has chosen, the beat of the page, the word whose sheet
 * is open, the level the word was saved at, and an honest failure when the write
 * was refused. The arrival is the shell's own shelf, so it is not a stage.
 *
 * [band] and [chosenBand] both start at A1: the page colours at A1 from entry, so
 * the selected treatment tells the truth rather than drawing a suggestion as a
 * choice (the owner's second review reverses the earlier "no band until tapped"
 * rule). Tapping another band makes it both the painted and the written one;
 * [tapped] is the word the reader touched, so the sheet answers **that** word;
 * [savedKey] and [savedLevel] are the save the page recolours by.
 */
data class GuideUiState(
    val stage: GuideStage = GuideStage.LANGUAGE,
    val selectedLanguage: String? = null,
    val sample: Sample? = null,
    val band: CefrLevel = GUIDE_DEFAULT_BAND,
    val chosenBand: CefrLevel = GUIDE_DEFAULT_BAND,
    val plain: Boolean = true,
    val lookupOpen: Boolean = false,
    val tapped: GuideNote? = null,
    val savedKey: WordKey? = null,
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
    private val startDownload: (String) -> Unit = {},
    private val mastery: MasteryController? = null,
    private val segmenter: TextSegmenter,
    private val translationLanguage: () -> String? = { null },
) {

    private val singleLanguage: String? = languages.singleOrNull()?.language

    /**
     * The screens' state. It always opens on the language question — or, in a
     * build that ships one sample, straight on the page — because the guide's
     * document records completion only: a mid-guide relaunch starts over.
     */
    var state by mutableStateOf(GuideUiState())
        private set

    private var chosenLanguage: String? = null

    /** Whether this run has a language question, so `Back` may return to it. */
    private val asksLanguage: Boolean = singleLanguage == null

    init {
        // A build that ships one sample has nothing to ask, so it chooses it and
        // starts its download at once; every other build waits for the answer.
        open()
    }

    /**
     * Selects [language] on the question: the row carries the selected treatment,
     * the background download starts, and nothing is written until `Continue`.
     */
    fun selectLanguage(language: String) {
        if (state.stage != GuideStage.LANGUAGE) return
        state = state.copy(selectedLanguage = language)
        startDownload(language)
    }

    /**
     * The screen's way forward: `Continue` on the question materialises the
     * selected language's sample and shows its page; on the page it moves to the
     * second beat and then the tap screen; on the tap screen, with the sheet
     * closed, it moves to the level screen.
     */
    fun advance() {
        state = when {
            state.stage == GuideStage.LANGUAGE -> continueFromQuestion()

            state.stage == GuideStage.PAGE && state.plain -> state.copy(plain = false)

            state.stage == GuideStage.PAGE -> {
                val demonstrated = state.sample?.let { chosen ->
                    guideNotes(chosen, state.band, segmenter, translationLanguage()).firstOrNull()
                }
                state.copy(
                    stage = GuideStage.TAP,
                    lookupOpen = false,
                    tapped = null,
                    savedKey = demonstrated?.key,
                    savedLevel = demonstrated?.let { note -> mastery?.entryFor(note.key)?.mastery },
                )
            }

            state.stage == GuideStage.TAP && !state.lookupOpen -> state.copy(stage = GuideStage.LEVEL)

            else -> state
        }
    }

    /**
     * Opens the sheet for the word [tap] landed on, when the sample's glossary
     * answers it: the sheet is the reader's tap, never an arrival, so a tap on
     * empty space or on a word with no meaning writes nothing.
     */
    fun tapWord(tap: WordTap) {
        val sample = state.sample?.takeIf { state.stage == GuideStage.TAP } ?: return
        val note = guideNoteFor(sample, tap, state.band, translationLanguage()) ?: return
        state = state.copy(lookupOpen = true, tapped = note)
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
     * Saves the word the sheet is open on at [level] to the real **Vocabulary**,
     * with the sample as the book it was saved from, and states it. The saved
     * level then overrides that word's mastery on the page, so the reader sees the
     * demonstration's point — a known word (level 4) reads as normal ink, with no
     * rule. A refused write says so and leaves the word unsaved.
     */
    @Suppress("ReturnCount") // Three guards name the three ways there is no word to save; one happy path follows.
    fun save(level: MasteryLevel) {
        val sample = state.sample ?: return
        val note = state.tapped
            ?: state.sample?.let { chosen ->
                guideNotes(chosen, state.band, segmenter, translationLanguage()).firstOrNull()
            }
            ?: return
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
        state = if (landed) {
            state.copy(savedKey = note.key, savedLevel = level, saveFailed = false)
        } else {
            state.copy(saveFailed = true)
        }
    }

    /** Writes [band] as the reader's level for the chosen language and repaints the page. */
    fun chooseBand(band: CefrLevel) {
        state = state.copy(band = band, chosenBand = band)
        chosenLanguage?.let { language -> mastery?.setLevel(language, band) }
    }

    /**
     * `Start reading`: writes the standing band as the reader's level for the
     * chosen language. A1 is written when the reader never tapped another band, so
     * the level the page demonstrated is the level the shelf receives; `Skip` does
     * not call this and writes nothing.
     */
    fun startReading() {
        if (state.stage != GuideStage.LEVEL) return
        chosenLanguage?.let { language -> mastery?.setLevel(language, state.chosenBand) }
    }

    /**
     * Puts the flow back at its first screen, so the Settings ▸ Application row
     * can replay it. A replay is a deliberate redo: it asks the language question
     * again, and the answer replaces the device's sample, so the demonstration and
     * the shelf cannot diverge (ADR-0049's update; item 12 of the feedback).
     */
    fun restart() {
        open()
    }

    /**
     * Opens on the language question, or on the page when the build ships a single
     * sample and has nothing to ask. It never resumes a stage: `guide.json` records
     * completion only, so an unfinished guide starts over on every launch.
     */
    private fun open() {
        val single = singleLanguage
        chosenLanguage = single
        if (single == null) {
            state = GuideUiState(stage = GuideStage.LANGUAGE)
        } else {
            begin(single)
            state = GuideUiState(stage = GuideStage.PAGE, sample = sampleOf(single), plain = true)
        }
    }

    /** `Continue` on the question: materialise the selected language and show its page. */
    private fun continueFromQuestion(): GuideUiState {
        val language = state.selectedLanguage ?: return state
        chosenLanguage = language
        begin(language)
        return GuideUiState(stage = GuideStage.PAGE, sample = sampleOf(language), plain = true)
    }

    /** Materialises [language]'s sample and asks for its download, once per answer. */
    private fun begin(language: String) {
        materialise(language)
        startDownload(language)
    }
}
