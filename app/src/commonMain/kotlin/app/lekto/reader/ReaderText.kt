package app.lekto.reader

import app.lekto.core.MasteryLookup
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.Chapter
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.flattened
import app.lekto.core.text.renderedLength

/**
 * A whole book as the reader consumes it: every block `core` produced, the
 * book's title for the chrome, the language its words are segmented in, and its
 * **Chapter list** (ADR-0037). It used to be called a "chapter" while it held
 * the whole book; a [Chapter] is now a real chapter.
 */
data class ReaderText(
    val title: String,
    val language: String?,
    val blocks: List<TextBlock>,
    val chapters: List<Chapter> = emptyList(),
)

/**
 * One row of the chapter list, ready for the reader: the [chapter], its [depth]
 * in the hierarchy, the character [startOffset] of its first block in the
 * rendered text, and the [number] the reader shows — `null` for a **Part** that
 * only groups, because it is not a reading unit (ADR-0037).
 */
data class ReaderChapterEntry(val chapter: Chapter, val depth: Int, val startOffset: Int, val number: Int?)

/**
 * The book's chapter list flattened to rows, numbered in reading order. A leaf
 * and a part with its own text are reading units and get a number; a part that
 * only groups does not. The offsets are into the same rendered text the reader
 * paginates, which is what lets a page name its chapter and a jump move to a
 * character.
 */
fun ReaderText.chapterEntries(): List<ReaderChapterEntry> {
    if (chapters.isEmpty()) return emptyList()
    val starts = blockOffsets(blocks)
    val end = blocks.renderedLength()
    var number = 0

    fun walk(list: List<Chapter>, depth: Int): List<ReaderChapterEntry> = list.flatMap { chapter ->
        val offset = starts.getOrElse(chapter.startBlock) { end }
        val entry = ReaderChapterEntry(
            chapter = chapter,
            depth = depth,
            startOffset = offset,
            number = if (chapter.isReadingUnit) ++number else null,
        )
        listOf(entry) + walk(chapter.children, depth + 1)
    }

    return walk(chapters, depth = 0).sortedBy { entry -> entry.startOffset }
}

/** The deepest chapter whose range holds [offset], or `null` before the first chapter. */
fun List<ReaderChapterEntry>.entryAt(offset: Int): ReaderChapterEntry? =
    lastOrNull { entry -> entry.startOffset <= offset }

/**
 * Every chapter's start as a character offset into the rendered text. Pagination
 * starts a page at each of them, so a chapter's first page is exact without
 * measuring the chapters before it (ADR-0037).
 */
fun ReaderText.chapterStartOffsets(): Set<Int> {
    val starts = blockOffsets(blocks)
    val end = blocks.renderedLength()
    return chapters.flattened().map { chapter -> starts.getOrElse(chapter.startBlock) { end } }.toSet()
}

/** The character offset of each block's start, the text's own block separator counted. */
private fun blockOffsets(blocks: List<TextBlock>): List<Int> = buildList {
    var offset = 0
    blocks.forEach { block ->
        add(offset)
        offset += block.text.length + BLOCK_SEPARATOR.length
    }
}

/**
 * How a chapter becomes rendered text: the [segmenter] that finds words, the
 * [lemmas] that give each its identity, and the [mastery] lookup that colours
 * them. Typography is **not** here — it is a **Reading preference** the reader
 * turns into [ReaderStyles] and hands to the word layer it builds (ADR-0035).
 *
 * [lemmas] is what makes word identity follow the dictionary pack (ADR-0006):
 * the tokeniser asks it for each word, so an inflection carries its lemma's key
 * and shares a mastery level and a vocabulary entry with it.
 */
data class ReaderRenderer(
    val segmenter: TextSegmenter,
    val mastery: MasteryLookup,
    val lemmas: LemmaLookup = LemmaLookup.None,
)

/**
 * What the reader shows: the parsed [text], the [renderer] that colours it,
 * the character [initialOffset] a resumed session opens at (0 for a book read
 * from the start), the [selectedRange] of the word whose lookup panel is open,
 * washed with the selection highlight so the word stays visible behind the panel,
 * and the [preferences] the reader typesets it with (ADR-0035). The offset is a
 * position into the text the [renderer]'s word layer builds; the reader restores
 * it from the book's saved progress.
 *
 * [masteryRevision] is bumped when the mastery behind [renderer] changes, so a
 * save recolours the visible page at once: the page's word layer is memoised,
 * and a plain state read would not force it to rebuild (issue #22).
 */
data class ReaderDocument(
    val text: ReaderText,
    val renderer: ReaderRenderer,
    val initialOffset: Int = 0,
    val selectedRange: IntRange? = null,
    val masteryRevision: Int = 0,
    val preferences: ReadingPreferences = ReadingPreferences.DEFAULT,
)
