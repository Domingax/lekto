package app.lekto.core.difficulty

import app.lekto.core.dictionary.DictionaryPackMissing
import app.lekto.core.dictionary.PackDatabaseFactory
import app.lekto.core.dictionary.PackDownloader
import app.lekto.core.text.baseLanguage
import app.lekto.core.vault.DerivedAssetStore

/** Where the device-local **Difficulty table** stands, for an honest message on screen. */
sealed interface DifficultyState {

    /** No table has been downloaded yet. */
    data object NotInstalled : DifficultyState

    /** A table this build understands is installed and openable. */
    data class Ready(val metadata: DifficultyMetadata) : DifficultyState

    /** A table is installed but its format is one this build cannot read. */
    data class Incompatible(val found: Int, val expected: Int) : DifficultyState

    /** A table file is present but unreadable or damaged. */
    data class Corrupt(val reason: String) : DifficultyState
}

/**
 * The **Difficulty table**'s lifecycle, one per reading language (ADR-0029): it
 * reports what is installed, downloads a published table on demand into the
 * device-local derived store, validates the format handshake and refuses a table
 * it does not understand (removing the refused file so a later download can
 * replace it).
 *
 * The table is a **Derived asset**: it lives under a [DerivedAssetStore], a
 * different root from the vault, so it can never enter the vault or an export.
 * Downloading is blocking; the application runs [install] off the UI thread.
 *
 * The [install] address is a parameter rather than a field because the
 * **Language manifest** names the asset it published for a language, and the app
 * fetches exactly that name (issue #148): the installer owns the lifecycle, the
 * catalogue owns the address.
 */
class DifficultyInstaller(
    private val derived: DerivedAssetStore,
    private val downloader: PackDownloader,
    private val databases: PackDatabaseFactory,
    private val language: String,
) : AutoCloseable {

    private var table: WordDifficulty? = null
    private var state: DifficultyState? = null

    /** The table's current state, inspecting the derived store once and caching the result. */
    fun status(): DifficultyState = state ?: inspect(destination()).also { inspected -> state = inspected }

    /** The open table when one is [DifficultyState.Ready], otherwise `null`. */
    fun open(): WordDifficulty? {
        status()
        return table
    }

    /**
     * Downloads the table from [url] and installs it, replacing any previous
     * version. A table this build refuses leaves the store clean, so a later
     * download can replace it.
     */
    fun install(url: String): DifficultyState {
        downloader.download(url, destination())
        val inspected = inspect(destination())
        state = inspected
        return inspected
    }

    /** Removes the installed table, if any. */
    fun remove(): DifficultyState {
        discard()
        state = DifficultyState.NotInstalled
        return DifficultyState.NotInstalled
    }

    override fun close() {
        table?.close()
        table = null
    }

    private fun destination(): String = derived.storedPath(pathOf(language))

    @Suppress("TooGenericExceptionCaught") // Opening the table may fail any way; report it, do not crash.
    private fun inspect(target: String): DifficultyState = try {
        validate(SqlWordDifficulty(databases.open(target)))
    } catch (ignored: DictionaryPackMissing) {
        DifficultyState.NotInstalled
    } catch (failure: Exception) {
        // A file that cannot be read is refused and removed, so it cannot be
        // handed to the reader and a later download starts from a clean store.
        discard()
        DifficultyState.Corrupt(failure.message ?: UNREADABLE)
    }

    /** Accepts a readable table, or names why this build refuses it and drops the refused file. */
    private fun validate(opened: WordDifficulty): DifficultyState {
        val version = opened.metadata.formatVersion
        if (version != WordDifficulty.FORMAT_VERSION) {
            opened.close()
            discard()
            return DifficultyState.Incompatible(version, WordDifficulty.FORMAT_VERSION)
        }
        table?.close()
        table = opened
        return DifficultyState.Ready(opened.metadata)
    }

    /** Closes and removes the installed table, so a refused or stale file cannot linger. */
    private fun discard() {
        table?.close()
        table = null
        derived.remove(pathOf(language))
    }

    companion object {
        /**
         * The derived-asset path a language's table lives at; app-private and
         * never exported (ADR-0005, ADR-0017). Keyed by the base tag, like the
         * level record and the pack (ADR-0018).
         */
        fun pathOf(language: String): String = "difficulty/${baseLanguage(language)}/table.sqlite"

        private const val UNREADABLE = "The reading-level data could not be read."
    }
}
