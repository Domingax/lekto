package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncTargetException
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * The two things the driver reads out of a WebDAV `207 Multi-Status`: each
 * `DAV:response`'s `href`, under the `DAV:` namespace, and its `getetag`, so a
 * listing can tell an unchanged item from a moved one without a `GET`
 * (ADR-0027).
 *
 * The XML arrives over the network, so the parser is hardened against external
 * entities and doctypes before it sees a document.
 */
internal object WebDavMultistatus {

    /** Every `DAV:response`'s href and ETag, in document order. */
    @Suppress("TooGenericExceptionCaught") // Any parse failure is one honest message, never a crash.
    fun entries(xml: String): List<WebDavResource> {
        val document = try {
            factory().newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            throw SyncTargetException("the server's listing could not be read", e)
        }
        val responses = document.getElementsByTagNameNS(DAV, "response")
        return (0 until responses.length).mapNotNull { index ->
            val response = responses.item(index) as? Element ?: return@mapNotNull null
            val path = response.getElementsByTagNameNS(DAV, "href").item(0)?.textContent ?: return@mapNotNull null
            WebDavResource(path, response.getElementsByTagNameNS(DAV, "getetag").item(0)?.textContent)
        }
    }

    private fun factory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeatureIfSupported(DISALLOW_DOCTYPE, true)
    }

    /** Sets [name] if this parser knows it; an unknown feature is not a parse failure. */
    private fun DocumentBuilderFactory.setFeatureIfSupported(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: ParserConfigurationException) {
            // The parser does not recognise the feature; the other defences still apply.
        }
    }

    private const val DAV = "DAV:"
    private const val DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
}
