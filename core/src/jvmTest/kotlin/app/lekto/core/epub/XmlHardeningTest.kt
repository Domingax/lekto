package app.lekto.core.epub

import app.lekto.core.epub.XmlHardening.setFeatureIfSupported
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The XML hardening is tolerant of feature names a platform's parser does not
 * know. This is the regression guard for the Android import failure: Android's
 * parser threw `ParserConfigurationException` on the Xerces feature
 * `http://apache.org/xml/features/nonvalidating/load-external-dtd`, which the
 * desktop JDK accepts, so a real EPUB imported on desktop and hung on Android
 * (issue #15).
 */
class XmlHardeningTest :
    FunSpec({

        test("setting a feature the parser does not know is tolerated") {
            val factory = DocumentBuilderFactory.newInstance()

            // A bogus feature URI is rejected the way Android rejects a Xerces
            // name; the helper must swallow it rather than fail the parse.
            factory.setFeatureIfSupported("http://example.invalid/feature/does-not-exist", true)

            factory.newDocumentBuilder().parse("<a/>".byteInputStream()).documentElement.tagName shouldBe "a"
        }

        test("the hardened factory parses a namespace-aware document") {
            val document = XmlHardening.hardenedFactory().newDocumentBuilder()
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

        test("a document with a DOCTYPE and an external entity is rejected, not fetched") {
            val xml = """
                <!DOCTYPE a [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <a>&xxe;</a>
            """.trimIndent()

            // The hardened factory disallows a DOCTYPE outright wherever the
            // platform supports that feature, so the external entity can never be
            // expanded. A platform without that feature still installs a blank
            // entity resolver, so the entity resolves to nothing.
            val result = runCatching {
                XmlHardening.hardenedFactory().newDocumentBuilder()
                    .apply { setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) } }
                    .parse(xml.byteInputStream())
            }

            val text = result.getOrNull()?.documentElement?.textContent.orEmpty()
            text shouldBe ""
            result.isSuccess shouldBe false
        }
    })
