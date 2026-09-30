package app.lekto.architecture

/**
 * The small text walk a Gradle build file needs: skip comments and strings, and
 * name the block a `{` opens. It exists so [GradleBuildFile] can find
 * `project(…)` references without being fooled by one inside a comment or a
 * string literal, which the repository's own build scripts do contain.
 */
internal object TextScan {

    /** The dotted name immediately before a `{`, e.g. `commonTest.dependencies`. */
    fun precedingName(text: String, braceIndex: Int): String {
        var end = braceIndex - 1
        while (end >= 0 && text[end].isWhitespace()) end--
        var start = end
        while (start >= 0 && text[start].isDottedNameChar()) start--
        return text.substring(start + 1, end + 1)
    }

    fun skipLine(text: String, start: Int): Int {
        val newline = text.indexOf('\n', start)
        return if (newline < 0) text.length else newline + 1
    }

    fun skipBlockComment(text: String, start: Int): Int {
        val end = text.indexOf("*/", start + COMMENT_OPENER_LENGTH)
        return if (end < 0) text.length else end + COMMENT_CLOSER_LENGTH
    }

    fun skipString(text: String, start: Int): Int {
        if (text.startsWith(TRIPLE_QUOTE, start)) {
            val end = text.indexOf(TRIPLE_QUOTE, start + TRIPLE_QUOTE.length)
            return if (end < 0) text.length else end + TRIPLE_QUOTE.length
        }
        return scanQuoted(text, start + 1)
    }

    private fun scanQuoted(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index += 2
                '"' -> return index + 1
                else -> index++
            }
        }
        return text.length
    }

    private fun Char.isDottedNameChar(): Boolean = isLetterOrDigit() || this == '_' || this == '.'

    private const val TRIPLE_QUOTE = "\"\"\""
    private const val COMMENT_OPENER_LENGTH = 2
    private const val COMMENT_CLOSER_LENGTH = 2
}
