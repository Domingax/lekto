package app.lekto.tools.dictionaries

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

/**
 * The offline dictionary-pack pipeline (ticket #17).
 *
 * Streams the raw Wiktextract extract, builds a trimmed SQLite pack, runs the
 * sanity gate, and writes the pack plus its NOTICE and manifest. A run over the
 * configured language set reads the committed `languages.json` and builds every
 * configured pack and **Difficulty table** into one directory, with one **Language
 * manifest** listing them all (issue #161). The CI workflow downloads the input, runs this CLI and
 * publishes the release (`.github/workflows/dictionary-pack.yml`); nothing here is
 * committed.
 */
fun main(args: Array<String>) {
    try {
        when (val options = CliOptions.parse(args)) {
            is CliOptions.Build -> runBuild(options)
            is CliOptions.Catalogue -> runCatalogue(options)
        }
    } catch (error: IllegalArgumentException) {
        fail(error)
    } catch (error: IllegalStateException) {
        fail(error)
    } catch (error: java.io.IOException) {
        fail(error)
    } catch (error: SQLException) {
        fail(error)
    }
}

private fun fail(error: Exception) {
    System.err.println("dictionaries: ${error.message ?: error::class.simpleName}")
    exitProcess(1)
}

/** Builds one pack and, optionally, one reading language's difficulty table. */
private fun runBuild(options: CliOptions.Build) {
    val pack = buildPack(options.input, options.provenance, options.readingLanguage, options.translationLanguage)
    runGate(pack, options.readingLanguage, options.translationLanguage, options.gate)
    writePack(pack, options.output, options.outputs.notice, options.outputs.manifest)
    println(packManifest(pack))
    val difficulty = options.difficulty?.let { diff ->
        val content = buildDifficulty(diff.input, pack.forms, options.provenance, options.difficultyLanguage)
        writeDifficulty(content, diff.output, diff.outputs.notice, diff.outputs.manifest)
        println(difficultyManifest(content))
        content
    }
    val languageManifest = buildLanguageManifest(
        dictionaries = listOf(LanguagePair(options.readingLanguage, options.translationLanguage)),
        difficulties = if (difficulty == null) emptyList() else listOf(options.difficultyLanguage),
    ).canonicalJson()
    Files.writeString(options.languageManifest, languageManifest)
    println(languageManifest)
}

/**
 * Builds every artefact the committed language set lists (issue #161) into one
 * output directory: each dictionary pack from the raw source, each reading
 * language's **Difficulty table** from its frequency list, and one **Language
 * manifest** unioning them. The manifest lists exactly what was written, so it
 * can never advertise an artefact the run did not build.
 */
internal fun runCatalogue(options: CliOptions.Catalogue) {
    val config = readLanguagesConfig(Files.readAllBytes(options.config))
    require(config.dictionaries.isNotEmpty() || config.difficulties.isNotEmpty()) {
        "the language set ${options.config} lists no artefacts to build"
    }
    Files.createDirectories(options.outputDir)
    val formsByReading = LinkedHashMap<String, List<PackForm>>()
    config.dictionaries.forEach { pair ->
        val pack = buildPack(options.input, options.provenance, pair.reading, pair.translation)
        runGate(pack, pair.reading, pair.translation, options.gate)
        val stem = dictionaryStem(pair.reading, pair.translation)
        writePack(
            pack,
            options.outputDir.resolve("$stem.sqlite"),
            options.outputDir.resolve("NOTICE-$stem"),
            options.outputDir.resolve("manifest-$stem.json"),
        )
        // The reading language's first configured pack supplies the form index its
        // difficulty is keyed by; the app's translation target is fixed, so the
        // first pair must translate into it.
        formsByReading.putIfAbsent(pair.reading, pack.forms)
    }
    config.difficulties.forEach { language ->
        val frequency = options.frequencies[language]
            ?: error("no --frequency $language=<path> for the catalogued difficulty of $language")
        // A reading language with no pack (Spanish, in the starting set) has no
        // lemmas to key by, so its words take their normalised surface form
        // (ADR-0029, issue #161).
        val content = buildDifficulty(frequency, formsByReading[language].orEmpty(), options.provenance, language)
        val stem = difficultyStem(language)
        writeDifficulty(
            content,
            options.outputDir.resolve("$stem.sqlite"),
            options.outputDir.resolve("NOTICE-$stem"),
            options.outputDir.resolve("manifest-$stem.json"),
        )
    }
    val languageManifest = buildLanguageManifest(config.dictionaries, config.difficulties)
    Files.writeString(options.languageManifest, languageManifest.canonicalJson())
    println(languageManifest.canonicalJson())
}

/** Writes a pack and its two release sidecars (attribution and provenance). */
private fun writePack(pack: PackContent, sqlite: Path, notice: Path, manifest: Path) {
    SqlitePackWriter.write(pack, sqlite)
    Files.writeString(notice, Attribution.notice(pack.provenance))
    Files.writeString(manifest, packManifest(pack))
}

/** Writes a difficulty table and its two release sidecars (attribution and provenance). */
private fun writeDifficulty(content: DifficultyContent, sqlite: Path, notice: Path, manifest: Path) {
    SqliteDifficultyWriter.write(content, sqlite)
    Files.writeString(notice, DifficultyAttribution.notice(content.provenance))
    Files.writeString(manifest, difficultyManifest(content))
}

private fun runGate(pack: PackContent, readingLanguage: String, translationLanguage: String, gate: GateOptions) {
    val problems = SanityGate.check(
        stats = pack.stats,
        readingLanguage = readingLanguage,
        translationLanguage = translationLanguage,
        previousLemmaCount = gate.previous[readingLanguage],
        collapseThreshold = gate.collapseThreshold,
    )
    if (problems.isNotEmpty()) {
        error(
            "sanity gate failed for $readingLanguage→$translationLanguage:\n" +
                problems.joinToString("\n") { "  - $it" },
        )
    }
}

private fun packManifest(content: PackContent): String =
    manifestJson.encodeToString(PackManifest(PackFormat.VERSION, content.provenance, content.stats))

private fun difficultyManifest(content: DifficultyContent): String = manifestJson.encodeToString(
    DifficultyManifest(
        formatVersion = DifficultyFormat.VERSION,
        provenance = content.provenance,
        calibration = content.calibration,
        stats = content.stats,
    ),
)

/**
 * Builds the reading language's **Difficulty table** (ADR-0029) from its frequency
 * list, keyed by the reading language's form index — the pack's lemmas where one
 * exists, the normalised surface form otherwise.
 */
private fun buildDifficulty(
    input: Path,
    forms: List<PackForm>,
    provenance: PackProvenance,
    language: String,
): DifficultyContent = openInput(input).use { stream ->
    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
        DifficultyBuilder(forms, provenance, language).build(reader)
    }
}

private fun buildPack(
    input: Path,
    provenance: PackProvenance,
    readingLanguage: String,
    translationLanguage: String,
): PackContent = openInput(input).use { stream ->
    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
        PackBuilder(provenance, readingLanguage, translationLanguage).build(reader)
    }
}

private fun openInput(path: Path): InputStream {
    val raw = Files.newInputStream(path)
    return if (path.fileName.toString().endsWith(".gz")) GZIPInputStream(raw, GZIP_BUFFER_BYTES) else raw
}

private const val GZIP_BUFFER_BYTES = 1 shl 16
