package app.lekto.core.text

/**
 * A word's position in the text it was segmented from: [surface] and the
 * [start]/[end] offsets into that text. A [TextSegmenter] returns these — where
 * the words are — and the tokeniser gives each one its identity ([WordToken]).
 */
data class WordSpan(val surface: String, val start: Int, val end: Int)

/**
 * Segments a block's text into its words. It is a seam: the domain asks for
 * words and never names ICU, so `commonMain` stays pure Kotlin while the JVM
 * supplies [app.lekto.core.text.IcuTextSegmenter] and a future Android target
 * supplies the platform's `android.icu` equivalent (ADR-0007).
 *
 * [language] is a BCP-47 tag ("en", "ja", "zh-Hans") or null for an unknown
 * language; passing it keeps segmentation locale-correct — dictionary-based for
 * CJK rather than per character.
 */
fun interface TextSegmenter {
    fun words(text: String, language: String?): List<WordSpan>
}
