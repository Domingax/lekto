package app.lekto.tools.dictionaries

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One definition and the French translations attached to it, in Wiktionary order. */
@Serializable
internal data class PackSense(val definition: String, val translations: List<String>)

/** One dictionary entry, keyed by `(language, lemma, partOfSpeech)`. */
@Serializable
internal data class PackEntry(
    val lemma: String,
    val language: String,
    val partOfSpeech: String? = null,
    val pronunciation: String? = null,
    val audioUrl: String? = null,
    val senses: List<PackSense> = emptyList(),
)

/** One surface form and the canonical lemma it resolves to, keyed `(surface, language)`. */
@Serializable
internal data class PackForm(val surface: String, val language: String, val lemma: String)

/** A French translation word and the Wiktextract sense label it was attached to. */
internal data class FrenchTranslation(val word: String, val sense: String?)

/** Where the pack's input came from, recorded for provenance (never a timestamp). */
@Serializable
internal data class PackProvenance(val sourceUrl: String, val sourceSha256: String, val extractionDate: String)

/** The measurable shape of one build, recorded in the manifest and the sanity gate. */
@Serializable
internal data class PackStats(
    val totalLines: Long,
    val malformedLines: Long,
    val englishEntries: Long,
    val englishEntriesWithFrench: Long,
    val lemmaCount: Int,
    val entryCount: Int,
    val formCount: Int,
    val senseCount: Int,
    val translationCount: Int,
)

/**
 * The in-memory pack: the deterministic output of one transform pass. The SQLite
 * writer serialises it; the golden test pins its canonical JSON.
 */
@Serializable
internal data class PackContent(
    val provenance: PackProvenance,
    val stats: PackStats,
    val entries: List<PackEntry>,
    val forms: List<PackForm>,
)

/** The canonical, non-pretty JSON the golden test compares against. */
internal val packJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** The pack's canonical JSON form: stable ordering, no timestamps, no wall clock. */
internal fun PackContent.canonicalJson(): String = packJson.encodeToString(this)

/** The release manifest: provenance and the measured shape, without the pack's bulk. */
@Serializable
internal data class PackManifest(val formatVersion: Int, val provenance: PackProvenance, val stats: PackStats)

/** The manifest is human-facing in release notes, so it is pretty-printed. */
internal val manifestJson: Json = Json {
    encodeDefaults = true
    prettyPrint = true
}
