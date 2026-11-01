package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.BookAsset
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.Chapter
import app.lekto.core.text.ParsedBook
import app.lekto.core.text.StructuredText
import app.lekto.core.text.TextBlock

/** Raised when an EPUB cannot be read: a missing entry, an absent rootfile, a broken OPF. */
class EpubParseException(message: String) : IllegalArgumentException(message)

/**
 * The EPUB [BookTextParser]: a ZIP container, an OPF package, a spine, XHTML.
 *
 * It is deliberately small and format-honest — it reads what a reflowable EPUB
 * must contain and ignores the rest (CSS, fixed layout, media overlays). The
 * parsing is split by concern: [EpubArchive] owns the container, [OpfDocument]
 * the package document, [EpubToc] the table of contents, [XhtmlBlocks] the
 * content documents.
 *
 * The book's **Chapter list** is derived beside its text (ADR-0037): the EPUB 3
 * `nav` document first, then the EPUB 2 NCX, then one chapter per spine document
 * when neither yields a usable entry. An entry is usable only when it targets a
 * spine document; its fragment resolves to a block index, falling back to the
 * document's start when the anchor is absent.
 *
 * The XML reader is injectable so a host test can drive the whole pipeline
 * through a platform parser — Android's, under Robolectric — rather than the
 * host JVM's Xerces. Production takes the tolerant default (ticket #49).
 */
@Suppress("TooManyFunctions") // The parser's stages: read, resolve, order, title — one function each.
class EpubParser internal constructor(private val opfXml: OpfXmlReader) : BookTextParser {

    constructor() : this(OpfXmlReader())

    override fun parse(bytes: ByteArray): ParsedBook {
        val archive = EpubArchive.of(bytes)
        val opfPath = OpfDocument.containerRootfile(archive.read(CONTAINER), opfXml)
        val packageDocument = OpfDocument.parse(archive.read(opfPath), opfXml)
        val baseDirectory = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val documents = packageDocument.spine.map { item ->
            val path = EpubArchive.resolve(baseDirectory, item.href)
            SpineDocument(
                path = path,
                content = XhtmlBlocks.parse(
                    bytes = archive.read(path),
                    base = path.substringBeforeLast('/', missingDelimiterValue = ""),
                    readAsset = { asset -> runCatching { archive.read(asset) }.getOrNull() },
                ),
            )
        }
        val parts = BookParts(documents)
        val chapters = tableOfContents(archive, packageDocument, baseDirectory, parts)
            ?: parts.spineChapters()
        return ParsedBook(
            text = StructuredText(
                title = packageDocument.title,
                language = packageDocument.language,
                blocks = parts.blocks,
                chapters = chapters,
            ),
            assets = parts.assets,
            cover = coverImage(archive, packageDocument.cover, baseDirectory),
        )
    }

    /**
     * The cover's raw bytes, or `null` for anything this build cannot show: no
     * declaration, a media type that is not an image (or is an SVG), an entry
     * the archive does not carry, or bytes whose header is not a raster image.
     * A cover is never allowed to fail an import (issue #259).
     */
    private fun coverImage(archive: EpubArchive, item: OpfDocument.ManifestItem?, baseDirectory: String): ByteArray? {
        val usable = item?.takeIf { candidate -> isCoverMediaType(candidate) }
        return usable?.let { candidate ->
            val path = EpubArchive.resolve(baseDirectory, candidate.href)
            runCatching { archive.read(path) }.getOrNull()
        }?.takeIf { bytes -> ImageHeader.sizeOf(bytes) != null }
    }

    /**
     * Whether [item] declares an image a platform decoder may read. The header,
     * not the label, decides the format; this only keeps a foreign type (a page,
     * a stylesheet) and the spec-legal SVG out, including the `image/jpg` alias
     * real EPUB 2 producers write.
     */
    private fun isCoverMediaType(item: OpfDocument.ManifestItem): Boolean {
        val mediaType = mediaTypeOf(item)
        return mediaType.startsWith("image/") && mediaType != "image/svg+xml"
    }

    /** The manifest media type, without parameters and case-insensitively. */
    private fun mediaTypeOf(item: OpfDocument.ManifestItem): String =
        item.mediaType.substringBefore(';').trim().lowercase()

    /** The book's chapters from its first usable table of contents, or `null` when it has none. */
    private fun tableOfContents(
        archive: EpubArchive,
        packageDocument: OpfDocument,
        baseDirectory: String,
        parts: BookParts,
    ): List<Chapter>? {
        val fromNav = packageDocument.nav
            ?.let { item -> tocEntries(archive, item, baseDirectory) }
            ?.let { entries -> parts.resolvedChapters(entries) }
            .orEmpty()
        if (fromNav.isNotEmpty()) return fromNav

        return packageDocument.ncx
            ?.let { item -> tocEntries(archive, item, baseDirectory) }
            ?.let { entries -> parts.resolvedChapters(entries) }
            ?.takeIf { chapters -> chapters.isNotEmpty() }
    }

