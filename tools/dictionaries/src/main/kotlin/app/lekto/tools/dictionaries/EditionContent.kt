package app.lekto.tools.dictionaries

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The on-disk contract of the intermediate the extraction mode writes (ADR-0046).
 *
 * It is a workflow artefact, never published: stage one writes it once per
 * **Reading language**, and stage two turns one content file and the shared index
 * into that pair's **Dictionary pack**. Bump on any change to either schema.
 */
object EditionFormat {
    const val VERSION: Int = 1
}

/**
 * One **Reading language**'s surface→lemma table, built once from the edition's
 * entries that carry at least one translation into a configured target and shared
 * by every pack of that language (ADR-0045). [translationLanguages] records the
 * target set the table was built for, so stage two can refuse an index that does
 * not cover the pair it is writing.
 */
@Serializable
internal data class FormIndex(
    val formatVersion: Int,
    val provenance: PackProvenance,
    val readingLanguage: String,
    val translationLanguages: List<String>,
    val forms: List<PackForm>,
)

/** The stream's counts a pack manifest records; the rest are derived from the content's entries. */
@Serializable
internal data class EditionStats(
    val totalLines: Long,
    val malformedLines: Long,
    val readingEntries: Long,
    val entriesWithTranslation: Long,
)

/**
 * One Translation language's content file: the words, senses and translations its
 * pack will hold, in the pack's fixed order (ADR-0046). The edition's provenance
 * travels with it, so the pack it becomes records the source it came from rather
 * than a flag passed when it is written.
 */
@Serializable
internal data class EditionContent(
    val formatVersion: Int,
    val provenance: PackProvenance,
    val readingLanguage: String,
    val translationLanguage: String,
    val stats: EditionStats,
    val entries: List<PackEntry>,
)

/** The canonical, non-pretty JSON of one intermediate file: stable ordering, no wall clock. */
internal val editionJson: Json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal fun FormIndex.canonicalJson(): String = editionJson.encodeToString(this)

internal fun EditionContent.canonicalJson(): String = editionJson.encodeToString(this)

/** The form index file's fixed name inside the extraction's output directory. */
internal fun formIndexFileName(readingLanguage: String): String = "form-index-$readingLanguage.json"

/** The content file's fixed name for one pair inside the extraction's output directory. */
internal fun contentFileName(readingLanguage: String, translationLanguage: String): String =
    "content-$readingLanguage-$translationLanguage.json"
