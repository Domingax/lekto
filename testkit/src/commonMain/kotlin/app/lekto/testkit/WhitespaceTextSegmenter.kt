package app.lekto.testkit

import app.lekto.core.text.TextSegmenter
import app.lekto.core.text.WordToken

/**
 * A deterministic [TextSegmenter] for tests: a word is a run of letters, digits
 * or internal apostrophes. It is locale-independent and needs no ICU, so the UI
 * tests do not depend on the machine's dictionaries.
 */
class WhitespaceTextSegmenter : TextSegmenter {

    override fun words(text: String, language: String?): List<WordToken> = WORD.findAll(text).map { match ->
        WordToken(surface = match.value, start = match.range.first, end = match.range.last + 1)
    }.toList()

    private companion object {
        private val WORD = Regex("[\\p{L}\\p{N}]+(?:'[\\p{L}]+)*")
    }
}
