package app.lekto.core.dictionary

/**
 * A **Translation shortcut** (CONTEXT.md): the [source] it opens and the [url]
 * that opens it with a phrase already filled in. Distinct from a
 * [DictionaryShortcut], which addresses a single word; this one carries a phrase
 * or sentence.
 */
data class TranslationShortcut(val source: DictionarySource, val url: String)

/**
 * The zero-configuration shortcut for [phrase] (CONTEXT.md, "Translation
 * shortcut"): Google Translate's page with the phrase already filled in,
 * translating from [from] to [to]. [from] and [to] are base BCP-47 subtags
 * (`en`, `fr`) with a null [from] meaning "detect"; a blank phrase or no target
 * half yields no shortcut. Like a **Dictionary shortcut**, Lekto never embeds or
 * scrapes the source — the shortcut is a plain outbound URL the browser opens.
 */
fun translationShortcut(phrase: String, from: String?, to: String?): TranslationShortcut? =
    DictionarySource.GOOGLE_TRANSLATE.canonicalUrl(phrase, from, to)?.let { url ->
        TranslationShortcut(DictionarySource.GOOGLE_TRANSLATE, url)
    }
