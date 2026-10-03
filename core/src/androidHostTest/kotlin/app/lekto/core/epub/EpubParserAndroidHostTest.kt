package app.lekto.core.epub

import app.lekto.testkit.TestResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The EPUB pipeline on a **simulated Android runtime** (ticket #49).
 *
 * `core/jvmTest` runs on the host JVM's Xerces, which accepts the Xerces-only
 * `DocumentBuilderFactory` features `XmlHardening` sets. Android's parser rejects
 * them, so a real EPUB once imported on desktop and failed on Android (issue #15)
 * without any test seeing it. This suite runs the same code under Robolectric and
 * drives the parse through Android's own `DocumentBuilderFactory`, so the
 * regression is exercised on the runtime that broke.
 *
 * The Android parser is `org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl`
 * from the `android-all` jar Robolectric runs against; `javax.*` is deliberately
 * never shadowed (Robolectric's `InstrumentationConfiguration`), so the host JVM's
 * factory would otherwise hide the difference — hence the explicit instantiation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EpubParserAndroidHostTest {

    @Test
    fun `a real EPUB imports through the Android XML parser`() {
        val book = EpubParser(androidOpfReader()).parse(TestResources.bytes("/epub/pg1952.epub"))

        assertEquals("The Yellow Wallpaper", book.title)
        assertEquals("en", book.language)
        assertTrue("the spine produced no blocks", book.blocks.isNotEmpty())
    }

    /**
     * The regression guard: if `XmlHardening` stops tolerating a feature Android
     * does not know, constructing the reader for the parsing test above — or this
     * call — throws `ParserConfigurationException`, and the host suite fails.
     */
    @Test
    fun `the hardened factory tolerates a parser that rejects Xerces-only features`() {
        val factory = XmlHardening.hardenedFactory(androidDocumentBuilderFactory())

        val reader = OpfXmlReader(factory)
        val title = OpfDocument.parse(OPF_DOCUMENT, reader).title

        assertEquals("Resilient", title)
    }
}

/** Android's own `DocumentBuilderFactory`, the one a device uses. */
private fun androidDocumentBuilderFactory(): DocumentBuilderFactory {
    val type = Class.forName(ANDROID_FACTORY)
    return type.getDeclaredConstructor().newInstance() as DocumentBuilderFactory
}

/** The production reader wired to Android's parser. */
private fun androidOpfReader(): OpfXmlReader {
    val factory = XmlHardening.hardenedFactory(androidDocumentBuilderFactory())
    return OpfXmlReader(factory)
}

private const val ANDROID_FACTORY = "org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl"

private val OPF_DOCUMENT = """
    <?xml version="1.0" encoding="UTF-8"?>
    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        <dc:identifier id="bookid">urn:isbn:9780000000099</dc:identifier>
        <dc:title>Resilient</dc:title>
        <dc:language>en</dc:language>
      </metadata>
    </package>
""".trimIndent().byteInputStream().readBytes()
