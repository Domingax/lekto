package app.lekto.core.text

/**
 * The sentence the word at `[start, end)` sits in, or `null` when the range is
 * not inside [text]. The **Context sentence** a vocabulary entry keeps, so the
 * word can be reviewed in context (CONTEXT.md, "Context sentence"; issue #22).
 *
 * A sentence ends at the first of [TERMINATORS] and begins after the previous
 * one, so a word near a block boundary still yields the sentence around it; the
 * ending punctuation is kept, and the result is trimmed. It is deliberately
 * simple — abbreviations and quotes are not parsed — because the sentence is
 * context, not a claim about grammar.
 */
fun contextSentence(text: String, start: Int, end: Int): String? {
    if (start < 0 || end > text.length || start >= end) return null
    var from = start
    while (from > 0 && text[from - 1] !in TERMINATORS) from--
    var to = end
    while (to < text.length && text[to] !in TERMINATORS) to++
    if (to < text.length) to++
    return text.substring(from, to).trim().ifEmpty { null }
}

/** The characters that end a sentence for [contextSentence]. */
private val TERMINATORS = charArrayOf('.', '!', '?', '\u2026', '\n', '\r')
