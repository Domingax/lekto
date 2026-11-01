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

    /**
     * A one-file EPUB whose EPUB 3 nav points inside the file at fragment
     * anchors, with a nested entry (ADR-0037). It also carries an NCX with
     * different labels, so the nav is proved to win.
     */
    fun nestedNav(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to NESTED_NAV_OPF,
        "OEBPS/nav.xhtml" to NESTED_NAV,
        "OEBPS/toc.ncx" to NESTED_NCX,
        "OEBPS/text/book.xhtml" to NESTED_BOOK,
    )

    /**
     * A nav that exercises the fallbacks (ADR-0037): an empty label takes the
     * chapter's first heading, a missing fragment starts its document, and an
     * entry outside the spine is dropped.
     */
    fun tocFallbacks(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to FALLBACK_OPF,
        "OEBPS/nav.xhtml" to FALLBACK_NAV,
        "OEBPS/text/book.xhtml" to FALLBACK_BOOK,
        "OEBPS/text/extra.xhtml" to FALLBACK_EXTRA,
    )

    /** An EPUB 2 book whose only table of contents is the NCX. */
    fun ncxOnly(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to NCX_OPF,
        "OEBPS/toc.ncx" to NCX,
        "OEBPS/text/one.xhtml" to NCX_CHAPTER_ONE,
        "OEBPS/text/two.xhtml" to NCX_CHAPTER_TWO,
    )

    /** A book with no nav and no NCX, so each spine document must become a chapter. */
    fun spineOnly(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to SPINE_ONLY_OPF,
        "OEBPS/text/untitled.xhtml" to SPINE_ONLY_UNTITLED,
        "OEBPS/text/titled.xhtml" to SPINE_ONLY_TITLED,
    )

    /** A minimal valid EPUB with one paragraph, for the happy-path assertions. */
    fun minimal(): ByteArray = archive(
        "META-INF/container.xml" to CONTAINER,
        "OEBPS/content.opf" to MINIMAL_OPF,
        "OEBPS/chapter.xhtml" to MINIMAL_CHAPTER,
    )

    /**
     * A book whose text is interleaved with pictures: a figure of its own, an
     * image inside a paragraph, and three that must fall back to their `alt` —
     * a remote source, an entry the archive does not carry, and a file that is
     * not an image at all (issue #263).
     */
    fun figures(): ByteArray = archiveBytes(
        "META-INF/container.xml" to CONTAINER.toByteArray(UTF_8),
        "OEBPS/content.opf" to FIGURES_OPF.toByteArray(UTF_8),
        "OEBPS/text/chapter.xhtml" to FIGURES_CHAPTER.toByteArray(UTF_8),
        "OEBPS/images/figure.png" to png(40, 20),
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
     * A bare OPF package with a title and a language and no manifest or spine,
     * so an XML-portability test parses the package document itself rather than
     * the whole book. Shared by the JVM and Android host suites, which both
     * assert `XmlHardening` tolerates the parser they run on.
     */
    fun opfDocument(): ByteArray = OPF_DOCUMENT.toByteArray(UTF_8)

    /**
     * Assembles a ZIP with a stored `mimetype` first — the one structural rule
     * every EPUB must satisfy — and the remaining entries deflated.
     */
    fun archive(vararg entries: Pair<String, String>): ByteArray =
        archiveBytes(*entries.map { (name, text) -> name to text.toByteArray(UTF_8) }.toTypedArray())

    /** The same archive, for an entry that is binary — an image, or a file pretending to be one. */
    fun archiveBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val all = listOf("mimetype" to "application/epub+zip".toByteArray(UTF_8)) + entries
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            all.forEachIndexed { index, (name, payload) ->
                val entry = ZipEntry(name)
                if (index == 0) entry.stored(payload)
                zip.putNextEntry(entry)
                zip.write(payload)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    /** A PNG header declaring [width]×[height]: enough for the reader, which reads sizes, not pixels. */
    private fun png(width: Int, height: Int): ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0, 0, 0, 13,
        'I'.code.toByte(), 'H'.code.toByte(), 'D'.code.toByte(), 'R'.code.toByte(),
        (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
        (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
        8, 2, 0, 0, 0,
        0, 0, 0, 0,
    )

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

    private val OPF_DOCUMENT = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000099</dc:identifier>
            <dc:title>Resilient</dc:title>
            <dc:language>en</dc:language>
          </metadata>
        </package>
    """.trimIndent()

    private val FIGURES_OPF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="en">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000012</dc:identifier>
            <dc:title>Pictures</dc:title>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
            <item id="ch" href="text/chapter.xhtml" media-type="application/xhtml+xml"/>
            <item id="fig" href="images/figure.png" media-type="image/png"/>
          </manifest>
          <spine>
            <itemref idref="ch"/>
          </spine>
        </package>
    """.trimIndent()

    /**
     * A figure of its own, an image inside a paragraph, and three pictures the
     * reader cannot show — remote, missing, and not an image — each with an
     * `alt` it must fall back to.
     */
    private val FIGURES_CHAPTER = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Pictures</title><meta charset="utf-8"/></head>
        <body>
          <h1>Pictures</h1>
          <div class="fig"><img src="../images/figure.png" alt="A bar chart"/></div>
          <p>Before <img src="../images/figure.png" alt="inline"/> after.</p>
          <p><img src="https://example.com/remote.png" alt="Remote picture"/></p>
          <p><img src="../images/missing.png" alt="Missing picture"/></p>
          <p><img src="chapter.xhtml" alt="Not a picture"/></p>
        </body>
        </html>
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

    private val NESTED_NAV_OPF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="en">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000020</dc:identifier>
            <dc:title>Nested</dc:title>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            <item id="book" href="text/book.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine toc="ncx">
            <itemref idref="book"/>
          </spine>
        </package>
    """.trimIndent()

    private val NESTED_NAV = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en">
        <head><title>Contents</title><meta charset="utf-8"/></head>
        <body>
          <nav epub:type="toc">
            <ol>
              <li>
                <a href="text/book.xhtml#one">First</a>
                <ol><li><a href="text/book.xhtml#sub">First A</a></li></ol>
              </li>
              <li><a href="text/book.xhtml#two">Second</a></li>
            </ol>
          </nav>
        </body>
        </html>
    """.trimIndent()

    private val NESTED_NCX = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <navMap>
            <navPoint id="n1"><navLabel><text>NCX One</text></navLabel><content src="text/book.xhtml#one"/></navPoint>
            <navPoint id="n2"><navLabel><text>NCX Two</text></navLabel><content src="text/book.xhtml#two"/></navPoint>
          </navMap>
        </ncx>
    """.trimIndent()

    private val NESTED_BOOK = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Book</title><meta charset="utf-8"/></head>
        <body>
          <h1 id="top">The Book</h1>
          <p>Text before the first chapter.</p>
          <h2 id="one">One</h2>
          <p>First chapter text.</p>
          <h3 id="sub">One A</h3>
          <p>Nested text.</p>
          <h2 id="two">Two</h2>
          <p>Second chapter text.</p>
        </body>
        </html>
    """.trimIndent()

    private val FALLBACK_OPF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="en">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000023</dc:identifier>
            <dc:title>Fallbacks</dc:title>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
            <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
            <item id="book" href="text/book.xhtml" media-type="application/xhtml+xml"/>
            <item id="extra" href="text/extra.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="book"/>
          </spine>
        </package>
    """.trimIndent()

    private val FALLBACK_NAV = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en">
        <head><title>Contents</title><meta charset="utf-8"/></head>
        <body>
          <nav epub:type="toc">
            <ol>
              <li><a href="text/book.xhtml#one"></a></li>
              <li><a href="text/book.xhtml#missing">Missing anchor</a></li>
              <li><a href="text/extra.xhtml#x">Outside the spine</a></li>
            </ol>
          </nav>
        </body>
        </html>
    """.trimIndent()

    private val FALLBACK_BOOK = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Book</title><meta charset="utf-8"/></head>
        <body>
          <h1 id="top">The Heading</h1>
          <p>Intro.</p>
          <h2 id="one">One</h2>
          <p>One text.</p>
        </body>
        </html>
    """.trimIndent()

    private val FALLBACK_EXTRA = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Extra</title><meta charset="utf-8"/></head>
        <body><h1>Extra</h1></body>
        </html>
    """.trimIndent()

    private val NCX_OPF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid" xml:lang="en">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000021</dc:identifier>
            <dc:title>NCX Book</dc:title>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
            <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
            <item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/>
            <item id="two" href="text/two.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine toc="ncx">
            <itemref idref="one"/>
            <itemref idref="two"/>
          </spine>
        </package>
    """.trimIndent()

    private val NCX = """
        <?xml version="1.0" encoding="UTF-8"?>
        <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
          <navMap>
            <navPoint id="n2" playOrder="1">
              <navLabel><text>Second Document</text></navLabel>
              <content src="text/two.xhtml"/>
            </navPoint>
            <navPoint id="n1" playOrder="2">
              <navLabel><text>First Document</text></navLabel>
              <content src="text/one.xhtml"/>
            </navPoint>
          </navMap>
        </ncx>
    """.trimIndent()

    private val NCX_CHAPTER_ONE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>One</title><meta charset="utf-8"/></head>
        <body><h1>One</h1><p>First document text.</p></body>
        </html>
    """.trimIndent()

    private val NCX_CHAPTER_TWO = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Two</title><meta charset="utf-8"/></head>
        <body><h1>Two</h1><p>Second document text.</p></body>
        </html>
    """.trimIndent()

    private val SPINE_ONLY_OPF = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid" xml:lang="en">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:identifier id="bookid">urn:isbn:9780000000022</dc:identifier>
            <dc:title>Bare</dc:title>
            <dc:language>en</dc:language>
          </metadata>
          <manifest>
            <item id="untitled" href="text/untitled.xhtml" media-type="application/xhtml+xml"/>
            <item id="titled" href="text/titled.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="untitled"/>
            <itemref idref="titled"/>
          </spine>
        </package>
    """.trimIndent()

    private val SPINE_ONLY_UNTITLED = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Untitled</title><meta charset="utf-8"/></head>
        <body><p>No heading here.</p></body>
        </html>
    """.trimIndent()

    private val SPINE_ONLY_TITLED = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
        <head><title>Titled</title><meta charset="utf-8"/></head>
        <body><h1>Bee</h1><p>With a heading.</p></body>
        </html>
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
