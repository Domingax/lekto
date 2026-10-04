package app.lekto.tools.dictionaries

/**
 * The surface→lemma index built while the pack streams. A surface can be claimed
 * by several lemmas (English "better" under both "good" and "well"), so the build
 * resolves the ambiguity to **one** canonical row per `(surface, language)`: the
 * surface's own lemma when one exists, otherwise the most frequent claimant,
 * then the shorter, then the lexicographically smallest. The tie-breaks are
 * arbitrary but fixed, which is what reproducibility demands.
 */
internal class FormIndex(private val language: String) {

    private val candidates = HashMap<String, HashMap<String, Int>>()

    fun add(surface: String, targetLemma: String) {
        val byLemma = candidates.getOrPut(surface) { HashMap() }
        byLemma[targetLemma] = (byLemma[targetLemma] ?: 0) + 1
    }

    fun toPackForms(): List<PackForm> = candidates.entries
        .map { (surface, byLemma) -> PackForm(surface, language, chooseLemma(surface, byLemma)) }
        .sortedWith(FORM_ORDER)

    private fun chooseLemma(surface: String, byLemma: Map<String, Int>): String {
        if (byLemma.containsKey(surface)) return surface
        return byLemma.entries.minWith(CANDIDATE_ORDER).key
    }

    private companion object {
        val FORM_ORDER = compareBy<PackForm>({ it.surface }, { it.language })
        val CANDIDATE_ORDER = compareByDescending<Map.Entry<String, Int>> { it.value }
            .thenBy { it.key.length }
            .thenBy { it.key }
    }
}
