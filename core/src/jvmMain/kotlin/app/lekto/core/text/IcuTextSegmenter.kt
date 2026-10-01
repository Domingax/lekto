package app.lekto.core.text

import com.ibm.icu.text.BreakIterator
import java.util.Locale

/**
 * The production [TextSegmenter]: ICU's word break iterator over the block text.
 *
 * ICU is used rather than the JDK's `java.text.BreakIterator` because it is
 * *dictionary-based* for scripts without spaces — Japanese, Chinese, Thai — so
 * "日本語" is one word, not three characters, matching what the DOM's
 * `Intl.Segmenter` gives `foliate-js` (ADR-0007; `docs/research/reader-substrate-compose-vs-dom.md`
 * §6.1). Android supplies the same ICU API through `android.icu.text.BreakIterator`,
 * so the two platforms segment alike; only the implementation differs.
 *
 * A word break iterator also returns the whitespace and punctuation between
 * words, tagged [BreakIterator.WORD_NONE], which are dropped: a [WordToken] is a
 * word the reader can colour and tap.
 */
class IcuTextSegmenter : TextSegmenter {

    override fun words(text: String, language: String?): List<WordToken> {
        if (text.isEmpty()) return emptyList()
        val iterator = BreakIterator.getWordInstance(locale(language))
        iterator.setText(text)
        return wordsOf(iterator, text)
    }

    private fun wordsOf(iterator: BreakIterator, text: String): List<WordToken> {
        val words = mutableListOf<WordToken>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (iterator.ruleStatus != BreakIterator.WORD_NONE) {
                words += WordToken(surface = text.substring(start, end), start = start, end = end)
            }
            start = end
            end = iterator.next()
        }
        return words
    }

    /**
     * The locale for [language], or the root locale when none is known. The root
     * locale is deliberate: falling back to the JVM default would make
     * segmentation depend on the machine's locale, which the harness forbids.
     */
    private fun locale(language: String?): Locale =
        if (language.isNullOrBlank()) Locale.ROOT else Locale.forLanguageTag(language)
}
