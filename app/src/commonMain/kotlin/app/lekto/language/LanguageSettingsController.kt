package app.lekto.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.language.AppLanguages
import app.lekto.core.language.LanguageSettingsStore
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.language.defaultAppLanguage
import app.lekto.core.language.defaultTranslationLanguage
import app.lekto.core.text.baseLanguage

/**
 * The reader's live language settings (ADR-0034): the **app language** — the
 * language of the app's own texts — and the **Translation language** — the
 * language every translation resolves into — as Compose state, read and written
 * through the device-local [LanguageSettingsStore], so the surfaces show a choice
 * at once and the reader's lookup, lemma index and phrase prompt follow it.
 *
 * The store is the truth; this holder only keeps the current values observable and
 * fills the first-launch defaults. Both settings are device-local configuration,
 * so neither is synced.
 */
class LanguageSettingsController(private val store: LanguageSettingsStore) {

    private var settings = store.load()

    /** The app language's base tag; one of [AppLanguages.SUPPORTED]. */
    var appLanguage by mutableStateOf(resolveAppLanguage(settings.appLanguage))
        private set

    /** The translation language's base tag, or `null` when the reader has not chosen one. */
    var translationLanguage by mutableStateOf(settings.translationLanguage)
        private set

    /** Chooses the app's own language; a language the app does not ship is ignored. */
    fun chooseAppLanguage(language: String) {
        if (!AppLanguages.supports(language)) return
        val tag = baseLanguage(language)
        settings = settings.copy(appLanguage = tag)
        store.save(settings)
        appLanguage = resolveAppLanguage(tag)
    }

    /** Chooses the language every translation resolves into; a blank language is ignored. */
    fun chooseTranslationLanguage(language: String) {
        if (language.isBlank()) return
        val tag = baseLanguage(language)
        settings = settings.copy(translationLanguage = tag)
        store.save(settings)
        translationLanguage = tag
    }

    /**
     * Applies the app-language first-launch default (ADR-0034): the device's
     * [systemLanguage] when the app ships it, and English otherwise. A reader who
     * has already chosen is left alone; no question is asked.
     */
    fun applyAppLanguageDefault(systemLanguage: String?) {
        if (settings.appLanguageChosen) return
        chooseAppLanguage(defaultAppLanguage(systemLanguage))
    }

    /**
     * Applies the translation-language first-launch default (ADR-0034): the
     * device's [systemLanguage] when [offered] publishes a pack that translates
     * into it, and nothing otherwise — the reader chooses on the **Languages**
     * surface. Called once the manifest is known, so it never overwrites a choice.
     */
    fun applyTranslationLanguageDefault(systemLanguage: String?, offered: List<OfferedLanguage>) {
        if (settings.translationLanguageChosen) return
        defaultTranslationLanguage(systemLanguage, offered)?.let(::chooseTranslationLanguage)
    }

    private fun resolveAppLanguage(language: String?): String =
        language?.takeIf(AppLanguages::supports) ?: AppLanguages.ENGLISH
}
