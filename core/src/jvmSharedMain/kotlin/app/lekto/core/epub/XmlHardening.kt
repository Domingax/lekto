package app.lekto.core.epub

import org.xml.sax.EntityResolver
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The shared hardening for every XML parser in the EPUB pipeline.
 *
 * `DocumentBuilderFactory`'s feature names are not all portable. `FEATURE_SECURE_PROCESSING`
 * is the JAXP-standard one, but `http://apache.org/xml/features/…` is a Xerces
 * name: the JDK knows it, and **Android's parser throws**
 * `ParserConfigurationException` on it (surfaced to the user as
 * `http://javax.xml.XMLConstants/feature/secure-processing`). Setting an optional
 * hardening feature must therefore never be a reason to refuse a book — a real
 * EPUB failed to import on Android before this tolerated an unknown feature.
 *
 * A `DOCTYPE` is deliberately **allowed**: an EPUB 2's `.ncx` table of contents
 * normally declares one, and rejecting it loses the book's own chapter list
 * (issue #291). The defence against external entities does not need that
 * rejection — [EXTERNAL_DTD_FEATURE] stops a DTD from being loaded,
 * `isExpandEntityReferences = false` keeps entities unexpanded, and
 * [hardenedBuilder] installs a blank `EntityResolver`, so nothing is ever
 * fetched.
 */
internal object XmlHardening {

    /** The Xerces feature that disables external DTD loading; not known on Android. */
    const val EXTERNAL_DTD_FEATURE: String = "http://apache.org/xml/features/nonvalidating/load-external-dtd"

    /**
     * The resolver every hardened builder installs: whatever the document names,
     * it answers with an empty source, so the parser never opens a URL.
     */
    val blankEntityResolver: EntityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }

    /** A `DocumentBuilderFactory` with namespace awareness on, hardening on where supported. */
    fun hardenedFactory(base: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()): DocumentBuilderFactory =
        base.apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeatureIfSupported(EXTERNAL_DTD_FEATURE, false)
        }

    /**
     * A builder with the defences a factory cannot carry: a blank entity resolver,
     * so a document can never make the parser fetch a URL, and the
     * [QuietErrorHandler], so a rejected document does not write Xerces'
     * `[Fatal Error]` line to stderr (issue #291).
     */
    fun hardenedBuilder(factory: DocumentBuilderFactory = hardenedFactory()): DocumentBuilder =
        factory.newDocumentBuilder().apply {
            setEntityResolver(blankEntityResolver)
            setErrorHandler(QuietErrorHandler)
        }

    /**
     * Sets [name] to [value], ignoring the platform's rejection of a feature it
     * does not recognise. The hardening features outside the JAXP standard are a
     * nicety; an unsupported name is not a parsing failure.
     */
    fun DocumentBuilderFactory.setFeatureIfSupported(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: javax.xml.parsers.ParserConfigurationException) {
            // The parser does not know this feature; the portable defences still apply.
        }
    }
}

/**
 * Xerces' default handler prints every parse problem to stderr, even when the
 * caller catches the exception and carries on. Warnings and recoverable errors
 * are ignored; a fatal error is rethrown so the caller still sees the failure.
 */
private object QuietErrorHandler : ErrorHandler {

    override fun warning(exception: SAXParseException) = Unit

    override fun error(exception: SAXParseException) = Unit

    override fun fatalError(exception: SAXParseException): Nothing = throw exception
}
