package app.lekto.core.dictionary

import app.lekto.core.text.baseLanguage
import app.lekto.core.text.normaliseSurface

/** The three honest outcomes of looking a word up in the offline dictionary. */
sealed interface WordLookup {

    /** The pack knows [lemma] and returns its [entries]; the query was [query]'s surface form. */
    data class Found(val query: String, val language: String?, val lemma: String, val entries: List<DictionaryEntry>) :
        WordLookup

    /** The pack is installed but does not know the word. */
    data class NotInDictionary(val query: String, val language: String?) : WordLookup

    /** No usable pack is installed, or the word cannot be keyed, so no offline answer exists. */
    data class Unavailable(val message: String) : WordLookup
}

/**
 * The word query the reading loop makes: it resolves a surface form to its lemma
 * through the pack's reverse index and returns the lemma's senses, translations
 * and pronunciation — all offline (issue #18; ADR-0006).
 *
 * The pack is keyed by a base language (`en`, `fr`), so a BCP-47 tag such as
 * `en-US` collapses to its primary subtag before the query; [pack] resolves the
 * reader's **Reading language** to the pack that translates it (issue #149).
 * When no pack is installed for that language, or the book's language is unknown,
 * it degrades to [WordLookup.Unavailable] with an honest message rather than
 * failing.
 */
class DictionaryLookup(private val pack: (String) -> DictionaryPack?) {

    /** Looks [surface] up in [language], returning an honest outcome when it is unknown or offline. */
    fun lookup(surface: String, language: String?): WordLookup {
        val code = language?.let(::baseLanguage)
        val dictionary = code?.let(pack)
        val query = surface.trim()
        return when {
            code == null -> WordLookup.Unavailable(UNKNOWN_LANGUAGE)
            dictionary == null -> WordLookup.Unavailable(NOT_INSTALLED)
            query.isEmpty() -> WordLookup.NotInDictionary(query, language)
            else -> resolve(dictionary, query, code, language)
        }
    }

    /** The pack's answer for a non-empty query in a known language. */
    private fun resolve(dictionary: DictionaryPack, query: String, code: String, language: String?): WordLookup {
        val normalised = normaliseSurface(query)
        val lemma = dictionary.lemmaOf(normalised, code) ?: normalised
        val entries = dictionary.entries(code, lemma)
        return if (entries.isEmpty()) {
            WordLookup.NotInDictionary(query, language)
        } else {
            WordLookup.Found(query, language, lemma, entries)
        }
    }

    companion object {
        /** The message the UI shows when a lookup cannot be answered offline. */
        const val NOT_INSTALLED: String = "The offline dictionary isn't installed yet."

        /** The message when the book carries no language, so no pack key can be formed. */
        const val UNKNOWN_LANGUAGE: String =
            "This book's language isn't known, so the offline dictionary can't be searched."
    }
}

/**
 * The first gloss an offline lookup found, or `null` when it found none
 * (ADR-0020): the first translation of the first entry that carries one, so a
 * polysemous word yields one coherent translation rather than a spliced list. A
 * blank gloss counts as none, so no surface ever shows an empty line. Both the
 * translation a save keeps and the gloss the **Vocabulary** list derives from the
 * pack (ADR-0032) are this rule.
 */
fun WordLookup.firstGloss(): String? = (this as? WordLookup.Found)
    ?.entries
    ?.firstNotNullOfOrNull { entry -> entry.translations.firstOrNull() }
    ?.takeIf { gloss -> gloss.isNotBlank() }
