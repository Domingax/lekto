package app.lekto.core

import app.lekto.core.text.WordKey
import kotlinx.serialization.Serializable

/**
 * How well the user knows a word, on the five-point scale CONTEXT.md defines:
 * 0 unknown, 1 recognized, 2 familiar, 3 learned, 4 known. Levels 0–3 are
 * highlighted; level 4 is invisible (normal text). The names read in the order a
 * reader acquires a word: you recognize it before it becomes familiar, and it is
 * learned before it is known.
 *
 * The number is kept because it is the scale's canonical value — it is what a
 * persisted mastery stores — even though the reader only needs [highlighted]
 * to paint a word.
 *
 * The reader colours a [app.lekto.core.text.WordToken] by this level, so the
 * ordering and [highlighted] are the domain facts the UI depends on — the
 * palette itself (`MasteryPalette` in `app`) is presentation and stays out of
 * the domain.
 */
@Suppress("MagicNumber") // The five levels *are* the numbers 0–4; naming them again adds nothing.
@Serializable
enum class MasteryLevel(val level: Int, val highlighted: Boolean) {
    UNKNOWN(0, true),
    RECOGNIZED(1, true),
    FAMILIAR(2, true),
    LEARNED(3, true),
    KNOWN(4, false),
    ;

    companion object {
        /** The level numbered [number], clamped into the scale's range (0–4). */
        fun forLevel(number: Int): MasteryLevel = entries[number.coerceIn(0, entries.lastIndex)]
    }
}

/**
 * The domain seam that maps a word to its [MasteryLevel]: the reader asks it of
 * every token and never reads a store itself.
 *
 * It is keyed by the token's [WordKey], not by the spelling, because mastery is
 * a fact about a word's identity (CONTEXT.md, "Word key"; ADR-0006): an
 * inflection and its lemma carry one key, so they share one level. The token
 * already carries the key the tokeniser computed with the dictionary pack's
 * lemma lookup, which is what lets this seam stay a pure function over identity.
 */
fun interface MasteryLookup {
    fun levelOf(key: WordKey): MasteryLevel

    companion object {
        /**
         * A lookup that knows every word, so nothing is highlighted. It stands in
         * where the user's vocabulary is not wired; unlike a demo map it makes no
         * claim about which words are known.
         */
        val AllKnown: MasteryLookup = MasteryLookup { MasteryLevel.KNOWN }
    }
}

/**
 * The default-mastery composition (ADR-0023, ADR-0030): a saved entry's level
 * always wins; otherwise the level derives from the word's **Word difficulty**
 * and the reader's **Reader level**; otherwise it is unknown.
 *
 * It is a composition of pure functions, not a store: [saved] returns the
 * user's explicit level for a key or `null` when the word is unsaved (which is
 * *not* the same as saving it at [MasteryLevel.UNKNOWN]), [readerLevel] returns
 * the reader's declared band for a language, and [difficulty] returns a word's
 * difficulty band. An unsaved word in a language with no level, or with no
 * difficulty band, is unknown — so a reader who has set nothing sees exactly
 * what they saw before (ADR-0030).
 */
fun defaultMasteryLookup(
    saved: (WordKey) -> MasteryLevel?,
    readerLevel: (String?) -> CefrLevel?,
    difficulty: (WordKey) -> CefrLevel?,
): MasteryLookup = MasteryLookup { key ->
    saved(key) ?: derivedMastery(difficulty(key), readerLevel(key.language))
}

/**
 * [difficultyBand] as a [MasteryLevel] against the [readerBand] on the shared
 * CEFR axis (ADR-0030): at or below the reader's band is known (level 4), each
 * band above drops one mastery level, flooring at unknown (level 0):
 *
 *     mastery = max(0, 4 − max(0, difficulty_band − reader_band))
 *
 * Either band absent means the signal is incomplete, so the word is unknown.
 */
fun derivedMastery(difficultyBand: CefrLevel?, readerBand: CefrLevel?): MasteryLevel {
    if (difficultyBand == null || readerBand == null) return MasteryLevel.UNKNOWN
    val bandsAbove = (difficultyBand.band - readerBand.band).coerceAtLeast(0)
    return MasteryLevel.forLevel(MasteryLevel.KNOWN.level - bandsAbove)
}
