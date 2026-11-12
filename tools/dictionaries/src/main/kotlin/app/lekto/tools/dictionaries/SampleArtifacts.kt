@file:Suppress("MagicNumber") // JDBC column indices are positional, as in the writer.

package app.lekto.tools.dictionaries

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Reads the built artefacts a sample is derived from (issue #297): the reading
 * language's **Difficulty table** and the **Dictionary pack**s of its
 * (Reading language × Translation language) pairs.
 *
 * The build regenerates a sample from a published data release, so it reads the
 * same SQLite files the app installs rather than a workflow-only intermediate.
 * The form index is shared by every pack of a language (ADR-0045), so the first
 * pack's `form` table is the one key rule; a language with no pack is keyed by
 * the normalised surface form, exactly as its difficulty table is (ADR-0029).
 */
internal fun readDifficultyTable(file: Path): Map<String, String> = open(file) { connection ->
    connection.query("SELECT word, band FROM difficulty") { rows -> rows.getString(1) to rows.getString(2) }.toMap()
}

/** One pack's `form` table: the surface→lemma index every pack of the language shares. */
internal fun readFormTable(file: Path): Map<String, String> = open(file) { connection ->
    connection.query("SELECT surface, lemma FROM form") { rows -> rows.getString(1) to rows.getString(2) }.toMap()
}

/** One pack's usable entries, with their senses and translations, in the pack's order. */
internal fun readPackEntries(file: Path): List<PackEntry> = open(file) { connection ->
    val senses = readSenses(connection)
    val translations = readTranslations(connection)
    connection.query(
        "SELECT id, language, lemma, part_of_speech, pronunciation, audio_url FROM entry ORDER BY id",
    ) { rows ->
        val id = rows.getLong(1)
        PackEntry(
            lemma = rows.getString(3),
            language = rows.getString(2),
            partOfSpeech = rows.getString(4),
            pronunciation = rows.getString(5),
            audioUrl = rows.getString(6),
            senses = senses[id].orEmpty().map { sense ->
                PackSense(sense.definition, translations[id to sense.ord].orEmpty())
            },
        )
    }
}

private data class SenseRow(val ord: Int, val definition: String)

private fun readSenses(connection: Connection): Map<Long, List<SenseRow>> =
    connection.query("SELECT entry_id, ord, definition FROM sense ORDER BY entry_id, ord") { rows ->
        rows.getLong(1) to SenseRow(rows.getInt(2), rows.getString(3))
    }.groupBy({ (entryId, _) -> entryId }, { (_, sense) -> sense })

private fun readTranslations(connection: Connection): Map<Pair<Long, Int>, List<String>> = connection.query(
    "SELECT entry_id, sense_ord, ord, text FROM translation ORDER BY entry_id, sense_ord, ord",
) { rows ->
    (rows.getLong(1) to rows.getInt(2)) to rows.getString(4)
}.groupBy({ (key, _) -> key }, { (_, text) -> text })

/** Opens one read-only connection to [file] and closes it when [block] returns. */
private fun <T> open(file: Path, block: (Connection) -> T): T =
    DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use(block)

/** Runs [sql] and maps each row, leaving the connection open for the caller's next query. */
private fun <T> Connection.query(sql: String, row: (ResultSet) -> T): List<T> = createStatement().use { statement ->
    statement.executeQuery(sql).use { rows ->
        buildList {
            while (rows.next()) add(row(rows))
        }
    }
}
