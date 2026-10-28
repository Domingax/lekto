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
