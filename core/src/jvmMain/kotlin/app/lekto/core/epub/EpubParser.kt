package app.lekto.core.epub

import app.lekto.core.text.BookTextParser
import app.lekto.core.text.StructuredText

/** Raised when an EPUB cannot be read: a missing entry, an absent rootfile, a broken OPF. */
class EpubParseException(message: String) : IllegalArgumentException(message)

/**
 * The EPUB [BookTextParser]: a ZIP container, an OPF package, a spine, XHTML.
 *
 * It is deliberately small and format-honest — it reads what a reflowable EPUB
 * must contain and ignores the rest (CSS, fixed layout, media overlays). The
 * parsing is split by concern: [EpubArchive] owns the container, [OpfDocument]
 * the package document, [XhtmlBlocks] the content documents. See
 * `docs/research/epub-to-tokens-spike.md` for what this covers and what it does
 * not yet.
 */
class EpubParser : BookTextParser {

    override fun parse(bytes: ByteArray): StructuredText {
        val archive = EpubArchive.of(bytes)
        val opfPath = OpfDocument.containerRootfile(archive.read(CONTAINER))
        val packageDocument = OpfDocument.parse(archive.read(opfPath))
        val baseDirectory = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        val blocks = packageDocument.spine.flatMap { item ->
            XhtmlBlocks.of(archive.read(EpubArchive.resolve(baseDirectory, item.href)))
        }
        return StructuredText(
            title = packageDocument.title,
            language = packageDocument.language,
            blocks = blocks,
        )
    }

    private companion object {
        const val CONTAINER = "META-INF/container.xml"
    }
}
