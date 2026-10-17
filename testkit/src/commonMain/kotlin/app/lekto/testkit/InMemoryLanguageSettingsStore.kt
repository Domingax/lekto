package app.lekto.testkit

import app.lekto.core.language.LanguageSettings
import app.lekto.core.language.LanguageSettingsStore
import app.lekto.core.text.baseLanguage

/**
 * An in-memory [LanguageSettingsStore] for tests: the app's composition and UI are
 * proved without a file, while the JSON-backed implementation is proved in
 * `core/commonTest` (docs/testing.md, "Test levels").
 */
class InMemoryLanguageSettingsStore(settings: LanguageSettings = LanguageSettings.NONE) : LanguageSettingsStore {

    private var stored = settings.collapsed()

    override fun load(): LanguageSettings = stored

    override fun save(settings: LanguageSettings) {
        stored = settings.collapsed()
    }
}

/** [settings] with each language collapsed to its base tag, so the fake matches the JSON store. */
private fun LanguageSettings.collapsed(): LanguageSettings = LanguageSettings(
    appLanguage = appLanguage?.let(::baseLanguage)?.takeIf { tag -> tag.isNotBlank() },
    translationLanguage = translationLanguage?.let(::baseLanguage)?.takeIf { tag -> tag.isNotBlank() },
)
