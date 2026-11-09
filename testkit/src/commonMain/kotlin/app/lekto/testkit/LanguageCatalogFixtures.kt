package app.lekto.testkit

import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.LanguageManifest
import app.lekto.core.language.ManifestDownloader
import app.lekto.core.language.OfferedArtifact
import app.lekto.core.language.OfferedDictionary
import app.lekto.core.language.OfferedLanguage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The **Language manifest** as an in-memory [LanguageCatalog] (issue #146): the
 * app's surface is proved without a network or a derived store, and a test can
 * drive the reload — what it lands on, whether it fails, and how many times it
 * ran. It is the fake the ticket names for the catalogue seam.
 */
class InMemoryLanguageCatalog(private var current: LanguageCatalogState = LanguageCatalogState.Unavailable) :
    LanguageCatalog {

    /** When set, the next [reload] fails with it; used to test the honest offline state. */
    var failure: Exception? = null

    /**
     * When set, [reload] runs it once its outcome is decided and before it reports
     * it: the seam a test holds a fetch in flight with, so the window where the
     * surface renders while a fetch runs is proved rather than raced.
     */
    var whileInFlight: (() -> Unit)? = null

    /** When set, a [reload] lands on it and [status] then reports it; used to test a fetch. */
    var onReload: LanguageCatalogState? = null

    /** How many reloads have been attempted. */
    var reloads: Int = 0
        private set

    override fun status(): LanguageCatalogState = current

    override fun reload(): LanguageCatalogState {
        reloads++
        val decided = failure
        whileInFlight?.invoke()
        decided?.let { throw it }
        onReload?.let { landed -> current = landed }
        return current
    }
}

/**
 * The manifest downloader as a fake: it serves [bytes] to the catalogue without a
 * network, and counts its calls, so the core lifecycle is proved against the same
 * seam the platform implements.
 */
class FakeManifestDownloader(var bytes: ByteArray = testLanguageManifest(emptyList())) : ManifestDownloader {

    /** When set, the next download fails with it. */
    var failure: Exception? = null

    /** How many downloads have been attempted. */
    var downloads: Int = 0
        private set

    override fun download(url: String): ByteArray {
        failure?.let { throw it }
        downloads++
        return bytes
    }
}

/**
 * The producer-shaped bytes of a **Language manifest** offering [languages].
 * [formatVersion] defaults to the handshake this build reads; a test passes a
 * different one to prove a manifest this build cannot read is refused.
 */
fun testLanguageManifest(
    languages: List<OfferedLanguage>,
    formatVersion: Int = LanguageManifest.FORMAT_VERSION,
): ByteArray = buildJsonObject {
    put("formatVersion", JsonPrimitive(formatVersion))
    put(
        "languages",
        JsonArray(
            languages.map { offered ->
                buildJsonObject {
                    put("language", offered.language)
                    offered.difficulty?.let { artifact ->
                        put("difficulty", artifact.toJson())
                    }
                    put(
                        "dictionaries",
                        buildJsonArray {
                            offered.dictionaries.forEach { dictionary ->
                                add(
                                    buildJsonObject {
                                        put("translationLanguage", dictionary.translationLanguage)
                                        putArtifact(dictionary.artifact)
                                    },
                                )
                            }
                        },
                    )
                }
            },
        ),
    )
}.toString().encodeToByteArray()

/** One published artefact as the manifest names it: its name, address, digest and optional size. */
fun testOfferedArtifact(
    name: String,
    url: String = "https://data.test/$name",
    sha256: String = TEST_SHA256,
    sizeBytes: Long? = null,
): OfferedArtifact = OfferedArtifact(name, url, sha256, sizeBytes)

/** The digest the fakes report, so a test can fetch an artefact the manifest vouches for. */
const val TEST_SHA256: String = "0000000000000000000000000000000000000000000000000000000000000000"

/** A digest no fixture asset has, so a test can prove a download whose bytes do not match is refused. */
const val MISMATCHED_SHA256: String = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

private fun JsonObjectBuilder.putArtifact(artifact: OfferedArtifact) {
    put("name", artifact.name)
    put("url", artifact.url)
    put("sha256", artifact.sha256)
    artifact.sizeBytes?.let { bytes -> put("sizeBytes", bytes) }
}

private fun OfferedArtifact.toJson(): JsonObject = buildJsonObject { putArtifact(this@toJson) }

/** A published **Dictionary pack** as the manifest names it, with a fetchable test address and digest. */
@Suppress("LongParameterList") // One fixture mirroring the manifest entry's fields, each independently overridable.
fun testOfferedDictionary(
    translationLanguage: String,
    name: String,
    url: String = "https://data.test/$name",
    sha256: String = TEST_SHA256,
    sizeBytes: Long? = null,
): OfferedDictionary = OfferedDictionary(
    translationLanguage = translationLanguage,
    artifact = testOfferedArtifact(name, url, sha256, sizeBytes),
)

/** One offered language, with a display name the manifest's tag implies. */
fun testOfferedLanguage(
    language: String,
    difficulty: OfferedArtifact? = null,
    dictionaries: List<OfferedDictionary> = emptyList(),
): OfferedLanguage = OfferedLanguage(
    language = language,
    displayName = app.lekto.core.language.readingLanguageName(language),
    difficulty = difficulty,
    dictionaries = dictionaries,
)
