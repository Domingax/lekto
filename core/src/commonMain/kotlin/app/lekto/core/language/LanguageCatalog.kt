package app.lekto.core.language

import app.lekto.core.vault.DerivedAssetStore

/** Where the device-local **Language manifest** stands, for an honest surface. */
sealed interface LanguageCatalogState {

    /** A manifest this build understands, as the languages it offers. */
    data class Available(val languages: List<OfferedLanguage>) : LanguageCatalogState

    /** No manifest and no cache, or a manifest this build refuses. */
    data object Unavailable : LanguageCatalogState
}

/**
 * Fetches the published **Language manifest**'s bytes from [url]. Blocking, like
 * the pack downloader: the application runs it off the UI thread.
 */
fun interface ManifestDownloader {
    fun download(url: String): ByteArray
}

/**
 * The **Language manifest**'s lifecycle (issue #146): the seam the app reads the
 * offered **Reading language**s through. It reports what is cached, and fetches
 * the published manifest on demand. An in-memory fake lives in `testkit`.
 */
interface LanguageCatalog {

    /** The catalogue as it stands without fetching: the cache, or [LanguageCatalogState.Unavailable]. */
    fun status(): LanguageCatalogState

    /** Fetches [url], caches it as a **Derived asset** and reports the new state. */
    fun reload(): LanguageCatalogState
}

/**
 * The manifest's device-local lifecycle over a [DerivedAssetStore]: it reads the
 * cached catalogue, downloads the published manifest on demand and validates the
 * format handshake. A manifest this build does not understand is refused and
 * reported unavailable; a refused or malformed fetch is not cached, so a
 * previously cached catalogue survives it.
 *
 * The manifest is a **Derived asset**: it lives under [DerivedAssetStore], a
 * different root from the vault, so it can never enter the vault or an export
 * (ADR-0005). Downloading is blocking; the application runs [reload] off the UI
 * thread.
 */
class ManifestLanguageCatalog(
    private val derived: DerivedAssetStore,
    private val downloader: ManifestDownloader,
    private val url: String,
) : LanguageCatalog {

    /** The cached catalogue, re-reading the stored bytes each time so a reload is visible. */
    override fun status(): LanguageCatalogState = read(derived.get(PATH))

    override fun reload(): LanguageCatalogState {
        val bytes = downloader.download(url)
        val state = read(bytes)
        // A refused or malformed fetch is not cached, so a previously cached
        // catalogue survives a bad fetch and a later reload starts clean.
        if (state is LanguageCatalogState.Available) derived.put(PATH, bytes)
        return state
    }

    private fun read(bytes: ByteArray?): LanguageCatalogState {
        if (bytes == null) return LanguageCatalogState.Unavailable
        return when (val read = readLanguageManifest(bytes)) {
            is LanguageManifestRead.Read -> LanguageCatalogState.Available(read.languages)
            is LanguageManifestRead.Refused, LanguageManifestRead.Malformed -> LanguageCatalogState.Unavailable
        }
    }

    companion object {
        /** The derived-asset path the manifest lives at; app-private and never exported (ADR-0005). */
        const val PATH: String = "language/manifest.json"
    }
}
