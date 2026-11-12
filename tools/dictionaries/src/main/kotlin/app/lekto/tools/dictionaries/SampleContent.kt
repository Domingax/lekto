package app.lekto.tools.dictionaries

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The **Sample catalogue**'s on-disk contract, owned by the producer (this
 * module).
 *
 * The number is what the reader's strict handshake compares against
 * (`SampleCatalog.FORMAT_VERSION` in `core`, issue #297). `tools/dictionaries`
 * is standalone and cannot depend on `core`, so the constant is duplicated there
 * and the two are guarded by an architecture test (ADR-0013).
 */
object SampleFormat {
    /** The sample format this build writes. Bump on any change to the schema. */
    const val VERSION: Int = 1
}

/** One short text authored for the guide, before its colours and meanings are computed. */
@Serializable
internal data class SampleSource(
    val language: String,
    val title: String,
    val author: String? = null,
    val attribution: String? = null,
    val paragraphs: List<String>,
)

/** One sample's colour map: the mastery level each word key takes for one reader band. */
internal typealias ColourMap = Map<String, String>

/** One sense of a sample glossary entry. */
@Serializable
internal data class SampleGlossarySense(val definition: String, val translations: List<String> = emptyList())

/** One word's meaning, extracted from a **Dictionary pack**. */
@Serializable
internal data class SampleGlossaryEntry(
    val lemma: String,
    val partOfSpeech: String? = null,
    val pronunciation: String? = null,
    val senses: List<SampleGlossarySense>,
)

/** One translation language's meanings for a sample's words, keyed by word key. */
@Serializable
internal data class SampleGlossary(val entries: Map<String, List<SampleGlossaryEntry>>)

/** One sample as the build emits it. */
@Serializable
internal data class SampleContent(
    val language: String,
    val title: String,
    val author: String? = null,
    val attribution: String? = null,
    val paragraphs: List<String>,
    val difficulty: Map<String, String?>,
    val forms: Map<String, String>,
    val colourMaps: Map<String, ColourMap>,
    val glossaries: Map<String, SampleGlossary>,
)

/** The catalogue the build writes: one sample per language it found a text for. */
@Serializable
internal data class SampleCatalogueDocument(val formatVersion: Int, val samples: List<SampleContent>)

/** The catalogue is app-facing and committed, so it is pretty-printed but deterministic. */
internal val sampleCatalogJson: Json = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

/** The catalogue's canonical JSON form: stable ordering, no timestamps, no wall clock. */
internal fun SampleCatalogueDocument.canonicalJson(): String = sampleCatalogJson.encodeToString(this)

/** Reads an authored sample text from its JSON source. */
internal fun readSampleSource(bytes: ByteArray): SampleSource =
    sampleCatalogJson.decodeFromString(bytes.decodeToString())
