@file:Suppress("MagicNumber") // Column indices into a fixed SELECT list are positional; naming each adds nothing.

package app.lekto.core.dictionary

/**
 * The [DictionaryPack] over any [PackDatabase]: the SQL and the row mapping live
 * here, once, so the two platforms' executors cannot drift in what they return.
 *
 * The queries mirror the producer's schema (`tools/dictionaries/SqlitePackWriter`)
 * and are read-only: a lemma's entries, an entry's senses and translations, and
 * the reverse surface→lemma index.
 */
class SqlDictionaryPack(private val database: PackDatabase) :
    DictionaryPack,
    AutoCloseable by database {

    override val metadata: PackMetadata = PackMetadata.from(database.formatVersion, readMetadata())

    override fun entries(language: String, lemma: String): List<DictionaryEntry> =
        database.rows(ENTRIES, listOf(language, lemma)).map(::entry)

    override fun lemmaOf(surface: String, language: String): String? =
        database.rows(LEMMA, listOf(surface, language)).firstOrNull()?.firstOrNull()

    private fun readMetadata(): Map<String, String> =
        database.rows(METADATA, emptyList()).associate { row -> row[0].orEmpty() to row[1].orEmpty() }

    private fun entry(row: PackRow): DictionaryEntry = DictionaryEntry(
        lemma = row[1].orEmpty(),
        language = row[2].orEmpty(),
        partOfSpeech = row[3],
        pronunciation = row[4],
        audioUrl = row[5],
        senses = senses(row[0].orEmpty()),
    )

    private fun senses(entryId: String): List<DictionarySense> = database.rows(SENSES, listOf(entryId)).map { row ->
        DictionarySense(row[1].orEmpty(), translations(entryId, row[0].orEmpty()))
    }

    private fun translations(entryId: String, senseOrd: String): List<String> =
        database.rows(TRANSLATIONS, listOf(entryId, senseOrd)).mapNotNull { row -> row[0] }

    private companion object {
        const val METADATA = "SELECT key, value FROM pack_metadata"
        const val ENTRIES =
            "SELECT id, lemma, language, part_of_speech, pronunciation, audio_url " +
                "FROM entry WHERE language = ? AND lemma = ? ORDER BY id"
        const val SENSES = "SELECT ord, definition FROM sense WHERE entry_id = ? ORDER BY ord"
        const val TRANSLATIONS =
            "SELECT text FROM translation WHERE entry_id = ? AND sense_ord = ? ORDER BY ord"
        const val LEMMA = "SELECT lemma FROM form WHERE surface = ? AND language = ?"
    }
}
