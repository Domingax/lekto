package app.lekto.core.epub

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * The EPUB's ZIP container, held in memory as entry name to bytes.
 *
 * A book is small enough to read whole — originals are a few megabytes — and
 * keeping the entries in a map makes spine resolution a pure lookup rather than
 * a second pass over the stream. A production pipeline would stream for very
 * large books; the spike does not (see `docs/research/epub-to-tokens-spike.md`).
 */
internal class EpubArchive(private val entries: Map<String, ByteArray>) {

    /** The bytes of [path], or a clear failure when the archive does not carry it. */
    fun read(path: String): ByteArray = entries[path] ?: throw EpubParseException("EPUB has no entry '$path'")

    companion object {

        /** Reads every file entry of [bytes]; directories are ignored. */
        fun of(bytes: ByteArray): EpubArchive {
            val entries = LinkedHashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    entry = zip.nextEntry
                }
            }
            if (entries.isEmpty()) throw EpubParseException("Not a ZIP archive")
            return EpubArchive(entries)
        }

        /**
         * Resolves the relative [href] against the directory holding the OPF,
         * collapsing `.` and `..` segments and dropping any fragment. Hrefs are
         * not percent-decoded yet; the spike records that gap.
         */
        fun resolve(baseDirectory: String, href: String): String {
            val target = href.substringBefore('#')
            val combined = if (baseDirectory.isEmpty()) target else "$baseDirectory/$target"
            val segments = ArrayDeque<String>()
            for (part in combined.split('/')) {
                when (part) {
                    "", "." -> Unit
                    ".." -> segments.removeLastOrNull()
                    else -> segments.addLast(part)
                }
            }
            return segments.joinToString("/")
        }
    }
}
