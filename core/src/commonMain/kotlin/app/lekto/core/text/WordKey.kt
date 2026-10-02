package app.lekto.core.text

/**
 * The identity of a word (ADR-0006): its [language] plus the [key] mastery and
 * vocabulary are keyed by — the lemma when the dictionary pack knows one, and
 * the normalised surface form otherwise. Keying on this rather than the spelling
 * is what keeps "mangeais", "mange" and "manger" one entry instead of three.
 */
data class WordKey(val language: String?, val key: String)

/**
 * Normalises a surface form for use as a fallback [WordKey]: Unicode-normalised
 * to NFC and case-folded to lower case. The fold is locale-independent, so a
 * book does not produce different vocabulary on a Turkish machine than on any
 * other; the NFC normalisation makes a composed "é" and a decomposed "e" +
 * combining acute share one key. Normalisation is idempotent
 * (docs/research/testing-harness.md, property 8).
 *
 * It is `expect`/`actual` for the same reason [TextSegmenter] is: the JVM
 * supplies the platform normaliser today, and an Android target will supply
 * `android.icu` behind the same seam (ADR-0007).
 */
expect fun normaliseSurface(surface: String): String

/**
 * The dictionary pack's lemma lookup: the seam that maps a surface form, in a
 * language, to its lemma. The pack that implements it is later work, so the
 * default [None] knows no lemmas and every word falls back to its normalised
 * surface form (ADR-0006).
 */
fun interface LemmaLookup {
    /** The lemma of [surface] in [language], or null when the dictionary does not know it. */
    fun lemmaOf(surface: String, language: String?): String?

    companion object {
        /** A [LemmaLookup] that knows no lemmas, so every key falls back to the normalised surface form. */
        val None: LemmaLookup = LemmaLookup { _, _ -> null }
    }
}

/**
 * The [WordKey] for [surface] in [language]: the lemma the dictionary pack forms
 * already canonicalise — used as-is (ADR-0006) — or the normalised surface form
 * when no lemma is known.
 */
fun wordKeyOf(surface: String, language: String?, lemmas: LemmaLookup = LemmaLookup.None): WordKey {
    val lemma = lemmas.lemmaOf(surface, language)
    return if (lemma != null) WordKey(language, lemma) else WordKey(language, normaliseSurface(surface))
}
