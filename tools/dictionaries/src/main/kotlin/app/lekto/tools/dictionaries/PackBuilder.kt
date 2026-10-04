package app.lekto.tools.dictionaries

import kotlinx.serialization.SerializationException
import java.io.BufferedReader

/**
 * The deterministic transform: raw Wiktextract JSONL in, the in-memory pack out.
 *
 * Records are filtered to English entries that carry a French translation, forms
 * are reversed into a surface→lemma index, and every key is normalised with
 * [normaliseSurface] so the app's lookups match. Ordering is fixed — entries by
 * `(language, lemma, pos)`, forms by `(surface, language)` — and no wall clock
 * or timestamp enters, so the same input yields the same pack (ticket #17).
 */
internal class PackBuilder(private val provenance: PackProvenance) {

    private var totalLines = 0L
    private var malformedLines = 0L
    private var englishEntries = 0L
    private var englishEntriesWithFrench = 0L

    private val entries = HashMap<EntryKey, EntryAccumulator>()
    private val forms = FormIndex(PackFormat.ENGLISH)

    fun build(reader: BufferedReader): PackContent {
        reader.forEachLine(::processLine)
        return assemble()
    }

    private fun processLine(line: String) {
        totalLines++
        if (couldBeEnglish(line)) consumeEnglish(line)
    }

    private fun consumeEnglish(line: String) {
        val raw = parse(line) ?: return
        if (raw.langCode == PackFormat.ENGLISH) consumeEntry(raw)
    }

    private fun consumeEntry(raw: RawEntry) {
        englishEntries++
        val lemma = raw.word?.let(::normaliseSurface)?.takeIf(::isSingleToken)
        val french = frenchTranslations(raw)
        if (lemma == null || french.isEmpty()) return
        englishEntriesWithFrench++
        accumulateEntry(raw, lemma, french)
        accumulateForms(raw, lemma)
    }

    private fun couldBeEnglish(line: String): Boolean = line.contains(ENGLISH_COMPACT) || line.contains(ENGLISH_SPACED)

    private fun parse(line: String): RawEntry? = try {
        wiktextractJson.decodeFromString<RawEntry>(line)
    } catch (_: SerializationException) {
        malformedLines++
        null
    } catch (_: IllegalArgumentException) {
        malformedLines++
        null
    }

    private fun accumulateEntry(raw: RawEntry, lemma: String, french: List<FrenchTranslation>) {
        val key = EntryKey(PackFormat.ENGLISH, lemma, raw.pos)
        val accumulator = entries.getOrPut(key) { EntryAccumulator(lemma, PackFormat.ENGLISH, raw.pos) }
        accumulator.pronunciation = accumulator.pronunciation ?: firstPronunciation(raw)
        accumulator.audioUrl = accumulator.audioUrl ?: firstAudioUrl(raw)
        mergeSenses(accumulator, raw, french)
    }

    private fun mergeSenses(accumulator: EntryAccumulator, raw: RawEntry, french: List<FrenchTranslation>) {
        val definitions = definitionsOf(raw).ifEmpty { listOf(SENSE_WITHOUT_DEFINITION) }
        definitions.forEach(accumulator::addDefinition)
        french.forEach { translation ->
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
            englishEntries = englishEntries,
            englishEntriesWithFrench = englishEntriesWithFrench,
            lemmaCount = packEntries.map(PackEntry::lemma).distinct().size,
            entryCount = packEntries.size,
            formCount = packForms.size,
            senseCount = packEntries.sumOf { it.senses.size },
            translationCount = packEntries.sumOf { entry -> entry.senses.sumOf { it.translations.size } },
        )
        return PackContent(provenance, stats, packEntries, packForms)
    }

    private companion object {
        const val ENGLISH_COMPACT = "\"lang_code\":\"en\""
        const val ENGLISH_SPACED = "\"lang_code\": \"en\""
        val ENTRY_ORDER = compareBy<PackEntry>({ it.language }, { it.lemma }, { it.partOfSpeech.orEmpty() })
    }
}

/** A sense with no English definition: a translation-only entry still needs one. */
internal const val SENSE_WITHOUT_DEFINITION: String = ""

private data class EntryKey(val language: String, val lemma: String, val partOfSpeech: String?)
