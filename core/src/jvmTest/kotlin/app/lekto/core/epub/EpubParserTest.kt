package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.InlineStyle
import app.lekto.core.text.TextRun
import app.lekto.testkit.EpubFixtures
import app.lekto.testkit.parseText
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
            val book = parser.parseText(EpubFixtures.awkward())

            book.title shouldBe "Awkward & Real"
            book.language shouldBe "en"
        }

        test("turns the spine into blocks, headings and list items in order") {
            val kinds = parser.parseText(EpubFixtures.awkward()).blocks.map { it.kind }

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
            val paragraph = parser.parseText(EpubFixtures.awkward()).blocks
                .first { it.text.startsWith("An awkward") }

            paragraph.runs shouldContainExactly listOf(
                TextRun("An "),
                TextRun("awkward", setOf(InlineStyle.EMPHASIS)),
                TextRun(" book with "),
                TextRun("nested ", setOf(InlineStyle.STRONG)),
                TextRun("inline", setOf(InlineStyle.STRONG, InlineStyle.EMPHASIS)),
                TextRun(" markup—and a line break.\nAfter the break."),
            )
        }

        test("normalises whitespace and decodes named and numeric entities") {
            val text = parser.parseText(EpubFixtures.awkward()).plainText()

            text shouldContain "Whitespace is collapsed, and spans are ignored but kept."
            text shouldContain "First item & entity"
            text shouldContain "Second item éaccent"
        }

        test("skips non-linear spine items") {
            val text = parser.parseText(EpubFixtures.awkward()).plainText()

            text shouldContain "Chapter One"
            text shouldContain "第二章"
            text.contains("Cover") shouldBe false
            text.contains("Contents") shouldBe false
        }

        test("parses a minimal archive") {
            val book = parser.parseText(EpubFixtures.minimal())

            book.title shouldBe "Minimal"
            book.language shouldBe "fr"
            book.plainText() shouldBe "Bonjour le monde."
        }

        test("fails clearly when the container names no rootfile") {
            shouldThrow<EpubParseException> {
                parser.parseText(EpubFixtures.withoutRootfile())
            }
        }

        test("fails clearly when the OPF the container names is absent") {
            shouldThrow<EpubParseException> {
                parser.parseText(EpubFixtures.withMissingOpf())
            }
        }

        test("an EPUB 3 cover-image resolves to its archive entry") {
            val cover = parser.parse(EpubFixtures.coverImage()).cover

            cover?.contentEquals(EpubFixtures.coverImageBytes()) shouldBe true
            cover?.let { bytes -> ImageHeader.sizeOf(bytes) } shouldBe ImageSize(60, 90)
        }

        test("an EPUB 2 meta cover resolves through the manifest id") {
            val cover = parser.parse(EpubFixtures.metaCover()).cover

            cover?.contentEquals(EpubFixtures.metaCoverBytes()) shouldBe true
            cover?.let { bytes -> ImageHeader.sizeOf(bytes) } shouldBe ImageSize(40, 20)
        }

        test("a guide cover is a page, never an image") {
            parser.parse(EpubFixtures.guideCover()).cover shouldBe null
        }

        test("a cover the archive does not carry is no cover, and not a failure") {
            parser.parse(EpubFixtures.withMissingCover()).cover shouldBe null
        }

        test("an SVG cover is no cover: neither platform decoder reads it") {
            parser.parse(EpubFixtures.svgCover()).cover shouldBe null
        }

        test("a corrupt cover keeps the import green and the book cover-less") {
            val parsed = parser.parse(EpubFixtures.corruptCover())

            parsed.cover shouldBe null
            parsed.text.blocks.isNotEmpty() shouldBe true
        }

        test("a book with no cover declaration carries none") {
            parser.parse(EpubFixtures.minimal()).cover shouldBe null
        }
    })
