package app.lekto.core.dictionary

/** One row of a pack query: the column values in order, with SQL `NULL` as `null`. */
typealias PackRow = List<String?>

/**
 * The read-only SQLite surface a [DictionaryPack] queries.
 *
 * It is deliberately tiny: the SQL and the row mapping live once, in
 * [SqlDictionaryPack] in `commonMain`, and each platform supplies an executor
 * behind this seam — `sqlite-jdbc` on the JVM, the Android framework's SQLite on
 * Android. The pack is opened in place by its app-private path, never read into
 * memory (ADR-0017).
 */
interface PackDatabase : AutoCloseable {

    /** The pack format version, the strict handshake's subject (`PRAGMA user_version`). */
    val formatVersion: Int

    /** Runs a read-only [sql] with [args] and returns its rows in order. */
    fun rows(sql: String, args: List<String>): List<PackRow>
}

/**
 * Opens a pack database by its app-private platform path. It throws
 * [DictionaryPackMissing] when no pack is installed there, so absence is a
 * distinct outcome from a corrupt file (which surfaces as a read failure). The
 * path is opaque to `commonMain` (ADR-0017); the platform backs this and never
 * leaks a folder assumption upward.
 */
fun interface PackDatabaseFactory {
    fun open(path: String): PackDatabase
}

/** Thrown by [PackDatabaseFactory.open] when no pack file lives at the path. */
class DictionaryPackMissing : Exception("no dictionary pack is installed")
