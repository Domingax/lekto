package app.lekto.dictionary

import app.lekto.core.dictionary.DictionaryLookup
import app.lekto.core.dictionary.DictionaryPackInstaller

/**
 * The dictionary pack the published workflow produces, at the stable
 * `releases/latest` alias the workflow uploads alongside the dated asset:
 * `.../releases/latest/download/<stable name>`. Pinning it here keeps the
 * download address in one named place.
 */
object DictionaryRelease {
    const val URL: String =
        "https://github.com/Domingax/lekto/releases/latest/download/lekto-dictionary-en-fr.sqlite.gz"

    /**
     * The language the published pack translates into. The source half is the
     * book's own language, so the lookup panel's reference shortcuts follow the
     * book — English word, French translation — just as the offline lookup does.
     */
    const val TARGET_LANGUAGE: String = "fr"
}

/**
 * The dictionary's device-local services: the [installer] that owns the pack's
 * lifecycle and the [lookup] the reader queries. The platform composition root
 * builds the installer with its own filesystem and SQLite factory; the app only
 * sees this bundle (issue #18).
 */
class DictionaryServices(val installer: DictionaryPackInstaller) {
    /** The word query, reading the installed pack or degrading when there is none. */
    val lookup: DictionaryLookup = DictionaryLookup { installer.open() }
}
