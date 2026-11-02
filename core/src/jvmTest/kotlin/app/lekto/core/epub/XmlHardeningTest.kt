package app.lekto.core.epub

import app.lekto.testkit.EpubFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

/**
 * The XML hardening is tolerant of feature names a platform's parser does not
 * know. This is the regression guard for the Android import failure: Android's
 * parser threw `ParserConfigurationException` on the Xerces feature
 * `http://apache.org/xml/features/nonvalidating/load-external-dtd`, which the
 * desktop JDK accepts, so a real EPUB imported on desktop and failed on Android
 * (issue #15).
 *
 * The end-to-end case runs the whole OPF parse through a factory that rejects
 * every non-JAXP feature, which is what Android does — so a future change that
 * sets an optional feature without tolerating rejection fails here even though
 * the desktop JDK would accept it. The fuller fix, running platform code on an
 * Android runtime, is ticket #49 (Robolectric host tests).
 */
class XmlHardeningTest :
    FunSpec({

        test("a feature the parser does not know never fails a parse") {
            val factory = DocumentBuilderFactory.newInstance()

            // A bogus feature URI is rejected the way Android rejects a Xerces
            // name; the helper must swallow it rather than fail the parse.
            XmlHardening.run {
                factory.setFeatureIfSupported("http://example.invalid/feature/does-not-exist", true)
            }

            factory.newDocumentBuilder().parse("<a/>".byteInputStream()).documentElement.tagName shouldBe "a"
        }

        test("the whole OPF parse survives a parser that rejects Xerces features, as Android does") {
            // The production tolerance runs against the rejecting parser: the
            // hardening factory must swallow Android's ParserConfigurationException
            // and still hand back a parser that reads the document.
            val reader = OpfXmlReader(XmlHardening.hardenedFactory(AndroidLikeFactory()))

            OpfDocument.parse(EpubFixtures.opfDocument(), reader).title shouldBe "Resilient"
            OpfDocument.containerRootfile(CONTAINER, reader) shouldBe "OEBPS/content.opf"
        }

        test("the hardened factory parses a namespace-aware document") {
            val document = XmlHardening.hardenedBuilder()
                .parse(
                    """
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Hardened</dc:title>
                      </metadata>
                    </package>
                    """.trimIndent().byteInputStream(),
                )

            document.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title")
                .item(0).textContent shouldBe "Hardened"
        }

        test("a DOCTYPE is accepted, as an EPUB 2 NCX carries one") {
            val xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><navMap/></ncx>
            """.trimIndent()

            val document = XmlHardening.hardenedBuilder().parse(xml.byteInputStream())

            document.documentElement.localName shouldBe "ncx"
        }

        test("the hardened builder answers an external entity with an empty source") {
            // The resolver is the portable defence: the parser never opens the
            // systemId, and an unsupported load-external-dtd feature changes nothing.
            val source = XmlHardening.blankEntityResolver.resolveEntity(null, "file:///etc/passwd")

            source?.characterStream?.readText() shouldBe ""
        }

        test("a DOCTYPE's external entity is never expanded") {
            val xml = """
                <!DOCTYPE a [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <a>&xxe;</a>
            """.trimIndent()

            // A DOCTYPE is allowed, so the external entity must be stopped by the
            // blank resolver and the unexpanded entity references instead: the
            // parse succeeds and the entity contributes no text.
            val document = XmlHardening.hardenedBuilder().parse(xml.byteInputStream())

            document.documentElement.textContent shouldBe ""
        }

        test("a rejected document throws without printing a fatal-error line") {
            val stderr = ByteArrayOutputStream()
            val original = System.err
            val failure = try {
                System.setErr(PrintStream(stderr, true, Charsets.UTF_8))
                runCatching { OpfXmlReader().read("<!DOCTYPE a><a>".encodeToByteArray()) }
            } finally {
                System.setErr(original)
            }

            failure.isFailure shouldBe true
            stderr.toString(Charsets.UTF_8) shouldBe ""
        }
    })

private val CONTAINER = """
    <?xml version="1.0" encoding="UTF-8"?>
    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
      <rootfiles>
        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
      </rootfiles>
    </container>
""".trimIndent().byteInputStream().readBytes()

/**
 * A [DocumentBuilderFactory] that rejects every non-JAXP feature name, the way
 * Android's parser does. Everything else delegates to a real factory, so a parse
 * that tolerates the rejection behaves as it would on Android.
 */
private class AndroidLikeFactory : DocumentBuilderFactory() {
    private val delegate = newInstance().apply { isNamespaceAware = true }

    override fun newDocumentBuilder(): DocumentBuilder = delegate.newDocumentBuilder()

    override fun setFeature(name: String, value: Boolean) {
        if (!name.startsWith(JAXP_PREFIX)) throw ParserConfigurationException("Android's parser does not know '$name'")
        delegate.setFeature(name, value)
    }

    override fun setNamespaceAware(awareness: Boolean) = delegate.setNamespaceAware(awareness)
    override fun isNamespaceAware(): Boolean = delegate.isNamespaceAware
    override fun setExpandEntityReferences(expand: Boolean) = delegate.setExpandEntityReferences(expand)
    override fun setValidating(validating: Boolean) = delegate.setValidating(validating)
    override fun isValidating(): Boolean = delegate.isValidating
    override fun setIgnoringElementContentWhitespace(whitespace: Boolean) =
        delegate.setIgnoringElementContentWhitespace(whitespace)

    override fun isIgnoringElementContentWhitespace(): Boolean = delegate.isIgnoringElementContentWhitespace
    override fun setIgnoringComments(ignore: Boolean) = delegate.setIgnoringComments(ignore)
    override fun isIgnoringComments(): Boolean = delegate.isIgnoringComments
    override fun setCoalescing(coalescing: Boolean) = delegate.setCoalescing(coalescing)
    override fun isCoalescing(): Boolean = delegate.isCoalescing
    override fun setAttribute(attribute: String, value: Any?) = Unit
    override fun getAttribute(attribute: String): Any? = delegate.getAttribute(attribute)
    override fun getFeature(name: String): Boolean = delegate.getFeature(name)

    private companion object {
        const val JAXP_PREFIX = "http://javax.xml.XMLConstants/"
    }
}
