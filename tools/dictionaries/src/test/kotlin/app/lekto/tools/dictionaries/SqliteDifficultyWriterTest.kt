package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.io.BufferedReader
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The SQLite difficulty table: schema, version handshake, content and byte-for-byte reproducibility. */
class SqliteDifficultyWriterTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `writes the format version and the wordfreq attribution metadata`() {
        val table = write(fixtureDifficulty(), "difficulty.sqlite")

        assertEquals(DifficultyFormat.VERSION, query(table, "PRAGMA user_version") { it.getInt(1) })
        assertEquals("CC BY-SA 4.0", value(table, "SELECT value FROM difficulty_metadata WHERE key = 'license'"))
        assertTrue(
            "Robyn Speer" in value(table, "SELECT value FROM difficulty_metadata WHERE key = 'attribution'"),
            "wordfreq's exact attribution name must travel inside the table",
        )
        assertTrue(
            "SUBTLEX" in value(table, "SELECT value FROM difficulty_metadata WHERE key = 'source_data'"),
            "the upstream SUBTLEX credit must travel inside the table",
        )
    }

    @Test
    fun `round-trips the word bands`() {
        val table = write(fixtureDifficulty(), "difficulty.sqlite")

        assertEquals(6, query(table, "SELECT COUNT(*) FROM difficulty") { it.getInt(1) })
        assertEquals("A1", value(table, "SELECT band FROM difficulty WHERE word = 'blorple' AND language = 'en'"))
        assertEquals("C2", value(table, "SELECT band FROM difficulty WHERE word = 'vrok' AND language = 'en'"))
    }

    @Test
    fun `is reproducible byte for byte from the committed fixture`() {
        val first = write(fixtureDifficulty(), "first.sqlite")
        val second = write(fixtureDifficulty(), "second.sqlite")

        assertEquals(-1L, Files.mismatch(first, second), "the same input must yield identical bytes")
        assertEquals(sha256(first), sha256(second), "the same input must yield the same hash")
    }

    private fun write(content: DifficultyContent, name: String): Path {
        val file = directory.resolve(name)
        SqliteDifficultyWriter.write(content, file)
        return file
    }

    private fun value(table: Path, sql: String): String = query(table, sql) { it.getString(1) }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }

    private fun <T> query(table: Path, sql: String, read: (java.sql.ResultSet) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:$table").use { connection: Connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    read(result)
                }
            }
        }
}

private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")

private fun fixtureDifficulty(): DifficultyContent = DifficultyBuilder(
    pack = PackBuilder(FIXTURE_PROVENANCE).build(
        requireNotNull(SqliteDifficultyWriterTest::class.java.getResourceAsStream("/en-fr-sample.jsonl")) {
            "the committed pack fixture must be on the test classpath"
        }.bufferedReader(),
    ),
    calibration = DifficultyCalibration(listOf(1, 2, 3, 4, 5, 6), "test", "test"),
).build(
    BufferedReader(
        StringReader(
            requireNotNull(
                SqliteDifficultyWriterTest::class.java.getResourceAsStream("/difficulty-frequency-sample.tsv"),
            ) {
                "the committed frequency fixture must be on the test classpath"
            }.bufferedReader().use(BufferedReader::readText),
        ),
    ),
)
