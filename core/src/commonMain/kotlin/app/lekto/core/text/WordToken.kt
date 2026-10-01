package app.lekto.core.text

/**
 * A single word found in a block: the tappable, colourable unit the reader
 * looks up (CONTEXT.md, "Word token"). [start] and [end] are offsets into the
 * block text the word was segmented from, so the reader can map a rendered span
 * back to its word and a tap back to its token.
 */
data class WordToken(val surface: String, val start: Int, val end: Int)

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
    fun words(text: String, language: String?): List<WordToken>
}
