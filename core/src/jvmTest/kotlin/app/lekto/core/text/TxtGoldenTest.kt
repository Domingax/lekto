package app.lekto.core.text

import app.lekto.testkit.TestResources
import app.lekto.testkit.parseText
import io.kotest.core.spec.style.FunSpec
import java.io.File
import kotlin.random.Random
import kotlin.test.fail

/**
 * The extraction golden the TXT parser's level demands (docs/testing.md#test-levels):
 * the structure inferred from a generated corpus and from one awkward real book is
 * pinned in `resources/golden/`, so a change that silently drops a chapter, splits
 * a heading or accepts a stray line fails here until the golden is deliberately
 * updated (ADR-0041).
 *
 * The real fixture is *Les Trois Mousquetaires*: CRLF, a Project Gutenberg header,
 * a table of contents packed into one paragraph and headings written on two lines.
 */
class TxtGoldenTest :
    FunSpec({

        test("the generated corpus parses to the pinned structure") {
            val actual = corpus().joinToString(separator = "\n") { (name, text) ->
                "=== $name\n${TxtParser().parseText(text.encodeToByteArray()).pinnedShape()}"
            }

            assertGolden("/golden/txt-corpus.txt", actual)
        }

        test("the real French book parses to the pinned outline") {
            val actual = TxtParser().parseText(TestResources.bytes("/txt/les-trois-mousquetaires.txt")).pinnedOutline()

            assertGolden("/golden/txt-les-trois-mousquetaires.txt", actual)
        }
    })

/** Every block and chapter, for a document small enough to pin whole. */
private fun StructuredText.pinnedShape(): String = buildString {
    append(header())
    blocks.forEachIndexed { index, block ->
        val level = block.headingLevel.takeIf { block.kind == BlockKind.HEADING }?.let { " $it" }.orEmpty()
        appendLine("  $index ${block.kind}$level ${block.text.brief()}")
    }
    appendChapters(chapters)
}

/** The title and the size of the text every golden line starts with. */
private fun StructuredText.header(): String = "title: ${title ?: "-"}\nblocks: ${blocks.size}\n"

/** One chapter per line, with the block it starts at. */
private fun StringBuilder.appendChapters(chapters: List<Chapter>): StringBuilder {
    appendLine("chapters: ${chapters.size}")
    chapters.forEach { chapter -> appendLine("  ${chapter.startBlock} ${chapter.title.ifEmpty { "-" }}") }
    return this
}

/** The block's opening words: a golden pins the structure, not the whole prose. */
private fun String.brief(): String = if (length <= BRIEF_LENGTH) this else "${take(BRIEF_LENGTH)}…"

/** Only the shape of a book too large to pin whole: its title, its chapters and their places. */
private fun StructuredText.pinnedOutline(): String = buildString {
    append(header())
    appendLine("headings: ${blocks.count { block -> block.kind == BlockKind.HEADING }}")
    appendLine("chars: ${blocks.sumOf { block -> block.text.length }}")
    appendChapters(chapters)
}

/**
 * The awkward documents the inference has to survive, then a seeded corpus whose
 * headings, hard wraps and paragraph counts vary within what a TXT really carries.
 */
