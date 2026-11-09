package app.lekto.difficulty

import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.difficulty.WordDifficulty
import app.lekto.core.text.baseLanguage

/**
 * The difficulty table's device-local services (ADR-0029; issue #148): a
 * per-reading-language [installer], built on demand and reused, and the [table]
 * the mastery composition reads. The platform composition root supplies the
 * factory — its own filesystem and SQLite factory — so the app only sees this
 * bundle, and a language the manifest publishes a table for is the only one that
 * ever gets one.
 */
class DifficultyServices(installerFor: (String) -> DifficultyInstaller) {

    private val newInstaller = installerFor
    private val installers = mutableMapOf<String, DifficultyInstaller>()

    /**
     * [language]'s installer, built once and reused. Keyed by the base tag, like
     * the level record and the table's own path (ADR-0018), so a tag with a
     * subtag shares its base language's table.
     */
    fun installer(language: String): DifficultyInstaller {
        val base = baseLanguage(language)
        return installers.getOrPut(base) { newInstaller(base) }
    }

    /**
     * The open table for [language], or `null` when none is installed. It is a
     * function rather than a value because the table is installed (and replaced)
     * at runtime; the mastery composition asks for it per lookup and gets the
     * current one.
     */
    fun table(language: String?): WordDifficulty? = language?.let { installer(it).open() }
}
