package app.lekto.tools.dictionaries

import java.io.BufferedReader

/** One edition's in-memory intermediate: the shared index and one content per Translation language. */
internal data class EditionIntermediate(val index: FormIndex, val contentsByTranslation: Map<String, EditionContent>) {
    /** The content file for [translationLanguage], matched through the canonical language code. */
    fun contentFor(translationLanguage: String): EditionContent =
        contentsByTranslation.getValue(canonicalLanguage(translationLanguage.lowercase()))
}

/**
 * Streams one edition once and builds its **Reading language**'s intermediate
 * (ADR-0046): the surface→lemma index shared by every pack of the language, and
 * one content file per configured **Translation language**.
 *
 * The index is built from every entry that carries at least one translation into
 * a configured target, so an entry that translates into only one target still
 * contributes its forms to every pack of the language (ADR-0045). Entries merge
 * on `(language, lemma, part of speech)`, ordering is fixed and no wall clock
 * enters, so the same edition yields byte-identical intermediates.
 */
internal class EditionExtractor(
    private val provenance: PackProvenance,
    private val readingLanguage: String,
    translationLanguages: Collection<String>,
) {

    private val targets = translationLanguages.map { canonicalLanguage(it.lowercase()) }.distinct().sorted()
    private val reading = ReadingEntries(readingLanguage)
    private val entries = targets.associateWith { HashMap<EntryKey, EntryAccumulator>() }
    private val entriesWithTranslation = HashMap<String, Long>()
    private val forms = FormIndexBuilder(readingLanguage)
    private var readingEntryCount = 0L

    fun extract(reader: BufferedReader): EditionIntermediate {
        require(targets.isNotEmpty()) { "an extraction needs at least one translation target" }
        reading.forEach(reader, ::consumeEntry)
        return assemble()
    }

    private fun consumeEntry(raw: RawEntry) {
        readingEntryCount++
        val lemma = raw.word?.let(::normaliseSurface)?.takeIf(::isSingleToken) ?: return
        val translationsByTarget = targets.associateWith { target -> translationsInto(raw, target) }
        if (translationsByTarget.values.all { it.isEmpty() }) return
        accumulateForms(raw, lemma)
        translationsByTarget.forEach { (target, translations) ->
            if (translations.isEmpty()) return@forEach
            entriesWithTranslation[target] = entriesWithTranslation.getOrDefault(target, 0L) + 1
            accumulateEntry(target, raw, lemma, translations)
        }
    }

    private fun accumulateEntry(target: String, raw: RawEntry, lemma: String, translations: List<Translation>) {
        val key = EntryKey(readingLanguage, lemma, raw.pos)
        val accumulator = entries.getValue(target).getOrPut(key) {
            EntryAccumulator(lemma, readingLanguage, raw.pos)
        }
        accumulator.pronunciation = accumulator.pronunciation ?: firstPronunciation(raw)
        accumulator.audioUrl = accumulator.audioUrl ?: firstAudioUrl(raw)
        mergeSenses(accumulator, raw, translations)
    }

    private fun mergeSenses(accumulator: EntryAccumulator, raw: RawEntry, translations: List<Translation>) {
        val definitions = definitionsOf(raw).ifEmpty { listOf(SENSE_WITHOUT_DEFINITION) }
        definitions.forEach(accumulator::addDefinition)
        translations.forEach { translation ->
            val definition = definitions[senseIndex(translation.sense, definitions)]
            accumulator.addTranslation(definition, translation.word)
        }
    }

    private fun accumulateForms(raw: RawEntry, lemma: String) {
        if (raw.formOf.isNotEmpty()) {
            raw.formOf.forEach { form -> addInflectedForm(lemma, form.word) }
            return
        }
        forms.add(lemma, lemma)
        raw.forms.forEach { form -> addInflectedForm(form.form, lemma) }
    }

    private fun addInflectedForm(surface: String?, lemma: String?) {
        val normalisedSurface = surface?.let(::normaliseSurface)?.takeIf(::isSingleToken) ?: return
        val normalisedLemma = lemma?.let(::normaliseSurface)?.takeIf(::isSingleToken) ?: return
        forms.add(normalisedSurface, normalisedLemma)
    }

    private fun assemble(): EditionIntermediate {
        val index = FormIndex(
            formatVersion = EditionFormat.VERSION,
            provenance = provenance,
            readingLanguage = readingLanguage,
            translationLanguages = targets,
            forms = forms.toPackForms(),
        )
        val contents = targets.associateWith { target ->
            EditionContent(
                formatVersion = EditionFormat.VERSION,
                provenance = provenance,
                readingLanguage = readingLanguage,
                translationLanguage = target,
                stats = EditionStats(
                    totalLines = reading.totalLines,
                    malformedLines = reading.malformedLines,
                    readingEntries = readingEntryCount,
                    entriesWithTranslation = entriesWithTranslation.getOrDefault(target, 0L),
                ),
                entries = entries.getValue(target).values.map { it.toPackEntry() }.sortedWith(ENTRY_ORDER),
            )
        }
        return EditionIntermediate(index, contents)
    }

    private companion object {
        val ENTRY_ORDER = compareBy<PackEntry>({ it.language }, { it.lemma }, { it.partOfSpeech.orEmpty() })
    }
}

private data class EntryKey(val language: String, val lemma: String, val partOfSpeech: String?)
