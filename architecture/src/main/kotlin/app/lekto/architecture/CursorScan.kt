package app.lekto.architecture

/**
 * The structural text walk the hand-cursor rule needs over one Kotlin source:
 * blank the prose so a bracket in a label cannot move a scan, and match a call's
 * brackets.
 *
 * It is built on [TextScan]'s comment and string skips rather than a Kotlin
 * parser, because the rule asks a small question — does this call carry
 * `handCursor`? — and a compiler front end would be far more machinery than that.
 */
internal object CursorScan {

    /** The same text with comments and string or char literals blanked, keeping indices and line breaks. */
    fun masked(text: String): String {
        val masked = text.toCharArray()
        var index = 0
        while (index < text.length) {
            index = maskAt(text, masked, index)
        }
        return String(masked)
    }

    private fun maskAt(text: String, masked: CharArray, index: Int): Int {
        val end = when {
            text.startsWith("//", index) -> TextScan.skipLine(text, index)
            text.startsWith("/*", index) -> TextScan.skipBlockComment(text, index)
            text[index] == '"' -> TextScan.skipString(text, index)
            text[index] == '\'' -> skipCharLiteral(text, index)
            else -> return index + 1
        }
        for (cursor in index until end) {
            if (masked[cursor] != '\n') masked[cursor] = ' '
        }
        return end
    }

    private fun skipCharLiteral(text: String, start: Int): Int {
        var index = start + 1
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index += 2
                '\'' -> return index + 1
                else -> index++
            }
        }
        return text.length
    }

    /** The index of the bracket matching the [openIndex] one, or the last index when unbalanced. */
    fun matchingDelimiter(masked: String, openIndex: Int, open: Char, close: Char): Int {
        var depth = 0
        for (index in openIndex until masked.length) {
            when (masked[index]) {
                open -> depth++

                close -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return masked.lastIndex
    }

    /** The index of the `(` matching the `)` at [closeIndex], or 0 when unbalanced. */
    fun matchingOpenParen(masked: String, closeIndex: Int): Int {
        var depth = 0
        for (index in closeIndex downTo 0) {
            when (masked[index]) {
                ')' -> depth++

                '(' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return 0
    }
}
