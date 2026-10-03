package app.lekto.reader

import app.lekto.core.MasteryLookup
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextSegmenter

/**
 * A parsed chapter as the reader consumes it: the blocks `core` produced, the
 * chapter title for the chrome, and the language its words are segmented in.
 */
data class ReaderChapter(val title: String, val language: String?, val blocks: List<TextBlock>)

/**
 * How a chapter becomes rendered text: the [segmenter] that finds words, the
 * [mastery] lookup that colours them, and the [styles] they are typeset in.
 */
data class ReaderRenderer(
    val segmenter: TextSegmenter,
    val mastery: MasteryLookup,
    val styles: ReaderStyles = ReaderStyles.Reading,
)

/**
 * What the reader shows: the parsed [chapter], the [renderer] that colours it,
 * and the character [initialOffset] a resumed session opens at (0 for a book
 * read from the start). The offset is a position into the text the [renderer]'s
 * word layer builds; the reader restores it from the book's saved progress.
 */
data class ReaderDocument(val chapter: ReaderChapter, val renderer: ReaderRenderer, val initialOffset: Int = 0)
