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
 * manifest** listing them all (issue #161); a publish run then completes that
 * manifest with each artefact's address, digest and size once the assets are
 * compressed (issue #305). The CI workflow downloads the input, runs this CLI and
 * publishes the release (`.github/workflows/dictionary-pack.yml`); nothing here is
 * committed.
 */
fun main(args: Array<String>) {
    try {
        when (val options = CliOptions.parse(args)) {
            is CliOptions.Build -> runBuild(options)
            is CliOptions.Catalogue -> runCatalogue(options)
            is CliOptions.Measure -> runMeasure(options)
            is CliOptions.Publish -> runPublish(options)
            is CliOptions.Extract -> runExtract(options)
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
    val extraction = extractEdition(
        options.input,
        options.provenance,
        options.readingLanguage,
        listOf(options.translationLanguage),
    )
    val pack = PackBuilder(
        extraction.contentFor(options.translationLanguage),
        extraction.index,
    ).build()
    runGate(pack, options.readingLanguage, options.translationLanguage, options.gate)
    writePack(pack, options.output, options.outputs.notice, options.outputs.manifest)
    println(packManifest(pack))
    val difficulty = options.difficulty?.let { diff ->
        val content =
            buildDifficulty(diff.input, extraction.index.forms, options.provenance, options.difficultyLanguage)
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
 * Stage one of the two-stage build (ADR-0046, issue #303): streams one edition
 * once and writes its **Reading language**'s form index beside one content file
 * per Translation language. Every file carries the edition's provenance, and the
 * same input yields the same bytes.
 */
internal fun runExtract(options: CliOptions.Extract) {
    val extraction = extractEdition(
        options.input,
        options.provenance,
        options.readingLanguage,
        options.translationLanguages,
    )
    Files.createDirectories(options.outputDir)
    Files.writeString(
        options.outputDir.resolve(formIndexFileName(options.readingLanguage)),
        extraction.index.canonicalJson(),
    )
    extraction.contentsByTranslation.forEach { (translation, content) ->
        Files.writeString(
            options.outputDir.resolve(contentFileName(options.readingLanguage, translation)),
            content.canonicalJson(),
        )
    }
    println(
        "wrote ${formIndexFileName(options.readingLanguage)} " +
            "and ${extraction.contentsByTranslation.size} content files",
    )
}

/**
 * Builds every artefact the committed language set lists (issue #161) into one
 * output directory: each dictionary pack from the raw source, each reading
 * language's **Difficulty table** from its frequency list, and one **Language
 * manifest** unioning them. Each edition is streamed once per reading language
 * and its form index is shared by every pack and table of that language
 * (ADR-0045, ADR-0046), so the manifest lists exactly what was written.
 */
internal fun runCatalogue(options: CliOptions.Catalogue) {
    val config = readLanguagesConfig(Files.readAllBytes(options.config))
    require(config.dictionaries.isNotEmpty() || config.difficulties.isNotEmpty()) {
        "the language set ${options.config} lists no artefacts to build"
    }
    Files.createDirectories(options.outputDir)
    val pairsByReading = config.dictionaries.groupBy(LanguagePair::reading)
    pairsByReading.forEach { (reading, pairs) ->
        val extraction = extractEdition(
            options.input,
            options.provenance,
            reading,
            pairs.map(LanguagePair::translation),
        )
        pairs.forEach { pair ->
            val pack = PackBuilder(
                extraction.contentFor(pair.translation),
                extraction.index,
            ).build()
            runGate(pack, pair.reading, pair.translation, options.gate)
            val stem = dictionaryStem(pair.reading, pair.translation)
            writePack(
                pack,
                options.outputDir.resolve("$stem.sqlite"),
                options.outputDir.resolve("NOTICE-$stem"),
                options.outputDir.resolve("manifest-$stem.json"),
            )
        }
        if (reading in config.difficulties) {
            writeCatalogueDifficulty(reading, extraction.index.forms, options)
        }
    }
    // A reading language with no pack (Spanish, in the starting set) has no lemmas
    // to key by, so its words take their normalised surface form (ADR-0029).
    (config.difficulties - pairsByReading.keys).forEach { language ->
        writeCatalogueDifficulty(language, emptyList(), options)
    }
    val languageManifest = buildLanguageManifest(config.dictionaries, config.difficulties)
    Files.writeString(options.languageManifest, languageManifest.canonicalJson())
    println(languageManifest.canonicalJson())
}

private fun writeCatalogueDifficulty(language: String, forms: List<PackForm>, options: CliOptions.Catalogue) {
    val frequency = options.frequencies[language]
        ?: error("no --frequency $language=<path> for the catalogued difficulty of $language")
    val content = buildDifficulty(frequency, forms, options.provenance, language)
    val stem = difficultyStem(language)
    writeDifficulty(
        content,
        options.outputDir.resolve("$stem.sqlite"),
        options.outputDir.resolve("NOTICE-$stem"),
        options.outputDir.resolve("manifest-$stem.json"),
    )
}

/**
 * Measures every candidate edition's pair coverage over the full file (issue
 * #302): one stream per reading language, so a language's edition is read once
 * and every target is counted in the same pass.
 */
internal fun runMeasure(options: CliOptions.Measure) {
    val targets = options.targets.ifEmpty { options.inputs.map(MeasureInput::language) }
    require(targets.isNotEmpty()) { "no translation targets to measure" }
    val editions = options.inputs.sortedBy(MeasureInput::edition).map { input ->
        openInput(input.path).use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                CoverageBuilder(input.language, targets, edition = input.edition).build(reader)
            }
        }
    }
    val json = CoverageReport(editions).canonicalJson()
    options.output.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(options.output, json)
    println(json)
}

/**
 * Completes a run's **Language manifest** for publication (issue #305): the
 * assets are already compressed, so every entry gains its address, the SHA-256 of
 * the bytes behind it and its size, and the manifest is written back. Publishing
 * this last is what lets the app follow a URL without racing a moving alias
 * (ADR-0047).
 */
internal fun runPublish(options: CliOptions.Publish) {
    val manifest = languageManifestJson.decodeFromString(
        LanguageManifest.serializer(),
        Files.readString(options.manifest),
    )
    val published = publishLanguageManifest(manifest, options.assetDir, options.assetBaseUrl)
    Files.writeString(options.manifest, published.canonicalJson())
    println(published.canonicalJson())
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

private fun extractEdition(
    input: Path,
    provenance: PackProvenance,
    readingLanguage: String,
    translationLanguages: Collection<String>,
): EditionIntermediate = openInput(input).use { stream ->
    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
        EditionExtractor(provenance, readingLanguage, translationLanguages).extract(reader)
    }
}

private fun openInput(path: Path): InputStream {
    val raw = Files.newInputStream(path)
    return if (path.fileName.toString().endsWith(".gz")) GZIPInputStream(raw, GZIP_BUFFER_BYTES) else raw
}

private const val GZIP_BUFFER_BYTES = 1 shl 16
