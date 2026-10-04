package app.lekto.testkit

import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryPack
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.PackMetadata

/**
 * The dictionary words every [DictionaryPack] test knows: "blorple" and its
 * inflected "blorpled", matching the committed SQLite fixture the pack pipeline
 * produces from `tools/dictionaries/src/test/resources/en-fr-sample.jsonl`.
 */
val BLORPLE: DictionaryEntry = DictionaryEntry(
    lemma = "blorple",
    language = "en",
    partOfSpeech = "verb",
    pronunciation = "/ˈblɔːpəl/",
    audioUrl = null,
    senses = listOf(
        DictionarySense("To move swiftly.", listOf("blorper")),
        DictionarySense("To flow.", listOf("fluxer")),
        DictionarySense("A point scored.", listOf("blorpoint")),
    ),
)

/** The pack metadata a test build carries, matching the producer's attribution rows. */
fun testPackMetadata(formatVersion: Int = DictionaryPack.FORMAT_VERSION): PackMetadata =
    PackMetadata.from(formatVersion, testPackMetadataValues())

/** The raw `pack_metadata` rows, for a fake [app.lekto.core.dictionary.PackDatabase]. */
fun testPackMetadataValues(): Map<String, String> = mapOf(
    "format_version" to DictionaryPack.FORMAT_VERSION.toString(),
    "attribution" to "Wiktionary contributors",
    "license" to "CC BY-SA 4.0",
    "license_url" to "https://creativecommons.org/licenses/by-sa/4.0/",
    "source" to "https://en.wiktionary.org/",
    "source_data" to "Wiktextract / Kaikki machine-readable extract",
    "modifications" to "Extracted, trimmed, normalised and re-formatted as a read-only SQLite database.",
    "notice" to "Derived from Wiktionary contributors, CC BY-SA 4.0.",
)

/** The entries the in-memory pack returns, keyed `(language, lemma)`. */
internal fun testEntries(): Map<Pair<String, String>, List<DictionaryEntry>> =
    mapOf(("en" to "blorple") to listOf(BLORPLE))

/** The reverse surface→lemma index the in-memory pack returns, keyed `(language, surface)`. */
internal fun testLemmas(): Map<Pair<String, String>, String> = mapOf(("en" to "blorpled") to "blorple")
