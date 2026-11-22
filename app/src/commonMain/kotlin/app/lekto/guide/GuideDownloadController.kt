package app.lekto.guide

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.difficulty.DifficultyState
import app.lekto.core.language.LanguageCatalog
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

    /** No manifest could be fetched: the transport or the network failed, so a retry is honest. */
    UNAVAILABLE,

    /** The published manifest was fetched but this build does not understand its format. No retry. */
    REFUSED,

    /** The manifest is readable but publishes no data for the chosen language. No retry. */
    LANGUAGE_NOT_OFFERED,

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
 * manifest that cannot be fetched leaves [GuideDownloadPhase.UNAVAILABLE]; one this
 * build refuses leaves [GuideDownloadPhase.REFUSED]; one that does not publish the
 * chosen language leaves [GuideDownloadPhase.LANGUAGE_NOT_OFFERED] rather than
 * failing the guide, and the line names which of the three it is.
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
    private val onOffered: (List<OfferedLanguage>) -> Unit = {},
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
     * and the phase follows them. A download already running for the same language,
     * or already installed for it, is left alone; choosing another language
     * retargets the transfer rather than stacking a second one.
     */
    fun start(language: String) {
        if (state.isRunning && sameLanguage(language)) return
        if (state.phase == GuideDownloadPhase.INSTALLED && sameLanguage(language)) return
        cancelWork()
        state = GuideDownloadUiState(GuideDownloadPhase.PREPARING, language)
        preparation = scope.launch { prepare(language) }
    }

    /**
     * Re-runs the preparation after a failure or an unreachable manifest: the
     * reader's `Retry`, which reads the manifest again and starts the installs
     * afresh rather than giving up silently. A refused handshake or a language the
     * manifest does not publish has no retry: nothing the reader can do would change
     * the answer, so the line offers none.
     */
    fun retry() {
        if (state.isRunning) return
        if (state.phase !in RETRYABLE_PHASES) return
        state.language?.let(::start)
    }

    /** Resumes a download the reader stopped, through the same preparation. */
    fun resume() {
        if (state.phase == GuideDownloadPhase.STOPPED) retry()
    }

    /** Stops the download at once, cancelling both installers and the line's watch. */
    fun stop() {
        if (!state.isRunning) return
        cancelWork()
        state = state.copy(phase = GuideDownloadPhase.STOPPED)
    }

    /** The preparation itself: read the manifest, plan the artefacts, start them and watch. */
    private fun prepare(language: String) {
        val plan = guideDownloadPlan(catalog, language, translationLanguage, onOffered)
        when (plan) {
            is GuideDownloadPlan.Ready -> launch(plan)
            is GuideDownloadPlan.Failed -> state = state.copy(phase = plan.phase)
        }
    }

    /** Starts a planned pair and follows it. */
    private fun launch(plan: GuideDownloadPlan.Ready) {
        target = plan.offered
        state = GuideDownloadUiState(GuideDownloadPhase.DOWNLOADING, plan.offered.language, plan.artifacts)
        startInstalls(plan.offered)
        watch(plan.offered, plan.artifacts)
    }

    /** Cancels the preparation and the watch, and stops whatever the installers are running. */
    private fun cancelWork() {
        val wasRunning = state.isRunning
        preparation?.cancel()
        watching?.cancel()
        target?.let { offered ->
            if (wasRunning && offered.difficulty != null) difficulty?.stop(offered.language)
            if (wasRunning) {
                guideChosenPack(offered, translationLanguage())?.let { chosen ->
                    dictionary?.stop(chosen.artifact.name)
                }
            }
        }
        target = null
    }

    private fun sameLanguage(language: String): Boolean =
        state.language?.let { chosen -> baseLanguage(chosen) == baseLanguage(language) } ?: false

    /** The artefacts the chosen language publishes: its table, and the pack for the reader's translation language. */
    private fun startInstalls(offered: OfferedLanguage) {
        offered.difficulty?.let { artifact -> difficulty?.install(offered.language, artifact) }
        guideChosenPack(offered, translationLanguage())?.let { published ->
            dictionary?.install(offered.language, published.translationLanguage, published.artifact)
        }
    }

    /** Follows both controllers' states until they are installed or one fails, unless the reader stopped them. */
    private fun watch(offered: OfferedLanguage, artifacts: List<OfferedArtifact>) {
        val tables = difficulty?.states ?: MutableStateFlow(emptyMap())
        val packs = dictionary?.states ?: MutableStateFlow(emptyMap())
        watching = scope.launch {
            val result = combine(tables, packs) { tableStates, packStates ->
                guideVerdict(offered, artifacts, translationLanguage(), InstallStates(tableStates, packStates))
            }.first { verdict -> verdict != Verdict.WAITING }
            if (state.phase != GuideDownloadPhase.DOWNLOADING) return@launch
            when (result) {
                Verdict.READY -> state = state.copy(phase = GuideDownloadPhase.INSTALLED)
                Verdict.FAILED -> state = state.copy(phase = GuideDownloadPhase.FAILED)
                Verdict.WAITING -> Unit
            }
        }
    }
}

