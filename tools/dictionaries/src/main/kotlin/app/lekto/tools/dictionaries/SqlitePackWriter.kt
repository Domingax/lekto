@file:Suppress("MagicNumber") // JDBC parameter indices are positional; the 4096 page size is SQLite's page unit.

package app.lekto.tools.dictionaries

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement

/**
 * Writes the in-memory [PackContent] to the pre-built, read-only SQLite file
 * ADR-0017 opens by path.
 *
 * The file is deterministic: rows are inserted in the pack's fixed order,
 * `user_version` carries the format handshake, and nothing records a timestamp.
 * `VACUUM` compacts the result, so the same content yields byte-identical files
 * under the pinned driver — the golden test's reproducibility evidence.
 */
internal object SqlitePackWriter {

    private val SCHEMA = listOf(
        """
        CREATE TABLE pack_metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE entry (
            id INTEGER PRIMARY KEY,
            language TEXT NOT NULL,
            lemma TEXT NOT NULL,
            part_of_speech TEXT,
            pronunciation TEXT,
            audio_url TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE sense (
            entry_id INTEGER NOT NULL REFERENCES entry(id),
            ord INTEGER NOT NULL,
            definition TEXT NOT NULL,
            PRIMARY KEY (entry_id, ord)
        ) WITHOUT ROWID
        """.trimIndent(),
        """
        CREATE TABLE translation (
            entry_id INTEGER NOT NULL,
            sense_ord INTEGER NOT NULL,
            ord INTEGER NOT NULL,
            text TEXT NOT NULL,
            PRIMARY KEY (entry_id, sense_ord, ord)
        ) WITHOUT ROWID
        """.trimIndent(),
        """
        CREATE TABLE form (
            surface TEXT NOT NULL,
            language TEXT NOT NULL,
            lemma TEXT NOT NULL,
            PRIMARY KEY (surface, language)
        ) WITHOUT ROWID
        """.trimIndent(),
        "CREATE INDEX entry_lookup ON entry(language, lemma)",
    )

    fun write(content: PackContent, file: Path) {
        file.parent?.let(Files::createDirectories)
        Files.deleteIfExists(file)
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            configure(connection)
            createSchema(connection)
            connection.autoCommit = false
            insertMetadata(connection, content)
            insertEntries(connection, content.entries)
            insertForms(connection, content.forms)
            connection.commit()
            connection.autoCommit = true
            connection.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun configure(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA page_size = 4096")
            statement.execute("PRAGMA user_version = ${PackFormat.VERSION}")
        }
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { statement -> SCHEMA.forEach(statement::execute) }
    }

    private fun insertMetadata(connection: Connection, content: PackContent) {
        connection.prepareStatement("INSERT INTO pack_metadata(key, value) VALUES(?, ?)").use { statement ->
            Attribution.metadata(content.provenance).forEach { (key, value) ->
                statement.setString(1, key)
                statement.setString(2, value)
                statement.executeUpdate()
            }
        }
    }

    private fun insertEntries(connection: Connection, entries: List<PackEntry>) {
        val statements = EntryStatements(
            entry = connection.prepareStatement(
                "INSERT INTO entry(id, language, lemma, part_of_speech, pronunciation, audio_url) " +
                    "VALUES(?, ?, ?, ?, ?, ?)",
            ),
            sense = connection.prepareStatement(
                "INSERT INTO sense(entry_id, ord, definition) VALUES(?, ?, ?)",
            ),
            translation = connection.prepareStatement(
                "INSERT INTO translation(entry_id, sense_ord, ord, text) VALUES(?, ?, ?, ?)",
            ),
        )
        entries.forEachIndexed { index, entry -> insertEntry(statements, index + 1L, entry) }
        statements.entry.close()
        statements.sense.close()
        statements.translation.close()
    }

    private fun insertEntry(statements: EntryStatements, id: Long, entry: PackEntry) {
        statements.entry.setLong(1, id)
        statements.entry.setString(2, entry.language)
        statements.entry.setString(3, entry.lemma)
        statements.entry.setString(4, entry.partOfSpeech)
        statements.entry.setString(5, entry.pronunciation)
        statements.entry.setString(6, entry.audioUrl)
        statements.entry.executeUpdate()
        entry.senses.forEachIndexed { ord, sense ->
            statements.sense.setLong(1, id)
            statements.sense.setInt(2, ord)
            statements.sense.setString(3, sense.definition)
            statements.sense.executeUpdate()
            insertTranslations(statements.translation, id, ord, sense.translations)
        }
    }

    private fun insertTranslations(
        statement: PreparedStatement,
        entryId: Long,
        senseOrd: Int,
        translations: List<String>,
    ) {
        translations.forEachIndexed { ord, text ->
            statement.setLong(1, entryId)
            statement.setInt(2, senseOrd)
            statement.setInt(3, ord)
            statement.setString(4, text)
            statement.executeUpdate()
        }
    }

    private fun insertForms(connection: Connection, forms: List<PackForm>) {
        connection.prepareStatement("INSERT INTO form(surface, language, lemma) VALUES(?, ?, ?)").use { statement ->
            forms.forEach { form ->
                statement.setString(1, form.surface)
                statement.setString(2, form.language)
                statement.setString(3, form.lemma)
                statement.executeUpdate()
            }
        }
    }
}

/** The three prepared statements one entry's row tree needs, held together. */
private class EntryStatements(
    val entry: PreparedStatement,
    val sense: PreparedStatement,
    val translation: PreparedStatement,
)