private fun corpus(): List<Pair<String, String>> = listOf(
    "prose" to "Un récit sans titre ni chapitre.\n\nIl continue.\n\nEt finit.",
    "title-and-chapters" to "Les Trois Mousquetaires\n\nChapitre II\n\nUn.\n\nChapitre III\n\nDeux.",
    "packed-table-of-contents" to buildString {
        appendLine("CHAPITRE PREMIER LES TROIS PRÉSENTS")
        repeat(20) { index -> appendLine("CHAPITRE ${roman(index + 2)} UN TITRE") }
        appendLine()
        appendLine("CHAPITRE II.")
        appendLine("L'ANTICHAMBRE")
        appendLine()
        appendLine("Un paragraphe.")
        appendLine()
        appendLine("CHAPITRE III.")
        appendLine("L'AUDIENCE")
        appendLine()
        appendLine("Un autre paragraphe.")
    },
    "restarted-numbering" to buildString {
        repeat(3) { index ->
            appendLine("CHAPTER ${index + 1}. A Title.")
            appendLine()
            appendLine("A paragraph.")
            appendLine()
        }
        repeat(3) { index ->
            appendLine("CHAPTER ${index + 1}. A Title.")
            appendLine()
            appendLine("A paragraph.")
            appendLine()
        }
    },
    "bare-numerals" to "1\n\nUn.\n\n2\n\nDeux.\n\n3\n\nTrois.",
    "scraped-page-numbers" to "12\n\nUn.\n\n13\n\nDeux.\n\n14\n\nTrois.",
    "descending-numbering" to "Chapter 3\n\nUn.\n\nChapter 2\n\nDeux.",
    "single-heading" to "Chapter I\n\nSon unique chapitre.",
    "two-character-first-line" to "Un\n\ntexte.",
    "sentence-first-line" to "Il était une fois.\n\nLa suite.",
    "japanese" to "第一章\n\n日本語の文章です。\n\n第二章\n\n続きです。",
    "crlf" to "Chapter I\r\n\r\nUn.\r\n\r\nChapter II\r\n\r\nDeux.\r\n",
) + generatedCorpus()

/** Twelve seeded documents: a heading style, a title line and a paragraph count that vary. */
private fun generatedCorpus(): List<Pair<String, String>> {
    val random = Random(SEED)
    return (1..DOCUMENTS).map { document ->
        val style = random.nextInt(STYLES)
        val body = buildString {
            if (random.nextInt(TITLE_LINE_ONE_IN) == 0) {
                appendLine("Le Livre $document")
                appendLine()
            }
            repeat(random.nextInt(MIN_CHAPTERS, MAX_CHAPTERS + 1)) { index ->
                appendLine(headingOf(style, index + 1))
                appendLine()
                repeat(random.nextInt(1, 3)) { appendLine("Un paragraphe du chapitre ${index + 1}.") }
                appendLine()
            }
        }
        "generated-$document" to body
    }
}

/**
 * One heading shape, used for the whole document: a real book keeps to one, and
 * mixing them is what the numbering guard rejects.
 */
private fun headingOf(style: Int, number: Int): String = when (style) {
    0 -> "Chapter $number"
    1 -> "Chapitre ${roman(number)}"
    2 -> "CHAPITRE $number. UN TITRE"
    else -> "$number"
}

/** The Roman form of [number], for the corpus's headings. */
private fun roman(number: Int): String {
    val numerals = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var remaining = number
    return buildString {
        for ((value, numeral) in numerals) {
            while (remaining >= value) {
                append(numeral)
                remaining -= value
            }
        }
    }
}

/**
 * The golden at [path], or a failure that writes the actual beside the report so
 * recording a deliberate change is a copy, not a transcription.
 */
private fun assertGolden(path: String, actual: String) {
    val expected = runCatching { TestResources.text(path) }.getOrNull()
    // The golden file ends with a newline, which is what a text file should do;
    // `TestResources.text` trims it, so compare the rendered shape without one.
    if (expected == actual.trimEnd('\n')) return
    val written = File("build/reports/golden$path").apply { parentFile.mkdirs() }.apply { writeText(actual) }
    fail("Golden $path differs from the parser's output. The actual is at ${written.absolutePath}.")
}

private const val SEED = 253
private const val DOCUMENTS = 12
private const val MIN_CHAPTERS = 2
private const val MAX_CHAPTERS = 4

/** How much of a block's text the golden pins; the rest is prose, not structure. */
private const val BRIEF_LENGTH = 60

/** One in this many generated books starts with a title line. */
private const val TITLE_LINE_ONE_IN = 3

/** How many heading shapes the generator draws from, one per document. */
private const val STYLES = 4
