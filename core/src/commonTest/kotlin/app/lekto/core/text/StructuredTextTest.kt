package app.lekto.core.text

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The rendered length of a parsed book (issue #196): the denominator of
 * **Reading progress**, so it must be exactly the text the reader lays out — the
 * blocks joined by a blank line, and nothing else.
 */
class StructuredTextTest :
    FunSpec({

        test("the rendered length joins the blocks with a blank line") {
            val text = StructuredText(
                title = null,
                language = "en",
                blocks = listOf(
                    block("On the quiet evening"),
                    block("the keeper lit the lantern"),
                    block("and waited"),
                ),
            )

            text.renderedLength() shouldBe text.blocks.joinToString(separator = "\n\n") { block -> block.text }.length
            text.renderedLength() shouldBe "On the quiet evening".length +
                "the keeper lit the lantern".length +
                "and waited".length +
                2 * 2
        }

        test("an empty book renders no text") {
            StructuredText(title = null, language = null, blocks = emptyList()).renderedLength() shouldBe 0
        }

        test("a single block renders just its text, with no separator") {
            StructuredText(title = null, language = null, blocks = listOf(block("Bonjour"))).renderedLength() shouldBe 7
        }
    })

private fun block(text: String): TextBlock = TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(text)))
