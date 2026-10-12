@file:Suppress("MagicNumber") // JDBC parameter indices are positional; the 4096 page size is SQLite's page unit.

package app.lekto.tools.dictionaries

import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * Writes the in-memory [DifficultyContent] to the pre-built, read-only SQLite
 * file the reader opens (ADR-0017, ADR-0029).
 *
 * The file is deterministic: rows are inserted in the build's fixed order,
 * `user_version` carries the format handshake, and nothing records a timestamp.
 * `VACUUM` compacts the result, so the same content yields byte-identical files
 * under the pinned driver — the reproducibility evidence a golden test pins.
 */
internal object SqliteDifficultyWriter {

    private val SCHEMA = listOf(
        """
        CREATE TABLE difficulty_metadata (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE difficulty (
            word TEXT NOT NULL,
            language TEXT NOT NULL,
            band TEXT NOT NULL,
            PRIMARY KEY (word, language)
        ) WITHOUT ROWID
        """.trimIndent(),
    )

    fun write(content: DifficultyContent, file: Path) {
        file.parent?.let(Files::createDirectories)
        Files.deleteIfExists(file)
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { connection ->
            configure(connection)
            createSchema(connection)
            connection.autoCommit = false
            insertMetadata(connection, content.provenance)
            insertRows(connection, content.rows)
            connection.commit()
            connection.autoCommit = true
            connection.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun configure(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA page_size = 4096")
            statement.execute("PRAGMA user_version = ${DifficultyFormat.VERSION}")
        }
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { statement -> SCHEMA.forEach(statement::execute) }
    }

    private fun insertMetadata(connection: Connection, provenance: PackProvenance) {
        connection.prepareStatement("INSERT INTO difficulty_metadata(key, value) VALUES(?, ?)").use { statement ->
            DifficultyAttribution.metadata(provenance).forEach { (key, value) ->
                statement.setString(1, key)
                statement.setString(2, value)
                statement.executeUpdate()
            }
        }
    }

    private fun insertRows(connection: Connection, rows: List<DifficultyRow>) {
        connection.prepareStatement("INSERT INTO difficulty(word, language, band) VALUES(?, ?, ?)").use { statement ->
            rows.forEach { row ->
                statement.setString(1, row.word)
                statement.setString(2, row.language)
                statement.setString(3, row.band)
                statement.executeUpdate()
            }
        }
    }
}
