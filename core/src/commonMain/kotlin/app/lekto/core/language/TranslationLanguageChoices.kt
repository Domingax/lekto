package app.lekto.core.language

import app.lekto.core.text.baseLanguage

/**
 * The **Translation language**s the **Language manifest** publishes a pack into
 * (ADR-0034), reduced to their base tags, distinct, in the order the surface
 * lists them (alphabetically by display name). A language no pack translates into
 * is not offered, so the reader can only choose a target a lookup can serve.
 */
fun publishedTranslationLanguages(offered: List<OfferedLanguage>): List<String> = offered
    .flatMap { language -> language.dictionaries }
    .map { dictionary -> baseLanguage(dictionary.translationLanguage) }
    .distinct()
    .sortedBy(::readingLanguageName)

/**
 * The **Translation language** a fresh reader starts with (ADR-0034): their
 * device's language when the manifest publishes a pack that translates into it,
 * and nothing otherwise — the reader chooses on the **Languages** surface. The
 * choice is a suggestion, never a question.
 *
 * The device language and the manifest's languages are both collapsed to their
 * base tag, so `fr-CA` on the device matches an `fr` pack.
 */
fun defaultTranslationLanguage(systemLanguage: String?, offered: List<OfferedLanguage>): String? {
    val language = systemLanguage?.takeIf { tag -> tag.isNotBlank() }?.let(::baseLanguage) ?: return null
    return language.takeIf { code -> code in publishedTranslationLanguages(offered) }
}
