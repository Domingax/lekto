package app.lekto.guide

import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.OfferedArtifact
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.text.baseLanguage

/**
 * What the guide's preparation would do for the chosen language: start its pair of
 * artefacts, or say why it cannot.
 */
internal sealed interface GuideDownloadPlan {

    /** The offered language and the artefacts published for it. */
    class Ready(val offered: OfferedLanguage, val artifacts: List<OfferedArtifact>) : GuideDownloadPlan

    /** The honest phase to report instead of downloading. */
    class Failed(val phase: GuideDownloadPhase) : GuideDownloadPlan
}

/**
 * Reads the **Language manifest** and plans [language]'s download: the cache first,
 * then one fetch. The three ways it can fail are kept apart, because the line must
 * name its cause — a fetch that throws is [GuideDownloadPhase.UNAVAILABLE] (the
 * connection line, retryable); bytes this build refuses are
 * [GuideDownloadPhase.REFUSED] (no retry); a readable manifest that does not publish
 * the language, or publishes no artefact for it, is
 * [GuideDownloadPhase.LANGUAGE_NOT_OFFERED] (no retry). [onOffered] receives the
 * manifest's languages so the caller can fill the translation default from them.
 */
internal fun guideDownloadPlan(
    catalog: LanguageCatalog?,
    language: String,
    translationLanguage: () -> String?,
    onOffered: (List<OfferedLanguage>) -> Unit,
): GuideDownloadPlan = when (val manifest = manifestLanguages(catalog)) {
    is ManifestRead.Languages -> planFor(manifest.offered, language, translationLanguage, onOffered)
    ManifestRead.Refused -> GuideDownloadPlan.Failed(GuideDownloadPhase.REFUSED)
    ManifestRead.Unreachable -> GuideDownloadPlan.Failed(GuideDownloadPhase.UNAVAILABLE)
}

/** What reading the manifest produced: its languages, or the honest reason it could not be read. */
private sealed interface ManifestRead {
    class Languages(val offered: List<OfferedLanguage>) : ManifestRead
    data object Refused : ManifestRead
    data object Unreachable : ManifestRead
}

/** The cached manifest, or one fetch when nothing is cached. */
private fun manifestLanguages(catalog: LanguageCatalog?): ManifestRead = when (val status = catalog?.status()) {
    is LanguageCatalogState.Available -> ManifestRead.Languages(status.languages)
    else -> fetchManifest(catalog)
}

/** One fetch: a thrown transport failure and a refused handshake are different answers. */
private fun fetchManifest(catalog: LanguageCatalog?): ManifestRead =
    when (val reloaded = runCatching { catalog?.reload() }.getOrNull()) {
        null -> ManifestRead.Unreachable
        is LanguageCatalogState.Available -> ManifestRead.Languages(reloaded.languages)
        LanguageCatalogState.Unavailable -> ManifestRead.Refused
    }

/** The plan for a readable manifest: the offered language's artefacts, or nothing to download. */
private fun planFor(
    languages: List<OfferedLanguage>,
    language: String,
    translationLanguage: () -> String?,
    onOffered: (List<OfferedLanguage>) -> Unit,
): GuideDownloadPlan {
    onOffered(languages)
    val offered = languages.firstOrNull { item -> baseLanguage(item.language) == baseLanguage(language) }
    val artifacts = offered?.let { item -> guidePlanOf(item, translationLanguage()) }.orEmpty()
    return if (offered == null || artifacts.isEmpty()) {
        GuideDownloadPlan.Failed(GuideDownloadPhase.LANGUAGE_NOT_OFFERED)
    } else {
        GuideDownloadPlan.Ready(offered, artifacts)
    }
}
