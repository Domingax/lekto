package app.lekto.testkit

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds real EPUB archives in memory for the parser tests (ticket #10).
 *
 * The archive is a genuine EPUB container — a stored `mimetype` first, a
 * `container.xml` pointing at an OPF, a manifest and a spine — not a stub of one.
 * [awkward] deliberately packs the quirks a real, ugly book carries: XHTML named
 * entities, a `<br/>`, whitespace runs, nested inline markup, a container element
 * around a paragraph, non-linear spine items, subdirectory hrefs, a CJK chapter
 * and non-content manifest items. It lives in `testkit` rather than a test source
 * set so the parser's tests share one fixture (docs/testing.md).
 */
object EpubFixtures {

    /** A deliberately awkward but valid EPUB, used for the extraction golden. */
    fun awkward(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to AWKWARD_OPF,
        "OEBPS/nav.xhtml" to NAV,
        "OEBPS/cover.xhtml" to COVER,
        "OEBPS/text/chapter1.xhtml" to CHAPTER_ONE,
        "OEBPS/text/chapter2.xhtml" to CHAPTER_TWO,
        "OEBPS/styles/main.css" to "body { font-family: serif; }",
        "OEBPS/images/cover.png" to "not-really-a-png",
    )

    /** A minimal valid EPUB with one paragraph, for the happy-path assertions. */
    fun minimal(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to MINIMAL_OPF,
        "OEBPS/chapter.xhtml" to MINIMAL_CHAPTER,
    )

    /**
     * The same archive with a `container.xml` that names no rootfile, so the
     * parser has nothing to open and must fail with a clear message.
     */
    fun withoutRootfile(): ByteArray = archive(
        "META-INF/container.xml" to SEALED_CONTAINER,
        "OEBPS/content.opf" to MINIMAL_OPF,
    )

    /** A container that points at an OPF the archive does not carry. */
    fun withMissingOpf(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
    )

    /**
     * Assembles a ZIP with a stored `mimetype` first — the one structural rule
     * every EPUB must satisfy — and the remaining entries deflated.
     */
    fun archive(vararg entries: Pair<String, String>): ByteArray {
        val all = listOf("mimetype" to "application/epub+zip") + entries
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            all.forEachIndexed { index, (name, content) ->
                val payload = content.toByteArray(UTF_8)
                val entry = ZipEntry(name)
                if (index == 0) entry.stored(payload)
                zip.putNextEntry(entry)
                zip.write(payload)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun ZipEntry.stored(payload: ByteArray) {
        method = ZipEntry.STORED
        size = payload.size.toLong()
        compressedSize = payload.size.toLong()
        crc = CRC32().apply { update(payload) }.value
    }

    private val CONTAINER = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private val SEALED_CONTAINER = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles/>
        </container>
    """.trimIndent()

    private val AWKWARD_OPF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="en">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000010</dc:identifier>
            <dc:title>Awkward &amp; Real</dc:title>
            <dc:language>en</dc:language>
            <dc:creator>Spike Author</dc:creator>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
            <item id="ch1" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
            <item id="ch2" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
            <item id="css" href="styles/main.css" media-type="text/css"/>
            <item id="img" href="images/cover.png" media-type="image/png"/>
          </manifest>
          <spine>
            <itemref idref="cover" linear="no"/>
            <itemref idref="nav" linear="no"/>
            <itemref idref="ch1"/>
            <itemref idref="ch2"/>
          </spine>
        </package>
    """.trimIndent()

    private val MINIMAL_OPF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="fr">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000011</dc:identifier>
            <dc:title>Minimal</dc:title>
            <dc:language>fr</dc:language>
          </metadata>
          <manifest>
            <item id="ch" href="chapter.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="ch"/>
          </spine>
        </package>
    """.trimIndent()

    private val NAV = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Contents</title><meta charset="utf-8"/></head>
        <body><nav epub:type="toc"><ol><li><a href="text/chapter1.xhtml">Chapter One</a></li></ol></nav></body>
        </html>
    """.trimIndent()

    private val COVER = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Cover</title><meta charset="utf-8"/></head>
        <body><div><img src="images/cover.png" alt="Cover"/></div></body>
        </html>
    """.trimIndent()

    private val CHAPTER_ONE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE html>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
        <head><title>Chapter One</title><meta charset="utf-8"/></head>
        <body>
          <h1>Chapter One</h1>
          <p>An <em>awkward</em> book&nbsp;with <strong>nested <em>inline</em></strong> markup&mdash;and a line break.<br/>After the break.</p>
          <p>Whitespace    is     collapsed, and <span class="x">spans</span> are ignored but kept.</p>
          <blockquote><p>A quoted <b>paragraph</b> with an <i>italic</i> turn.</p></blockquote>
          <ul>
            <li>First item &amp; entity</li>
            <li>Second item &#233;accent</li>
          </ul>
          <p>Trailing <a href="https://example.com">link text</a> ends it.</p>
        </body>
        </html>
    """.trimIndent()

    private val CHAPTER_TWO = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="ja" lang="ja">
        <head><title>第二章</title><meta charset="utf-8"/></head>
        <body>
          <h2>第二章</h2>
          <p>日本語の文章です。これはテストです。</p>
        </body>
        </html>
    """.trimIndent()

    private val MINIMAL_CHAPTER = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="fr">
        <head><title>Un</title><meta charset="utf-8"/></head>
        <body><p>Bonjour le monde.</p></body>
        </html>
    """.trimIndent()
}
