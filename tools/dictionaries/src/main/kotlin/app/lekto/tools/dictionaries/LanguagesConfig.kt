package app.lekto.tools.dictionaries

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One (Reading language × Translation language) pair, as the language set and the manifest name it. */
@Serializable
internal data class LanguagePair(val reading: String, val translation: String)

/**
 * The committed, producer-side language set of what the pipeline builds (issue
 * #161): the packs, by (Reading language × Translation language) pair, and the
 * **Difficulty table** per reading language. One run builds exactly this set into
 * one release, so the app is never told about data the run did not publish. It is
 * the producer's input; the app reads the **Language manifest** a run derives from
 * it, never this file (CONTEXT.md, "Language manifest").
 */
@Serializable
internal data class LanguagesConfig(
    val dictionaries: List<LanguagePair> = emptyList(),
    val difficulties: List<String> = emptyList(),
)

internal val languagesConfigJson: Json = Json { ignoreUnknownKeys = true }

/** Reads the committed language set from its bytes. */
internal fun readLanguagesConfig(bytes: ByteArray): LanguagesConfig =
    languagesConfigJson.decodeFromString(bytes.decodeToString())
