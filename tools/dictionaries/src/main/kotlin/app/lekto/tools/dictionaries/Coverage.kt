package app.lekto.tools.dictionaries

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader

/**
 * How many lemmas one (Reading language × Translation language) pair yields from
 * a full edition (issue #302). A pair below the roster threshold is dropped from
 * the **Language manifest** (ADR-0047), which needs a lemma count, not a share of
 * entries, so this pass counts exactly what the pack build would.
 */
@Serializable
internal data class PairCoverage(
    val translation: String,
    val entriesWithTranslation: Long,
    val lemmas: Int,
    val entries: Int,
)

/** One reading language's full-edition pass: its entries and its yield into each measured target. */
@Serializable
internal data class EditionCoverage(
    val edition: String,
    val language: String,
    val totalLines: Long,
    val malformedLines: Long,
    val readingEntries: Long,
    val pairs: List<PairCoverage>,
)

/** The measured matrix, one row per reading language, in a deterministic order. */
@Serializable
internal data class CoverageReport(val editions: List<EditionCoverage>)

internal val coverageJson: Json = Json {
    encodeDefaults = true
    prettyPrint = true
}

internal fun CoverageReport.canonicalJson(): String = coverageJson.encodeToString(this)

/**
 * Streams one edition once and records, per measured translation language, how
 * many of the reading language's entries carry a translation into it and how many
 * distinct lemmas those entries yield.
 *
 * The filters are the pack build's, so the counts match what the build would
 * publish: the stream is [ReadingEntries], headwords are normalised with
 * [normaliseSurface] and kept only when [isSingleToken], and entries merge on
 * `(language, lemma, part of speech)`. A target missing from [targets] is
 * ignored, which keeps the measured matrix the candidate roster rather than
 * every language the editions happen to mention.
 */
internal class CoverageBuilder(
    private val readingLanguage: String,
    targets: Collection<String>,
    private val edition: String = readingLanguage,
) {

    private val requestedTargets = targets.map { canonicalLanguage(it.lowercase()) }.toSet()
    private val pairs = requestedTargets.associateWithTo(sortedMapOf()) { TargetCoverage() }
    private val reading = ReadingEntries(readingLanguage)

    private var readingEntryCount = 0L

    fun build(reader: BufferedReader): EditionCoverage {
        reading.forEach(reader, ::consumeEntry)
        return EditionCoverage(
            edition = edition,
            language = readingLanguage,
            totalLines = reading.totalLines,
            malformedLines = reading.malformedLines,
            readingEntries = readingEntryCount,
            pairs = pairs.map { (translation, coverage) ->
                PairCoverage(
                    translation = translation,
                    entriesWithTranslation = coverage.entriesWithTranslation,
                    lemmas = coverage.lemmas.size,
                    entries = coverage.entries.size,
                )
            },
        )
    }

    private fun consumeEntry(raw: RawEntry) {
        readingEntryCount++
        val lemma = raw.word?.let(::normaliseSurface)?.takeIf(::isSingleToken) ?: return
        val counted = HashSet<String>(4)
        raw.translations.forEach { translation ->
            val target = translation.langCode?.let(::canonicalLanguage) ?: return@forEach
            if (target !in requestedTargets || target in counted) return@forEach
            if (translation.word.isNullOrBlank()) return@forEach
            counted += target
            pairs.getValue(target).record(lemma, raw.pos)
        }
    }

    private class TargetCoverage {
        var entriesWithTranslation = 0L
        val lemmas = HashSet<String>()
        val entries = HashSet<LemmaKey>()

        fun record(lemma: String, partOfSpeech: String?) {
            entriesWithTranslation++
            lemmas += lemma
            entries += LemmaKey(lemma, partOfSpeech)
        }
    }

    private data class LemmaKey(val lemma: String, val partOfSpeech: String?)
}
