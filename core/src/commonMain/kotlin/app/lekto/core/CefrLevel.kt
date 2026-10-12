package app.lekto.core

import kotlinx.serialization.Serializable

/**
 * A band on the six-point CEFR scale A1–C2 (CONTEXT.md, "Reader level", "Word
 * difficulty"). It is the single ordinal axis both sit on, so a word's
 * **Word difficulty** and the reader's **Reader level** compare directly
 * (ADR-0029, ADR-0030): [band] is the number the default-mastery rule
 * subtracts.
 *
 * The reader declares a level as one of these, and the **Difficulty table**
 * labels each word's frequency band with one. A word at or below the reader's
 * band reads as known, and each band above drops one mastery level (ADR-0030).
 */
@Suppress("MagicNumber") // The six levels *are* the bands 0–5; naming them again adds nothing.
@Serializable
enum class CefrLevel(val band: Int) {
    A1(0),
    A2(1),
    B1(2),
    B2(3),
    C1(4),
    C2(5),
    ;

    companion object {
        /** The level whose [band] is [band], or `null` when no such band exists. */
        fun ofBand(band: Int): CefrLevel? = entries.firstOrNull { level -> level.band == band }
    }
}
