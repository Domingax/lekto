package app.lekto.tools.dictionaries

import java.nio.file.Path

/** The sanity-gate inputs: a previous lemma count per reading language, and the collapse threshold. */
internal data class GateOptions(val previous: Map<String, Int>, val collapseThreshold: Double)

/** Where the release sidecars are written. */
internal data class OutputPaths(val notice: Path, val manifest: Path)

/**
 * The difficulty build's inputs and outputs (ADR-0029). It is optional: a run
 * without a frequency source builds the dictionary pack alone.
 */
internal data class DifficultyOptions(val input: Path, val output: Path, val outputs: OutputPaths)

/** One measured edition: the source label, the reading language its entries carry, and the file. */
internal data class MeasureInput(val edition: String, val language: String, val path: Path)

/**
 * The parsed command line, one shape per pipeline stage.
 *
 * [Build] builds **one** pack for a (Reading language × Translation language) pair
 * and, optionally, one reading language's **Difficulty table** — the original
 * single-artefact run, now language-aware. [Catalogue] reads the committed
 * [LanguagesConfig] and builds **every** configured pack and table into one
 * output directory, so a release carries the whole offered set (issue #161).
 * [Extract] writes one language's intermediate (issue #303); [Pair] and
 * [Difficulty] turn that intermediate into one pair's pack or one language's table
 * (issue #304). [Measure] is the full-edition coverage pass that chooses the
 * roster (issue #302). [Publish] completes that run's **Language manifest** once
 * the assets are compressed, writing the address, digest and size publication
 * knows (issue #305); [Assemble] builds that manifest from the artefacts a
 * decomposed run actually produced (issue #306).
 *
 * The input is the raw Wiktextract `.jsonl[.gz]`. The provenance values are
 * recorded, never a timestamp. The workflow supplies every path; defaults are
 * only conveniences for a local run.
 */
internal sealed interface CliOptions {

    data class Build(
        val input: Path,
        val output: Path,
        val provenance: PackProvenance,
        val gate: GateOptions,
        val outputs: OutputPaths,
        val languageManifest: Path,
        val readingLanguage: String,
        val translationLanguage: String,
        val difficultyLanguage: String,
        val difficulty: DifficultyOptions?,
    ) : CliOptions

    data class Catalogue(
        val input: Path,
        val config: Path,
        val outputDir: Path,
        val provenance: PackProvenance,
        val gate: GateOptions,
        val frequencies: Map<String, Path>,
        val languageManifest: Path,
    ) : CliOptions

    /**
     * The full-edition coverage pass (issue #302): one stream per reading
     * language's edition, recording how many lemmas each measured translation
     * language yields. Targets default to the input languages, so a run over the
     * candidate editions measures the matrix among them.
     */
    data class Measure(val inputs: List<MeasureInput>, val targets: List<String>, val output: Path) : CliOptions

    /**
     * Completes a built **Language manifest** for publication (issue #305,
     * ADR-0047): the artefacts live in [assetDir], and [assetBaseUrl] is the
     * address prefix the app will follow them at.
     */
    data class Publish(val manifest: Path, val assetDir: Path, val assetBaseUrl: String) : CliOptions

    /**
     * The assembly stage (issue #306, ADR-0047): the decomposed run's separate
     * jobs leave their pairs' and tables' per-artefact manifests in [assetDir], so
     * this scans what succeeded, drops a pack below the published lemma floor, and
     * writes the one movable `manifest.json` naming exactly the compressed files
     * there.
     */
    data class Assemble(val assetDir: Path, val assetBaseUrl: String) : CliOptions

    /**
     * Stage one of the two-stage build (ADR-0046, issue #303): streams one
     * edition once and writes its **Reading language**'s form index beside one
     * content file per `--extract-target`.
     */
    data class Extract(
        val input: Path,
        val outputDir: Path,
        val provenance: PackProvenance,
        val readingLanguage: String,
        val translationLanguages: List<String>,
    ) : CliOptions

    /**
     * Stage two of the two-stage build (ADR-0046, issue #304): one pair's
     * content file and the **Reading language**'s shared index in, only that
     * pair's **Dictionary pack**, NOTICE and manifest out. The provenance comes
     * from the content file; [previousLemmaCount] is the pair's count in the
     * last published manifest, absent for a pair that has never been published.
     */
    data class Pair(
        val content: Path,
        val formIndex: Path,
        val outputDir: Path,
        val previousLemmaCount: Int?,
        val collapseThreshold: Double,
    ) : CliOptions

    /**
     * The difficulty stage (ADR-0046, issue #304): a **Reading language**'s
     * shared index and its frequency list in, that language's **Difficulty
     * table**, NOTICE and manifest out. The provenance comes from the index.
     */
    data class Difficulty(val formIndex: Path, val frequency: Path, val outputDir: Path) : CliOptions

