package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.SCENE_BREAK_TEXT
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
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
 * Pieces of structure the walk keeps rather than flattens: a `<br>` is a hard
 * line break inside its block, a `ul`/`ol` keeps each item's marker, rank and
 * depth, a `<pre>` keeps its whitespace verbatim, and an `<hr>` becomes a
 * scene-break block instead of disappearing.
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
    fun aPreformattedBlockKeepsItsLineBreaksAndIndentation() {
        val block = XhtmlBlocks.of(
            "<html><body><pre>def main():\n    return 0</pre></body></html>".toByteArray(),
        ).single()

        block.kind shouldBe BlockKind.PREFORMATTED
        block.text shouldBe "def main():\n    return 0"
    }

    @Test
    fun aPreformattedBlockDropsOnlyTheNewlineTheHtmlSpecDrops() {
        // The spec ignores exactly one newline after the start tag, so a second
        // one is content: the pretty-printer's blank first line goes, a real one stays.
        val block = XhtmlBlocks.of(
            "<html><body><pre>\n\nkept blank line</pre></body></html>".toByteArray(),
        ).single()

        block.text shouldBe "\nkept blank line"
    }

    @Test
    fun aPreformattedBlockKeepsItsBlankLinesAndTrailingWhitespace() {
        val block = XhtmlBlocks.of(
            "<html><body><pre>one\n\ntwo  \n</pre></body></html>".toByteArray(),
        ).single()

        block.text shouldBe "one\n\ntwo  \n"
    }

    @Test
    fun aPreformattedBlocksInlineStylesAndBreaksSurvive() {
        val block = XhtmlBlocks.of(
            "<html><body><pre>plain <em>soft</em><br/>next</pre></body></html>".toByteArray(),
        ).single()

        block.kind shouldBe BlockKind.PREFORMATTED
        block.text shouldBe "plain soft\nnext"
        block.runs.single { run -> run.text == "soft" }.styles shouldBe setOf(InlineStyle.EMPHASIS)
    }

    @Test
    fun aSceneBreakKeepsTheAuthorsOrnament() {
        val blocks = XhtmlBlocks.of(
            "<html><body><p>before</p><hr/><p>after</p></body></html>".toByteArray(),
        )

        blocks.map { block -> block.kind } shouldContainExactly listOf(
            BlockKind.PARAGRAPH,
            BlockKind.SCENE_BREAK,
            BlockKind.PARAGRAPH,
        )
        blocks.map { block -> block.text } shouldContainExactly listOf("before", SCENE_BREAK_TEXT, "after")
        SCENE_BREAK_TEXT shouldBe "· · ·"
    }

    @Test
    fun aSceneBreakInsideAQuoteTakesItsDepth() {
        val blocks = XhtmlBlocks.of(
            "<html><body><blockquote><p>quoted</p><hr/></blockquote></body></html>".toByteArray(),
        )

        blocks.single { block -> block.kind == BlockKind.SCENE_BREAK }.quoteDepth shouldBe 1
    }

    @Test
    fun superscriptSubscriptAndSmallCarryTheirInlineStyle() {
        // A footnote marker, an exponent and a formula read flat without their
        // styles: `<sup>`/`<sub>`/`<small>` are the common inline tags for them.
        val block = XhtmlBlocks.of(
            """
            <html><body>
              <p>H<sub>2</sub>O, 2<sup>nd</sup> and <small>fine print</small>.</p>
            </body></html>
            """.trimIndent().toByteArray(),
        ).single()

        block.text shouldBe "H2O, 2nd and fine print."
        block.runs shouldContainExactly listOf(
            TextRun("H"),
            TextRun("2", setOf(InlineStyle.SUBSCRIPT)),
            TextRun("O, 2"),
            TextRun("nd", setOf(InlineStyle.SUPERSCRIPT)),
            TextRun(" and "),
            TextRun("fine print", setOf(InlineStyle.SMALL)),
            TextRun("."),
        )
    }

    @Test
    fun aSmallSuperscriptKeepsBothStyles() {
        val run = XhtmlBlocks.of(
            """
            <html><body><p><small>note<sup>1</sup></small></p></body></html>
            """.trimIndent().toByteArray(),
        ).single().runs.last()

        run shouldBe TextRun("1", setOf(InlineStyle.SMALL, InlineStyle.SUPERSCRIPT))
    }

    @Test
    fun aTableKeepsOneBlockPerRowWithItsCellsJoined() {
        // A caption and each row stay whole: the cells sit on one line, so the
        // row/column relation is visible without a layout pass (issue #264).
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <table>
                <caption>Weekly plan</caption>
                <thead><tr><th>Day</th><th>Load</th></tr></thead>
                <tbody>
                  <tr><td>Monday</td><td>Squat</td></tr>
                  <tr><td>Tuesday</td><td>Rest</td></tr>
                </tbody>
              </table>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf(
            "Weekly plan",
            "Day · Load",
            "Monday · Squat",
            "Tuesday · Rest",
        )
        blocks.map { block -> block.kind } shouldContainExactly listOf(
            BlockKind.CAPTION,
            BlockKind.TABLE_ROW,
            BlockKind.TABLE_ROW,
            BlockKind.TABLE_ROW,
        )
    }

    @Test
    fun aHeaderCellIsStrongAndTheCellSeparatorIsPlain() {
        // A direct `<tr>` (no `thead`/`tbody`) is a row; leaving the separator
        // plain keeps it from inheriting the bold of the cells around it.
        val row = XhtmlBlocks.of(
            """
            <html><body>
              <table><tr><th>Lift</th><th>Load</th></tr></table>
            </body></html>
            """.trimIndent().toByteArray(),
        ).single()

        row.kind shouldBe BlockKind.TABLE_ROW
        row.runs shouldContainExactly listOf(
            TextRun("Lift", setOf(InlineStyle.STRONG)),
            TextRun(" · "),
            TextRun("Load", setOf(InlineStyle.STRONG)),
        )
    }

    @Test
    fun aCellWrappingParagraphsKeepsEveryWord() {
        // A cell whose content is block markup is read flattened, or its words
        // would be dropped: real programme tables write each cell as a `<p>`.
        val row = XhtmlBlocks.of(
            """
            <html><body>
              <table><tr><td>Day</td><td><p>5×5</p><p>squat</p></td></tr></table>
            </body></html>
            """.trimIndent().toByteArray(),
        ).single()

        row.text shouldBe "Day · 5×5\n\nsquat"
    }

    @Test
    fun aRowWithoutWordsEmitsNoBlock() {
        val blocks = XhtmlBlocks.of(
            """
            <html><body>
              <table><tr><td></td><td> </td></tr><tr><td>kept</td></tr></table>
            </body></html>
            """.trimIndent().toByteArray(),
        )

        blocks.map { block -> block.text } shouldContainExactly listOf("kept")
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
