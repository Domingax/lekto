package app.lekto.core.sample

import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One **Sample**: the short text the first-launch guide demonstrates the reader
 * on, with the colours and meanings it needs before any **Dictionary pack** is
 * downloaded (issue #297).
 *
 * The text is a real reading session, not a drawing of one. Its six [colourMaps]
 * are the **Difficulty table**'s output at build time, one map per CEFR band, so
 * a recolour on the guide's level screen reads the same frozen computation the
 * reader's own books use. Each glossary is the meanings of the sample's words
 * extracted from one **Dictionary pack**, keyed by the same word identity as the
 * maps, so a tap can be answered before any download.
 *
 * A Sample is a **Derived asset** (ADR-0005): the app materialises it into the
 * derived store under [identity], never into the **Vault**, and it is never
 * exported or synced.
 */
data class Sample(
    val language: String,
    val title: String,
    val author: String?,
    val attribution: String?,
    val paragraphs: List<String>,
    val difficulty: Map<String, CefrLevel?>,
    val forms: Map<String, String>,
    val colourMaps: Map<CefrLevel, Map<String, MasteryLevel>>,
    val glossaries: Map<String, SampleGlossary>,
) {
    /** The stable identity the materialised sample carries, `sample:<language>` (issue #278). */
    val identity: String get() = identityOf(language)

    /** The paragraphs as the one text a reader opens. */
    val text: String get() = paragraphs.joinToString(separator = "\n\n")

    /** The colours for [readerBand], or an empty map when this sample carries none. */
    fun colourMap(readerBand: CefrLevel): Map<String, MasteryLevel> = colourMaps[readerBand].orEmpty()

    companion object {
        /** The fixed identity of one language's sample, `sample:<language>` (issue #278). */
        fun identityOf(language: String): String = "sample:$language"
    }
}

/**
 * One translation language's meanings for a sample's words, keyed by word key.
 * A key may carry several entries — one per part of speech — exactly as a
 * **Dictionary pack** answers the one word.
 */
data class SampleGlossary(val entries: Map<String, List<SampleGlossaryEntry>>) {

    /** The meanings of the word keyed [key], empty when this glossary does not know it. */
    fun entriesOf(key: String): List<SampleGlossaryEntry> = entries[key].orEmpty()
}

/** One meaning extracted from a **Dictionary pack** at build time. */
data class SampleGlossaryEntry(
    val lemma: String,
    val partOfSpeech: String?,
    val pronunciation: String?,
    val senses: List<SampleSense>,
)

/** One definition and its translations into the glossary's language. */
data class SampleSense(val definition: String, val translations: List<String>)

/** The **Sample catalogue**'s format handshake (issue #297). */
object SampleCatalog {
    /**
     * The catalogue format this reader understands, duplicated from the
     * producer's `SampleFormat.VERSION` because `tools/dictionaries` depends on
     * no Lekto module (ADR-0013). An architecture test keeps the two in step.
     */
    const val FORMAT_VERSION: Int = 1
}

/** The samples a build shipped, in a fixed order. */
data class SampleCatalogContent(val samples: List<Sample>) {
    /** The sample for [language], or `null` when this build ships none. */
    fun sampleOf(language: String): Sample? = samples.firstOrNull { sample -> sample.language == language }
}

/** What reading a **Sample catalogue**'s bytes produced. */
sealed interface SampleCatalogRead {

    /** A catalogue this build understands, as the samples it ships. */
    data class Read(val catalogue: SampleCatalogContent) : SampleCatalogRead

    /** A catalogue written in a format this build does not read; its version is [found]. */
    data class Refused(val found: Int) : SampleCatalogRead

    /** Bytes that are not a sample catalogue, or an entry this build refuses to read. */
    data object Malformed : SampleCatalogRead
}

/**
 * Reads a **Sample catalogue** from the bytes the app ships (issue #297): a pure
 * function from bytes to the samples. The handshake is strict — a catalogue whose
 * [CatalogDocument.formatVersion] is not [SampleCatalog.FORMAT_VERSION] is
 * [SampleCatalogRead.Refused], never misread — and an entry that cannot answer the
 * guide is [SampleCatalogRead.Malformed] rather than a sample with missing
 * colours: a sample with no paragraph, fewer than six maps, a blank word or a
 * meaning with no definition would show a page the product cannot explain.
 *
 * Unknown keys are tolerated at the same format version, so an additive field
 * does not break an older app; the version bump is what signals a breaking change.
 */
fun readSampleCatalog(bytes: ByteArray): SampleCatalogRead {
    val document = try {
        sampleCatalogJson.decodeFromString<CatalogDocument>(bytes.decodeToString())
    } catch (ignored: SerializationException) {
        return SampleCatalogRead.Malformed
    }
    val samples = document.samples.map(SampleDocument::toSample)
    return when {
        document.formatVersion != SampleCatalog.FORMAT_VERSION -> SampleCatalogRead.Refused(document.formatVersion)
        samples.areValid() -> SampleCatalogRead.Read(SampleCatalogContent(samples))
        else -> SampleCatalogRead.Malformed
    }
}

/** The wire shape of the catalogue, owned by the producer (`tools/dictionaries`). */
@Serializable
private data class CatalogDocument(val formatVersion: Int, val samples: List<SampleDocument> = emptyList())

@Serializable
private data class SampleDocument(
    val language: String,
    val title: String,
    val author: String? = null,
    val attribution: String? = null,
    val paragraphs: List<String> = emptyList(),
    val difficulty: Map<String, CefrLevel?> = emptyMap(),
    val forms: Map<String, String> = emptyMap(),
    val colourMaps: Map<CefrLevel, Map<String, MasteryLevel>> = emptyMap(),
    val glossaries: Map<String, GlossaryDocument> = emptyMap(),
)

@Serializable
private data class GlossaryDocument(val entries: Map<String, List<EntryDocument>> = emptyMap())

@Serializable
private data class EntryDocument(
    val lemma: String,
    val partOfSpeech: String? = null,
    val pronunciation: String? = null,
    val senses: List<SenseDocument> = emptyList(),
)

@Serializable
private data class SenseDocument(val definition: String, val translations: List<String> = emptyList())

private fun SampleDocument.toSample(): Sample = Sample(
    language = language,
    title = title,
    author = author,
    attribution = attribution,
    paragraphs = paragraphs,
    difficulty = difficulty,
    forms = forms,
    colourMaps = colourMaps,
    glossaries = glossaries.mapValues { (_, glossary) -> glossary.toGlossary() },
)

private fun GlossaryDocument.toGlossary(): SampleGlossary = SampleGlossary(
    entries = entries.mapValues { (_, glossaryEntries) ->
        glossaryEntries.map { entry ->
            SampleGlossaryEntry(
                lemma = entry.lemma,
                partOfSpeech = entry.partOfSpeech,
                pronunciation = entry.pronunciation,
                senses = entry.senses.map { sense -> SampleSense(sense.definition, sense.translations) },
            )
        }
    },
)

/**
 * Whether every sample can answer the guide: a non-blank identity and title, at
 * least one paragraph, the six CEFR maps, no blank word key and no glossary entry
 * without a definition. Duplicate languages are refused because a language's
 * sample identity must be unique.
 */
private fun List<Sample>.areValid(): Boolean {
    if (map(Sample::language).toSet().size != size) return false
    return all { sample -> isValid(sample) }
}

private fun isValid(sample: Sample): Boolean = sample.hasText() && sample.hasMaps() && sample.hasGlossaries()

private fun Sample.hasText(): Boolean = language.isNotBlank() &&
    title.isNotBlank() &&
    paragraphs.isNotEmpty() &&
    paragraphs.none(String::isBlank)

private fun Sample.hasMaps(): Boolean = difficulty.keys.none(String::isBlank) &&
    forms.keys.none(String::isBlank) &&
    forms.values.none(String::isBlank) &&
    forms.values.all { word -> word in difficulty } &&
    colourMaps.keys == CefrLevel.entries.toSet() &&
    colourMaps.values.all { map -> map.keys == difficulty.keys }

private fun Sample.hasGlossaries(): Boolean = glossaries.keys.none(String::isBlank) &&
    glossaries.values.all { glossary -> isValid(glossary) }

private fun isValid(glossary: SampleGlossary): Boolean = glossary.entries.all { (key, entries) ->
    key.isNotBlank() &&
        entries.isNotEmpty() &&
        entries.all { entry ->
            entry.lemma.isNotBlank() &&
                entry.senses.isNotEmpty() &&
                entry.senses.all { sense -> sense.definition.isNotBlank() }
        }
}

private val sampleCatalogJson: Json = Json { ignoreUnknownKeys = true }
