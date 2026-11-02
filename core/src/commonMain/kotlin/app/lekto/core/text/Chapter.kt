package app.lekto.core.text

import kotlinx.serialization.Serializable

/**
 * A titled division of a **Book**, beginning at [startBlock] and holding the
 * chapters nested inside it (ADR-0037). A **Chapter** that contains others is a
 * **Part**: it may carry text of its own before its first child, or only group
 * them.
 *
 * [startBlock] is an index into [StructuredText.blocks], so a chapter names a
 * place in the same text the reader paginates; nothing here is a character
 * offset, which keeps a chapter independent of the typography a **Reading
 * preference** changes.
 */
@Serializable
data class Chapter(val title: String, val startBlock: Int, val children: List<Chapter> = emptyList()) {
    /** Whether this chapter groups others. */
    val isPart: Boolean get() = children.isNotEmpty()

    /**
     * Whether this chapter is a reading unit: a leaf, or a **Part** that carries
     * text before its first child. A part that only groups is not numbered, so
     * "Chapter 4" counts what the reader can actually read (ADR-0037).
     */
    val isReadingUnit: Boolean
        get() = children.isEmpty() || children.first().startBlock > startBlock
}

/**
 * Every chapter in reading order, a part before its children. The blocks the
 * list covers are contiguous because a chapter runs until the next one starts.
 */
fun List<Chapter>.flattened(): List<Chapter> = buildList {
    for (chapter in this@flattened) {
        add(chapter)
        addAll(chapter.children.flattened())
    }
}

/** The reading units of the list, in reading order; the numbers the reader shows. */
fun List<Chapter>.readingUnits(): List<Chapter> = flattened().filter { chapter -> chapter.isReadingUnit }

/**
 * The deepest chapter whose range holds [blockIndex], or `null` before the first
 * chapter (ADR-0037). The last started chapter wins, because a chapter runs to
 * the next one and the deepest child starts last.
 */
fun List<Chapter>.chapterAt(blockIndex: Int): Chapter? =
    flattened().lastOrNull { chapter -> chapter.startBlock <= blockIndex }

/**
 * The chapter's first character in the rendered text: [blockStarts] indexed by
 * [Chapter.startBlock], with [textEnd] for a chapter the parser placed past the
 * text. It is the one rule the reader's chapter offsets and the cached index
 * share, so a shelf and the page cannot name a different start.
 */
fun Chapter.characterOffsetIn(blockStarts: List<Int>, textEnd: Int): Int = blockStarts.getOrElse(startBlock) { textEnd }

/**
 * A **Chapter**'s start as a character offset into the rendered text: the shape
 * a surface holds when it must name the chapter holding a **Reading position**
 * without the blocks that offset was measured from (issue #260).
 *
 * It is a **Derived asset** beside the parsed text (ADR-0043), so it carries no
 * block index — a block is only meaningful with the text it came from.
 */
@Serializable
data class ChapterStart(val title: String, val startOffset: Int)

/**
 * The chapter list flattened to its starts in reading order, a part before its
 * children. A chapter whose [Chapter.startBlock] is past the text reads as the
 * text's end, exactly as the reader's own chapter offsets do (ADR-0037).
 */
fun StructuredText.chapterStarts(): List<ChapterStart> {
    if (chapters.isEmpty()) return emptyList()
    val starts = blocks.blockOffsets()
    val end = blocks.renderedLength()
    return chapters.flattened()
        .map { chapter -> ChapterStart(chapter.title, chapter.characterOffsetIn(starts, end)) }
        .sortedBy { start -> start.startOffset }
}

/**
 * The title of the deepest chapter whose range holds [offset], or `null` before
 * the first chapter (ADR-0037). A book with one chapter names none: it has no
 * chapter list, so the title would only repeat the book's own (ADR-0039). A
 * surface that shows no chapter list shows no chapter either.
 */
fun List<ChapterStart>.chapterTitleAt(offset: Int): String? {
    if (size <= 1) return null
    return lastOrNull { start -> start.startOffset <= offset }?.title
}
