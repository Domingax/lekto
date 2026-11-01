package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.TextBlock
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/**
 * The block walk over one content document.
 *
 * Real converter output — Project Gutenberg's ebookmaker, and the tools behind
 * the Zola books — writes the heading's anchor as a **self-closing** `<a/>`.
 * The HTML parser cannot know it closes, so it leaves the `<a>` open and the
 * rest of the chapter lands inside it. The walk must still read that chapter.
 *
 * Two pieces of structure the walk keeps rather than flattens: a `<br>` is a
 * hard line break inside its block, and a `ul`/`ol` keeps each item's marker,
 * rank and depth.
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

    @Test
    fun aHardBreakStaysInsideItsBlock() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <p>First line<br/>second line</p>
              <p>Next paragraph.</p>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf(
            "First line\nsecond line",
            "Next paragraph.",
        )
    }

    @Test
    fun aBreakAtTheEndOfABlockAddsNoEmptyLine() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body><p>Line<br/></p></body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("Line")
    }

    @Test
    fun twoBreaksInARowKeepTheirBlankLine() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body><p>Before<br/><br/>After</p></body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("Before\n\nAfter")
    }

    @Test
    fun theSourceFilesOwnLineWrappingIsNotAHardBreak() {
        // A pretty-printed EPUB wraps its paragraphs across source lines; only
        // `<br>` is the author's break. The rest collapses like any space.
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <p>It is very seldom that mere ordinary
              people like John and myself
              secure ancestral halls.</p>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf(
            "It is very seldom that mere ordinary people like John and myself secure ancestral halls.",
        )
    }

    @Test
    fun anUnorderedListMarksEveryItem() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <ul><li>Double KB press</li><li>See saw press</li></ul>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("• Double KB press", "• See saw press")
        blocks.map { block -> block.kind } shouldContainExactly listOf(
            BlockKind.LIST_ITEM,
            BlockKind.LIST_ITEM,
        )
        blocks.forEach { block -> block.listMarker shouldBe "• " }
    }

    @Test
    fun anOrderedListNumbersFromItsStart() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <ol start="3"><li>Warm up</li><li>Lift</li></ol>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("3. Warm up", "4. Lift")
    }

    @Test
    fun anItemCanOverrideItsOwnRank() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <ol><li>One</li><li value="7">Seven</li><li>Eight</li></ol>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("1. One", "7. Seven", "8. Eight")
    }

    @Test
    fun aNestedListSitsOneLevelDeeper() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <ul><li>Outer<ul><li>Inner</li></ul></li></ul>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("• Outer", "• Inner")
        blocks.map { block -> block.listDepth } shouldContainExactly listOf(0, 1)
    }

    @Test
    fun anItemWrappingParagraphsKeepsItsWords() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <ul><li><p>First paragraph.</p><p>Second paragraph.</p></li></ul>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("• First paragraph.\n\nSecond paragraph.")
    }

    @Test
    fun anItemThatWritesItsOwnBulletIsNotDoubleMarked() {
        // Project Gutenberg's licence types the bullet into the item itself.
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <ul><li>• You pay a royalty fee.</li></ul>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("• You pay a royalty fee.")
        blocks.single().listMarker shouldBe ""
    }

    @Test
    fun aListMarkerIsTheBlocksOwnText() {
        val block = XhtmlBlocks.of(
            """
            <html><body><ol><li>Only</li></ol></body></html>
            """.trimIndent().toByteArray(),
        ).single()

        block.listMarker shouldBe "1. "
        block.text.take(block.listMarker.length) shouldBe block.listMarker
        block.markerRange shouldBe (0 until block.listMarker.length)
    }

    @Test
    fun aBookWithoutStructureKeepsPlainParagraphs() {
        val blocks: List<TextBlock> = XhtmlBlocks.of(
            """
            <html><body><p>Nothing to mark.</p></body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.single().listMarker shouldBe ""
        blocks.single().markerRange.isEmpty() shouldBe true
    }
}
