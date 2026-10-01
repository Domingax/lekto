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
