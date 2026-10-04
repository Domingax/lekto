package app.lekto.tools.dictionaries

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The subset of one Wiktextract JSONL record the pack keeps.
 *
 * Wiktextract emits one object per line with far more than this (etymology,
 * examples, categories, …). Only the fields the pack needs are declared;
 * everything else is skipped, so a field Wiktextract adds tomorrow does not
 * break the pipeline. Unknown keys are ignored, not rejected.
 */
@Serializable
internal data class RawEntry(
    val word: String? = null,
    @SerialName("lang_code") val langCode: String? = null,
    val pos: String? = null,
    val senses: List<RawSense> = emptyList(),
    val sounds: List<RawSound> = emptyList(),
    val translations: List<RawTranslation> = emptyList(),
    val forms: List<RawForm> = emptyList(),
    @SerialName("form_of") val formOf: List<RawFormOf> = emptyList(),
)

@Serializable
internal data class RawSense(val glosses: List<String> = emptyList())

@Serializable
internal data class RawSound(
    val ipa: String? = null,
    val audio: String? = null,
    @SerialName("ogg_url") val oggUrl: String? = null,
    @SerialName("mp3_url") val mp3Url: String? = null,
)

@Serializable
internal data class RawTranslation(
    @SerialName("lang_code") val langCode: String? = null,
    val word: String? = null,
    val sense: String? = null,
)

@Serializable
internal data class RawForm(val form: String? = null)

@Serializable
internal data class RawFormOf(val word: String? = null)

/** The JSON reader for Wiktextract lines: tolerant of the keys it does not name. */
internal val wiktextractJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}
