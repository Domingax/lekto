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
 * compressed (issue #305). The decomposed run is `.github/workflows/dictionary-data.yml`,
 * a reusable workflow the data repository calls (issue #306, ADR-0047); nothing
 * here is committed.
 */
fun main(args: Array<String>) {
    try {
        run(CliOptions.parse(args))
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

/** Dispatches one parsed invocation to its pipeline stage. */
private fun run(options: CliOptions) {
    when (options) {
        is CliOptions.Build -> runBuild(options)
        is CliOptions.Catalogue -> runCatalogue(options)
        is CliOptions.Measure -> runMeasure(options)
        is CliOptions.Publish -> runPublish(options)
        is CliOptions.Assemble -> runAssemble(options)
        is CliOptions.Extract -> runExtract(options)
        is CliOptions.Pair -> runPair(options)
        is CliOptions.Difficulty -> runDifficulty(options)
        is CliOptions.Sample -> runSample(options)
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
    runGate(
        pack,
        LanguagePair(options.readingLanguage, options.translationLanguage),
        options.gate.previous[options.readingLanguage],
        options.gate.collapseThreshold,
    )
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
        buildReadingLanguage(reading, pairs, reading in config.difficulties, options)
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

/** Builds one reading language's packs and, when the set asks for it, its Difficulty table. */
private fun buildReadingLanguage(
    reading: String,
    pairs: List<LanguagePair>,
    wantsDifficulty: Boolean,
    options: CliOptions.Catalogue,
) {
    val extraction = extractEdition(
        options.input,
        options.provenance,
        reading,
        pairs.map(LanguagePair::translation),
    )
    pairs.forEach { pair ->
        val pack = PackBuilder(extraction.contentFor(pair.translation), extraction.index).build()
        runGate(pack, pair, options.gate.previous[pair.reading], options.gate.collapseThreshold)
        val stem = dictionaryStem(pair.reading, pair.translation)
        writePack(
            pack,
            options.outputDir.resolve("$stem.sqlite"),
            options.outputDir.resolve("NOTICE-$stem"),
            options.outputDir.resolve("manifest-$stem.json"),
        )
    }
    if (wantsDifficulty) writeCatalogueDifficulty(reading, extraction.index.forms, options)
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

/**
 * Builds the **Sample catalogue** the app ships (issue #297): every authored text
 * in the source directory becomes one sample, coloured by its reading language's
 * built **Difficulty table** and given one glossary per **Dictionary pack** the
 * run's published **Language manifest** names for that language. The roster is
 * bounded by the sample sources themselves: adding a reading language is a source
 * and its published packs, never a code branch or a roster edit.
 *
 * A language the manifest offers no difficulty table or no dictionary for is
 * refused, because the guide must not show a colour it cannot compute or a tap it
 * cannot answer. The catalogue is deterministic and carries no timestamp.
 */
internal fun runSample(options: CliOptions.Sample) {
    val manifest = languageManifestJson.decodeFromString<LanguageManifest>(
        Files.readString(options.manifest),
    )
    val sources = readSampleSources(options.sourceDir)
    val languages = sources.map { source -> source.language.lowercase() }
    require(languages.toSet().size == languages.size) {
        "two sample sources declare the same language: ${languages.joinToString()}"
    }
    val artifacts = artifactsByName(options.artifactsDir)
    val samples = sources.map { source -> sampleFor(source, manifest, artifacts, options.artifactsDir) }
        .sortedBy(SampleContent::language)
    val catalogue = SampleCatalogueDocument(SampleFormat.VERSION, samples)
    val json = catalogue.canonicalJson()
    options.output.toAbsolutePath().parent?.let(Files::createDirectories)
    Files.writeString(options.output, json)
    println(json)
}

/** Every authored sample text in [directory], in file order. */
private fun readSampleSources(directory: Path): List<SampleSource> {
    val sources = Files.list(directory).use { files ->
        files.filter { file -> file.fileName.toString().endsWith(".json") }
            .sorted()
            .map { file -> readSampleSource(Files.readAllBytes(file)) }
            .toList()
    }
    require(sources.isNotEmpty()) { "the sample source directory $directory holds no sample text" }
    return sources
}

/** One authored text, built from the artifacts the manifest published for its language. */
private fun sampleFor(
    source: SampleSource,
    manifest: LanguageManifest,
    artifacts: Map<String, Path>,
    artifactsDir: Path,
): SampleContent {
    val language = source.language.lowercase()
    val published = manifest.languages.firstOrNull { offered -> offered.language == language }
        ?: error("the published manifest offers no reading language $language")
    val difficulty = requireNotNull(published.difficulty) {
        "the published manifest offers no difficulty table for $language, so a sample has no colours"
    }
    require(published.dictionaries.isNotEmpty()) {
        "the published manifest offers no dictionary for $language, so a sample would have no meanings"
    }
    val difficultyName = difficulty.name.removeSuffix(".gz")
    val difficultyFile = artifacts[difficultyName]
        ?: error("the manifest publishes $difficultyName but $artifactsDir does not hold it")
    val packFiles = published.dictionaries.associate { dictionary ->
        val sqlite = dictionary.name.removeSuffix(".gz")
        val file = artifacts[sqlite]
            ?: error("the manifest publishes $sqlite but $artifactsDir does not hold it")
        dictionary.translationLanguage to file
    }.toSortedMap()
    return SampleBuilder(
        source = source.copy(language = language),
        difficultyByKey = readDifficultyTable(difficultyFile),
        formIndex = readFormTable(packFiles.values.first()),
        packs = packFiles.mapValues { (_, file) -> readPackEntries(file) },
    ).build()
}

/** Every `.sqlite` artefact in one built-data directory, by its file name. */
private fun artifactsByName(directory: Path): Map<String, Path> = Files.list(directory).use { files ->
    files.filter { file -> file.fileName.toString().endsWith(".sqlite") }.toList()
}.associate { file -> file.fileName.toString() to file }

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

internal fun openInput(path: Path): InputStream {
    val raw = Files.newInputStream(path)
    return if (path.fileName.toString().endsWith(".gz")) GZIPInputStream(raw, GZIP_BUFFER_BYTES) else raw
}

private const val GZIP_BUFFER_BYTES = 1 shl 16
