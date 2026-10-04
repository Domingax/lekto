package app.lekto.tools.dictionaries

import java.nio.file.Path

/** The sanity-gate inputs: the previous build's lemma count and the collapse threshold. */
internal data class GateOptions(val previousLemmaCount: Int?, val collapseThreshold: Double)

/** Where the release sidecars are written. */
internal data class OutputPaths(val notice: Path, val manifest: Path)

/**
 * The parsed command line.
 *
 * The input is the raw Wiktextract `.jsonl[.gz]`; the output is the SQLite pack.
 * The provenance values are recorded in the pack, never in a timestamp. The
 * workflow supplies every path; defaults are only conveniences for a local run.
 */
internal data class CliOptions(
    val input: Path,
    val output: Path,
    val provenance: PackProvenance,
    val gate: GateOptions,
    val outputs: OutputPaths,
) {
    companion object {
        fun parse(args: Array<String>): CliOptions {
            val values = parseFlags(args)
            val output = path(values, "--output")
            val threshold = optionalDouble(values, "--collapse-threshold") ?: SanityGate.DEFAULT_COLLAPSE_THRESHOLD
            require(threshold > 0.0 && threshold <= 1.0) { "--collapse-threshold must be in (0, 1], got $threshold" }
            return CliOptions(
                input = path(values, "--input"),
                output = output,
                provenance = PackProvenance(
                    sourceUrl = required(values, "--source-url"),
                    sourceSha256 = required(values, "--source-sha256"),
                    extractionDate = required(values, "--extraction-date"),
                ),
                gate = GateOptions(
                    previousLemmaCount = optionalInt(values, "--previous-lemma-count"),
                    collapseThreshold = threshold,
                ),
                outputs = OutputPaths(
                    notice = optionalPath(values, "--notice") ?: output.resolveSibling("NOTICE"),
                    manifest = optionalPath(values, "--manifest") ?: output.resolveSibling("manifest.json"),
                ),
            )
        }
    }
}

private val KNOWN_FLAGS = setOf(
    "--input",
    "--output",
    "--source-url",
    "--source-sha256",
    "--extraction-date",
    "--previous-lemma-count",
    "--collapse-threshold",
    "--notice",
    "--manifest",
)

private fun parseFlags(args: Array<String>): Map<String, String> {
    val values = LinkedHashMap<String, String>()
    var index = 0
    while (index < args.size) {
        val flag = args[index]
        require(flag in KNOWN_FLAGS) { "unknown argument '$flag'" }
        require(index + 1 < args.size) { "missing value for '$flag'" }
        values[flag] = args[index + 1]
        index += 2
    }
    return values
}

private fun required(values: Map<String, String>, flag: String): String =
    values[flag] ?: throw IllegalArgumentException("missing required argument '$flag'")

private fun path(values: Map<String, String>, flag: String): Path = Path.of(required(values, flag))

private fun optionalPath(values: Map<String, String>, flag: String): Path? = values[flag]?.let(Path::of)

private fun optionalInt(values: Map<String, String>, flag: String): Int? =
    values[flag]?.let { it.toIntOrNull() ?: throw IllegalArgumentException("'$flag' must be an integer, got '$it'") }

private fun optionalDouble(values: Map<String, String>, flag: String): Double? =
    values[flag]?.let { it.toDoubleOrNull() ?: throw IllegalArgumentException("'$flag' must be a number, got '$it'") }
