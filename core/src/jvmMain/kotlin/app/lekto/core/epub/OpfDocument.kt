package app.lekto.core.epub

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The OPF package document: the book's metadata and the reading order.
 *
 * It reads `META-INF/container.xml` for the path to the OPF, then the OPF's
 * manifest (id to href) and spine (ordered idrefs). Spine items marked
 * `linear="no"` — covers, navigation — are dropped, and so is any item that is
 * not an XHTML content document.
 */
internal class OpfDocument(val title: String?, val language: String?, val spine: List<ManifestItem>) {

    /** A manifest entry the spine points at. */
    data class ManifestItem(val href: String, val mediaType: String)

    companion object {

        private const val CONTAINER_NAMESPACE = "urn:oasis:names:tc:opendocument:xmlns:container"
        private const val OPF_NAMESPACE = "http://www.idpf.org/2007/opf"
        private const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"

        /** The OPF path named by the first rootfile in `container.xml`. */
        fun containerRootfile(containerXml: ByteArray): String {
            val rootfiles = parseXml(containerXml).getElementsByTagNameNS(CONTAINER_NAMESPACE, "rootfile")
            for (index in 0 until rootfiles.length) {
                val path = (rootfiles.item(index) as Element).getAttribute("full-path")
                if (path.isNotEmpty()) return path
            }
            throw EpubParseException("container.xml names no rootfile")
        }

        /** Parses the OPF: its title, language and linear reading order. */
        fun parse(opfXml: ByteArray): OpfDocument {
            val document = parseXml(opfXml)
            val manifest = manifest(document)
            return OpfDocument(
                title = text(document, DC_NAMESPACE, "title"),
                language = text(document, DC_NAMESPACE, "language"),
                spine = spine(document, manifest),
            )
        }

        private fun manifest(document: Document): Map<String, ManifestItem> {
            val items = document.getElementsByTagNameNS(OPF_NAMESPACE, "item")
            return buildMap {
                for (index in 0 until items.length) {
                    val item = items.item(index) as Element
                    put(
                        item.getAttribute("id"),
                        ManifestItem(item.getAttribute("href"), item.getAttribute("media-type")),
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

        private val ManifestItem.isContentDocument: Boolean
            get() = mediaType.contains("xhtml") || mediaType.contains("html")

        private fun text(document: Document, namespace: String, tag: String): String? =
            (document.getElementsByTagNameNS(namespace, tag).item(0) as? Element)
                ?.textContent
                ?.trim()
                ?.ifEmpty { null }

        /**
         * A hardened, namespace-aware XML parser. External entity and DTD loading
         * is off and a blank resolver is installed, so a document cannot make the
         * build fetch a URL — the classic XXE trap in a parser that reads
         * untrusted files.
         */
        private fun parseXml(xml: ByteArray): Document {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            return factory.newDocumentBuilder()
                .apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
                .parse(ByteArrayInputStream(xml))
        }
    }
}
