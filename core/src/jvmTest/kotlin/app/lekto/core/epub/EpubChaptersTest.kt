package app.lekto.core.epub

import app.lekto.core.text.BlockKind
import app.lekto.core.text.Chapter
import app.lekto.core.text.chapterAt
import app.lekto.core.text.readingUnits
import app.lekto.testkit.EpubFixtures
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * The book's **Chapter list**, derived from the table of contents (ADR-0037,
 * issue #198): the EPUB 3 nav first, then the NCX, then one chapter per spine
 * document. A fragment anchor becomes the block it points at, ordering follows
 * the spine rather than `playOrder`, and an empty label falls back to the
 * chapter's first heading then its file name.
 */
class EpubChaptersTest :
    FunSpec({

        val parser = EpubParser()

        test("a nav resolves fragment anchors to block starts, in one file") {
            val book = parser.parse(EpubFixtures.nestedNav())

            book.chapters shouldContainExactly listOf(
                Chapter("First", startBlock = 2, children = listOf(Chapter("First A", startBlock = 4))),
                Chapter("Second", startBlock = 6),
            )
        }

        test("the nav wins over an NCX that names the same entries differently") {
            val book = parser.parse(EpubFixtures.nestedNav())

            book.chapters.map { chapter -> chapter.title } shouldContainExactly listOf("First", "Second")
        }

        test("text before the first chapter has no chapter, and the last chapter runs to the end") {
            val chapters = parser.parse(EpubFixtures.nestedNav()).chapters

            chapters.chapterAt(0) shouldBe null
            chapters.chapterAt(1) shouldBe null
            chapters.chapterAt(2)?.title shouldBe "First"
            chapters.chapterAt(5)?.title shouldBe "First A"
            chapters.chapterAt(7)?.title shouldBe "Second"
        }

        test("a part that carries text is a reading unit; a leaf is one too") {
            val chapters = parser.parse(EpubFixtures.nestedNav()).chapters

            chapters.readingUnits().map { chapter -> chapter.title } shouldContainExactly
                listOf("First", "First A", "Second")
        }

        test("the NCX is read when there is no nav, ordered by the spine not by playOrder") {
            val book = parser.parse(EpubFixtures.ncxOnly())

            book.chapters shouldContainExactly listOf(
                Chapter("First Document", startBlock = 0),
                Chapter("Second Document", startBlock = 2),
            )
        }

        test("a label falls back to the first heading, an unresolved fragment to the document start") {
            val book = parser.parse(EpubFixtures.tocFallbacks())

            book.chapters shouldContainExactly listOf(
                Chapter("Missing anchor", startBlock = 0),
                Chapter("One", startBlock = 2),
            )
            // The entry outside the spine is dropped, so its heading never appears.
            book.chapters.map { chapter -> chapter.title } shouldContainExactly listOf("Missing anchor", "One")
        }

        test("no usable table of contents makes one chapter per spine document") {
            val book = parser.parse(EpubFixtures.spineOnly())

            book.chapters shouldContainExactly listOf(
                Chapter("untitled.xhtml", startBlock = 0),
                Chapter("Bee", startBlock = 1),
            )
        }

        test("a one-file book still exposes its blocks, headings and anchors") {
            val book = parser.parse(EpubFixtures.nestedNav())

            book.blocks.map { block -> block.kind } shouldContainExactly listOf(
                BlockKind.HEADING,
                BlockKind.PARAGRAPH,
                BlockKind.HEADING,
                BlockKind.PARAGRAPH,
                BlockKind.HEADING,
                BlockKind.PARAGRAPH,
                BlockKind.HEADING,
                BlockKind.PARAGRAPH,
            )
        }
    })
