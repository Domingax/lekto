package app.lekto.tools.dictionaries

/** The French translations an entry carries, in source order, blank words dropped. */
internal fun frenchTranslations(raw: RawEntry): List<FrenchTranslation> = raw.translations
    .filter { it.langCode == PackFormat.FRENCH }
    .mapNotNull { translation ->
        translation.word?.takeIf(String::isNotBlank)?.let { FrenchTranslation(it, translation.sense) }
    }

/** The first non-blank IPA on the entry's sounds, or null. Accent tags are not IPA tags. */
internal fun firstPronunciation(raw: RawEntry): String? =
    raw.sounds.firstNotNullOfOrNull { it.ipa?.takeIf(String::isNotBlank) }

/**
 * The first audio URL on the entry's sounds, or null. Prefers the transcoded MP3,
 * then the Ogg original, then the bare filename, which is resolved to its
 * Wikimedia Commons page.
 */
internal fun firstAudioUrl(raw: RawEntry): String? =
    raw.sounds.firstNotNullOfOrNull { it.mp3Url?.takeIf(String::isNotBlank) }
        ?: raw.sounds.firstNotNullOfOrNull { it.oggUrl?.takeIf(String::isNotBlank) }
        ?: raw.sounds.firstNotNullOfOrNull { it.audio?.takeIf(String::isNotBlank) }?.let(::commonsFileUrl)

private fun commonsFileUrl(audio: String): String =
    if (audio.startsWith("http")) audio else "https://commons.wikimedia.org/wiki/File:$audio"

/** The entry's senses' first glosses, trimmed and de-duplicated, in Wiktionary order. */
internal fun definitionsOf(raw: RawEntry): List<String> =
    raw.senses.mapNotNull { it.glosses.firstOrNull()?.trim()?.takeIf(String::isNotBlank) }.distinct()

/**
 * The sense a translation's label belongs to. A label is Wiktextract's short
 * sense key, not the full gloss, so it matches by normalised equality first, then
 * by containment; an unmatched or missing label falls back to the first sense.
 * The rule is deterministic, which the reproducibility golden depends on.
 */
internal fun senseIndex(label: String?, definitions: List<String>): Int {
    if (label.isNullOrBlank()) return 0
    val needle = normaliseLabel(label)
    val exact = definitions.indexOfFirst { normaliseLabel(it) == needle }
    val partial = definitions.indexOfFirst { normaliseLabel(it).contains(needle) }
    return when {
        exact >= 0 -> exact
        partial >= 0 -> partial
        else -> 0
    }
}

private fun normaliseLabel(label: String): String = label.lowercase().trim().trimEnd('.')
