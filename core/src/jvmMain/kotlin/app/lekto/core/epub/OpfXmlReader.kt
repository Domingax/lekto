package app.lekto.core.epub

import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads an XML document the way the EPUB pipeline needs it: namespace-aware,
 * hardened, and **portable**.
 *
 * The factory is a parameter so a test can hand in one whose parser rejects the
 * Xerces-only feature names, which is exactly what Android's parser does — the
 * behaviour that made a real EPUB import on desktop and fail on Android
 * (issue #15). Production takes the tolerant default, [XmlHardening.hardenedFactory].
 *
 * The defence that does not depend on a feature name is applied to the builder
 * here: a blank entity resolver, so a document can never make the parser fetch a
 * URL. (`isExpandEntityReferences = false` is set on the factory itself.)
 */
internal class OpfXmlReader(private val factory: DocumentBuilderFactory = XmlHardening.hardenedFactory()) {

    /** Parses [xml] into a [Document]. */
    fun read(xml: ByteArray): Document = factory.newDocumentBuilder()
        .apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
        .parse(ByteArrayInputStream(xml))
}
