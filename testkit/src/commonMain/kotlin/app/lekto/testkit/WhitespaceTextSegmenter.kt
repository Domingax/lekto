package app.lekto.testkit

import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.WordSpan

/**
 * A deterministic [TextSegmenter] for tests: a word is a run of letters, digits
 * or internal apostrophes. It is locale-independent and needs no ICU, so the UI
 * tests do not depend on the machine's dictionaries.
 */
class WhitespaceTextSegmenter : TextSegmenter {

    override fun words(text: String, language: String?): List<WordSpan> = WORD.findAll(text).map { match ->
        WordSpan(surface = match.value, start = match.range.first, end = match.range.last + 1)
    }.toList()

    private companion object {
        private val WORD = Regex("[\\p{L}\\p{N}]+(?:'[\\p{L}]+)*")
    }
}
