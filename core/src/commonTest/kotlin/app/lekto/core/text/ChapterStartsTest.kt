package app.lekto.core.text

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Naming the **Chapter** a **Reading position** sits in without the blocks it
 * was measured against (issue #260): a chapter start is a character offset into
 * the rendered text, and the deepest chapter at or before the position wins.
 */
class ChapterStartsTest :
    FunSpec({

        // Block offsets: "Front" 0, "One" 7, "Two" 12, "Three" 17.
        val text = StructuredText(
            title = "A Book",
            language = "en",
            blocks = listOf(
                paragraph("Front"),
                paragraph("One"),
                paragraph("Two"),
                paragraph("Three"),
            ),
            chapters = listOf(
                Chapter("One", startBlock = 1),
                Chapter("Two", startBlock = 2, children = listOf(Chapter("Two-a", startBlock = 3))),
            ),
        )

        test("a chapter start is its first block's character offset") {
            text.chapterStarts() shouldBe listOf(
                ChapterStart("One", 7),
                ChapterStart("Two", 12),
                ChapterStart("Two-a", 17),
            )
        }

        test("the deepest chapter at or before the offset names the position") {
            val starts = text.chapterStarts()

            starts.chapterTitleAt(7) shouldBe "One"
            starts.chapterTitleAt(11) shouldBe "One"
            starts.chapterTitleAt(12) shouldBe "Two"
            starts.chapterTitleAt(17) shouldBe "Two-a"
            starts.chapterTitleAt(999) shouldBe "Two-a"
        }

        test("a position before the first chapter names none") {
            text.chapterStarts().chapterTitleAt(6) shouldBe null
        }

        test("a part with text before its first child names itself there") {
            val part = StructuredText(
                title = "A Book",
                language = "en",
                blocks = listOf(paragraph("Front"), paragraph("Part"), paragraph("Child")),
                chapters = listOf(
                    Chapter("Part One", startBlock = 1, children = listOf(Chapter("Child", startBlock = 2))),
                ),
            ).chapterStarts()

            part.chapterTitleAt(7) shouldBe "Part One"
            part.chapterTitleAt(13) shouldBe "Child"
        }

        test("a book with one chapter names none, because it has no chapter list") {
            val single = StructuredText(
                title = "A Book",
                language = "en",
                blocks = listOf(paragraph("Only")),
                chapters = listOf(Chapter("Only", startBlock = 0)),
            ).chapterStarts()

            single.chapterTitleAt(0) shouldBe null
        }

        test("a book with no chapter list names none") {
            val bare = StructuredText(
                title = "A Book",
                language = "en",
                blocks = listOf(paragraph("Only")),
            )

            bare.chapterStarts() shouldBe emptyList()
            bare.chapterStarts().chapterTitleAt(0) shouldBe null
        }

        test("a chapter whose start block is past the text reads as the end") {
            val past = StructuredText(
                title = "A Book",
                language = "en",
                blocks = listOf(paragraph("One")),
                chapters = listOf(Chapter("One", startBlock = 0), Chapter("After", startBlock = 99)),
            ).chapterStarts()

            past.last().startOffset shouldBe 3
            past.chapterTitleAt(3) shouldBe "After"
        }
    })

private fun paragraph(text: String) = TextBlock(BlockKind.PARAGRAPH, listOf(TextRun(text)))
