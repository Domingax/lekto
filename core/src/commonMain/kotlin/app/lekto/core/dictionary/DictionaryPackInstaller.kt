package app.lekto.core.dictionary

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
 * The dictionary pack's lifecycle: it reports what is installed, downloads the
 * published pack on demand into the device-local derived store, validates the
 * format handshake and refuses a pack it does not understand (issue #18;
 * ADR-0005, ADR-0017).
 *
 * The pack is a **derived asset**: it lives under a [DerivedAssetStore], which is
 * a different root from the vault, so it can never enter the vault or an export.
 * Downloading is blocking; the application runs [install] off the UI thread.
 */
class DictionaryPackInstaller(
    private val derived: DerivedAssetStore,
    private val downloader: PackDownloader,
    private val databases: PackDatabaseFactory,
    private val url: String,
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
    fun install(): DictionaryPackState {
        val target = destination()
        downloader.download(url, target)
        val inspected = inspect(target)
        if (inspected !is DictionaryPackState.Ready) {
            pack?.close()
            pack = null
            derived.remove(PACK_PATH)
        }
        state = inspected
        return inspected
    }

    /** Removes the installed pack, if any. */
    fun remove(): DictionaryPackState {
        pack?.close()
        pack = null
        derived.remove(PACK_PATH)
        state = DictionaryPackState.NotInstalled
        return DictionaryPackState.NotInstalled
    }

    override fun close() {
        pack?.close()
        pack = null
    }

    private fun destination(): String = derived.storedPath(PACK_PATH)

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

    companion object {
        /** The derived-asset path the pack lives at; app-private and never exported (ADR-0005). */
        const val PACK_PATH: String = "dictionary/en-fr/pack.sqlite"

        private const val UNREADABLE = "The dictionary pack could not be read."
    }
}
