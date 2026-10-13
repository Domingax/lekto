package app.lekto.core.language

import app.lekto.core.text.baseLanguage

/**
 * The English name this build shows for a **Reading language** (issue #146),
 * falling back to the tag itself when the language is unknown. It is
 * deliberately a fixed, locale-independent table rather than the JVM locale, so
 * the surface and its tests read the same on every machine.
 *
 * The app's UI is English today, so the names are English; a localised name would
 * be the platform's, not the domain's, to supply.
 */
fun readingLanguageName(code: String): String = READING_LANGUAGE_NAMES[baseLanguage(code)] ?: code

private val READING_LANGUAGE_NAMES: Map<String, String> = mapOf(
    "ar" to "Arabic",
    "cs" to "Czech",
    "da" to "Danish",
    "de" to "German",
    "el" to "Greek",
    "en" to "English",
    "es" to "Spanish",
    "fi" to "Finnish",
    "fr" to "French",
    "he" to "Hebrew",
    "hi" to "Hindi",
    "hu" to "Hungarian",
    "id" to "Indonesian",
    "it" to "Italian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "nl" to "Dutch",
    "no" to "Norwegian",
    "pl" to "Polish",
    "pt" to "Portuguese",
    "ro" to "Romanian",
    "ru" to "Russian",
    "sv" to "Swedish",
    "th" to "Thai",
    "tr" to "Turkish",
    "uk" to "Ukrainian",
    "vi" to "Vietnamese",
    "zh" to "Chinese",
)
