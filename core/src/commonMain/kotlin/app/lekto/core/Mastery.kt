package app.lekto.core

/**
 * How well the user knows a word, on the five-point scale CONTEXT.md defines:
 * 0 unknown, 1 familiar, 2 recognized, 3 mastered, 4 known. Levels 0–3 are
 * highlighted; level 4 is invisible (normal text).
 *
 * The number is kept because it is the scale's canonical value — it is what a
 * persisted mastery will store — even though the reader only needs [highlighted]
 * to paint a word.
 *
 * The reader colours a [app.lekto.core.text.WordToken] by this level, so the
 * ordering and [highlighted] are the domain facts the UI depends on — the
 * palette itself (`MasteryPalette` in `app`) is presentation and stays out of
 * the domain.
 */
@Suppress("MagicNumber") // The five levels *are* the numbers 0–4; naming them again adds nothing.
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
 * Spike scope: ADR-0006 keys mastery on `(language, lemma)` when a lemma is
 * known and on the normalised surface form otherwise. The dictionary pack that
 * supplies lemmas is later work, so this seam takes the surface form and the
 * language — enough to key the fallback correctly — and the keying can widen to
 * the lemma behind the seam without touching the reader.
 */
fun interface MasteryLookup {
    fun levelOf(word: String, language: String?): MasteryLevel
}
