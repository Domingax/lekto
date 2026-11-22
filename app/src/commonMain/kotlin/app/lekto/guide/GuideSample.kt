package app.lekto.guide

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.sample.Sample
import app.lekto.core.sample.SampleGlossary
import app.lekto.core.text.BlockKind
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.WordKey
import app.lekto.core.text.baseLanguage
import app.lekto.core.text.contextSentence
import app.lekto.core.text.normaliseSurface
import app.lekto.core.text.wordKeyOf
import app.lekto.reader.ReaderDocument
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderText
import app.lekto.reader.WordTap

/** The band the guide paints the sample at before the reader chooses one, and after **Skip**. */
val GUIDE_DEFAULT_BAND: CefrLevel = CefrLevel.A1

/**
 * One of the four words the coloured page explains (issue #277): the level's chip
 * and number, the word as the page shows it, the level's name and the sample's
 * own meaning. The reader's page draws the word in its colour; this is the note
 * that makes the colour mean something.
 */
data class GuideNote(
    val key: WordKey,
    val surface: String,
    val start: Int,
    val end: Int,
    val level: MasteryLevel,
    val meaning: String,
    val context: String?,
)

/** The sample's demonstrated paragraph as the reader's one-page document, with no chapter list. */
fun guideText(sample: Sample, band: CefrLevel, segmenter: TextSegmenter): ReaderText {
    val paragraph = guideParagraph(sample, band, segmenter)
    return ReaderText(
        title = sample.title,
        language = sample.language,
        blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(paragraph)))),
        chapters = emptyList(),
    )
}

/**
 * The paragraph the beat demonstrates at [band]: the one whose coloured words cover
 * the most distinct highlighted **Mastery level**s, so the page can show one word
 * per level (all four when the sample has them). Ties keep the earlier paragraph.
 * A level no paragraph carries is simply absent from the notes.
 */
private fun guideParagraph(sample: Sample, band: CefrLevel, segmenter: TextSegmenter): String =
    sample.paragraphs.maxByOrNull(BandColours(sample, band, segmenter)::levelsIn).orEmpty()

/** One sample at one band, ready to say how many highlighted levels a paragraph shows. */
private class BandColours(private val sample: Sample, band: CefrLevel, private val segmenter: TextSegmenter) {
    private val lemmas: LemmaLookup = guideLemmas(sample)
    private val colours: Map<String, MasteryLevel> = sample.colourMap(band)

    /** The distinct highlighted levels [paragraph] shows at this band. */
    fun levelsIn(paragraph: String): Int = segmenter.words(paragraph, sample.language)
        .map { span -> colours[wordKeyOf(span.surface, sample.language, lemmas).key] ?: MasteryLevel.KNOWN }
        .filter(MasteryLevel::highlighted)
        .distinct()
        .size
}

/**
 * The sample's own colors as the reader's mastery seam (issue #277): [plain]
 * paints nothing at all, and otherwise each word takes the level the sample's
 * map for [band] carries. A word the reader saved during the demonstration takes
 * the level they saved, so the demonstration shows the save's consequence.
 *
 * The sample is a **Derived asset**, so this reads no vault and needs no
 * **Dictionary pack**.
 */
fun guideMastery(
    sample: Sample,
    band: CefrLevel,
    plain: Boolean,
    saved: Pair<WordKey, MasteryLevel>? = null,
): MasteryLookup {
    if (plain) return MasteryLookup.AllKnown
    val colours = sample.colourMap(band)
    return MasteryLookup { key ->
        when {
            saved != null && key == saved.first -> saved.second

            key.language?.let(::baseLanguage) == baseLanguage(sample.language) ->
                colours[key.key] ?: MasteryLevel.KNOWN

            else -> MasteryLevel.KNOWN
        }
    }
}

/**
 * The sample's forms as a lemma lookup (ADR-0006): the words the page segments
 * carry the same identity the sample's maps and glossary are keyed by, so the
 * colours and the tapped word agree exactly.
 */
fun guideLemmas(sample: Sample): LemmaLookup = LemmaLookup { surface, language ->
    if (language != null && baseLanguage(language) != baseLanguage(sample.language)) {
        null
    } else {
        sample.forms[normaliseSurface(surface)]
    }
}