    companion object {
        fun parse(args: Array<String>): CliOptions {
            val values = parseFlags(args)
            return when {
                values.containsKey("--assemble-release") -> assemble(values)
                values.containsKey("--publish-manifest") -> publish(values)
                values.containsKey("--measure-input") -> measure(values)
                values.containsKey("--extract-target") -> extract(values)
                values.containsKey("--content") -> pair(values)
                values.containsKey("--form-index") -> difficulty(values)
                values.containsKey("--languages-config") -> catalogue(values)
                else -> build(values)
            }
        }

        private fun extract(values: Map<String, List<String>>): Extract = Extract(
            input = path(values, "--input"),
            outputDir = path(values, "--output-dir"),
            provenance = provenance(values),
            readingLanguage = required(values, "--reading-language").lowercase(),
            translationLanguages = values["--extract-target"].orEmpty()
                .map(String::lowercase)
                .distinct(),
        )

        private fun measure(values: Map<String, List<String>>): Measure = Measure(
            inputs = values["--measure-input"].orEmpty().map(::measureInput),
            targets = values["--measure-target"].orEmpty().map(String::lowercase),
            output = path(values, "--measure-output"),
        )

        /** Parses `<edition>[:<language>]=<path>`; the edition labels the source, the language filters its entries. */
        private fun measureInput(assignment: String): MeasureInput {
            val separator = assignment.indexOf('=')
            require(separator > 0) { "--measure-input expects <edition>[:<language>]=<path>, got '$assignment'" }
            val key = assignment.substring(0, separator)
            val edition = key.substringBefore(':')
            val language = if (':' in key) key.substringAfter(':') else edition
            require(edition.isNotBlank() && language.isNotBlank()) {
                "--measure-input expects a non-blank edition and language, got '$key'"
            }
            return MeasureInput(edition, language.lowercase(), Path.of(assignment.substring(separator + 1)))
        }

        private fun publish(values: Map<String, List<String>>): Publish = Publish(
            manifest = path(values, "--publish-manifest"),
            assetDir = path(values, "--asset-dir"),
            assetBaseUrl = required(values, "--asset-url-base"),
        )

        private fun assemble(values: Map<String, List<String>>): Assemble = Assemble(
            assetDir = path(values, "--assemble-release"),
            assetBaseUrl = required(values, "--asset-url-base"),
        )

        private fun build(values: Map<String, List<String>>): Build {
            val output = path(values, "--output")
            val readingLanguage = (value(values, "--reading-language") ?: PackFormat.ENGLISH).lowercase()
            return Build(
                input = path(values, "--input"),
                output = output,
                provenance = provenance(values),
                gate = gate(values, defaultLanguage = readingLanguage),
                outputs = OutputPaths(
                    notice = optionalPath(values, "--notice") ?: output.resolveSibling("NOTICE"),
                    manifest = optionalPath(values, "--manifest") ?: output.resolveSibling("manifest.json"),
                ),
                languageManifest = optionalPath(values, "--language-manifest")
                    ?: output.resolveSibling("language-manifest.json"),
                readingLanguage = readingLanguage,
                translationLanguage = (value(values, "--translation-language") ?: PackFormat.FRENCH).lowercase(),
                // The difficulty is a property of the reading language, so it
                // defaults to it; a single-artefact run keys the table by that
                // language's pack form index.
                difficultyLanguage = (value(values, "--difficulty-language") ?: readingLanguage).lowercase(),
                difficulty = difficultyOptions(values),
            )
        }

        private fun catalogue(values: Map<String, List<String>>): Catalogue {
            val outputDir = path(values, "--output-dir")
            return Catalogue(
                input = path(values, "--input"),
                config = path(values, "--languages-config"),
                outputDir = outputDir,
                provenance = provenance(values),
                gate = gate(values, defaultLanguage = null),
                frequencies = assignments(values, "--frequency") { Path.of(it) },
                languageManifest = optionalPath(values, "--language-manifest")
                    ?: outputDir.resolve("language-manifest.json"),
            )
        }

        private fun provenance(values: Map<String, List<String>>): PackProvenance = PackProvenance(
            sourceUrl = required(values, "--source-url"),
            sourceSha256 = required(values, "--source-sha256"),
            extractionDate = required(values, "--extraction-date"),
        )

        /**
         * The gate's previous counts. A `Build` value is a bare integer, keyed by
         * its one reading language; a `Catalogue` value is `<language>=<count>`.
         */
        private fun gate(values: Map<String, List<String>>, defaultLanguage: String?): GateOptions {
            val threshold = collapseThreshold(values)
            val counts = values["--previous-lemma-count"].orEmpty()
            val previous = if (defaultLanguage != null && counts.size == 1 && '=' !in counts.single()) {
                mapOf(defaultLanguage to intValue(counts.single(), "--previous-lemma-count"))
            } else {
                assignments(values, "--previous-lemma-count") { raw -> intValue(raw, "--previous-lemma-count") }
            }
            return GateOptions(previous, threshold)
        }

        private fun difficultyOptions(values: Map<String, List<String>>): DifficultyOptions? {
            val input = optionalPath(values, "--frequency-input") ?: return null
            val output = path(values, "--difficulty-output")
            return DifficultyOptions(
                input = input,
                output = output,
                outputs = OutputPaths(
                    notice = optionalPath(values, "--difficulty-notice") ?: output.resolveSibling("NOTICE"),
                    manifest = optionalPath(values, "--difficulty-manifest") ?: output.resolveSibling("manifest.json"),
                ),
            )
        }
    }
}

