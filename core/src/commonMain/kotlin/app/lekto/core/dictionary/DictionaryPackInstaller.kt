package app.lekto.core.dictionary

import app.lekto.core.text.baseLanguage
import app.lekto.core.vault.DerivedAssetStore

/** Where the device-local dictionary pack stands, for an honest message on screen. */
sealed interface DictionaryPackState {

    /** No pack has been downloaded yet. */
    data object NotInstalled : DictionaryPackState

    /** A pack this build understands is installed and openable. */
    data class Ready(val metadata: PackMetadata) : DictionaryPackState

    /** A pack is installed but its format is one this build cannot read. */
    data class Incompatible(val found: Int, val expected: Int) : DictionaryPackState

    /** A pack file is present but unreadable or damaged. */
    data class Corrupt(val reason: String) : DictionaryPackState
}

/**
 * The dictionary pack's lifecycle, one per (Reading language × **Translation
 * language**) pair (issue #149): it reports what is installed for the pair,
 * downloads the published pack on demand into the device-local derived store,
 * validates the format handshake and refuses a pack it does not understand
 * (issue #18; ADR-0005, ADR-0017).
 *
 * The pack is a **derived asset**: it lives under a [DerivedAssetStore], which is
 * a different root from the vault, so it can never enter the vault or an export.
 * Downloading is blocking; the application runs [install] off the UI thread.
 *
 * The [install] address is a parameter rather than a field because the
 * **Language manifest** names the asset it published for a pair, and the app
 * fetches exactly that name (issue #149): the installer owns the lifecycle, the
 * catalogue owns the address.
 */
class DictionaryPackInstaller(
    private val derived: DerivedAssetStore,
    private val downloader: PackDownloader,
    private val databases: PackDatabaseFactory,
    private val readingLanguage: String,
    private val translationLanguage: String,
) : AutoCloseable {

    private var pack: DictionaryPack? = null
    private var state: DictionaryPackState? = null

    /** The pack's current state, inspecting the derived store once and caching the result. */
    fun status(): DictionaryPackState = state ?: inspect(destination()).also { inspected -> state = inspected }

    /** The open pack when one is [DictionaryPackState.Ready], otherwise `null`. */
    fun open(): DictionaryPack? {
        status()
        return pack
    }

    /**
     * Downloads the pack from [url] and installs it, replacing any previous
     * version. A pack this build refuses leaves the store clean, so a later
     * download can replace it.
     */
    fun install(url: String): DictionaryPackState {
        downloader.download(url, destination())
        val inspected = inspect(destination())
        if (inspected !is DictionaryPackState.Ready) discard()
        state = inspected
        return inspected
    }

    /** Removes the installed pack, if any. */
    fun remove(): DictionaryPackState {
        discard()
        state = DictionaryPackState.NotInstalled
        return DictionaryPackState.NotInstalled
    }

    override fun close() {
        pack?.close()
        pack = null
    }

    private fun destination(): String = derived.storedPath(pathOf(readingLanguage, translationLanguage))

    @Suppress("TooGenericExceptionCaught") // Opening the pack may fail any way; report it, do not crash.
    private fun inspect(target: String): DictionaryPackState = try {
        validate(SqlDictionaryPack(databases.open(target)))
    } catch (ignored: DictionaryPackMissing) {
        DictionaryPackState.NotInstalled
    } catch (failure: Exception) {
        DictionaryPackState.Corrupt(failure.message ?: UNREADABLE)
    }

    /** Accepts a readable pack, or names why this build refuses it. */
    private fun validate(opened: DictionaryPack): DictionaryPackState {
        val version = opened.metadata.formatVersion
        if (version != DictionaryPack.FORMAT_VERSION) {
            opened.close()
            return DictionaryPackState.Incompatible(version, DictionaryPack.FORMAT_VERSION)
        }
        pack?.close()
        pack = opened
        return DictionaryPackState.Ready(opened.metadata)
    }

    /** Closes and removes the installed pack, so a refused or stale file cannot linger. */
    private fun discard() {
        pack?.close()
        pack = null
        derived.remove(pathOf(readingLanguage, translationLanguage))
    }

    companion object {
        /**
         * The derived-asset path a pair's pack lives at; app-private and never
         * exported (ADR-0005, ADR-0017). Keyed by the two base tags, like the
         * difficulty table and the level record (ADR-0018).
         */
        fun pathOf(readingLanguage: String, translationLanguage: String): String =
            "dictionary/${baseLanguage(readingLanguage)}-${baseLanguage(translationLanguage)}/pack.sqlite"

        private const val UNREADABLE = "The dictionary pack could not be read."
    }
}
