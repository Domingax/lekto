package app.lekto.core.language

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * One **Reading language** the **Language manifest** offers (issue #146), with
 * the **Derived asset**s the pipeline published for it: an optional **Difficulty
 * table** and the **Dictionary pack**s keyed by their (Reading language ×
 * **Translation language**) pair.
 *
 * [displayName] is the name the surface shows — the language's English name when
 * this build knows it, otherwise the tag — so the ordering the ticket asks for
 * ("alphabetically by display name") is a decision the domain owns rather than
 * each screen.
 */
data class OfferedLanguage(
    val language: String,
    val displayName: String,
    val difficulty: String?,
    val dictionaries: List<OfferedDictionary>,
)

/** A **Dictionary pack** offered for a (Reading language × **Translation language**) pair. */
data class OfferedDictionary(val translationLanguage: String, val name: String)

/** What reading a **Language manifest**'s bytes produced. */
sealed interface LanguageManifestRead {

    /** A manifest this build understands, as the languages it offers. */
    data class Read(val languages: List<OfferedLanguage>) : LanguageManifestRead

    /** A manifest written in a format this build does not read; its version is [found]. */
    data class Refused(val found: Int) : LanguageManifestRead

    /** Bytes that are not a manifest at all. */
    data object Malformed : LanguageManifestRead
}

/**
 * Reads a **Language manifest** from its published bytes (issue #146): a pure
 * function from bytes to a catalogue. The handshake is strict — a manifest whose
 * [ManifestDocument.formatVersion] is not [LanguageManifest.FORMAT_VERSION] is
 * [LanguageManifestRead.Refused], never misread as an older or newer shape.
 *
 * Unknown keys are tolerated so an additive field at the same format version does
 * not break an older app; the version bump is what signals a breaking change.
 */
fun readLanguageManifest(bytes: ByteArray): LanguageManifestRead {
    val document = try {
        manifestJson.decodeFromString<ManifestDocument>(bytes.decodeToString())
    } catch (ignored: SerializationException) {
        return LanguageManifestRead.Malformed
    }
    return if (document.formatVersion == LanguageManifest.FORMAT_VERSION) {
        LanguageManifestRead.Read(document.languages.map(ManifestLanguage::toOffered))
    } else {
        LanguageManifestRead.Refused(document.formatVersion)
    }
}

/** The wire shape of the manifest, owned by the producer (`tools/dictionaries`). */
@Serializable
private data class ManifestDocument(val formatVersion: Int, val languages: List<ManifestLanguage> = emptyList())

@Serializable
private data class ManifestLanguage(
    val language: String,
    val difficulty: ManifestArtifact? = null,
    val dictionaries: List<ManifestDictionary> = emptyList(),
)

@Serializable
private data class ManifestArtifact(val name: String)

@Serializable
private data class ManifestDictionary(val translationLanguage: String, val name: String)

private fun ManifestLanguage.toOffered(): OfferedLanguage = OfferedLanguage(
    language = language,
    displayName = readingLanguageName(language),
    difficulty = difficulty?.name,
    dictionaries = dictionaries.map { dictionary ->
        OfferedDictionary(dictionary.translationLanguage, dictionary.name)
    },
)

private val manifestJson: Json = Json { ignoreUnknownKeys = true }
