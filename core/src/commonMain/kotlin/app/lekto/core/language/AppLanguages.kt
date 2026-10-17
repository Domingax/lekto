package app.lekto.core.language

import app.lekto.core.text.baseLanguage

/**
 * The languages the app itself ships, so one of them can be its **app language**
 * (ADR-0034): the language of its texts and the language every translation
 * resolves into. English is the base, the one every other is defined against.
 */
object AppLanguages {

    /** English, the base language the app is written in. */
    const val ENGLISH: String = "en"

    /** French, the second language the app ships. */
    const val FRENCH: String = "fr"

    /** The languages the app ships, the base first. */
    val SUPPORTED: List<String> = listOf(ENGLISH, FRENCH)

    /** Whether the app ships [language]'s base tag, so it can be chosen as the app language. */
    fun supports(language: String?): Boolean = language?.let(::baseLanguage) in SUPPORTED
}

/**
 * The **app language** a device starts with (ADR-0034): its own language when the
 * app ships it, and English otherwise. The first launch asks no question — the
 * reader changes it in Settings when they want another.
 */
fun defaultAppLanguage(systemLanguage: String?): String =
    systemLanguage?.let(::baseLanguage)?.takeIf(AppLanguages::supports) ?: AppLanguages.ENGLISH
