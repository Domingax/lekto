package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The SQLite pack: schema, version handshake, content and byte-for-byte reproducibility. */
class SqlitePackWriterTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `writes the format version and the attribution metadata`() {
        val pack = write(fixturePack(), "pack.sqlite")

        assertEquals(PackFormat.VERSION, query(pack, "PRAGMA user_version") { it.getInt(1) })
        assertEquals("CC BY-SA 4.0", value(pack, "SELECT value FROM pack_metadata WHERE key = 'license'"))
        assertEquals("0".repeat(64), value(pack, "SELECT value FROM pack_metadata WHERE key = 'source_sha256'"))
        assertTrue(
            "Attribution-ShareAlike 4.0 International" in
                value(pack, "SELECT value FROM pack_metadata WHERE key = 'license_text'"),
            "the full CC BY-SA 4.0 text must travel inside the pack",
        )
    }

    @Test
    fun `round-trips entries senses and translations`() {
        val pack = write(fixturePack(), "pack.sqlite")

        assertEquals(8, query(pack, "SELECT COUNT(*) FROM entry") { it.getInt(1) })
        assertEquals(
            3,
            query(
                pack,
                "SELECT COUNT(*) FROM sense s JOIN entry e ON e.id = s.entry_id " +
                    "WHERE e.lemma = 'blorple' AND e.part_of_speech = 'verb'",
            ) { it.getInt(1) },
        )
        assertEquals(
            "blorpoint",
            value(
                pack,
                "SELECT t.text FROM translation t JOIN entry e ON e.id = t.entry_id " +
                    "WHERE e.lemma = 'blorple' ORDER BY t.sense_ord DESC, t.ord DESC LIMIT 1",
            ),
        )
    }

    @Test
    fun `answers a form lookup and a lemma lookup`() {
        val pack = write(fixturePack(), "pack.sqlite")

        assertEquals("blorple", value(pack, "SELECT lemma FROM form WHERE surface = 'blorpled' AND language = 'en'"))
        assertEquals("plim", value(pack, "SELECT lemma FROM form WHERE surface = 'plimmer' AND language = 'en'"))
        assertEquals(
            "A coffeehouse.",
            value(pack, "SELECT definition FROM sense s JOIN entry e ON e.id = s.entry_id WHERE e.lemma = 'frungé'"),
        )
    }

    @Test
    fun `is reproducible byte for byte from the committed fixture`() {
        // Two independent end-to-end builds (JSONL -> SQLite), not one content
        // reused: this is the "same inputs yield the same pack" evidence.
        val first = write(fixturePack(), "first.sqlite")
        val second = write(fixturePack(), "second.sqlite")

        assertEquals(-1L, Files.mismatch(first, second), "the same input must yield identical bytes")
        assertEquals(sha256(first), sha256(second), "the same input must yield the same hash")
    }

    private fun write(content: PackContent, name: String): Path {
        val file = directory.resolve(name)
        SqlitePackWriter.write(content, file)
        return file
    }

    private fun value(pack: Path, sql: String): String = query(pack, sql) { it.getString(1) }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }

    private fun <T> query(pack: Path, sql: String, read: (java.sql.ResultSet) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:$pack").use { connection: Connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    read(result)
                }
            }
        }
}

private fun fixturePack(): PackContent = PackBuilder(FIXTURE_PROVENANCE)
    .build(
        requireNotNull(SqlitePackWriterTest::class.java.getResourceAsStream("/en-fr-sample.jsonl")) {
            "the committed fixture must be on the test classpath"
        }.bufferedReader(),
    )

private val FIXTURE_PROVENANCE = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")
