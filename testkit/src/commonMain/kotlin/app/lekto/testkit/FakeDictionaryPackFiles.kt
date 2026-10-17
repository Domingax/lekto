package app.lekto.testkit

import app.lekto.core.dictionary.DictionaryEntry
import app.lekto.core.dictionary.DictionaryPack
import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.DictionaryPackMissing
import app.lekto.core.dictionary.DictionarySense
import app.lekto.core.dictionary.PackDatabase
import app.lekto.core.dictionary.PackDatabaseFactory
import app.lekto.core.dictionary.PackDownloader
import app.lekto.core.dictionary.PackRow
import app.lekto.core.vault.DerivedAssetStore

/**
 * A [PackDatabase] held in memory: the installer's state machine and the reader's
 * queries are proved without a SQLite file, while the real reader is proved
 * against a built pack in `jvmTest`. It answers the version and the
 * `pack_metadata` rows the installer inspects, and — since the **Vocabulary**
 * list derives its translation from the pack (issue #67; ADR-0032) — the four
 * read-only queries a word lookup makes, over the same [testEntries] and
 * [testLemmas] fixture the committed pack is built from, so a test that installs
 * this fake can resolve a word the way the real pack would.
 */
class FakePackDatabase(
    override val formatVersion: Int = DictionaryPack.FORMAT_VERSION,
    private val values: Map<String, String> = testPackMetadataValues(),
    entries: Map<Pair<String, String>, List<DictionaryEntry>> = testEntries(),
    private val lemmas: Map<Pair<String, String>, String> = testLemmas(),
) : PackDatabase {

    /** The `entry` rows per `(language, lemma)`, each carrying the id its senses join back on. */
    private val entriesByKey: Map<Pair<String, String>, List<PackRow>> = entries.mapValues { (key, words) ->
        words.mapIndexed { index, entry ->
            listOf(
                idOf(key.first, key.second, index),
                entry.lemma,
                entry.language,
                entry.partOfSpeech,
                entry.pronunciation,
                entry.audioUrl,
            )
        }
    }

    /** Each entry id's senses, in order, so the `sense` and `translation` queries can join. */
    private val sensesById: Map<String, List<DictionarySense>> = entries.flatMap { (key, words) ->
        words.mapIndexed { index, entry -> idOf(key.first, key.second, index) to entry.senses }
    }.toMap()

    override fun rows(sql: String, args: List<String>): List<PackRow> = when {
        sql.contains("pack_metadata") -> values.map { (key, value) -> listOf(key, value) }
        sql.contains("FROM entry") -> pair(args)?.let(entriesByKey::get).orEmpty()
        sql.contains("FROM sense") -> senseRows(args.getOrNull(0))
        sql.contains("FROM translation") -> translationRows(args.getOrNull(0), args.getOrNull(1))
        sql.contains("FROM form") -> lemmaRows(args)
        else -> emptyList()
    }

    override fun close() = Unit

    /** The `sense` rows for [entryId], ordered by their position. */
    private fun senseRows(entryId: String?): List<PackRow> = entryId?.let(sensesById::get).orEmpty()
        .mapIndexed { ord, sense -> listOf(ord.toString(), sense.definition) }

    /** The `translation` rows for [entryId]'s sense [ord], in order. */
    private fun translationRows(entryId: String?, ord: String?): List<PackRow> {
        val sense = ord?.toIntOrNull()?.let { index -> entryId?.let(sensesById::get)?.getOrNull(index) }
        return sense?.translations.orEmpty().map { text -> listOf(text) }
    }

    /**
     * The `form` row for the `(surface, language)` arguments the query passes in
     * that order, or none when the surface is not an inflection. The fixture is
     * keyed `(language, surface)`, as the entry query is, so the pair is swapped
     * to match.
     */
    private fun lemmaRows(args: List<String>): List<PackRow> {
        val surface = args.getOrNull(0)
        val language = args.getOrNull(1)
        val lemma = if (surface == null || language == null) null else lemmas[language to surface]
        return listOfNotNull(lemma).map { value -> listOf(value) }
    }

    /** The query's first two arguments as a pair, or `null` when the query passed fewer. */
    private fun pair(args: List<String>): Pair<String, String>? = if (args.size >= 2) args[0] to args[1] else null

    private fun idOf(language: String, lemma: String, index: Int): String = "$language\u0000$lemma\u0000$index"
}

/**
 * The platform pack I/O as a fake: a download writes a marker through the
 * [derived] store and the factory opens whatever version was configured, so an
 * installer test can drive download, refusal and removal without a network or a
 * real SQLite file. It is keyed by the (Reading language × **Translation
 * language**) pair the installer serves (issue #149).
 */
class FakeDictionaryPackFiles(
    private val derived: DerivedAssetStore,
    val readingLanguage: String = "en",
    val translationLanguage: String = "fr",
    var version: Int = DictionaryPack.FORMAT_VERSION,
) : PackDownloader,
    PackDatabaseFactory {

    /** When set, the next download fails with it; used to test graceful failure. */
    var failure: Exception? = null

    /** When set, opening the pack throws it; used to test a corrupt pack. */
    var openFailure: Exception? = null

    /** How many downloads have been attempted. */
    var downloads: Int = 0
        private set

    override fun download(url: String, destination: String) {
        failure?.let { throw it }
        downloads++
        derived.put(DictionaryPackInstaller.pathOf(readingLanguage, translationLanguage), PACK_MARKER)
    }

    override fun open(path: String): PackDatabase {
        openFailure?.let { throw it }
        if (derived.get(DictionaryPackInstaller.pathOf(readingLanguage, translationLanguage)) == null) {
            throw DictionaryPackMissing()
        }
        return FakePackDatabase(version)
    }

    private companion object {
        /** Distinctive bytes, so a test can prove the pack never reaches the vault's export. */
        val PACK_MARKER = "dictionary-pack-bytes".encodeToByteArray()
    }
}
