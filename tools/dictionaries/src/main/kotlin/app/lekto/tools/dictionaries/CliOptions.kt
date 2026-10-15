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

/**
 * The parsed command line, in two shapes.
 *
 * [Build] builds **one** pack for a (Reading language × Translation language) pair
 * and, optionally, one reading language's **Difficulty table** — the original
 * single-artefact run, now language-aware. [Catalogue] reads the committed
 * [LanguagesConfig] and builds **every** configured pack and table into one
 * output directory, so a release carries the whole offered set (issue #161).
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

    companion object {
        fun parse(args: Array<String>): CliOptions {
            val values = parseFlags(args)
            return if (values.containsKey("--languages-config")) catalogue(values) else build(values)
        }

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
            val threshold = optionalDouble(values, "--collapse-threshold") ?: SanityGate.DEFAULT_COLLAPSE_THRESHOLD
            require(threshold > 0.0 && threshold <= 1.0) { "--collapse-threshold must be in (0, 1], got $threshold" }
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
    "--frequency",
    "--frequency-input",
    "--difficulty-output",
    "--difficulty-notice",
    "--difficulty-manifest",
    "--reading-language",
    "--translation-language",
    "--difficulty-language",
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
