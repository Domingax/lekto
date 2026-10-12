package app.lekto.tools.dictionaries

import java.io.BufferedReader

/**
 * The frequency source the difficulty build reads: one `form<TAB>weight` line
 * per surface form, where the weight is a positive, linear frequency (for
 * wordfreq, `10 ** zipf_frequency(form)` — the per-billion occurrence count its
 * Zipf score is the logarithm of).
 *
 * wordfreq's lists are **surface forms** while the pack is keyed by **lemma**
 * (ADR-0029), so the builder aggregates each form's weight onto its word key
 * rather than banding forms directly.
 */
internal object FrequencyList {

    /** Every `(form, weight)` pair in [reader], in file order; a malformed line is skipped. */
    fun read(reader: BufferedReader): List<Pair<String, Double>> = buildList {
        reader.forEachLine { line ->
            val pair = parse(line) ?: return@forEachLine
            add(pair)
        }
    }

    private fun parse(line: String): Pair<String, Double>? {
        val tab = line.indexOf('\t')
        if (tab <= 0) return null
        val form = line.substring(0, tab).trim()
        val weight = line.substring(tab + 1).trim().toDoubleOrNull()
        return if (form.isEmpty() || weight == null || weight < 0.0) null else form to weight
    }
}