/** The one-page document the guide's page screens host in the embedded block. */
@Suppress("LongParameterList") // The sample, the band, the beat, the segmenter, the selection and the save.
fun guideDocument(
    sample: Sample,
    band: CefrLevel,
    plain: Boolean,
    segmenter: TextSegmenter,
    selectedRange: IntRange? = null,
    saved: Pair<WordKey, MasteryLevel>? = null,
): ReaderDocument = ReaderDocument(
    text = guideText(sample, band, segmenter),
    renderer = ReaderRenderer(
        segmenter = segmenter,
        mastery = guideMastery(sample, band, plain, saved),
        lemmas = guideLemmas(sample),
    ),
    selectedRange = selectedRange,
    masteryRevision = if (plain) 0 else band.band + 1 + (saved?.second?.level ?: 0),
)

/**
 * The four notes under the coloured page, one per highlighted **Mastery level** in
 * the order a reader acquires them, each the first word of the demonstrated
 * paragraph at that level, with the sample's meaning in [translationLanguage] (or
 * another glossary when the chosen one is absent). The paragraph is the one whose
 * colours cover the most levels at the band (ties keep the earlier one); a band
 * with fewer coloured levels carries fewer notes rather than inventing one.
 */
fun guideNotes(
    sample: Sample,
    band: CefrLevel,
    segmenter: TextSegmenter,
    translationLanguage: String?,
): List<GuideNote> {
    val paragraph = guideParagraph(sample, band, segmenter)
    val lemmas = guideLemmas(sample)
    val colours = sample.colourMap(band)
    val words = segmenter.words(paragraph, sample.language)
    val tokens = words.map { span ->
        val key = wordKeyOf(span.surface, sample.language, lemmas)
        GuideNote(
            key = key,
            surface = span.surface,
            start = span.start,
            end = span.end,
            level = colours[key.key] ?: MasteryLevel.KNOWN,
            meaning = meaningOf(sample, translationLanguage, key),
            context = contextSentence(paragraph, span.start, span.end),
        )
    }
    return MasteryLevel.entries
        .filter(MasteryLevel::highlighted)
        .mapNotNull { level -> tokens.firstOrNull { token -> token.level == level } }
}

/**
 * [tap]'s word as a note, when the sample's glossary answers it (issue #277's
 * feedback): the sheet opens for **that** word, its meaning read from the same
 * glossary, and a word the glossary does not carry answers nothing.
 */
fun guideNoteFor(sample: Sample, tap: WordTap, band: CefrLevel, translationLanguage: String?): GuideNote? {
    val meanings = glossaryFor(sample, translationLanguage)?.entriesOf(tap.token.key.key).orEmpty()
    if (meanings.isEmpty()) return null
    val meaning = meanings.first().senses.firstOrNull()
    return GuideNote(
        key = tap.token.key,
        surface = tap.token.surface,
        start = tap.token.start,
        end = tap.token.end,
        level = sample.colourMap(band)[tap.token.key.key] ?: MasteryLevel.KNOWN,
        meaning = meaning?.translations?.firstOrNull() ?: meaning?.definition.orEmpty(),
        context = tap.contextSentence,
    )
}

/**
 * The real lookup panel's answer for [note], built from the sample's glossary so
 * it works before any **Dictionary pack** is installed. An entry the glossary does
 * not carry reports the word as unknown rather than inventing a meaning.
 */
fun guideLookup(sample: Sample, note: GuideNote, translationLanguage: String?): WordLookup {
    val meanings = glossaryFor(sample, translationLanguage)?.entriesOf(note.key.key).orEmpty()
    if (meanings.isEmpty()) return WordLookup.NotInDictionary(note.surface, sample.language)
    return WordLookup.Found(
        query = note.surface,
        language = sample.language,
        lemma = meanings.first().lemma,
        entries = meanings.map { meaning ->
            DictionaryEntry(
                lemma = meaning.lemma,
                language = sample.language,
                partOfSpeech = meaning.partOfSpeech,
                pronunciation = meaning.pronunciation,
                audioUrl = null,
                senses = meaning.senses.map { sense -> DictionarySense(sense.definition, sense.translations) },
            )
        },
    )
}

/** The glossary the guide answers in: the chosen translation language's, or the sample's first. */
private fun glossaryFor(sample: Sample, translationLanguage: String?): SampleGlossary? {
    val chosen = translationLanguage?.let(::baseLanguage)
    return sample.glossaries.entries
        .firstOrNull { (language, _) -> baseLanguage(language) == chosen }
        ?.value
        ?: sample.glossaries.values.firstOrNull()
}

/** The first meaning for [key] in the sample's glossary, in the reader's language. */
private fun meaningOf(sample: Sample, translationLanguage: String?, key: WordKey): String =
    glossaryFor(sample, translationLanguage)
        ?.entriesOf(key.key)
        ?.firstOrNull()
        ?.senses
        ?.firstOrNull()
        ?.let { sense -> sense.translations.firstOrNull() ?: sense.definition }
        .orEmpty()
