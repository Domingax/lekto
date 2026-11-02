package app.lekto.core.book

import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.BlockKind
import app.lekto.core.text.ImageRef
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextRun
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Which paragraph the **Resume snippet** names (issue #258): the first
 * **Paragraph** at or after the saved **Reading position**, with headings, list
 * items and pictures skipped. The ranges are the rendered text's, so the
 * separator between two blocks counts exactly as it does for pagination.
 */
class BookSnippetTest :
    FunSpec({

        fun paragraph(text: String) = TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(text)))
        fun heading(text: String) = TextBlock(BlockKind.HEADING, listOf(TextRun(text)), headingLevel = 1)
        fun item(text: String) = TextBlock(BlockKind.LIST_ITEM, listOf(TextRun(text)), listMarker = "• ")
        fun image() = TextBlock(BlockKind.IMAGE, listOf(TextRun("")), image = ImageRef("x", 1f))

        fun startOf(blocks: List<TextBlock>, index: Int): Int =
            blocks.take(index).sumOf { block -> block.text.length + BLOCK_SEPARATOR.length }

        test("a position in a paragraph names that paragraph") {
            val blocks = listOf(paragraph("First"), paragraph("Second"))

            blocks.paragraphAt(startOf(blocks, 1) + 2)?.text shouldBe "Second"
        }

        test("a heading at the saved position is skipped for the paragraph after it") {
            val blocks = listOf(heading("Chapter One"), paragraph("The text"))

            blocks.paragraphAt(0)?.text shouldBe "The text"
        }

        test("a list item is never the reminder") {
            val blocks = listOf(item("A bullet"), paragraph("The text"))

            blocks.paragraphAt(0)?.text shouldBe "The text"
        }

        test("a picture is never the reminder") {
            val blocks = listOf(image(), paragraph("The text"))

            blocks.paragraphAt(0)?.text shouldBe "The text"
        }

        test("a position between two paragraphs names the next one") {
            val blocks = listOf(paragraph("First"), paragraph("Second"))
            val between = startOf(blocks, 1) - 1

            blocks.paragraphAt(between)?.text shouldBe "Second"
        }

        test("a position at the very end names the paragraph it closes") {
            val blocks = listOf(paragraph("First"), paragraph("Second"))
            val end = startOf(blocks, 1) + blocks[1].text.length

            blocks.paragraphAt(end)?.text shouldBe "Second"
        }

        test("a position past the last paragraph names nothing") {
            val blocks = listOf(paragraph("First"), paragraph("Second"))

            blocks.paragraphAt(startOf(blocks, 1) + blocks[1].text.length + 1) shouldBe null
        }

        test("an empty text names nothing") {
            emptyList<TextBlock>().paragraphAt(0) shouldBe null
        }

        test("a text with no paragraph names nothing") {
            listOf(heading("Chapter One"), item("A bullet")).paragraphAt(0) shouldBe null
        }

        test("a blank paragraph is skipped for the next real one") {
            val blocks = listOf(paragraph(""), paragraph("The text"))

            blocks.paragraphAt(0)?.text shouldBe "The text"
        }
    })
