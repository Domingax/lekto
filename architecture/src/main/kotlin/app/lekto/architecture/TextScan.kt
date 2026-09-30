package app.lekto.architecture

/**
 * The small text walk a Gradle build file needs: skip comments and strings, name
 * the block a `{` opens, and name the configuration a `project(…)` call sits in.
 *
 * It exists so [GradleBuildFile] can find `project(…)` references without being
 * fooled by one inside a comment or a string literal, and can tell a test-scoped
 * declaration (`commonTest.dependencies { … }`, `testImplementation(…)`) from a
 * production one. The repository's own build scripts exercise both forms.
 */
internal object TextScan {

    /** The text of the line a `{` opens on, e.g. `getByName("desktopTest").dependencies`. */
    fun blockHeader(text: String, braceIndex: Int): String {
        val lineStart = text.lastIndexOf('\n', braceIndex - 1) + 1
        return text.substring(lineStart, braceIndex).trim()
    }

    /** The identifier of the call a value sits in, e.g. `testImplementation`. */
    fun callName(text: String, index: Int): String {
        var cursor = index - 1
        while (cursor >= 0 && text[cursor].isWhitespace()) cursor--
        if (cursor < 0 || text[cursor] != '(') return ""
        cursor--
        while (cursor >= 0 && text[cursor].isWhitespace()) cursor--
        var start = cursor
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) start--
        return text.substring(start + 1, cursor + 1)
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

    private const val TRIPLE_QUOTE = "\"\"\""
    private const val COMMENT_OPENER_LENGTH = 2
    private const val COMMENT_CLOSER_LENGTH = 2
}
