package app.lekto.core

import app.lekto.core.text.WordKey
import kotlinx.serialization.Serializable

/**
 * How well the user knows a word, on the five-point scale CONTEXT.md defines:
 * 0 unknown, 1 familiar, 2 recognized, 3 mastered, 4 known. Levels 0–3 are
 * highlighted; level 4 is invisible (normal text).
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
    FAMILIAR(1, true),
    RECOGNIZED(2, true),
    MASTERED(3, true),
    KNOWN(4, false),
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
