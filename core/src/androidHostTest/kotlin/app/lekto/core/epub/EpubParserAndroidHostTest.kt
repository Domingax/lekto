package app.lekto.core.epub

import app.lekto.testkit.EpubFixtures
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
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class EpubParserAndroidHostTest {

    @Test
    fun `a real EPUB imports through the Android XML parser`() {
        val book = EpubParser(OpfXmlReader(androidFactory())).parse(TestResources.bytes("/epub/pg1952.epub"))

        assertEquals("The Yellow Wallpaper", book.title)
        assertEquals("en", book.language)
        assertTrue("the spine produced no blocks", book.blocks.isNotEmpty())
    }

    /**
     * The focused regression guard: if `XmlHardening` stops tolerating a feature
     * Android does not know, `androidFactory()` throws `ParserConfigurationException`
     * here and in the parsing test above, and the host suite fails.
     */
    @Test
    fun `the hardened factory tolerates a parser that rejects Xerces-only features`() {
        val title = OpfDocument.parse(EpubFixtures.opfDocument(), OpfXmlReader(androidFactory())).title

        assertEquals("Resilient", title)
    }
}

/** Android's own parser, hardened the way production hardens the host JVM's. */
private fun androidFactory(): DocumentBuilderFactory {
    val type = Class.forName(ANDROID_FACTORY)
    val parser = type.getDeclaredConstructor().newInstance() as DocumentBuilderFactory
    return XmlHardening.hardenedFactory(parser)
}

private const val ANDROID_FACTORY = "org.apache.harmony.xml.parsers.DocumentBuilderFactoryImpl"
