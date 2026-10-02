package app.lekto.core.text

/**
 * A single word found in a block, with the identity it is keyed by: the
 * tappable, colourable unit the reader looks up (CONTEXT.md, "Word token";
 * ADR-0006). [start] and [end] are offsets into the text the word was tokenised
 * from, so the reader can map a rendered span back to its word and a tap back to
 * its token, and [key] is what mastery and vocabulary hang off.
 */
data class WordToken(val surface: String, val start: Int, val end: Int, val key: WordKey) {
    /**
     * The same token moved by [offset]. A block's tokens are offsets into the
     * block, so the reader shifts them when the block joins a chapter's text.
     */
    fun shifted(offset: Int): WordToken = copy(start = start + offset, end = end + offset)
}