/** Both installers' current states, bundled so a verdict stays a small function. */
private class InstallStates(val tables: Map<String, DifficultyUiState>, val packs: Map<String, DictionaryUiState>)

/** The phases a retry can improve: the transport failed, an artefact failed, or the reader stopped. */
private val RETRYABLE_PHASES = setOf(
    GuideDownloadPhase.UNAVAILABLE,
    GuideDownloadPhase.FAILED,
    GuideDownloadPhase.STOPPED,
)

/** The artefacts [offered] publishes: its table, and the pack for the reader's translation language. */
internal fun guidePlanOf(offered: OfferedLanguage, translationLanguage: String?): List<OfferedArtifact> =
    listOfNotNull(offered.difficulty, guideChosenPack(offered, translationLanguage)?.artifact)

/** The reader's own translation language's pack, or the first published one. */
private fun guideChosenPack(offered: OfferedLanguage, translationLanguage: String?): OfferedDictionary? {
    val wanted = translationLanguage?.let(::baseLanguage)
    return offered.dictionaries.firstOrNull { published -> baseLanguage(published.translationLanguage) == wanted }
        ?: offered.dictionaries.firstOrNull()
}

/** Both installers' states as one verdict: every planned artefact installed, or one failed. */
private fun guideVerdict(
    offered: OfferedLanguage,
    artifacts: List<OfferedArtifact>,
    translationLanguage: String?,
    states: InstallStates,
): Verdict {
    val outcomes = artifacts.mapNotNull { artifact ->
        guideOutcomeOf(offered, artifact, translationLanguage, states)
    }
    return when {
        outcomes.any { outcome -> outcome == Outcome.FAILED } -> Verdict.FAILED
        outcomes.isEmpty() && artifacts.isEmpty() -> Verdict.READY
        outcomes.isNotEmpty() && outcomes.all { outcome -> outcome == Outcome.DONE } -> Verdict.READY
        else -> Verdict.WAITING
    }
}

/** One planned artefact's state: the table's when it is the table, the pack's when it is the pack. */
private fun guideOutcomeOf(
    offered: OfferedLanguage,
    artifact: OfferedArtifact,
    translationLanguage: String?,
    states: InstallStates,
): Outcome? = when {
    artifact == offered.difficulty -> states.tables[baseLanguage(offered.language)]?.let { table ->
        guideOutcome(
            failed = table.error != null,
            ready = table.status is DifficultyState.Ready,
            broken = table.status is DifficultyState.Corrupt ||
                table.status is DifficultyState.Incompatible,
        )
    }

    artifact == guideChosenPack(offered, translationLanguage)?.artifact -> states.packs[artifact.name]?.let { pack ->
        guideOutcome(
            failed = pack.error != null,
            ready = pack.status is DictionaryPackState.Ready,
            broken = pack.status is DictionaryPackState.Corrupt ||
                pack.status is DictionaryPackState.Incompatible,
        )
    }

    else -> null
}

/** The one verdict from an artefact's flags: a failed or unreadable artefact loses, an installed one wins. */
private fun guideOutcome(failed: Boolean, ready: Boolean, broken: Boolean): Outcome = when {
    failed || broken -> Outcome.FAILED
    ready -> Outcome.DONE
    else -> Outcome.WAITING
}

private enum class Outcome { WAITING, DONE, FAILED }

private enum class Verdict { WAITING, READY, FAILED }
