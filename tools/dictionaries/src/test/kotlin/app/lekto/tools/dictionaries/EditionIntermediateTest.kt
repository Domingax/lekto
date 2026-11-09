package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Stage one of the two-stage build (ADR-0046, issue #303): one edition stream
 * yields the Reading language's form index and one content file per Translation
 * language, deterministically, with the edition's provenance on every file.
 */
class EditionIntermediateTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `pins the intermediate built from the committed fixture`() {
        val intermediate = EditionFixtures.extract(EditionFixtures.text())
        val content = intermediate.contentFor("fr")

        assertEquals(golden("form-index-en.json"), intermediate.index.canonicalJson())
        assertEquals(golden("content-en-fr.json"), content.canonicalJson())
    }

    @Test
    fun `an extraction without a translation target is refused`() {
        assertFailsWith<IllegalArgumentException> {
            EditionFixtures.extract("", translationLanguages = emptyList())
        }
    }

    @Test
    fun `the same edition yields the same intermediate`() {
        val first = EditionFixtures.extract(EditionFixtures.text())
        val second = EditionFixtures.extract(EditionFixtures.text())

        assertEquals(first.index.canonicalJson(), second.index.canonicalJson())
        assertEquals(
            first.contentFor("fr").canonicalJson(),
            second.contentFor("fr").canonicalJson(),
        )
    }

    @Test
    fun `two content files of one edition share the persisted form index`() {
        val source = """
            |{"word":"blorple","lang_code":"en","pos":"noun","senses":[{"glosses":["A thing."]}],"translations":[{"lang_code":"fr","word":"truc"}],"forms":[{"form":"blorples"}]}
            |{"word":"glim","lang_code":"en","pos":"noun","senses":[{"glosses":["Another thing."]}],"translations":[{"lang_code":"es","word":"cosa"}],"forms":[{"form":"glims"}]}
        """.trimMargin()
        val input = directory.resolve("edition.jsonl")
        Files.writeString(input, source)
        val output = directory.resolve("out")
        runExtract(CliOptions.Extract(input, output, EditionFixtures.provenance, "en", listOf("fr", "es")))

        val index = editionJson.decodeFromString<FormIndex>(Files.readString(output.resolve("form-index-en.json")))
        val frenchContent = editionJson.decodeFromString<EditionContent>(
            Files.readString(output.resolve("content-en-fr.json")),
        )
        val spanishContent = editionJson.decodeFromString<EditionContent>(
            Files.readString(output.resolve("content-en-es.json")),
        )
        val frenchPack = PackBuilder(frenchContent, index).build()
        val spanishPack = PackBuilder(spanishContent, index).build()

        assertEquals(listOf("blorple"), frenchPack.entries.map(PackEntry::lemma))
        assertEquals(listOf("glim"), spanishPack.entries.map(PackEntry::lemma))
        assertEquals(index.forms, frenchPack.forms)
        assertEquals(frenchPack.forms, spanishPack.forms)
        assertEquals(
            setOf("blorple", "blorples", "glim", "glims"),
            frenchPack.forms.map(PackForm::surface).toSet(),
            "a form whose entry translates into only one target still belongs to both packs",
        )
    }

    @Test
    fun `the extraction mode writes the index and one content file per target`() {
        val input = directory.resolve("edition.jsonl")
        Files.writeString(input, EditionFixtures.text())
        val output = directory.resolve("out")

        runExtract(CliOptions.Extract(input, output, EditionFixtures.provenance, "en", listOf("fr", "es")))

        val indexFile = output.resolve("form-index-en.json")
        assertTrue(Files.exists(indexFile))
        assertTrue(Files.exists(output.resolve("content-en-fr.json")))
        assertTrue(Files.exists(output.resolve("content-en-es.json")))

        val index = editionJson.decodeFromString<FormIndex>(Files.readString(indexFile))
        val content = editionJson.decodeFromString<EditionContent>(
            Files.readString(output.resolve("content-en-fr.json")),
        )
        assertEquals(EditionFormat.VERSION, index.formatVersion)
        assertEquals(EditionFixtures.provenance, index.provenance)
        assertEquals("en", index.readingLanguage)
        assertEquals(listOf("es", "fr"), index.translationLanguages)
        assertEquals("fr", content.translationLanguage)
        assertEquals(EditionFixtures.provenance, content.provenance)
    }

    @Test
    fun `the extraction mode rewrites the same bytes on a second run`() {
        val input = directory.resolve("edition.jsonl")
        Files.writeString(input, EditionFixtures.text())
        val output = directory.resolve("out")
        val options = CliOptions.Extract(input, output, EditionFixtures.provenance, "en", listOf("fr"))
        runExtract(options)
        val firstIndex = Files.readString(output.resolve("form-index-en.json"))
        val firstContent = Files.readString(output.resolve("content-en-fr.json"))

        runExtract(options)

        assertEquals(firstIndex, Files.readString(output.resolve("form-index-en.json")))
        assertEquals(firstContent, Files.readString(output.resolve("content-en-fr.json")))
    }
}

private fun golden(name: String): String =
    requireNotNull(EditionIntermediateTest::class.java.getResourceAsStream("/golden/$name")) {
        "the committed golden /golden/$name must be on the test classpath"
    }.bufferedReader().use { it.readText() }.trimEnd('\n')

/** The fixture every dictionary test shares, so its provenance and filters cannot drift. */
internal object EditionFixtures {

    val provenance = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04")

    fun extract(
        jsonl: String,
        readingLanguage: String = PackFormat.ENGLISH,
        translationLanguages: List<String> = listOf(PackFormat.FRENCH),
        provenance: PackProvenance = this.provenance,
    ): EditionIntermediate = EditionExtractor(provenance, readingLanguage, translationLanguages)
        .extract(StringReader(jsonl).buffered())

    fun pack(
        jsonl: String = text(),
        readingLanguage: String = PackFormat.ENGLISH,
        translationLanguage: String = PackFormat.FRENCH,
    ): PackContent {
        val intermediate = extract(jsonl, readingLanguage, listOf(translationLanguage))
        return PackBuilder(intermediate.contentFor(translationLanguage), intermediate.index).build()
    }

    fun text(): String =
        requireNotNull(Thread.currentThread().contextClassLoader?.getResourceAsStream("en-fr-sample.jsonl")) {
            "the committed fixture must be on the test classpath"
        }.bufferedReader().use { it.readText() }
}
