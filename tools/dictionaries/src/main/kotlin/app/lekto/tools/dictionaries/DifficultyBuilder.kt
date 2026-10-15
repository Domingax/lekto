package app.lekto.tools.dictionaries

import java.io.BufferedReader

/**
 * The difficulty transform: a frequency list plus the reading language's form
 * index in, the in-memory **Difficulty table** out (ADR-0029).
 *
 * Each form's weight is aggregated onto the **word key** the tokeniser would
 * compute — the lemma the reader's form index knows, the normalised surface form
 * otherwise — so a word's difficulty and its mastery never disagree on its
 * identity. An empty index (a reading language with no dictionary pack, issue
 * #161) therefore keys every word by its normalised surface form. Keys are ranked
 * by total weight, ties broken by key, and cut into A1–C2 bands at the calibrated
 * sizes ([Calibration]); a key below the top carries no band. Ordering is fixed
 * and no wall clock enters, so the same input yields the same table.
 */
internal class DifficultyBuilder(
    forms: List<PackForm>,
    private val provenance: PackProvenance,
    private val language: String = DifficultyFormat.ENGLISH,
    private val calibration: DifficultyCalibration = Calibration.RECORD,
) {

    private val formIndex: Map<String, String> = forms.associate { form -> form.surface to form.lemma }

    fun build(reader: BufferedReader): DifficultyContent {
        val frequencies = FrequencyList.read(reader)
        val weights = aggregate(frequencies)
        val ranked = weights.entries.sortedWith(KEY_ORDER)
        val rows = ranked.mapIndexedNotNull { index, entry ->
            Calibration.bandFor(index + 1, calibration.cutSizes)?.let { band ->
                DifficultyRow(entry.key, language, band)
            }
        }
        val stats = DifficultyStats(
            frequencyForms = frequencies.size.toLong(),
            wordKeys = ranked.size,
            bandedWords = rows.size,
        )
        return DifficultyContent(provenance, calibration, stats, rows)
    }

    /** Sum each form's weight onto the word key it belongs to. */
    private fun aggregate(frequencies: List<Pair<String, Double>>): Map<String, Double> {
        val weights = HashMap<String, Double>()
        frequencies.forEach { (form, weight) ->
            val normalised = normaliseSurface(form)
            val key = formIndex[normalised] ?: normalised
            weights.merge(key, weight, Double::plus)
        }
        return weights
    }

    private companion object {
        /** Heaviest key first; ties broken by key, so the ranking is total and stable. */
        val KEY_ORDER: Comparator<Map.Entry<String, Double>> =
            compareByDescending<Map.Entry<String, Double>> { entry -> entry.value }.thenBy { entry -> entry.key }
    }
}
