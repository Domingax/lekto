package app.lekto.navigation

import app.lekto.core.book.ReadingSession
import app.lekto.text.TextKey

/**
 * The shell's three sections — and no fourth (docs/design.md §6): **Library**,
 * **Vocabulary** and **Settings**. A section is a place the bar offers, not a
 * screen: the screens a section owns (the **Languages** surface, **Appearance**,
 * the attribution screen) are entries inside it, so the bar marks the section
 * while one of its screens is open.
 *
 * **Import** is deliberately absent: it is the Library's action, not a place to
 * go.
 */
enum class ShellSection(val label: TextKey) {
    LIBRARY(TextKey.LIBRARY),
    VOCABULARY(TextKey.VOCABULARY),
    SETTINGS(TextKey.SETTINGS),
    ;

    /** The section's first screen, which its bar item opens. */
    val root: Destination
        get() = when (this) {
            LIBRARY -> Destination.Library
            VOCABULARY -> Destination.Vocabulary
            SETTINGS -> Destination.Settings
        }
}

/**
 * Where the app is, one value per place, so the back stack holds values rather
 * than a set of flags.
 *
 * [section] is the section the shell marks current, or `null` for a screen that
 * covers the shell: the reader leaves it and is full-screen (docs/design.md §6).
 */
sealed interface Destination {

    /** The section the shell marks while this destination is shown; `null` when it covers the shell. */
    val section: ShellSection?

    /** The Library: the books, the search, the import and the way into a book. */
    data object Library : Destination {
        override val section: ShellSection = ShellSection.LIBRARY
    }

    /** The Vocabulary list: the words the reader saved. */
    data object Vocabulary : Destination {
        override val section: ShellSection = ShellSection.VOCABULARY
    }

    /** Settings: the index of everything else, and the way to the surfaces below. */
    data object Settings : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /**
     * The **Languages** surface (issue #146): an entry inside **Settings**, not a
     * section (ADR-0034), so the bar keeps Settings current while it is open.
     */
    data object Languages : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /** The **Appearance** screen (ADR-0036): an entry inside **Settings**. */
    data object Appearance : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /**
     * The **App language** screen (ADR-0034; issue #214): the languages the app
     * ships, chosen a screen away from the index.
     */
    data object AppLanguage : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /** The **LLM provider** screen (issue #213): an entry inside **Settings**. */
    data object ProviderSettings : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /** The **Vault and sync** screen (issue #213): an entry inside **Settings**. */
    data object VaultSettings : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /**
     * The attribution screen (issue #149), reached from the **Languages** surface
     * for the data it offers and from the Settings index (issue #214).
     */
    data object Attribution : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /** The **About** screen (issue #214): the version, the licence and the source. */
    data object About : Destination {
        override val section: ShellSection = ShellSection.SETTINGS
    }

    /** The reader over an opened book: it covers the shell and has no section of its own. */
    data class Reading(val session: ReadingSession) : Destination {
        override val section: ShellSection? = null
    }
}
