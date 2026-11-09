package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stage two of the two-stage build (ADR-0046, issue #304): one pair's content file
 * and the Reading language's shared index yield only that pair's release
 * artefacts, and the index plus a frequency list yields one Difficulty table.
 * Both record the intermediate's edition as provenance and are reproducible.
 */
class PairAssemblyTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `writes only one pair's sqlite, notice and manifest`() {
        val intermediate = extract()
        val out = directory.resolve("out")

        runPair(intermediate.contentFor("fr"), intermediate.index, out)

        val stem = "lekto-dictionary-en-fr"
        assertEquals(
            setOf("$stem.sqlite", "NOTICE-$stem", "manifest-$stem.json"),
            list(out),
            "stage two writes one pair's artefacts and nothing else",
        )
        val manifest = manifestJson.decodeFromString<PackManifest>(
            Files.readString(out.resolve("manifest-$stem.json")),
        )
        assertEquals(EditionFixtures.provenance, manifest.provenance, "provenance comes from the content file")
        assertEquals(1, manifest.stats.lemmaCount)
        assertTrue("Lekto offline dictionary pack" in Files.readString(out.resolve("NOTICE-$stem")))
    }

    @Test
    fun `two content files of one language write packs with the same form table`() {
        val intermediate = extract()
        val french = directory.resolve("fr")
        val spanish = directory.resolve("es")

        runPair(intermediate.contentFor("fr"), intermediate.index, french)
        runPair(intermediate.contentFor("es"), intermediate.index, spanish)

        val frenchForms = forms(french.resolve("lekto-dictionary-en-fr.sqlite"))
        assertEquals(frenchForms, forms(spanish.resolve("lekto-dictionary-en-es.sqlite")))
        assertEquals(
            setOf("blorple", "blorples", "glim", "glims"),
            frenchForms.map { it.surface }.toSet(),
        )
    }

    @Test
    fun `the pair's release artefacts are byte-identical across two runs`() {
        val intermediate = extract()
        val first = directory.resolve("first")
        val second = directory.resolve("second")

        runPair(intermediate.contentFor("fr"), intermediate.index, first)
        runPair(intermediate.contentFor("fr"), intermediate.index, second)

        val stem = "lekto-dictionary-en-fr"
        assertEquals(-1L, Files.mismatch(first.resolve("$stem.sqlite"), second.resolve("$stem.sqlite")))
        assertEquals(
            Files.readString(first.resolve("manifest-$stem.json")),
            Files.readString(second.resolve("manifest-$stem.json")),
        )
        assertEquals(
            Files.readString(first.resolve("NOTICE-$stem")),
            Files.readString(second.resolve("NOTICE-$stem")),
        )
    }

    @Test
    fun `the difficulty table is byte-identical across two runs`() {
        val intermediate = extract()
        val frequency = frequency()
        val first = directory.resolve("first")
        val second = directory.resolve("second")

        runDifficulty(CliOptions.Difficulty(intermediate.index, frequency, first))
        runDifficulty(CliOptions.Difficulty(intermediate.index, frequency, second))

        val stem = "lekto-difficulty-en"
        assertEquals(-1L, Files.mismatch(first.resolve("$stem.sqlite"), second.resolve("$stem.sqlite")))
        assertEquals(
            Files.readString(first.resolve("manifest-$stem.json")),
            Files.readString(second.resolve("manifest-$stem.json")),
        )
        assertEquals(
            setOf("$stem.sqlite", "NOTICE-$stem", "manifest-$stem.json"),
            list(first),
        )
        val manifest = manifestJson.decodeFromString<DifficultyManifest>(
            Files.readString(first.resolve("manifest-$stem.json")),
        )
        assertEquals(EditionFixtures.provenance, manifest.provenance, "provenance comes from the form index")
    }

    @Test
    fun `a pair below its drift threshold fails without touching its sibling`() {
        val intermediate = extract()
        val french = directory.resolve("fr")
        val spanish = directory.resolve("es")
        runPair(intermediate.contentFor("es"), intermediate.index, spanish)
        val sibling = Files.readAllBytes(spanish.resolve("lekto-dictionary-en-es.sqlite"))

        val error = assertFailsWith<IllegalStateException> {
            runPair(intermediate.contentFor("fr"), intermediate.index, french, previousLemmaCount = 1_000)
        }

        assertTrue("coverage collapsed" in error.message.orEmpty())
        assertFalse(Files.exists(french.resolve("lekto-dictionary-en-fr.sqlite")))
        assertEquals(
            sibling.toList(),
            Files.readAllBytes(spanish.resolve("lekto-dictionary-en-es.sqlite")).toList(),
            "the sibling's artefacts stay untouched",
        )
    }

    @Test
    fun `a newly added pair has no previous count and is not gated`() {
        val intermediate = extract()
        val out = directory.resolve("out")

        runPair(intermediate.contentFor("fr"), intermediate.index, out, previousLemmaCount = null)

        assertTrue(Files.exists(out.resolve("lekto-dictionary-en-fr.sqlite")))
    }

    @Test
    fun `refuses an index not built for the pair's translation language`() {
        val intermediate = extract()
        val index = editionJson.decodeFromString<FormIndex>(Files.readString(intermediate.index))
        val foreignIndex = directory.resolve("form-index-en.json")
        Files.writeString(foreignIndex, index.copy(translationLanguages = listOf("it")).canonicalJson())

        val error = assertFailsWith<IllegalArgumentException> {
            runPair(intermediate.contentFor("fr"), foreignIndex, directory.resolve("out"))
        }

        assertTrue("not built for fr" in error.message.orEmpty())
    }

    private fun extract(): Intermediate {
        val input = directory.resolve("edition.jsonl")
        Files.writeString(input, SOURCE)
        val output = directory.resolve("intermediate")
        runExtract(CliOptions.Extract(input, output, EditionFixtures.provenance, "en", listOf("fr", "es")))
        return Intermediate(output.resolve("form-index-en.json")) { translation ->
            output.resolve("content-en-$translation.json")
        }
    }

    private fun runPair(content: Path, index: Path, out: Path, previousLemmaCount: Int? = null) {
        runPair(
            CliOptions.Pair(
                content = content,
                formIndex = index,
                outputDir = out,
                previousLemmaCount = previousLemmaCount,
                collapseThreshold = SanityGate.DEFAULT_COLLAPSE_THRESHOLD,
            ),
        )
    }

    private fun frequency(): Path {
        val file = directory.resolve("wordfreq-en.tsv")
        Files.writeString(file, "blorple\t100\nblorples\t50\nglim\t10\n")
        return file
    }

    private fun list(directory: Path): Set<String> = Files.list(directory).use { stream ->
        stream.map { it.fileName.toString() }.toList().toSet()
    }

    private fun forms(pack: Path): List<PackForm> =
        DriverManager.getConnection("jdbc:sqlite:${pack.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT surface, language, lemma FROM form ORDER BY surface, language")
                    .use { result ->
                        buildList {
                            while (result.next()) {
                                add(
                                    PackForm(
                                        result.getString("surface"),
                                        result.getString("language"),
                                        result.getString("lemma"),
                                    ),
                                )
                            }
                        }
                    }
            }
        }

    private class Intermediate(val index: Path, val contentFor: (String) -> Path)

    private companion object {
        /** One English entry translating into both targets, and one only into Spanish. */
        val SOURCE = """
            {"word":"blorple","lang_code":"en","pos":"verb","senses":[{"glosses":["To move swiftly."]}],"translations":[{"lang_code":"fr","word":"blorper"},{"lang_code":"es","word":"blorplear"}],"forms":[{"form":"blorples"}]}
            {"word":"glim","lang_code":"en","pos":"noun","senses":[{"glosses":["Another thing."]}],"translations":[{"lang_code":"es","word":"cosa"}],"forms":[{"form":"glims"}]}
        """.trimIndent()
    }
}
