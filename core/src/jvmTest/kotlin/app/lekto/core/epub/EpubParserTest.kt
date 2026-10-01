package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.TextRun
import app.lekto.testkit.EpubFixtures
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * The EPUB pipeline of the spike (ticket #10): a real ZIP/OPF/spine archive
 * parses into structured paragraphs that keep their inline formatting. The
 * fixture is deliberately awkward, so the parser is exercised against the quirks
 * a book actually carries rather than a clean hello-world.
 */
class EpubParserTest :
    FunSpec({

        val parser = EpubParser()

        test("reads the metadata from the OPF") {
            val book = parser.parse(EpubFixtures.awkward())

            book.title shouldBe "Awkward & Real"
            book.language shouldBe "en"
        }

        test("turns the spine into blocks, headings and list items in order") {
            val kinds = parser.parse(EpubFixtures.awkward()).blocks.map { it.kind }

            kinds shouldContainExactly listOf(
                BlockKind.HEADING,
                BlockKind.PARAGRAPH,
                BlockKind.PARAGRAPH,
                BlockKind.PARAGRAPH,
                BlockKind.LIST_ITEM,
                BlockKind.LIST_ITEM,
                BlockKind.PARAGRAPH,
                BlockKind.HEADING,
                BlockKind.PARAGRAPH,
            )
        }

        test("preserves nested inline formatting as runs") {
            val paragraph = parser.parse(EpubFixtures.awkward()).blocks
                .first { it.text.startsWith("An awkward") }

            paragraph.runs shouldContainExactly listOf(
                TextRun("An "),
                TextRun("awkward", setOf(InlineStyle.EMPHASIS)),
                TextRun(" book with "),
                TextRun("nested ", setOf(InlineStyle.STRONG)),
                TextRun("inline", setOf(InlineStyle.STRONG, InlineStyle.EMPHASIS)),
                TextRun(" markup—and a line break. After the break."),
            )
        }

        test("normalises whitespace and decodes named and numeric entities") {
            val text = parser.parse(EpubFixtures.awkward()).plainText()

            text shouldContain "Whitespace is collapsed, and spans are ignored but kept."
            text shouldContain "First item & entity"
            text shouldContain "Second item éaccent"
        }

        test("skips non-linear spine items") {
            val text = parser.parse(EpubFixtures.awkward()).plainText()

            text shouldContain "Chapter One"
            text shouldContain "第二章"
            text.contains("Cover") shouldBe false
            text.contains("Contents") shouldBe false
        }

        test("parses a minimal archive") {
            val book = parser.parse(EpubFixtures.minimal())

            book.title shouldBe "Minimal"
            book.language shouldBe "fr"
            book.plainText() shouldBe "Bonjour le monde."
        }

        test("fails clearly when the container names no rootfile") {
            shouldThrow<EpubParseException> {
                parser.parse(EpubFixtures.withoutRootfile())
            }
        }

        test("fails clearly when the OPF the container names is absent") {
            shouldThrow<EpubParseException> {
                parser.parse(EpubFixtures.withMissingOpf())
            }
        }
    })
