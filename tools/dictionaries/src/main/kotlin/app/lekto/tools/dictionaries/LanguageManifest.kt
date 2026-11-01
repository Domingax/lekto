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
internal data class ManifestArtifact(val name: String, val sizeBytes: Long? = null)

/**
 * A **Dictionary pack** published for one (Reading language × Translation
 * language) pair, with the translation language named and its stable asset.
 */
@Serializable
internal data class ManifestDictionary(val translationLanguage: String, val name: String, val sizeBytes: Long? = null)

/**
 * The manifest is published and app-facing, so it is pretty-printed. Defaults are
 * omitted, so an artefact a run did not publish is simply absent rather than a
 * `null` — the app reads a missing field as the artefact being unavailable.
 */
internal val languageManifestJson: Json = Json { prettyPrint = true }

/** The manifest's canonical JSON form: stable ordering, no timestamps, no wall clock. */
internal fun LanguageManifest.canonicalJson(): String = languageManifestJson.encodeToString(this)

/**
 * Builds the manifest from the outputs of one pipeline run: the packs (by
 * [LanguagePair]) and the **Difficulty table**s (by reading language) it built.
 * The languages come from the artefacts themselves, so a run that published
 * nothing yields an empty manifest and never an invented entry; a reading
 * language with several packs carries them all (issue #161).
 */
internal fun buildLanguageManifest(dictionaries: List<LanguagePair>, difficulties: List<String>): LanguageManifest {
    val languages = LinkedHashMap<String, ManifestLanguage>()
    dictionaries.forEach { pair ->
        val existing = languages[pair.reading]
        languages[pair.reading] = (existing ?: ManifestLanguage(pair.reading)).copy(
            dictionaries = existing?.dictionaries.orEmpty() +
                ManifestDictionary(
                    translationLanguage = pair.translation,
                    name = dictionaryAssetName(pair.reading, pair.translation),
                ),
        )
    }
    difficulties.forEach { language ->
        val existing = languages[language]
        languages[language] = (existing ?: ManifestLanguage(language)).copy(
            difficulty = ManifestArtifact(difficultyAssetName(language)),
        )
    }
    return LanguageManifest(LanguageManifestFormat.VERSION, languages.values.toList())
}

/** The date-free asset stem the release publishes a difficulty table under (matching the workflow alias). */
internal fun difficultyStem(language: String): String = "lekto-difficulty-$language"

/** The date-free asset stem the release publishes a dictionary pack under (matching the workflow alias). */
internal fun dictionaryStem(readingLanguage: String, translationLanguage: String): String =
    "lekto-dictionary-$readingLanguage-$translationLanguage"

/** The date-free gzipped asset the release publishes a difficulty table under. */
internal fun difficultyAssetName(language: String): String = "${difficultyStem(language)}.sqlite.gz"

/** The date-free gzipped asset the release publishes a dictionary pack under. */
internal fun dictionaryAssetName(readingLanguage: String, translationLanguage: String): String =
    "${dictionaryStem(readingLanguage, translationLanguage)}.sqlite.gz"
