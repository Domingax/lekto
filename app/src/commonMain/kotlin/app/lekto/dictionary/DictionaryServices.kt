package app.lekto.dictionary

import app.lekto.core.dictionary.DictionaryLookup
import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.dictionaryLemmas
import app.lekto.core.text.LemmaLookup
import app.lekto.core.text.baseLanguage

/**
 * The dictionary packs the published workflow produces, at the stable
 * `releases/latest` alias the workflow uploads: one per (Reading language ×
 * **Translation language**) pair (issue #149). [artifact] names the asset by the
 * same rule the pipeline does, and [url] addresses that name at the alias, so a
 * caller that holds the name the **Language manifest** published fetches exactly
 * it.
 */
object DictionaryRelease {

    /**
     * The language the published packs translate into. The source half is the
     * book's own language, so the lookup panel's reference shortcuts follow the
     * book — English word, French translation — just as the offline lookup does.
     */
    const val TARGET_LANGUAGE: String = "fr"

    /** The date-free asset the release publishes a pair's pack under. */
    fun artifact(readingLanguage: String, translationLanguage: String): String =
        "lekto-dictionary-$readingLanguage-$translationLanguage.sqlite.gz"

    /** The stable, date-free URL the release publishes [artifact] at. */
    fun url(artifact: String): String = "https://github.com/Domingax/lekto/releases/latest/download/$artifact"
}

/**
 * The dictionary's device-local services (issues #18, #149): a per-pair
 * [installer], built on demand and reused, and the [lookup] and [lemmas] the
 * reader queries. The platform composition root supplies the factory — its own
 * filesystem and SQLite factory — so the app only sees this bundle, and a pair
 * the manifest publishes a pack for is the only one that ever gets one.
 *
 * The lookup and word identity resolve their pack by the book's **Reading
 * language**, against the fixed [DictionaryRelease.TARGET_LANGUAGE], so a pack
 * installed for one language powers lookups in that language alone.
 */
class DictionaryServices(
    installerFor: (readingLanguage: String, translationLanguage: String) -> DictionaryPackInstaller,
) {

    private val newInstaller = installerFor
    private val installers = mutableMapOf<Pair<String, String>, DictionaryPackInstaller>()

    /**
     * The [readingLanguage] × [translationLanguage] pair's installer, built once
     * and reused. Keyed by the two base tags, like the level record and the
     * pack's own path (ADR-0018), so a tag with a subtag shares its base pair's
     * pack.
     */
    fun installer(readingLanguage: String, translationLanguage: String): DictionaryPackInstaller {
        val pair = baseLanguage(readingLanguage) to baseLanguage(translationLanguage)
        return installers.getOrPut(pair) { newInstaller(pair.first, pair.second) }
    }

    /**
     * The word query for [language], reading the installed pack for the pair it
     * translates. The pair is resolved here, on the caller's thread, so the
     * per-pair installer cache is only ever written from that thread; the query
     * itself is blocking, so the caller runs it off the UI thread.
     */
    fun lookup(language: String?): DictionaryLookup {
        val code = language?.let(::baseLanguage) ?: return DictionaryLookup { null }
        val installer = installer(code, DictionaryRelease.TARGET_LANGUAGE)
        return DictionaryLookup { installer.open() }
    }

    /**
     * The pack's lemma index as the word-identity seam (ADR-0006): the tokeniser
     * asks it so an inflection carries its lemma's key, which is what makes a
     * saved inflection update its lemma's entry (issue #22). It is read on the
     * reader's own thread, like the surface's own installer lookups.
     */
    val lemmas: LemmaLookup = dictionaryLemmas { code -> installer(code, DictionaryRelease.TARGET_LANGUAGE).open() }
}
