package app.lekto.tools.dictionaries

import kotlinx.serialization.SerializationException
import java.io.BufferedReader

/**
 * Streams a raw Wiktextract file and yields only [language]'s entries.
 *
 * The cheap `lang_code` prefilter skips the other languages' lines before a full
 * parse. A line that carries the marker but does not parse is counted and
 * skipped, so one bad record does not lose the whole edition. Both the pack
 * build and the coverage pass read through here, so their filters cannot drift.
 */
internal class ReadingEntries(private val language: String) {

    var totalLines = 0L
        private set

    /** Lines carrying the language marker that did not parse as a JSON object. */
    var malformedLines = 0L
        private set

    fun forEach(reader: BufferedReader, consume: (RawEntry) -> Unit) {
        reader.forEachLine { line ->
            totalLines++
            if (!couldBe(line, language)) return@forEachLine
            val raw = parse(line) ?: return@forEachLine
            if (raw.langCode == language) consume(raw)
        }
    }

    private fun parse(line: String): RawEntry? = try {
        wiktextractJson.decodeFromString<RawEntry>(line)
    } catch (_: SerializationException) {
        malformedLines++
        null
    } catch (_: IllegalArgumentException) {
        malformedLines++
        null
    }
}
