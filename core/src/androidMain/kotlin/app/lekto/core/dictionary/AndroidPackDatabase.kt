@file:Suppress("DEPRECATION") // SQLiteDatabase.openDatabase(path, factory, flags) is the API available at minSdk 24.

package app.lekto.core.dictionary

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * The Android [PackDatabase]: the framework's read-only SQLite opens the pack by
 * its app-private path (ADR-0017). It is the Android twin of the JVM
 * `SqlitePackDatabase`; both execute the SQL held once in [SqlDictionaryPack], so
 * the two clients answer a query identically.
 */
class AndroidPackDatabase(private val database: SQLiteDatabase) : PackDatabase {

    override val formatVersion: Int = database.version

    override fun rows(sql: String, args: List<String>): List<PackRow> =
        database.rawQuery(sql, args.toTypedArray()).use(::readRows)

    override fun close() = database.close()

    private fun readRows(rows: Cursor): List<PackRow> = buildList {
        while (rows.moveToNext()) {
            add((0 until rows.columnCount).map { column -> rows.getString(column) })
        }
    }
}

/** Opens the read-only Android SQLite pack at a `storedPath`; throws [DictionaryPackMissing] when there is none. */
object AndroidPackDatabaseFactory : PackDatabaseFactory {

    override fun open(path: String): PackDatabase {
        val file = File(path)
        if (!file.isFile) throw DictionaryPackMissing()
        return AndroidPackDatabase(SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY))
    }
}
