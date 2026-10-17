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
 * Every query resolves its pack by the book's **Reading language** **and** the
 * reader's **app language** (ADR-0034) — the language a pack translates into —
 * both taken as arguments: the choice can move at runtime, and a reader whose
 * language has no pack reports it honestly rather than falling back to a fixed
 * `fr`.
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
     * The word query for the [readingLanguage] × [translationLanguage] pair. The
     * pair is resolved here, on the caller's thread, so the per-pair installer
     * cache is only ever written from that thread; the query itself is blocking,
     * so the caller runs it off the UI thread. An unknown reading language or an
     * unchosen translation language yields a lookup that answers nothing, so the
     * panel reports the offline dictionary unavailable.
     */
    fun lookup(readingLanguage: String?, translationLanguage: String?): DictionaryLookup {
        val code = readingLanguage?.let(::baseLanguage)
        val translation = translationLanguage?.let(::baseLanguage)
        val pack = if (code == null || translation == null) null else installer(code, translation)
        return DictionaryLookup { pack?.open() }
    }

    /**
     * The pack's lemma index as the word-identity seam (ADR-0006) for the
     * [translationLanguage] pair: the tokeniser asks it so an inflection carries
     * its lemma's key, which is what makes a saved inflection update its lemma's
     * entry (issue #22). It is read on the reader's own thread, like the
     * surface's own installer lookups; a reader who has chosen no translation
     * language has no pack, so every word keys on its normalised surface form.
     */
    fun lemmas(translationLanguage: String?): LemmaLookup {
        val translation = translationLanguage?.let(::baseLanguage) ?: return LemmaLookup.None
        return dictionaryLemmas { code -> installer(code, translation).open() }
    }
}
