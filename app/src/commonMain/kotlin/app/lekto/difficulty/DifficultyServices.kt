package app.lekto.difficulty

import app.lekto.core.difficulty.DifficultyInstaller
import app.lekto.core.difficulty.WordDifficulty

/**
 * The **Difficulty table** the published workflow produces, at the stable
 * `releases/latest` alias the workflow uploads: one per reading language
 * (ADR-0029). Pinning the address here keeps the download in one named place.
 *
 * Only English is produced today; the per-language artifact is what keeps other
 * reading languages open as future work.
 */
object DifficultyRelease {

    /** The only reading language this build ships a difficulty table for. */
    const val LANGUAGE: String = "en"

    /** The stable, date-free URL the app fetches [language]'s table from. */
    fun url(language: String): String =
        "https://github.com/Domingax/lekto/releases/latest/download/lekto-difficulty-$language.sqlite.gz"
}

/**
 * The difficulty table's device-local services (ADR-0029): the [installer] that
 * owns the table's lifecycle and the [table] accessor the mastery composition
 * reads. The platform composition root builds the installer with its own
 * filesystem and SQLite factory; the app only sees this bundle.
 */
class DifficultyServices(val installer: DifficultyInstaller) {

    /**
     * The open table, or `null` when none is installed. It is a function rather
     * than a value because the table is installed (and replaced) at runtime; the
     * mastery composition asks for it per lookup and gets the current one.
     */
    val table: () -> WordDifficulty? = { installer.open() }
}
