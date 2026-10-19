package app.lekto.core.epub

import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test

/**
 * The block walk over one content document.
 *
 * Real converter output — Project Gutenberg's ebookmaker, and the tools behind
 * the Zola books — writes the heading's anchor as a **self-closing** `<a/>`.
 * The HTML parser cannot know it closes, so it leaves the `<a>` open and the
 * rest of the chapter lands inside it. The walk must still read that chapter.
 */
class XhtmlBlocksTest {

    @Test
    fun aSelfClosingAnchorInAHeadingDoesNotSwallowTheChapter() {
        val blocks = XhtmlBlocks.of(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
              <h1 class="Heading-1"><a id="auto-toc11"/>XI<br/></h1>
              <p>Nana grandissait, devenait garce.</p>
              <p>Une seconde phrase.</p>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf(
            "XI",
            "Nana grandissait, devenait garce.",
            "Une seconde phrase.",
        )
    }

    @Test
    fun aHeadingWithoutTheStrayAnchorKeepsEveryParagraph() {
        val blocks = XhtmlBlocks.of(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
              <h1>Chapter One</h1>
              <p>First paragraph.</p>
              <p>Second paragraph.</p>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf(
            "Chapter One",
            "First paragraph.",
            "Second paragraph.",
        )
    }
}
