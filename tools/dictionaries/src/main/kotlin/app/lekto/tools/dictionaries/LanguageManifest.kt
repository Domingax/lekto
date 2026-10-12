package app.lekto.tools.dictionaries

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The **Language manifest** (issue #145): the small, versioned catalogue of what
 * the pipeline published for reading — each **Reading language** and the
 * artefacts published for it, a **Difficulty table** per reading language and a
 * **Dictionary pack** per (Reading language × **Translation language**) pair,
 * with the translation language named.
 *
 * It is a **Derived asset** the app fetches from one stable, date-free release
 * alias (`releases/latest/download/language-manifest.json`) and caches, so the
 * app never advertises data the pipeline did not publish. The producer and the
 * consumer each declare the format version; an architecture test keeps the two
 * in step (ADR-0013).
 */
@Serializable
internal data class LanguageManifest(val formatVersion: Int, val languages: List<ManifestLanguage>)

/** One **Reading language** and the artefacts published for it. */
@Serializable
internal data class ManifestLanguage(
    val language: String,
    val difficulty: ManifestArtifact? = null,
    val dictionaries: List<ManifestDictionary> = emptyList(),
)

/** A **Difficulty table** published for one reading language, named by its stable asset. */
@Serializable
internal data class ManifestArtifact(val name: String)

/**
 * A **Dictionary pack** published for one (Reading language × Translation
 * language) pair, with the translation language named and its stable asset.
 */
@Serializable
internal data class ManifestDictionary(val translationLanguage: String, val name: String)

/**
 * The manifest is published and app-facing, so it is pretty-printed. Defaults are
 * omitted, so an artefact a run did not publish is simply absent rather than a
 * `null` — the app reads a missing field as the artefact being unavailable.
 */
internal val languageManifestJson: Json = Json { prettyPrint = true }

/** The manifest's canonical JSON form: stable ordering, no timestamps, no wall clock. */
internal fun LanguageManifest.canonicalJson(): String = languageManifestJson.encodeToString(this)

/**
 * Builds the manifest from the outputs of one pipeline run: the pack it built and
 * the **Difficulty table** it built, if any. The languages come from the
 * artefacts themselves, so a run that published nothing yields an empty manifest
 * and never an invented entry.
 */
internal fun buildLanguageManifest(pack: PackContent?, difficulty: DifficultyContent?): LanguageManifest {
    val languages = LinkedHashMap<String, ManifestLanguage>()
    if (pack != null) {
        languages[PackFormat.ENGLISH] = ManifestLanguage(
            language = PackFormat.ENGLISH,
            dictionaries = listOf(
                ManifestDictionary(
                    translationLanguage = PackFormat.FRENCH,
                    name = dictionaryAssetName(PackFormat.ENGLISH, PackFormat.FRENCH),
                ),
            ),
        )
    }
    if (difficulty != null) {
        val existing = languages[DifficultyFormat.ENGLISH]
        languages[DifficultyFormat.ENGLISH] = (existing ?: ManifestLanguage(DifficultyFormat.ENGLISH)).copy(
            difficulty = ManifestArtifact(difficultyAssetName(DifficultyFormat.ENGLISH)),
        )
    }
    return LanguageManifest(LanguageManifestFormat.VERSION, languages.values.toList())
}

/** The date-free asset the release publishes a difficulty table under (matching the workflow alias). */
internal fun difficultyAssetName(language: String): String = "lekto-difficulty-$language.sqlite.gz"

/** The date-free asset the release publishes a dictionary pack under (matching the workflow alias). */
internal fun dictionaryAssetName(readingLanguage: String, translationLanguage: String): String =
    "lekto-dictionary-$readingLanguage-$translationLanguage.sqlite.gz"
