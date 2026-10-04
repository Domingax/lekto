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
 * Streams the raw Wiktextract extract, builds a trimmed EN→FR SQLite pack, runs
 * the sanity gate, and writes the pack plus its NOTICE and manifest. The CI
 * workflow downloads the input, runs this CLI and publishes the release
 * (`.github/workflows/dictionary-pack.yml`); nothing here is committed.
 */
fun main(args: Array<String>) {
    try {
        runPipeline(CliOptions.parse(args))
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

private fun runPipeline(options: CliOptions) {
    val content = build(options)
    val problems = SanityGate.check(content.stats, options.gate.previousLemmaCount, options.gate.collapseThreshold)
    if (problems.isNotEmpty()) {
        error("sanity gate failed:\n" + problems.joinToString("\n") { "  - $it" })
    }
    SqlitePackWriter.write(content, options.output)
    Files.writeString(options.outputs.notice, Attribution.notice(options.provenance))
    val manifest = manifestJson.encodeToString(PackManifest(PackFormat.VERSION, content.provenance, content.stats))
    Files.writeString(options.outputs.manifest, manifest)
    println(manifest)
}

private fun build(options: CliOptions): PackContent = openInput(options.input).use { stream ->
    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
        PackBuilder(options.provenance).build(reader)
    }
}

private fun openInput(path: Path): InputStream {
    val raw = Files.newInputStream(path)
    return if (path.fileName.toString().endsWith(".gz")) GZIPInputStream(raw, GZIP_BUFFER_BYTES) else raw
}

private const val GZIP_BUFFER_BYTES = 1 shl 16
