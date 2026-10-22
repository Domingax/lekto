package app.lekto.architecture

/**
 * Where a dotted modifier chain starts and ends, over the [CursorScan]-masked
 * text: `Modifier.fillMaxWidth().clickable` starts at `Modifier`, and a chain that
 * continues after the call — `.clickable { … }.handCursor()` — ends at the last
 * call. The hand-cursor rule reads that whole span to decide whether a surface
 * carries the pointer.
 */
internal object ChainScan {

    /** The start of the dotted modifier chain that ends at [dotIndex]. */
    fun start(masked: String, dotIndex: Int): Int {
        var cursor = dotIndex
        while (true) {
            val segment = segmentBefore(masked, cursor)
            if (!segment.continues) return segment.start
            cursor = segment.dot
        }
    }

    /** The end of the dotted chain that starts at the call closing at [closeIndex]. */
    fun end(masked: String, closeIndex: Int): Int {
        var end = closeIndex
        while (true) {
            val next = nextNonWhitespace(masked, end + 1)
            when {
                next >= masked.length -> return end
                masked[next] == '{' -> end = CursorScan.matchingDelimiter(masked, next, '{', '}')
                masked[next] != '.' -> return end
                else -> end = chainedCallEnd(masked, next)
            }
        }
    }

    private fun segmentBefore(masked: String, dotIndex: Int): Segment {
        var index = whitespaceBefore(masked, dotIndex - 1)
        if (index >= 0 && masked[index] == ')') {
            index = whitespaceBefore(masked, CursorScan.matchingOpenParen(masked, index) - 1)
        }
        if (index < 0 || !isIdentifierPart(masked[index])) return Segment(index + 1, -1, continues = false)
        val start = identifierStart(masked, index)
        val before = whitespaceBefore(masked, start - 1)
        val continues = before >= 0 && masked[before] == '.'
        return Segment(start, before, continues)
    }

    /** The end of `.name(…)` after [dotIndex]. */
    private fun chainedCallEnd(masked: String, dotIndex: Int): Int {
        var index = nextNonWhitespace(masked, dotIndex + 1)
        while (index < masked.length && isIdentifierPart(masked[index])) index++
        return argumentEnd(masked, nextNonWhitespace(masked, index))
    }

    private fun argumentEnd(masked: String, index: Int): Int = when {
        index >= masked.length -> index - 1
        masked[index] == '(' -> CursorScan.matchingDelimiter(masked, index, '(', ')')
        masked[index] == '{' -> CursorScan.matchingDelimiter(masked, index, '{', '}')
        else -> index - 1
    }

    private fun nextNonWhitespace(masked: String, from: Int): Int {
        var index = from
        while (index < masked.length && masked[index].isWhitespace()) index++
        return index
    }

    private fun whitespaceBefore(masked: String, from: Int): Int {
        var index = from
        while (index >= 0 && masked[index].isWhitespace()) index--
        return index
    }

    private fun identifierStart(masked: String, from: Int): Int {
        var index = from
        while (index >= 0 && isIdentifierPart(masked[index])) index--
        return index + 1
    }

    private fun isIdentifierPart(char: Char): Boolean = char.isLetterOrDigit() || char == '_'

    private data class Segment(val start: Int, val dot: Int, val continues: Boolean)
}
