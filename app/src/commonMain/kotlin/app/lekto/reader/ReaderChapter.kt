package app.lekto.reader

import app.lekto.core.MasteryLookup
import app.lekto.core.reading.ReadingPreferences
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TextSegmenter

/**
 * A parsed chapter as the reader consumes it: the blocks `core` produced, the
 * chapter title for the chrome, and the language its words are segmented in.
 */
data class ReaderChapter(val title: String, val language: String?, val blocks: List<TextBlock>)

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
 * What the reader shows: the parsed [chapter], the [renderer] that colours it,
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
    val chapter: ReaderChapter,
    val renderer: ReaderRenderer,
    val initialOffset: Int = 0,
    val selectedRange: IntRange? = null,
    val masteryRevision: Int = 0,
    val preferences: ReadingPreferences = ReadingPreferences.DEFAULT,
)
