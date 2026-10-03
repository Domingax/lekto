package app.lekto.core.text

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The TXT parser: line endings, blank-line paragraphs and whitespace are
 * normalised, and no title or language is invented (issue #15).
 */
class TxtParserTest :
    FunSpec({

        test("a blank line separates paragraphs and a single newline is a space") {
            val text = TxtParser().parse("First line\nwrapped here.\n\nSecond paragraph.".encodeToByteArray())

            text.blocks.map { it.text } shouldContainExactly listOf("First line wrapped here.", "Second paragraph.")
            text.blocks.all { it.kind == BlockKind.PARAGRAPH } shouldBe true
        }

        test("CRLF line endings and runs of whitespace are normalised") {
            val text = TxtParser().parse("A\t\tline  with   spaces.\r\n\r\nNext\r".encodeToByteArray())

            text.blocks.map { it.text } shouldContainExactly listOf("A line with spaces.", "Next")
        }

        test("blank and whitespace-only input has no blocks") {
            TxtParser().parse("\n\n   \n".encodeToByteArray()).blocks shouldBe emptyList()
            TxtParser().parse(ByteArray(0)).blocks shouldBe emptyList()
        }

        test("a text file carries no title or language") {
            val text = TxtParser().parse("Bonjour le monde.".encodeToByteArray())

            text.title shouldBe null
            text.language shouldBe null
            text.plainText() shouldBe "Bonjour le monde."
        }

        test("a multi-byte character survives decoding") {
            val text = TxtParser().parse("日本語の文章です。".encodeToByteArray())

            text.plainText() shouldBe "日本語の文章です。"
        }
    })
