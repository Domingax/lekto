package app.lekto.tools.dictionaries

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The config-driven catalogue run (issue #161): one invocation reads the committed
 * `languages.json`, builds every configured pack and **Difficulty table** into one
 * directory, and writes one **Language manifest** unioning them, so a release
 * carries the whole offered set on one alias.
 */
class CatalogueTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `builds every configured artefact and one manifest`() {
        val out = directory.resolve("out")

        runCatalogue(options(DEFAULT_CONFIG))

        listOf(
            "lekto-dictionary-en-fr.sqlite",
            "lekto-dictionary-en-es.sqlite",
            "lekto-dictionary-fr-es.sqlite",
            "lekto-difficulty-en.sqlite",
            "lekto-difficulty-es.sqlite",
        ).forEach { name -> assertTrue(Files.exists(out.resolve(name)), "$name must be built") }

        val manifest = Files.readString(out.resolve("language-manifest.json"))
        listOf(
            "lekto-dictionary-en-fr.sqlite.gz",
            "lekto-dictionary-en-es.sqlite.gz",
            "lekto-dictionary-fr-es.sqlite.gz",
            "lekto-difficulty-en.sqlite.gz",
            "lekto-difficulty-es.sqlite.gz",
        ).forEach { name -> assertTrue(name in manifest, "$name must be listed in the manifest") }
    }

    @Test
    fun `a difficulty without a frequency input is refused`() {
        val config = """
            {"dictionaries":[{"reading":"en","translation":"fr"}],"difficulties":["en"]}
        """.trimIndent()

        val error = assertFailsWith<IllegalStateException> {
            runCatalogue(options(config, frequencies = emptyMap()))
        }

        assertTrue("no --frequency en" in error.message.orEmpty())
    }

    @Test
    fun `an empty catalogue is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            runCatalogue(options("""{"dictionaries":[],"difficulties":[]}"""))
        }

        assertTrue("lists no artefacts" in error.message.orEmpty())
    }

    private fun options(config: String, frequencies: Map<String, Path> = defaultFrequencies()): CliOptions.Catalogue {
        val source = directory.resolve("source.jsonl")
        Files.writeString(source, SOURCE)
        val configPath = directory.resolve("languages-${config.hashCode()}.json")
        Files.writeString(configPath, config)
        val out = directory.resolve("out")
        return CliOptions.Catalogue(
            input = source,
            config = configPath,
            outputDir = out,
            provenance = PackProvenance("test://fixture", "0".repeat(64), "2026-10-04"),
            gate = GateOptions(previous = emptyMap(), collapseThreshold = 0.8),
            frequencies = frequencies,
            languageManifest = out.resolve("language-manifest.json"),
        )
    }

    private fun defaultFrequencies(): Map<String, Path> {
        val en = directory.resolve("wordfreq-en.tsv")
        Files.writeString(en, "blorple\t100\n")
        val es = directory.resolve("wordfreq-es.tsv")
        Files.writeString(es, "cosa\t100\n")
        return mapOf("en" to en, "es" to es)
    }

    private companion object {
        val DEFAULT_CONFIG = """
            {
              "dictionaries": [
                { "reading": "en", "translation": "fr" },
                { "reading": "en", "translation": "es" },
                { "reading": "fr", "translation": "es" }
              ],
              "difficulties": ["en", "es"]
            }
        """.trimIndent()

        /** One English entry with both translations, and one French entry into Spanish. */
        val SOURCE = """
            {"word":"blorple","lang_code":"en","pos":"verb","senses":[{"glosses":["To move swiftly."]}],"translations":[{"lang_code":"fr","word":"blorper"},{"lang_code":"es","word":"blorplear"}]}
            {"word":"truc","lang_code":"fr","pos":"noun","senses":[{"glosses":["Une chose."]}],"translations":[{"lang_code":"es","word":"cosa"}]}
        """.trimIndent()
    }
}