    /** Reads a TOC document named by [item], or `null` when it cannot be read. */
    private fun tocEntries(
        archive: EpubArchive,
        item: OpfDocument.ManifestItem,
        baseDirectory: String,
    ): List<TocEntry>? {
        val path = EpubArchive.resolve(baseDirectory, item.href)
        val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
        return runCatching {
            if (item.mediaType == NCX_MEDIA_TYPE) {
                EpubToc.ncx(archive.read(path), directory, opfXml)
            } else {
                EpubToc.nav(archive.read(path), directory)
            }
        }.getOrNull()
    }

    /** A content document in the spine, with the blocks and anchors it contributes. */
    private data class SpineDocument(val path: String, val content: XhtmlDocument)

    /** A TOC entry resolved to a place in the book. */
    private data class ResolvedEntry(val title: String, val start: Int, val children: List<ResolvedEntry>)

    /**
     * The book's spine, its blocks and the block index each document starts at;
     * the parts every chapter decision reads together rather than as loose lists.
     */
    private class BookParts(val documents: List<SpineDocument>) {
        val starts: List<Int> =
            documents.runningFold(0) { start, document -> start + document.content.blocks.size }.dropLast(1)
        val blocks: List<TextBlock> = documents.flatMap { document -> document.content.blocks }

        /**
         * The images the spine references, one [BookAsset] per archive path. Two
         * documents naming the same picture share one asset, because the derived
         * asset name is the path's hash.
         */
        val assets: List<BookAsset> = documents
            .flatMap { document ->
                document.content.images.map { (path, bytes) -> BookAsset(imageAssetName(path), bytes) }
            }
            .distinctBy { asset -> asset.name }

        /** One chapter per spine document, titled by its first heading then its file name. */
        fun spineChapters(): List<Chapter> = documents.mapIndexed { index, document ->
            Chapter(
                title = firstHeading(starts[index], starts[index] + document.content.blocks.size)
                    ?: fileName(document.path),
                startBlock = starts[index],
            )
        }

        /**
         * Resolves a TOC tree to chapters, dropping entries that target no spine
         * document and promoting the children of a dropped entry. It orders
         * siblings by spine and block index, normalises a part's start to its
         * first child's, and fills each title's fallbacks.
         */
        fun resolvedChapters(entries: List<TocEntry>): List<Chapter> {
            val resolved = resolveEntries(entries)
            if (resolved.isEmpty()) return emptyList()
            val flat = resolved.flattened()
            return resolved.map { entry -> chapterOf(entry, flat) }
        }

        private fun resolveEntries(entries: List<TocEntry>): List<ResolvedEntry> = entries.flatMap { entry ->
            val children = resolveEntries(entry.children)
            val start = entry.href?.let { href -> startOf(href) }
            // An entry that targets no spine document is dropped; its children
            // take its place rather than disappearing with it.
            if (start != null) listOf(ResolvedEntry(entry.label, start, children)) else children
        }.sortedBy { entry -> entry.start }

        /**
         * The global block index [href] points at, or `null` when it targets no
         * spine document. An unresolved fragment falls back to the start of its
         * document.
         */
        private fun startOf(href: String): Int? {
            val path = href.substringBefore('#')
            val fragment = href.substringAfter('#', missingDelimiterValue = "")
            val index = documents.indexOfFirst { document -> document.path == path }
            if (index == -1) return null
            val local = fragment.takeIf { it.isNotEmpty() }
                ?.let { anchor -> documents[index].content.anchors[anchor] }
                ?: 0
            return starts[index] + local
        }

        /** Every entry then its children, in reading order. */
        private fun List<ResolvedEntry>.flattened(): List<ResolvedEntry> =
            flatMap { entry -> listOf(entry) + entry.children.flattened() }

        /** Turns the resolved tree into chapters, titles and all. */
        private fun chapterOf(entry: ResolvedEntry, flat: List<ResolvedEntry>): Chapter {
            val children = entry.children.map { child -> chapterOf(child, flat) }
            val start = children.firstOrNull()?.let { child -> minOf(entry.start, child.startBlock) } ?: entry.start
            val end = rangeEnd(entry, flat)
            return Chapter(
                title = entry.title.ifBlank { firstHeading(start, end) ?: fileNameAt(start) },
                startBlock = start,
                children = children,
            )
        }

        /** Where [entry]'s subtree ends: the next entry that is not inside it, or the book's end. */
        private fun rangeEnd(entry: ResolvedEntry, flat: List<ResolvedEntry>): Int {
            val index = flat.indexOf(entry)
            return flat.drop(index + 1).firstOrNull { next -> next.start > entry.start }?.start ?: blocks.size
        }

        /** The first heading block in `[start, end)`, or `null`. */
        fun firstHeading(start: Int, end: Int): String? =
            blocks.subList(start.coerceIn(0, blocks.size), end.coerceIn(0, blocks.size))
                .firstOrNull { block -> block.kind == BlockKind.HEADING }
                ?.text
                ?.takeIf { text -> text.isNotBlank() }

        private fun fileNameAt(start: Int): String {
            val index = starts.indexOfLast { blockStart -> blockStart <= start }.coerceAtLeast(0)
            return fileName(documents[index].path)
        }

        private fun fileName(path: String): String = path.substringAfterLast('/')
    }

    private companion object {
        const val CONTAINER = "META-INF/container.xml"
        const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"
    }
}
