@file:Suppress("MagicNumber") // Column indices into a fixed SELECT list are positional; naming each adds nothing.

package app.lekto.core.difficulty

import app.lekto.core.CefrLevel
import app.lekto.core.dictionary.PackDatabase
import app.lekto.core.text.WordKey
import app.lekto.core.text.baseLanguage

/**
 * The [WordDifficulty] over any [PackDatabase]: the SQL and the row mapping live
 * here, once, so the two platforms' executors cannot drift in what they answer.
 *
 * The table is small, so it is read into memory **once** on open rather than
 * queried per word — the reader asks for a band for every **Word token** while
 * paginating (ADR-0029). Rows are keyed by `(language, key)`, the key being the
 * same identity the tokeniser computes (ADR-0006), so difficulty and mastery
 * never disagree on a word's identity.
 */
class SqlWordDifficulty(private val database: PackDatabase) :
    WordDifficulty,
    AutoCloseable by database {

    override val metadata: DifficultyMetadata =
        DifficultyMetadata.from(database.formatVersion, readMetadata())

    private val bands: Map<Pair<String, String>, CefrLevel> = load()

    override fun levelOf(key: WordKey): CefrLevel? = key.language
        ?.let(::baseLanguage)
        ?.let { language -> bands[language to key.key] }

    private fun load(): Map<Pair<String, String>, CefrLevel> {
        val bands = mutableMapOf<Pair<String, String>, CefrLevel>()
        database.rows(BANDS, emptyList()).forEach { row ->
            val word = row.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@forEach
            val language = row.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@forEach
            val band = row.getOrNull(2)?.let(::cefrLevel) ?: return@forEach
            bands[language to word] = band
        }
        return bands
    }

    private fun readMetadata(): Map<String, String> =
        database.rows(METADATA, emptyList()).associate { row -> row[0].orEmpty() to row[1].orEmpty() }

    private fun cefrLevel(name: String): CefrLevel? = CefrLevel.entries.firstOrNull { level -> level.name == name }

    private companion object {
        const val METADATA = "SELECT key, value FROM difficulty_metadata"
        const val BANDS = "SELECT word, language, band FROM difficulty"
    }
}
