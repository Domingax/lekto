package app.lekto.tools.dictionaries

/**
 * The sanity gate that replaces a pre-committed source hash (ticket #17).
 *
 * Kaikki overwrites its raw file weekly and keeps no dated archive, so pinning a
 * hash would need a human on every run. Instead the run fails only on a real
 * anomaly: an empty read, a source schema that no longer yields entries for the
 * reading language or translations into the target language, a pack with no
 * lemmas, or a coverage collapse below the threshold of the previous build's
 * lemma count. Ordinary weekly drift flows through unattended.
 */
internal object SanityGate {

    const val DEFAULT_COLLAPSE_THRESHOLD: Double = 0.8

    /** The problems that must stop the build; an empty list means the pack is sane. */
    @Suppress("LongParameterList") // The pack's pair, the previous count for the gate and the threshold.
    fun check(
        stats: PackStats,
        readingLanguage: String,
        translationLanguage: String,
        previousLemmaCount: Int?,
        collapseThreshold: Double,
    ): List<String> = buildList {
        if (stats.totalLines == 0L) {
            add("the input streamed zero lines: the source is empty or unreadable")
        }
        if (stats.totalLines > 0L && stats.readingEntries == 0L) {
            add("no $readingLanguage entries parsed: the source schema may have changed")
        }
        if (stats.readingEntries > 0L && stats.entriesWithTranslation == 0L) {
            add(
                "no $readingLanguage entry carried a $translationLanguage translation: " +
                    "coverage collapsed or the schema changed",
            )
        }
        if (stats.lemmaCount == 0) {
            add("the pack has no lemmas")
        }
        addCoverageCollapse(this, stats.lemmaCount, previousLemmaCount, collapseThreshold)
    }

    private fun addCoverageCollapse(
        problems: MutableList<String>,
        lemmaCount: Int,
        previousLemmaCount: Int?,
        collapseThreshold: Double,
    ) {
        if (previousLemmaCount == null || previousLemmaCount <= 0) return
        val minimum = kotlin.math.floor(previousLemmaCount * collapseThreshold).toInt()
        if (lemmaCount < minimum) {
            problems += "lemma count $lemmaCount is below $minimum " +
                "($collapseThreshold of the previous $previousLemmaCount): coverage collapsed"
        }
    }
}
