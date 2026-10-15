package app.lekto.tools.dictionaries

import kotlinx.serialization.SerializationException
import java.io.BufferedReader

/**
 * The deterministic transform: raw Wiktextract JSONL in, the in-memory pack out.
 *
 * Records are filtered to [readingLanguage] entries that carry a
 * [translationLanguage] translation, forms are reversed into a surface→lemma
 * index, and every key is normalised with [normaliseSurface] so the app's lookups
 * match. Ordering is fixed — entries by `(language, lemma, pos)`, forms by
 * `(surface, language)` — and no wall clock or timestamp enters, so the same input
 * yields the same pack (ticket #17). The pair is a parameter (issue #161), so one
 * build produces any (Reading language × Translation language) pack; the defaults
 * are the en→fr pack the app shipped with.
 */
internal class PackBuilder(
    private val provenance: PackProvenance,
    private val readingLanguage: String = PackFormat.ENGLISH,
    private val translationLanguage: String = PackFormat.FRENCH,
) {

    private var totalLines = 0L
    private var malformedLines = 0L
    private var readingEntries = 0L
    private var entriesWithTranslation = 0L

    private val entries = HashMap<EntryKey, EntryAccumulator>()
    private val forms = FormIndex(readingLanguage)

    fun build(reader: BufferedReader): PackContent {
        reader.forEachLine(::processLine)
        return assemble()
    }

    private fun processLine(line: String) {
        totalLines++
        if (couldBe(line, readingLanguage)) consumeReading(line)
    }

    private fun consumeReading(line: String) {
        val raw = parse(line) ?: return
        if (raw.langCode == readingLanguage) consumeEntry(raw)
    }

    private fun consumeEntry(raw: RawEntry) {
        readingEntries++
        val lemma = raw.word?.let(::normaliseSurface)?.takeIf(::isSingleToken)
        val translations = translationsInto(raw, translationLanguage)
        if (lemma == null || translations.isEmpty()) return
        entriesWithTranslation++
        accumulateEntry(raw, lemma, translations)
        accumulateForms(raw, lemma)
    }

    private fun couldBe(line: String, language: String): Boolean =
        line.contains(langCodeCompact(language)) || line.contains(langCodeSpaced(language))

    private fun parse(line: String): RawEntry? = try {
        wiktextractJson.decodeFromString<RawEntry>(line)
    } catch (_: SerializationException) {
        malformedLines++
        null
    } catch (_: IllegalArgumentException) {
        malformedLines++
        null
    }

    private fun accumulateEntry(raw: RawEntry, lemma: String, translations: List<Translation>) {
        val key = EntryKey(readingLanguage, lemma, raw.pos)
        val accumulator = entries.getOrPut(key) { EntryAccumulator(lemma, readingLanguage, raw.pos) }
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

    private fun assemble(): PackContent {
        val packEntries = entries.values.map { it.toPackEntry() }.sortedWith(ENTRY_ORDER)
        val packForms = forms.toPackForms()
        val stats = PackStats(
            totalLines = totalLines,
            malformedLines = malformedLines,
            readingEntries = readingEntries,
            entriesWithTranslation = entriesWithTranslation,
            lemmaCount = packEntries.map(PackEntry::lemma).distinct().size,
            entryCount = packEntries.size,
            formCount = packForms.size,
            senseCount = packEntries.sumOf { it.senses.size },
            translationCount = packEntries.sumOf { entry -> entry.senses.sumOf { it.translations.size } },
        )
        return PackContent(provenance, stats, packEntries, packForms)
    }

    private companion object {
        val ENTRY_ORDER = compareBy<PackEntry>({ it.language }, { it.lemma }, { it.partOfSpeech.orEmpty() })
    }
}

/** The JSON line fragment a compact-serialised entry's language carries. */
internal fun langCodeCompact(language: String): String = "\"lang_code\":\"$language\""

/** The JSON line fragment a pretty-serialised entry's language carries. */
internal fun langCodeSpaced(language: String): String = "\"lang_code\": \"$language\""

/** A sense with no reading-language definition: a translation-only entry still needs one. */
internal const val SENSE_WITHOUT_DEFINITION: String = ""

private data class EntryKey(val language: String, val lemma: String, val partOfSpeech: String?)
