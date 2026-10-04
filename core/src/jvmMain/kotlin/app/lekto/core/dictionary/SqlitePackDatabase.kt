package app.lekto.core.dictionary

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * The JVM [PackDatabase]: the pre-built, read-only SQLite pack opened in place
 * through `sqlite-jdbc` (ADR-0017). It is desktop-only: Android's target uses the
 * framework's SQLite behind the same seam (`AndroidDictionary` in `app`), because
 * `sqlite-jdbc`'s bundled natives do not run on Android.
 */
class SqlitePackDatabase(private val connection: Connection) : PackDatabase {

    override val formatVersion: Int = connection.createStatement().use { statement ->
        statement.executeQuery("PRAGMA user_version").use { rows ->
            rows.next()
            rows.getInt(1)
        }
    }

    override fun rows(sql: String, args: List<String>): List<PackRow> =
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { index, argument -> statement.setString(index + 1, argument) }
            statement.executeQuery().use(::readRows)
        }

    override fun close() = connection.close()

    private fun readRows(rows: java.sql.ResultSet): List<PackRow> = buildList {
        while (rows.next()) {
            add((1..rows.metaData.columnCount).map { column -> rows.getString(column) })
        }
    }
}

/** Opens the read-only SQLite pack at a `storedPath`; throws [DictionaryPackMissing] when there is none. */
object SqlitePackDatabaseFactory : PackDatabaseFactory {

    override fun open(path: String): PackDatabase {
        val file = File(path)
        if (!file.isFile) throw DictionaryPackMissing()
        val config = SQLiteConfig().apply { setReadOnly(true) }
        return SqlitePackDatabase(DriverManager.getConnection("jdbc:sqlite:${file.path}", config.toProperties()))
    }
}
