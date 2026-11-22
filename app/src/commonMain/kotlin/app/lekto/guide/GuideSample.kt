package app.lekto.guide

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.MasteryLookup
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
import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.WordLookup
import app.lekto.reader.ReaderDocument
import app.lekto.reader.ReaderRenderer
import app.lekto.reader.ReaderText

/** The band the guide paints the sample at before the reader chooses one, and after **Skip**. */
val GUIDE_DEFAULT_BAND: CefrLevel = CefrLevel.B1

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

/** The sample's first paragraph as the reader's one-page document, with no chapter list. */
fun guideText(sample: Sample): ReaderText {
    val first = sample.paragraphs.firstOrNull().orEmpty()
    return ReaderText(
        title = sample.title,
        language = sample.language,
        blocks = listOf(TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(first)))),
        chapters = emptyList(),
    )
}

/**
 * The sample's own colors as the reader's mastery seam (issue #277): [plain]
 * paints nothing at all, and otherwise each word takes the level the sample's
 * map for [band] carries. The sample is a **Derived asset**, so this reads no
 * vault and needs no **Dictionary pack**.
 */
fun guideMastery(sample: Sample, band: CefrLevel, plain: Boolean): MasteryLookup {
    if (plain) return MasteryLookup.AllKnown
    val colours = sample.colourMap(band)
    return MasteryLookup { key ->
        if (key.language?.let(::baseLanguage) == baseLanguage(sample.language)) {
            colours[key.key] ?: MasteryLevel.KNOWN
        } else {
            MasteryLevel.KNOWN
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

/** The one-page document the guide's page screens host in the real reader. */
fun guideDocument(
    sample: Sample,
    band: CefrLevel,
    plain: Boolean,
    segmenter: TextSegmenter,
    selectedRange: IntRange? = null,
): ReaderDocument = ReaderDocument(
    text = guideText(sample),
    renderer = ReaderRenderer(
        segmenter = segmenter,
        mastery = guideMastery(sample, band, plain),
        lemmas = guideLemmas(sample),
    ),
    selectedRange = selectedRange,
    masteryRevision = if (plain) 0 else band.band + 1,
)

/**
 * The four notes under the coloured page, one per highlighted **Mastery level** in
 * the order a reader acquires them, each the first word of the paragraph at that
 * level, with the sample's meaning in [translationLanguage] (or another glossary
 * when the chosen one is absent). A band with fewer coloured levels carries fewer
 * notes rather than inventing one.
 */
fun guideNotes(
    sample: Sample,
    band: CefrLevel,
    segmenter: TextSegmenter,
    translationLanguage: String?,
): List<GuideNote> {
    val paragraph = sample.paragraphs.firstOrNull().orEmpty()
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
