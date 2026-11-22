package app.lekto.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.LanguageCatalog
import app.lekto.core.language.LanguageCatalogState
import app.lekto.core.language.OfferedArtifact
import app.lekto.core.language.OfferedDictionary
import app.lekto.core.language.OfferedLanguage
import app.lekto.core.text.baseLanguage
import app.lekto.dictionary.DictionaryController
import app.lekto.dictionary.DictionaryUiState
import app.lekto.difficulty.DifficultyController
import app.lekto.difficulty.DifficultyUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Where the first launch's background download stands (issue #277). */
enum class GuideDownloadPhase {

    /** No language has been chosen yet. */
    IDLE,

    /** The reader chose a language; the manifest is being read. */
    PREPARING,

    /** The table and the pack are downloading. */
    DOWNLOADING,

    /** Both artefacts are installed. */
    INSTALLED,

    /** The reader stopped the download. */
    STOPPED,

    /** The manifest does not publish the chosen language, or no manifest could be read. */
    UNAVAILABLE,

    /** An artefact's download failed. */
    FAILED,
}

/**
 * What the guide and the shelf say about the background download (issue #277): the
 * phase, the language, the artefacts it covers and their published size, so the
 * line can name the cost before the bytes arrive.
 */
data class GuideDownloadUiState(
    val phase: GuideDownloadPhase = GuideDownloadPhase.IDLE,
    val language: String? = null,
    val artifacts: List<OfferedArtifact> = emptyList(),
) {

    /** The combined published size of the artefacts, or `null` when the manifest names none. */
    val sizeBytes: Long?
        get() = artifacts.mapNotNull(OfferedArtifact::sizeBytes).takeIf { sizes -> sizes.isNotEmpty() }?.sum()

    /** Whether a download is in flight or on its way. */
    val isRunning: Boolean
        get() = phase == GuideDownloadPhase.PREPARING || phase == GuideDownloadPhase.DOWNLOADING
}

/**
 * The first launch's background download (issue #277): after the language answer
 * it reads the **Language manifest** — only then, so the language list itself
 * never waits on a fetch — and starts the chosen language's **Difficulty table**
 * and **Dictionary pack** through the same controllers the **Languages** surface
 * uses, announcing their combined size and letting the reader stop them.
 *
 * The demonstration never gates on it: every guide screen reads the sample's own
 * colour maps and glossary, so it works before, during and after the download. A
 * manifest that cannot be read leaves the state [GuideDownloadPhase.UNAVAILABLE]
 * rather than failing the guide.
 *
 * The pack it chooses is the reader's own **Translation language** when the
 * manifest publishes one, otherwise the first published pack: the guide lists
 * languages, not pairs, and a first launch still wants its lookup answered.
 */
