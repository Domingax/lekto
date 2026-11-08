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
    val difficulty: OfferedArtifact?,
    val dictionaries: List<OfferedDictionary>,
)

/**
 * A **Derived asset** the manifest published (issue #305): the address the app
 * follows, and the digest that proves the bytes behind it are the ones the
 * manifest measured. [name] stays the asset's identity — the manifest and the
 * surface key their state by it — while [url] is the only address the app
 * fetches, so the app never composes one from the name (ADR-0047).
 */
data class OfferedArtifact(val name: String, val url: String, val sha256: String, val sizeBytes: Long? = null)

/** A **Dictionary pack** offered for a (Reading language × **Translation language**) pair. */
data class OfferedDictionary(val translationLanguage: String, val artifact: OfferedArtifact)

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
 * Every artefact must name its own address and digest: a manifest that omits
 * either is malformed rather than silently fetched by a composed URL (issue #305,
 * ADR-0047).
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

/** A published artefact as the manifest names it: its name, address, digest and optional size. */
@Serializable
private data class ManifestArtifact(val name: String, val url: String, val sha256: String, val sizeBytes: Long? = null)

@Serializable
private data class ManifestDictionary(
    val translationLanguage: String,
    val name: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long? = null,
)

private fun ManifestLanguage.toOffered(): OfferedLanguage = OfferedLanguage(
    language = language,
    displayName = readingLanguageName(language),
    difficulty = difficulty?.toOffered(),
    dictionaries = dictionaries.map(ManifestDictionary::toOffered),
)

private fun ManifestArtifact.toOffered(): OfferedArtifact = OfferedArtifact(name, url, sha256, sizeBytes)

private fun ManifestDictionary.toOffered(): OfferedDictionary = OfferedDictionary(
    translationLanguage = translationLanguage,
    artifact = OfferedArtifact(name, url, sha256, sizeBytes),
)

private val manifestJson: Json = Json { ignoreUnknownKeys = true }
