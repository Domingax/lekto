package app.lekto.core.text

/**
 * One piece of a [TokenisedText]: a [Word] or the [Separator] text between two
 * words. Keeping the separators is what lets the input be reconstructed exactly
 * (docs/research/testing-harness.md, property 7).
 */
sealed interface TextUnit {
    /** This unit as it appears in the input. */
    val text: String

    /** A word token, with the identity the tokeniser gave it. */
    data class Word(val token: WordToken) : TextUnit {
        override val text: String get() = token.surface
    }

    /** The non-word text before, between or after words. */
    data class Separator(override val text: String) : TextUnit
}

/**
 * A text and the words found in it, with the separators between them.
 * Concatenating every token's surface with its separators — [text] — reconstructs
 * the input, so tokenisation is lossless.
 */
data class TokenisedText(val units: List<TextUnit>) {
    /** The word tokens, in reading order. */
    val words: List<WordToken> get() = units.mapNotNull { unit -> (unit as? TextUnit.Word)?.token }

    /** The input reconstructed from the tokens and their separators. */
    val text: String get() = units.joinToString(separator = "") { unit -> unit.text }
}

/**
 * Tokenises [text] in [language]: the [segmenter] finds the words, [lemmas] gives
 * each its identity ([wordKeyOf]), and the gaps between words are kept as
 * separators so [TokenisedText.text] round-trips.
 */
fun tokenise(
    text: String,
    language: String?,
    segmenter: TextSegmenter,
    lemmas: LemmaLookup = LemmaLookup.None,
): TokenisedText {
    val units = mutableListOf<TextUnit>()
    var cursor = 0
    segmenter.words(text, language).forEach { span ->
        if (span.start > cursor) units += TextUnit.Separator(text.substring(cursor, span.start))
        val token = WordToken(span.surface, span.start, span.end, wordKeyOf(span.surface, language, lemmas))
        units += TextUnit.Word(token)
        cursor = span.end
    }
    if (cursor < text.length) units += TextUnit.Separator(text.substring(cursor))
    return TokenisedText(units)
}