private fun pair(values: Map<String, List<String>>): CliOptions.Pair = CliOptions.Pair(
    content = path(values, "--content"),
    formIndex = path(values, "--form-index"),
    outputDir = path(values, "--output-dir"),
    previousLemmaCount = value(values, "--previous-lemma-count")
        ?.let { intValue(it, "--previous-lemma-count") },
    collapseThreshold = collapseThreshold(values),
)

private fun difficulty(values: Map<String, List<String>>): CliOptions.Difficulty = CliOptions.Difficulty(
    formIndex = path(values, "--form-index"),
    frequency = path(values, "--frequency-input"),
    outputDir = path(values, "--output-dir"),
)

private fun collapseThreshold(values: Map<String, List<String>>): Double {
    val threshold = optionalDouble(values, "--collapse-threshold") ?: SanityGate.DEFAULT_COLLAPSE_THRESHOLD
    require(threshold > 0.0 && threshold <= 1.0) { "--collapse-threshold must be in (0, 1], got $threshold" }
    return threshold
}

private val KNOWN_FLAGS = setOf(
    "--input",
    "--output",
    "--output-dir",
    "--languages-config",
    "--source-url",
    "--source-sha256",
    "--extraction-date",
    "--previous-lemma-count",
    "--collapse-threshold",
    "--notice",
    "--manifest",
    "--language-manifest",
    "--publish-manifest",
    "--assemble-release",
    "--asset-dir",
    "--asset-url-base",
    "--frequency",
    "--frequency-input",
    "--difficulty-output",
    "--difficulty-notice",
    "--difficulty-manifest",
    "--reading-language",
    "--translation-language",
    "--difficulty-language",
    "--measure-input",
    "--measure-target",
    "--measure-output",
    "--extract-target",
    "--content",
    "--form-index",
)

/** Parses `--flag value` pairs, keeping a flag's repeated values in order. */
private fun parseFlags(args: Array<String>): Map<String, List<String>> {
    val values = LinkedHashMap<String, MutableList<String>>()
    var index = 0
    while (index < args.size) {
        val flag = args[index]
        require(flag in KNOWN_FLAGS) { "unknown argument '$flag'" }
        require(index + 1 < args.size) { "missing value for '$flag'" }
        values.getOrPut(flag) { mutableListOf() }.add(args[index + 1])
        index += 2
    }
    return values
}

private fun required(values: Map<String, List<String>>, flag: String): String =
    value(values, flag) ?: throw IllegalArgumentException("missing required argument '$flag'")

private fun value(values: Map<String, List<String>>, flag: String): String? = values[flag]?.first()

private fun path(values: Map<String, List<String>>, flag: String): Path = Path.of(required(values, flag))

private fun optionalPath(values: Map<String, List<String>>, flag: String): Path? = value(values, flag)?.let(Path::of)

private fun optionalDouble(values: Map<String, List<String>>, flag: String): Double? = value(values, flag)?.let { raw ->
    raw.toDoubleOrNull() ?: throw IllegalArgumentException("'$flag' must be a number, got '$raw'")
}

private fun intValue(raw: String, flag: String): Int =
    raw.toIntOrNull() ?: throw IllegalArgumentException("'$flag' must be an integer, got '$raw'")

/** Parses repeated `<key>=<value>` flags into an ordered map. */
private fun <T> assignments(
    values: Map<String, List<String>>,
    flag: String,
    transform: (String) -> T,
): Map<String, T> = values[flag].orEmpty().associate { entry ->
    val separator = entry.indexOf('=')
    require(separator > 0) { "'$flag' expects <key>=<value>, got '$entry'" }
    entry.substring(0, separator).lowercase() to transform(entry.substring(separator + 1))
}
