package app.lekto.tools.dictionaries

/**
 * The frequency → CEFR calibration (ADR-0029; docs/research/word-difficulty-data-licences.md §4).
 *
 * A word key's band is read off its rank among the language's keys ordered by
 * aggregated frequency, cut at the published CEFR vocabulary sizes (Milton &
 * Alexiou's *XLex* mapping, adapted from Meara & Milton 2003):
 *
 *     A1 < 1500 ≤ A2 ≤ 2500 ≤ B1 ≤ 3250 ≤ B2 ≤ 3750 ≤ C1 ≤ 4500 ≤ C2 ≤ 5000
 *
 * Anything below the top 5000 carries no band, so it stays unknown (ADR-0030).
 * The cut points are a data choice recorded in the artifact's manifest, so
 * re-calibrating them needs no code change.
 */
internal object Calibration {

    /** The band labels, in ascending order A1…C2. */
    val BANDS: List<String> = listOf("A1", "A2", "B1", "B2", "C1", "C2")

    /** The cumulative word-key counts the ranks are cut at, in the same order as [BANDS]. */
    val CUT_SIZES: List<Int> = listOf(1500, 2500, 3250, 3750, 4500, 5000)

    const val SOURCE: String =
        "Milton & Alexiou / Meara & Milton (2009), XLex vocabulary sizes " +
            "(1500 / 2500 / 3250 / 3750 / 4500 / 5000)"

    const val VALIDATION: String =
        "Validated against the Wolfer & Lew (2025) median corpus frequencies per CEFR level."

    /** The calibration recorded in the artifact's manifest. */
    val RECORD: DifficultyCalibration = DifficultyCalibration(CUT_SIZES, SOURCE, VALIDATION)

    /** The band for a 1-based [rank], or `null` when it falls below the calibrated top. */
    fun bandFor(rank: Int, cutSizes: List<Int> = CUT_SIZES): String? = cutSizes
        .indexOfFirst { cut -> rank <= cut }
        .takeIf { index -> index >= 0 }
        ?.let { index -> BANDS[index] }
}
