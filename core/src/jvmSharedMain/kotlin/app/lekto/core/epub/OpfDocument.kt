package app.lekto.core.epub

import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * The OPF package document: the book's metadata and the reading order.
 *
 * It reads `META-INF/container.xml` for the path to the OPF, then the OPF's
 * manifest (id to [ManifestItem]) and spine (ordered idrefs). Spine items marked
 * `linear="no"` — covers, navigation — are dropped, and so is any item that is
 * not an XHTML content document.
 *
 * It also names the two tables of contents an EPUB can carry (ADR-0037): the
 * EPUB 3 navigation document, found by its `properties="nav"` manifest entry and
 * allowed outside the spine, and the EPUB 2 NCX, found by the spine's `toc`
 * attribute or by its media type.
 *
 * It names the book's cover image too (issue #259): the EPUB 3 manifest item with
 * `properties="cover-image"`, or the item an EPUB 2 `<meta name="cover">` names by
 * id. A `guide` reference of type `cover` points at a page, not an image, so it
 * is deliberately not chased.
 *
 * XML parsing goes through an [OpfXmlReader], which is injectable so a test can
 * run the whole parse against a parser that rejects the Xerces-only feature
 * names — the Android behaviour that broke a real import (issue #15).
 */
@Suppress("LongParameterList") // The OPF's named parts: title, language, spine, nav, ncx and cover.
internal class OpfDocument(
    val title: String?,
    val language: String?,
    val spine: List<ManifestItem>,
    val nav: ManifestItem? = null,
    val ncx: ManifestItem? = null,
    val cover: ManifestItem? = null,
) {

    /** A manifest entry, with the properties and media type that classify it. */
    data class ManifestItem(val href: String, val mediaType: String, val properties: Set<String> = emptySet()) {
        val isContentDocument: Boolean
            get() = mediaType.contains("xhtml") || mediaType.contains("html")

        val isNavigationDocument: Boolean
            get() = "nav" in properties

        /** Whether this entry is the EPUB 3 cover image. */
        val isCoverImage: Boolean
            get() = "cover-image" in properties
    }

    companion object {

        private const val CONTAINER_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:container"
        private const val OPF_NAMESPACE = "http://www.idpf.org/2007/opf"
        private const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
        private const val NCX_MEDIA_TYPE = "application/x-dtbncx+xml"

        /** The OPF path named by the first rootfile in `container.xml`. */
        fun containerRootfile(containerXml: ByteArray, reader: OpfXmlReader = OpfXmlReader()): String {
            val rootfiles = reader.read(containerXml).getElementsByTagNameNS(CONTAINER_NAMESPACE, "rootfile")
            for (index in 0 until rootfiles.length) {
                val path = (rootfiles.item(index) as Element).getAttribute("full-path")
                if (path.isNotEmpty()) return path
            }
            throw EpubParseException("container.xml names no rootfile")
        }

        /** Parses the OPF: its title, language, linear reading order and tables of contents. */
        fun parse(opfXml: ByteArray, reader: OpfXmlReader = OpfXmlReader()): OpfDocument {
            val document = reader.read(opfXml)
            val manifest = manifest(document)
            return OpfDocument(
                title = text(document, DC_NAMESPACE, "title"),
                language = text(document, DC_NAMESPACE, "language"),
                spine = spine(document, manifest),
                nav = manifest.values.firstOrNull { item -> item.isNavigationDocument },
                ncx = ncx(document, manifest),
                cover = cover(document, manifest),
            )
        }

        private fun manifest(document: Document): Map<String, ManifestItem> {
            val items = document.getElementsByTagNameNS(OPF_NAMESPACE, "item")
            return buildMap {
                for (index in 0 until items.length) {
                    val item = items.item(index) as Element
                    val id = item.getAttribute("id")
                    put(
                        id,
                        ManifestItem(
                            href = item.getAttribute("href"),
                            mediaType = item.getAttribute("media-type"),
                            properties = item.getAttribute("properties").split(' ').filter { it.isNotBlank() }.toSet(),
                        ),
                    )
                }
            }
        }

        private fun spine(document: Document, manifest: Map<String, ManifestItem>): List<ManifestItem> {
            val refs = document.getElementsByTagNameNS(OPF_NAMESPACE, "itemref")
            return (0 until refs.length)
                .map { index -> refs.item(index) as Element }
                .filter { ref -> ref.getAttribute("linear") != "no" }
                .mapNotNull { ref -> manifest[ref.getAttribute("idref")] }
                .filter { item -> item.isContentDocument }
        }

        /**
         * The NCX: the manifest entry named by the spine's `toc` attribute, or
         * the first entry whose media type is the NCX one.
         */
        private fun ncx(document: Document, manifest: Map<String, ManifestItem>): ManifestItem? {
            val spines = document.getElementsByTagNameNS(OPF_NAMESPACE, "spine")
            val tocId = (0 until spines.length)
                .map { index -> (spines.item(index) as Element).getAttribute("toc") }
                .firstOrNull { id -> id.isNotEmpty() }
            return tocId?.let { id -> manifest[id] }
                ?: manifest.values.firstOrNull { item -> item.mediaType == NCX_MEDIA_TYPE }
        }

        /**
         * The cover image: the EPUB 3 `cover-image` property first, then the
         * EPUB 2 de-facto `<meta name="cover" content="<manifest id>"/>`. A
         * `guide` reference names a page for readers to open, not an image, so
         * it is not a fallback. The media type and the bytes are the caller's to
         * validate, because only it can read the archive.
         */
        private fun cover(document: Document, manifest: Map<String, ManifestItem>): ManifestItem? =
            manifest.values.firstOrNull { item -> item.isCoverImage }
                ?: metaCoverId(document)?.let { id -> manifest[id] }

        /** The manifest id an EPUB 2 `<meta name="cover">` names, if the OPF carries one. */
        private fun metaCoverId(document: Document): String? {
            val metas = document.getElementsByTagNameNS(OPF_NAMESPACE, "meta")
            return (0 until metas.length)
                .map { index -> metas.item(index) as Element }
                .firstOrNull { meta -> meta.getAttribute("name") == "cover" }
                ?.getAttribute("content")
                ?.takeIf { id -> id.isNotEmpty() }
        }

        private fun text(document: Document, namespace: String, tag: String): String? =
            (document.getElementsByTagNameNS(namespace, tag).item(0) as? Element)
                ?.textContent
                ?.trim()
                ?.ifEmpty { null }
    }
}