@Suppress("LongParameterList") // The catalogues, the installers, the choice and the scope.
class GuideDownloadController(
    private val catalog: LanguageCatalog?,
    private val difficulty: DifficultyController?,
    private val dictionary: DictionaryController?,
    private val translationLanguage: () -> String?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
) {

    /** The current phase and what it covers, observed by the guide's line and the shelf's. */
    var state by mutableStateOf(GuideDownloadUiState())
        private set

    private var preparation: Job? = null
    private var watching: Job? = null
    private var target: OfferedLanguage? = null

    /**
     * Starts [language]'s download: the manifest is read (or refetched) off the
     * UI thread, the published table and pack are handed to the real controllers,
     * and the phase follows them. A download already running, installed or stopped
     * is left alone, so a revisited screen never starts a second transfer.
     */
    fun start(language: String) {
        if (state.isRunning || state.phase == GuideDownloadPhase.INSTALLED) return
        if (state.phase == GuideDownloadPhase.STOPPED) return
        state = GuideDownloadUiState(GuideDownloadPhase.PREPARING, language)
        preparation = scope.launch {
            val offered = offeredLanguage(language)
            if (offered == null) {
                state = state.copy(phase = GuideDownloadPhase.UNAVAILABLE)
                return@launch
            }
            target = offered
            val artifacts = planOf(offered)
            if (artifacts.isEmpty()) {
                state = state.copy(phase = GuideDownloadPhase.UNAVAILABLE)
                return@launch
            }
            state = GuideDownloadUiState(GuideDownloadPhase.DOWNLOADING, offered.language, artifacts)
            startInstalls(offered)
            watch(offered, artifacts)
        }
    }

    /** Stops the download at once, cancelling both installers and the line's watch. */
    fun stop() {
        if (!state.isRunning) return
        preparation?.cancel()
        watching?.cancel()
        target?.let { offered ->
            offered.difficulty?.let { artifact -> difficulty?.stop(offered.language) }
            chosenPack(offered)?.let { chosen -> dictionary?.stop(chosen.artifact.name) }
        }
        state = state.copy(phase = GuideDownloadPhase.STOPPED)
    }

    /** The manifest's offered [language], read first from the cache and fetched only when nothing is cached. */
    private fun offeredLanguage(language: String): OfferedLanguage? {
        val status = catalog?.status()
        val languages = when (status) {
            is LanguageCatalogState.Available -> status.languages
            else -> (runCatching { catalog?.reload() }.getOrNull() as? LanguageCatalogState.Available)?.languages
        }.orEmpty()
        return languages.firstOrNull { offered -> baseLanguage(offered.language) == baseLanguage(language) }
    }

    /** The artefacts the chosen language publishes: its table, and the pack for the reader's translation language. */
    private fun planOf(offered: OfferedLanguage): List<OfferedArtifact> = listOfNotNull(
        offered.difficulty,
        chosenPack(offered)?.artifact,
    )

    /** The reader's own translation language's pack, or the first published one. */
    private fun chosenPack(offered: OfferedLanguage): OfferedDictionary? {
        val wanted = translationLanguage()?.let(::baseLanguage)
        return offered.dictionaries.firstOrNull { published -> baseLanguage(published.translationLanguage) == wanted }
            ?: offered.dictionaries.firstOrNull()
    }

    private fun startInstalls(offered: OfferedLanguage) {
        offered.difficulty?.let { artifact -> difficulty?.install(offered.language, artifact) }
        chosenPack(offered)?.let { published ->
            dictionary?.install(offered.language, published.translationLanguage, published.artifact)
        }
    }

    /** Follows both controllers' states until they are installed or one fails, unless the reader stopped them. */
    private fun watch(offered: OfferedLanguage, artifacts: List<OfferedArtifact>) {
        val difficultyStates = difficulty?.states ?: MutableStateFlow(emptyMap())
        val dictionaryStates = dictionary?.states ?: MutableStateFlow(emptyMap())
        watching = scope.launch {
            val result = combine(difficultyStates, dictionaryStates) { tables, packs ->
                verdict(offered, artifacts, tables, packs)
            }.first { verdict -> verdict != Verdict.WAITING }
            if (state.phase != GuideDownloadPhase.DOWNLOADING) return@launch
            when (result) {
                Verdict.READY -> state = state.copy(phase = GuideDownloadPhase.INSTALLED)
                Verdict.FAILED -> state = state.copy(phase = GuideDownloadPhase.FAILED)
                Verdict.WAITING -> Unit
            }
        }
    }

    private fun verdict(
        offered: OfferedLanguage,
        artifacts: List<OfferedArtifact>,
        tables: Map<String, DifficultyUiState>,
        packs: Map<String, DictionaryUiState>,
    ): Verdict {
        val outcomes = artifacts.mapNotNull { artifact -> outcomeOf(offered, artifact, tables, packs) }
        return when {
            outcomes.any { outcome -> outcome == Outcome.FAILED } -> Verdict.FAILED
            outcomes.isEmpty() && artifacts.isEmpty() -> Verdict.READY
            outcomes.isNotEmpty() && outcomes.all { outcome -> outcome == Outcome.DONE } -> Verdict.READY
            else -> Verdict.WAITING
        }
    }

    /** One planned artefact's state: the table's when it is the table, the pack's when it is the pack. */
    private fun outcomeOf(
        offered: OfferedLanguage,
        artifact: OfferedArtifact,
        tables: Map<String, DifficultyUiState>,
        packs: Map<String, DictionaryUiState>,
    ): Outcome? = when {
        artifact == offered.difficulty -> tables[baseLanguage(offered.language)]?.let { table ->
            outcome(
                failed = table.error != null,
                ready = table.status is DifficultyState.Ready,
                broken = table.status.isBroken(),
            )
        }

        artifact == chosenPack(offered)?.artifact -> packs[artifact.name]?.let { pack ->
            outcome(
                failed = pack.error != null,
                ready = pack.status is DictionaryPackState.Ready,
                broken = pack.status.isBroken(),
            )
        }

        else -> null
    }

    /** The one verdict from an artefact's flags: a failed or unreadable artefact loses, an installed one wins. */
    private fun outcome(failed: Boolean, ready: Boolean, broken: Boolean): Outcome = when {
        failed || broken -> Outcome.FAILED
        ready -> Outcome.DONE
        else -> Outcome.WAITING
    }

    private fun DifficultyState.isBroken(): Boolean = this is DifficultyState.Corrupt || this is DifficultyState.Incompatible

    private fun DictionaryPackState.isBroken(): Boolean =
        this is DictionaryPackState.Corrupt || this is DictionaryPackState.Incompatible

    private enum class Outcome { WAITING, DONE, FAILED }

    private enum class Verdict { WAITING, READY, FAILED }
}
