package app.lekto.core.text

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The TXT parser: line endings, blank-line paragraphs and whitespace are
 * normalised, and no language is invented (issue #15). It infers the structure a
 * plain text carries — chapter headings and a title — because the file declares
 * neither (ADR-0041).
 */
class TxtParserTest :
    FunSpec({

        /** The parsed text alone: a TXT carries no assets, so the test never needs the envelope. */
        fun parse(bytes: ByteArray): StructuredText = TxtParser().parse(bytes).text

        test("a blank line separates paragraphs and a single newline is a space") {
            val text = parse("First line\nwrapped here.\n\nSecond paragraph.".encodeToByteArray())

            text.blocks.map { it.text } shouldContainExactly listOf("First line wrapped here.", "Second paragraph.")
            text.blocks.all { it.kind == BlockKind.PARAGRAPH } shouldBe true
        }

        test("CRLF line endings and runs of whitespace are normalised") {
            val text = parse("A\t\tline  with   spaces.\r\n\r\nNext\r".encodeToByteArray())

            text.blocks.map { it.text } shouldContainExactly listOf("A line with spaces.", "Next")
        }

        test("blank and whitespace-only input has no blocks") {
            parse("\n\n   \n".encodeToByteArray()).blocks shouldBe emptyList()
            parse(ByteArray(0)).blocks shouldBe emptyList()
        }

        test("a text file carries no language") {
            parse("Bonjour le monde.".encodeToByteArray()).language shouldBe null
        }

        test("a text file with no recognisable heading is one chapter covering the whole text") {
            val text = parse("Bonjour le monde.\n\nComment vas-tu ?".encodeToByteArray())

            text.chapters shouldContainExactly listOf(Chapter("", startBlock = 0))
            text.blocks.none { block -> block.kind == BlockKind.HEADING } shouldBe true
        }

        test("a multi-byte character survives decoding") {
            val text = parse("日本語の文章です。".encodeToByteArray())

            text.plainText() shouldBe "日本語の文章です。"
        }

        test("numbered headings become chapters and heading blocks") {
            val text = parse(
                "Chapter I\n\nIt begins.\n\nChapter II\n\nIt continues.\n\nChapter III\n\nIt ends."
                    .encodeToByteArray(),
            )

            text.chapters shouldContainExactly listOf(
                Chapter("Chapter I", startBlock = 0),
                Chapter("Chapter II", startBlock = 2),
                Chapter("Chapter III", startBlock = 4),
            )
            text.blocks.filter { block -> block.kind == BlockKind.HEADING }
                .map { block -> block.text } shouldContainExactly
                listOf("Chapter I", "Chapter II", "Chapter III")
            text.blocks.filter { block -> block.kind == BlockKind.HEADING }
                .all { block -> block.headingLevel == 1 } shouldBe true
        }

        test("the blocks keep their count and their text, only the kind changes") {
            val text = parse("Chapter I\n\nFirst.\n\nChapter II\n\nSecond.".encodeToByteArray())

            text.blocks.map { block -> block.text } shouldContainExactly
                listOf("Chapter I", "First.", "Chapter II", "Second.")
        }

        test("French chapter headings and roman numerals are recognised") {
            val text = parse("Chapitre II\n\nUn.\n\nChapitre III\n\nDeux.".encodeToByteArray())

            text.chapters.map { chapter -> chapter.title } shouldContainExactly listOf("Chapitre II", "Chapitre III")
        }

        test("a heading whose title is hard-wrapped into the same paragraph stays one chapter") {
            val text = parse(
                "Chapter II.\nThe Carpet-Bag.\n\nA.\n\nChapter III.\nAnother.\n\nB.".encodeToByteArray(),
            )

            text.chapters.map { chapter -> chapter.title } shouldContainExactly
                listOf("Chapter II. The Carpet-Bag.", "Chapter III. Another.")
        }

        test("the word chapter inside a sentence is not a chapter") {
            val text = parse("The next chapter is long.\n\nIt is.".encodeToByteArray())

            text.chapters shouldContainExactly listOf(Chapter("", startBlock = 0))
        }

        test("a long paragraph that opens with a chapter word is not a heading") {
            val text = parse(
                (
                    "Chapter 1 recounts a long argument that runs on well past what a heading " +
                        "would ever be, and it is prose."
                    ).encodeToByteArray(),
            )

            text.chapters shouldContainExactly listOf(Chapter("", startBlock = 0))
            text.blocks.single().kind shouldBe BlockKind.PARAGRAPH
        }

        test("headings whose numbering does not increase are not a chapter list") {
            val text = parse("Chapter 3\n\nA.\n\nChapter 2\n\nB.".encodeToByteArray())

            text.chapters shouldContainExactly listOf(Chapter("", startBlock = 0))
        }

        test("a single heading is not a chapter list") {
            val text = parse("Chapter I\n\nIts only chapter.".encodeToByteArray())

            text.chapters shouldContainExactly listOf(Chapter("", startBlock = 0))
            text.blocks.map { block -> block.kind } shouldContainExactly
                listOf(BlockKind.PARAGRAPH, BlockKind.PARAGRAPH)
        }

        test("a bare numeral series counts from one and steps by one") {
            val text = parse("1\n\nUn.\n\n2\n\nDeux.".encodeToByteArray())

            text.chapters.map { chapter -> chapter.title } shouldContainExactly listOf("1", "2")
        }

        test("lowercase roman numerals count the same as uppercase ones") {
            val text = parse("i\n\nUn.\n\nii\n\nDeux.".encodeToByteArray())

            text.chapters.map { chapter -> chapter.title } shouldContainExactly listOf("i", "ii")
        }

        test("scraped page numbers are not a chapter list") {
            val text = parse("12\n\nA.\n\n13\n\nB.".encodeToByteArray())

            text.chapters shouldContainExactly listOf(Chapter("", startBlock = 0))
        }

        test("the first line is the title when it reads like one") {
            parse("Les Trois Mousquetaires\n\nChapter I\n\nText.".encodeToByteArray()).title shouldBe
                "Les Trois Mousquetaires"
        }

        test("the title is the first line, not the paragraph it opens") {
            val text = parse(
                "Les Trois Mousquetaires\npar Alexandre Dumas\n\nChapter I\n\nText.".encodeToByteArray(),
            )

            text.title shouldBe "Les Trois Mousquetaires"
        }

        test("a first line that ends a sentence is not a title") {
            parse("Il était une fois.\n\nLa suite.".encodeToByteArray()).title shouldBe null
        }

        test("a first line that is a chapter heading is not a title") {
            parse("Chapter I\n\nText.".encodeToByteArray()).title shouldBe null
        }

        test("a first line that opens a chapter without a numeral is not a title") {
            parse("CHAPITRE PREMIER\n\nText.\n\nCHAPITRE II\n\nSuite.".encodeToByteArray()).title shouldBe
                null
        }

        test("a long first paragraph is not a title") {
            val paragraph = "Ce paragraphe commence le récit et continue bien au-delà de ce qu'un titre occupe"

            parse("$paragraph\n\nLa suite.".encodeToByteArray()).title shouldBe null
        }
    })
