package app.lekto.core.epub

import javax.xml.XMLConstants
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
 * The portable defences live with the parser that uses them: `isExpandEntityReferences`
 * and a blank `EntityResolver` inside `OpfDocument.parseXml`.
 */
internal object XmlHardening {

    /** The Xerces feature that disables external DTD loading; not known on Android. */
    const val EXTERNAL_DTD_FEATURE: String = "http://apache.org/xml/features/nonvalidating/load-external-dtd"

    /** The Xerces feature that rejects a DOCTYPE outright; not known on Android. */
    const val DISALLOW_DOCTYPE_FEATURE: String = "http://apache.org/xml/features/disallow-doctype-decl"

    /** A `DocumentBuilderFactory` with namespace awareness on, hardening on where supported. */
    fun hardenedFactory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        setFeatureIfSupported(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeatureIfSupported(EXTERNAL_DTD_FEATURE, false)
        setFeatureIfSupported(DISALLOW_DOCTYPE_FEATURE, true)
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
