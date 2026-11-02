package app.lekto.core.epub

import org.w3c.dom.Document
import java.io.ByteArrayInputStream
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
 * The defences a factory cannot carry — the blank entity resolver and the quiet
 * error handler — come from [XmlHardening.hardenedBuilder] (issue #291).
 */
internal class OpfXmlReader(private val factory: DocumentBuilderFactory = XmlHardening.hardenedFactory()) {

    /** Parses [xml] into a [Document]. */
    fun read(xml: ByteArray): Document = XmlHardening.hardenedBuilder(factory).parse(ByteArrayInputStream(xml))
}
