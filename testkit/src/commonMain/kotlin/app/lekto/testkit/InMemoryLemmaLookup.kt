package app.lekto.testkit

import app.lekto.core.text.LemmaLookup

/**
 * An in-memory [LemmaLookup] for tests: the dictionary pack's reverse index as a
 * map from surface form to lemma. A surface the map does not name is unknown, so
 * the key falls back to the normalised surface form, exactly as the production
 * seam does (ADR-0006).
 */
class InMemoryLemmaLookup(private val lemmas: Map<String, String>) : LemmaLookup {
    override fun lemmaOf(surface: String, language: String?): String? = lemmas[surface]
}
