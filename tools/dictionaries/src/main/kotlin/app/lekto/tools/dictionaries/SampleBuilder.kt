package app.lekto.tools.dictionaries

/**
 * Builds one **Sample** from its authored text, the language's **Difficulty
 * table** and the **Dictionary pack**s already built for that language (issue
 * #297).
 *
 * Nothing here is authored twice. The word keys are the reader's own identity —
 * the form index's lemma where one exists, the normalised surface form otherwise
 * (ADR-0006) — the six colour maps are the table's bands composed with the
 * default-mastery rule (ADR-0030), and each glossary is a cut of one pack's
 * entries. The same input therefore yields the same catalogue, and a changed
 * table or pack changes the maps or glossaries rather than leaving a frozen
 * illustration behind.
 */
internal class SampleBuilder(
    private val source: SampleSource,
    private val difficultyByKey: Map<String, String>,
    private val formIndex: Map<String, String>,
    private val packs: Map<String, List<PackEntry>>,
) {

    fun build(): SampleContent {
        require(source.title.isNotBlank()) { "sample ${source.language} has no title" }
        val paragraphs = source.paragraphs.map(String::trim)
        require(paragraphs.isNotEmpty() && paragraphs.none(String::isBlank)) {
            "sample ${source.language} has no paragraph to read"
        }
        val keys = sampleWordKeys(paragraphs, formIndex)
        val difficulty = keys.associateWith { key -> difficultyByKey[key] }.toSortedMap()
        val maps = CEFR_BANDS.associateWith { readerBand ->
            keys.associateWith { key -> sampleMastery(difficultyByKey[key], readerBand) }.toSortedMap()
        }
        val highlighted = keys.filter { key -> maps.values.any { map -> map.getValue(key) != MASTERY_KNOWN } }
        val glossaries = packs.toSortedMap().mapValues { (translation, entries) ->
            glossary(translation, highlighted, entries)
        }
        return SampleContent(
            language = source.language,
            title = source.title,
            author = source.author,
            attribution = source.attribution,
            paragraphs = paragraphs,
            difficulty = difficulty,
            forms = sampleWordForms(paragraphs, formIndex),
            colourMaps = maps,
            glossaries = glossaries,
        )
    }

    private fun glossary(translation: String, highlighted: List<String>, entries: List<PackEntry>): SampleGlossary {
        val byLemma = entries
            .mapNotNull(PackEntry::usable)
            .groupBy(PackEntry::lemma)
            .mapValues { (lemma, lemmaEntries) -> meanings(lemma, lemmaEntries) }
        val missing = highlighted.filter { key -> key !in byLemma }
        require(missing.isEmpty()) {
            "the $translation glossary does not cover ${missing.size} highlighted " +
                "word(s) of ${source.language}: ${missing.joinToString()}"
        }
        return SampleGlossary(
            entries = highlighted.mapNotNull { key -> byLemma[key]?.let { meanings -> key to meanings } }.toMap(),
        )
    }

    /**
     * A sample's meaning is one compact answer, not a copy of the pack: the first
     * highlighted word's entries, each with its first defined sense, so the shipped
     * catalogue stays small. The full pack still answers a real lookup once it is
     * installed; the sample only has to answer before that.
     */
    private fun meanings(lemma: String, entries: List<PackEntry>): List<SampleGlossaryEntry> = entries
        .mapNotNull { entry ->
            val sense = entry.senses.firstOrNull { it.definition.isNotBlank() }
                ?: return@mapNotNull null
            SampleGlossaryEntry(
                lemma = lemma,
                partOfSpeech = entry.partOfSpeech,
                pronunciation = entry.pronunciation,
                senses = listOf(
                    SampleGlossarySense(
                        definition = sense.definition,
                        translations = sense.translations.take(MAX_SAMPLE_TRANSLATIONS),
                    ),
                ),
            )
        }
        .take(MAX_SAMPLE_ENTRIES)
}

/** The word key of every word token in [paragraphs], deduplicated and sorted. */
internal fun sampleWordKeys(paragraphs: List<String>, formIndex: Map<String, String>): List<String> =
    sampleSurfaces(paragraphs)
        .map { surface -> formIndex[surface] ?: surface }
        .distinct()
        .sorted()

/**
 * The surface→word-key mappings the sample's own tokens need before a **Dictionary
 * pack** is installed: only the forms whose key differs from the normalised
 * surface, so the guide's page can key a tap exactly as the reader will once the
 * pack arrives. `core`'s keyed-by-surface fallback already covers the rest.
 */
internal fun sampleWordForms(paragraphs: List<String>, formIndex: Map<String, String>): Map<String, String> =
    sampleSurfaces(paragraphs)
        .mapNotNull { surface ->
            val key = formIndex[surface] ?: return@mapNotNull null
            if (key == surface) null else surface to key
        }
        .toMap()
        .toSortedMap()

/** Every word token's normalised surface, in reading order, with duplicates kept. */
private fun sampleSurfaces(paragraphs: List<String>): List<String> = WORD.findAcross(paragraphs).map(::normaliseSurface)

/**
 * The default-mastery rule (ADR-0030) over the sample's bands, kept in step with
 * `core`'s `derivedMastery`: at or below the reader's band is known, each band
 * above drops one mastery level, flooring at unknown. A word the table does not
 * place is unknown. `tools/dictionaries` depends on no Lekto module, so the rule
 * is restated here; the sample's maps are the reader's own computation, frozen.
 */
internal fun sampleMastery(difficultyBand: String?, readerBand: String): String {
    val difficultyIndex = CEFR_BANDS.indexOf(difficultyBand)
    val readerIndex = CEFR_BANDS.indexOf(readerBand)
    if (difficultyIndex < 0 || readerIndex < 0) return MASTERY_LEVELS.first()
    val bandsAbove = (difficultyIndex - readerIndex).coerceAtLeast(0)
    return MASTERY_LEVELS[(MASTERY_LEVELS.lastIndex - bandsAbove).coerceIn(0, MASTERY_LEVELS.lastIndex)]
}

/** A pack entry that can answer a lookup: at least one sense with a definition. */
private fun PackEntry.usable(): PackEntry? {
    val defined = senses.filter { sense -> sense.definition.isNotBlank() }
    return if (defined.isEmpty()) null else copy(senses = defined)
}

/** The producer's copy of the CEFR bands, in order, matching `core`'s `CefrLevel` (ADR-0013). */
internal val CEFR_BANDS: List<String> = listOf("A1", "A2", "B1", "B2", "C1", "C2")

/** The producer's copy of the five mastery levels, in order, matching `core`'s `MasteryLevel` (ADR-0013). */
internal val MASTERY_LEVELS: List<String> = listOf("UNKNOWN", "RECOGNIZED", "FAMILIAR", "LEARNED", "KNOWN")

/** The level that is never highlighted; a map's other values are the coloured words. */
internal const val MASTERY_KNOWN: String = "KNOWN"

/** At most two parts of speech per sample word, so the catalogue stays small. */
private const val MAX_SAMPLE_ENTRIES: Int = 2

/** At most three translations per sense; the sample demonstrates, the pack answers. */
private const val MAX_SAMPLE_TRANSLATIONS: Int = 3

private val WORD = Regex("""[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*""")

/** Every word token across [paragraphs], in reading order. */
private fun Regex.findAcross(paragraphs: List<String>): List<String> =
    paragraphs.flatMap { paragraph -> findAll(paragraph).map { match -> match.value }.toList() }
