package app.lekto.testkit

import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.LanguageManifest
import app.lekto.core.language.ManifestDownloader
import app.lekto.core.language.OfferedDictionary
import app.lekto.core.language.OfferedLanguage
import kotlinx.serialization.json.JsonArray
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

    /** When set, a [reload] lands on it and [status] then reports it; used to test a fetch. */
    var onReload: LanguageCatalogState? = null

    /** How many reloads have been attempted. */
    var reloads: Int = 0
        private set

    override fun status(): LanguageCatalogState = current

    override fun reload(): LanguageCatalogState {
        reloads++
        failure?.let { throw it }
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
                    offered.difficulty?.let { name -> put("difficulty", buildJsonObject { put("name", name) }) }
                    put(
                        "dictionaries",
                        buildJsonArray {
                            offered.dictionaries.forEach { dictionary ->
                                add(
                                    buildJsonObject {
                                        put("translationLanguage", dictionary.translationLanguage)
                                        put("name", dictionary.name)
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

/** One offered language, with a display name the manifest's tag implies. */
fun testOfferedLanguage(
    language: String,
    difficulty: String? = null,
    dictionaries: List<OfferedDictionary> = emptyList(),
): OfferedLanguage = OfferedLanguage(
    language = language,
    displayName = app.lekto.core.language.readingLanguageName(language),
    difficulty = difficulty,
    dictionaries = dictionaries,
)
