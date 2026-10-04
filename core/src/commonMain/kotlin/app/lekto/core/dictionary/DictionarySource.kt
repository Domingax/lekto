package app.lekto.core.dictionary

/**
 * An external reference service for a single word (CONTEXT.md, "Dictionary
 * source"): WordReference, Reverso, Linguee and Google Translate. Lekto never
 * embeds or scrapes them — all four forbid automated access — so the lookup
 * panel offers one **shortcut** per source that opens the word's canonical page
 * in the platform browser (docs/research/dictionary-and-ai-integration.md).
 */
enum class DictionarySource(val label: String) {
    WORD_REFERENCE("WordReference"),
    REVERSO("Reverso"),
    LINGUEE("Linguee"),
    GOOGLE_TRANSLATE("Google Translate"),
}

/** A reference shortcut: which [source] it opens, and the canonical [url] it opens. */
data class DictionaryShortcut(val source: DictionarySource, val url: String)

/**
 * The shortcuts the panel offers for [term], translating from [from] to [to] —
 * two base BCP-47 subtags (`en`, `fr`) with a null [from] meaning "detect". A
 * source that cannot address the pair is omitted rather than handed a wrong page,
 * so the caller renders only shortcuts that lead somewhere real.
 */
fun dictionaryShortcuts(term: String, from: String?, to: String?): List<DictionaryShortcut> =
    DictionarySource.entries.mapNotNull { source ->
        source.canonicalUrl(term, from, to)?.let { url -> DictionaryShortcut(source, url) }
    }

/**
 * The canonical page for [term] on this source, or null when the source cannot
 * address the language pair. [term] is the surface form as it appeared in the
 * book and is percent-encoded; a blank term yields no page.
 */
fun DictionarySource.canonicalUrl(term: String, from: String?, to: String?): String? {
    val word = term.trim()
    if (word.isEmpty()) return null
    return when (this) {
        DictionarySource.WORD_REFERENCE -> wordReferenceUrl(word, from, to)
        DictionarySource.REVERSO -> reversoUrl(word, from, to)
        DictionarySource.LINGUEE -> lingueeUrl(word, from, to)
        DictionarySource.GOOGLE_TRANSLATE -> googleTranslateUrl(word, from, to)
    }
}

/** WordReference addresses a pair as a two-code path segment, e.g. `enfr`. */
private fun wordReferenceUrl(word: String, from: String?, to: String?): String? {
    if (from == null || to == null) return null
    return "https://www.wordreference.com/${from.lowercase()}${to.lowercase()}/${encode(word, spaceAsPlus = false)}"
}

/** Reverso's path is the full pair name, e.g. `english-french`, then the word with `+` for spaces. */
private fun reversoUrl(word: String, from: String?, to: String?): String? {
    val pair = languagePair(from, to) ?: return null
    return "https://context.reverso.net/translation/$pair/${encode(word, spaceAsPlus = true)}"
}

/** Linguee's search page takes the pair in the path and the word as the `query` parameter. */
private fun lingueeUrl(word: String, from: String?, to: String?): String? {
    val pair = languagePair(from, to) ?: return null
    return "https://www.linguee.com/$pair/search?source=auto&query=${encode(word, spaceAsPlus = true)}"
}

/**
 * Google Translate takes codes and `auto` for a source it must detect; with no
 * target half it cannot address the word.
 */
private fun googleTranslateUrl(word: String, from: String?, to: String?): String? {
    val target = to?.lowercase()?.takeIf { code -> code.isNotEmpty() } ?: return null
    val source = from?.lowercase()?.takeIf { code -> code.isNotEmpty() } ?: "auto"
    return "https://translate.google.com/?sl=$source&tl=$target" +
        "&text=${encode(word, spaceAsPlus = true)}&op=translate"
}

/** The full `english-french` pair name Reverso and Linguee use, or null when this build does not know a code. */
private fun languagePair(from: String?, to: String?): String? {
    val fromName = languageName(from)
    val toName = languageName(to)
    return if (fromName == null || toName == null) null else "$fromName-$toName"
}

private fun languageName(code: String?): String? = when (code?.lowercase()) {
    "en" -> "english"
    "fr" -> "french"
    else -> null
}

private const val HEX = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val HIGH_NIBBLE_SHIFT = 4
private const val LOW_NIBBLE_MASK = 0xF

/**
 * Percent-encodes [value] for a URL path or query. Unreserved characters pass
 * through; a space becomes `+` when [spaceAsPlus] and `%20` otherwise; every
 * other byte, including non-ASCII UTF-8, is escaped so the URL is always ASCII.
 */
private fun encode(value: String, spaceAsPlus: Boolean): String {
    val out = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and BYTE_MASK
        val char = code.toChar()
        when {
            char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-._~" -> out.append(char)
            char == ' ' -> out.append(if (spaceAsPlus) '+' else "%20")
            else -> out.append('%').append(HEX[code shr HIGH_NIBBLE_SHIFT]).append(HEX[code and LOW_NIBBLE_MASK])
        }
    }
    return out.toString()
}
