package app.lekto.settings

import app.lekto.LocalChanges
import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.core.sync.SyncEngine
import app.lekto.core.sync.SyncOutcome
import app.lekto.core.sync.SyncOutcomeStore
import app.lekto.core.sync.SyncReport
import app.lekto.core.sync.SyncSchedule
import app.lekto.core.sync.SyncSettings
import app.lekto.core.sync.SyncSettingsStore
import app.lekto.core.sync.SyncTarget
import app.lekto.text.AppMessage
import app.lekto.text.TextKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pieces a platform entry point supplies so sync can be configured from the
 * settings screen (issue #28): where the non-secret configuration is kept, and
 * how to build a driver — and the engine over it — for a configuration. Bundled
 * like [app.lekto.LlmServices], so the root composable's signature grows in one
 * named place.
 *
 * A platform with no driver supplies no [SyncServices], so the section says so
 * rather than offering a dead action. Both clients now wire one (issue #116;
 * ADR-0026), so this is the degraded path a platform without a transport would
 * take.
 */
data class SyncServices(
    /** The app-private, non-secret sync configuration (issue #28). */
    val settings: SyncSettingsStore,
    /** Builds the driver for [settings], authenticating with the application password. */
    val target: (SyncSettings, String) -> SyncTarget,
    /** Builds the engine that moves the local vault through a [SyncTarget]. */
    val engine: (SyncTarget) -> SyncEngine,
    /** Keeps the last run's outcome device-locally, so it survives a restart (issue #121). */
    val outcomes: SyncOutcomeStore,
)

/**
 * The inline outcome of a sync action — a save, a connection test, enabling or
 * disconnecting (issue #28): a [Success] or a [Failure], so the section renders
 * the failure in the error colour and nothing has to read a boolean flag.
 */
sealed interface SyncResult {
    val message: AppMessage

    /** The action completed; [message] is a short confirmation. */
    data class Success(override val message: AppMessage) : SyncResult

    /** The action did not complete; [message] is an honest reason, never carrying a password. */
    data class Failure(override val message: AppMessage) : SyncResult
}

/**
 * What the settings screen renders for sync (issue #28). It never holds the
 * **application password**: the password is entered in a field the screen owns
 * and handed to the controller only for a save or a test, so a secret cannot leak
 * through this value's `toString`.
 *
 * [available] is `false` only where no driver is wired, so the section can say so
 * rather than offer a dead action; it never gates the reader. [lastSync] is the
 * last run's outcome, kept so the user can see it at rest, not only as it
 * happens.
 */
data class SyncUiState(
    val available: Boolean = false,
    val config: SyncSettings = SyncSettings.DEFAULT,
    val hasStoredPassword: Boolean = false,
    val testing: Boolean = false,
    val syncing: Boolean = false,
    val result: SyncResult? = null,
    val lastSync: SyncResult? = null,
) {

    /**
     * The one-line status the section shows at rest, so enabling is always
     * visible. It claims sync is on only when an endpoint and a stored password
     * make a run possible, so the flag can never promise a sync that cannot
     * happen.
     */
    val status: AppMessage
        get() = when {
            !config.isConfigured -> NOT_SET_UP
            config.enabled && hasStoredPassword -> SYNC_ON
            else -> SYNC_OFF
        }

    companion object {
        val SYNC_ON: AppMessage = AppMessage.Text(TextKey.SYNC_STATUS_ON)
        val SYNC_OFF: AppMessage = AppMessage.Text(TextKey.SYNC_STATUS_OFF)
        val NOT_SET_UP: AppMessage = AppMessage.Text(TextKey.SYNC_STATUS_NOT_SET_UP)
    }
}

/**
 * The sync section's state holder (issue #28; ADR-0009, ADR-0023): it saves the
 * WebDAV endpoint app-privately and the application password in the **Secret
 * store** (ADR-0021), tests the connection off the UI thread, turns sync on or
 * off as a deliberate act, runs a manual sync, and disconnects without leaving
 * the local vault unusable.
 *
 * Since issue #121 it also runs sync **automatically** while it is on
 * (ADR-0028): [start] triggers a run at app start and begins a bounded periodic
 * timer, [onResumed] triggers a run when the app returns to the foreground, and a
 * local change — a book imported, a word saved or deleted, a reading position
 * moved — pushes a run after a short settle. Any trigger raised while a run is in
 * flight is coalesced into one follow-up rather than a storm. Turning sync off
 * ([disable], [disconnect]) stops every trigger. The last outcome is persisted
 * device-locally ([SyncServices.outcomes]) so it survives a restart, and a failure
 * is retried on the next trigger.
 *
 * The [dispatcher] is injected because testing a connection and syncing block,
 * and a hard-coded one cannot be driven by virtual time; the [scope] carries that
 * work (docs/testing.md, "Deterministic seams").
 */
@Suppress("TooManyFunctions", "LongParameterList") // One state holder; its constructor threads the injected seams.
class SyncController(
    private val services: SyncServices,
    private val secrets: SecretStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
    /**
     * Called after a sync attempt, so a screen that reads the vault — the library
     * — reloads what the sync moved rather than showing its stale list (issue #116).
     */
    private val onSynced: () -> Unit = {},
    /** Notified by the writers of local content and progress changes (issue #121). */
    private val changes: LocalChanges,
    /** The automatic-run cadence and its backoff (issue #121; ADR-0028). */
    private val schedule: SyncSchedule = SyncSchedule(),
) {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    /**
     * Whether the app has asked for automatic sync ([start] was called). The
     * periodic timer is armed only once the app is running, so a controller built
     * in a test that never starts automation raises no trigger of its own.
     */
    private var started = false
    private var pending = false
    private var failures = 0
    private var run: Job? = null
    private var ticker: Job? = null
    private var changeJobs: List<Job> = emptyList()

    /**
     * Arms automatic sync (issue #121; ADR-0028): an app-start run once sync is on,
     * a bounded periodic timer for as long as it stays on, and a push for each
     * local change. Calling it again is harmless. Turning sync on later ([enable])
     * arms the same triggers.
     */
    fun start() {
        if (started) return
        started = true
        if (!_state.value.config.enabled) return
        startCollectingChanges()
        requestSync()
        scheduleTicker()
    }

    /**
     * Asks for a run because the app returned to the foreground (issue #121): the
     * platform raises it on resume, and it coalesces with any run in flight like
     * the periodic trigger. A no-op while sync is off.
     */
    fun onResumed() {
        requestSync()
    }

    /** Sets the WebDAV server address the driver connects to. */
    fun setServerUrl(value: String) {
        _state.update { current -> current.copy(config = current.config.copy(serverUrl = value), result = null) }
    }

    /** Sets the username the driver authenticates as. */
    fun setUsername(value: String) {
        _state.update { current -> current.copy(config = current.config.copy(username = value), result = null) }
    }

    /**
     * Persists the endpoint app-privately and, when [password] is not blank,
     * stores the application password in the **Secret store**. Neither enters the
     * vault; an unavailable store is reported inline rather than silently
     * dropping the password.
     */
    fun save(password: String) {
        services.settings.save(_state.value.config)
        val failure = password.takeIf { it.isNotBlank() }?.let(::storePassword)
        _state.update { current ->
            current.copy(
                hasStoredPassword = storedPassword() != null,
                result = failure?.let { message -> SyncResult.Failure(message) }
                    ?: SyncResult.Success(SAVED),
            )
        }
    }

    /**
     * Tests the configured endpoint, using [password] when the field is not blank
     * and the stored password otherwise, so a saved server can be re-tested
     * without retyping.
     */
    fun test(password: String) {
        if (_state.value.testing) return
        val typed = password.takeIf { it.isNotBlank() }
        _state.update { current -> current.copy(testing = true, result = null) }
        scope.launch {
            val secret = typed ?: storedPassword()
            val result = if (secret == null) {
                SyncResult.Failure(NO_PASSWORD)
            } else {
                withContext(dispatcher) { probe(_state.value.config, secret) }
            }
            _state.update { current -> current.copy(testing = false, result = result) }
        }
    }

    /**
     * Turns sync on as a deliberate act (issue #28): it needs a usable endpoint
     * and a stored password, so enabling can never silently sync nothing. Turning
     * it on runs the first sync immediately, so the choice has a visible outcome
     * rather than only a stored flag.
     */
    fun enable() {
        val config = _state.value.config
        val failure = when {
            !config.isConfigured -> SyncResult.Failure(NOT_CONFIGURED)
            storedPassword() == null -> SyncResult.Failure(NO_PASSWORD)
            else -> null
        }
        if (failure != null) {
            _state.update { current -> current.copy(result = failure) }
            return
        }
        services.settings.save(config.copy(enabled = true))
        _state.update { current ->
            current.copy(config = current.config.copy(enabled = true), result = SyncResult.Success(SyncUiState.SYNC_ON))
        }
        syncNow()
        if (started) {
            startCollectingChanges()
            scheduleTicker()
        }
    }

    /** Turns sync off, leaving the endpoint configured and the vault local and usable. */
    fun disable() {
        stopTriggers()
        services.settings.save(_state.value.config.copy(enabled = false))
        _state.update { current ->
            current.copy(
                config = current.config.copy(enabled = false),
                result = SyncResult.Success(SyncUiState.SYNC_OFF),
            )
        }
    }

    /**
     * Asks for one run (issue #28, issue #121): the engine reconciles the local
     * vault with the target off the UI thread, and its outcome — moved records, or
     * a recoverable failure — is reported in plain language and kept device-locally
     * so it survives a restart. A sync that fails changes nothing locally, so the
     * app stays fully usable.
     *
     * The manual action, the periodic timer, app start and resume all funnel here,
     * so a trigger raised while a run is in flight is coalesced into a single
     * follow-up: runs never overlap, and a burst of triggers is not a storm.
     */
    fun syncNow() {
        requestSync()
    }

    /**
     * Disconnects (issue #28): the endpoint is cleared, sync is turned off and the
     * stored password is removed. The local vault is untouched, so the app remains
     * fully usable offline; when the **Secret store** cannot be reached the
     * password stays behind and that is reported honestly (ADR-0021).
     */
    fun disconnect() {
        stopTriggers()
        services.settings.save(SyncSettings.DEFAULT)
        services.outcomes.clear()
        val deletion = secrets.delete(PASSWORD_KEY)
        _state.update { current ->
            current.copy(
                config = SyncSettings.DEFAULT,
                hasStoredPassword = deletion is SecretResult.Unavailable && current.hasStoredPassword,
                lastSync = null,
                result = (deletion as? SecretResult.Unavailable)?.let { outcome ->
                    SyncResult.Failure(AppMessage.core(outcome.message))
                }
                    ?: SyncResult.Success(DISCONNECTED),
            )
        }
    }

    /** Clears the last inline result, so the section returns to its resting state. */
    fun dismissResult() {
        _state.update { current -> current.copy(result = null) }
    }

    private fun initialState(): SyncUiState {
        val config = services.settings.load()
        return SyncUiState(
            available = true,
            config = config,
            hasStoredPassword = storedPassword() != null,
            lastSync = services.outcomes.load()?.toResult(),
        )
    }

    /**
     * The one gate every automatic trigger passes (issue #121): a no-op while sync
     * is off, a single follow-up when a run is already in flight, and otherwise a
     * run. This is what stops triggers from overlapping or from becoming a storm.
     */
    private fun requestSync() {
        val config = _state.value.config
        if (!config.enabled) return
        if (run?.isActive == true) {
            pending = true
            return
        }
        beginRun(config)
    }

    /**
     * Starts one run for [config]: it needs both halves — the endpoint and the
     * stored password — and when either is missing the honest reason names the one
     * that is, recorded like any other outcome.
     */
    private fun beginRun(config: SyncSettings) {
        val password = if (config.isConfigured) storedPassword() else null
        if (password == null) {
            recordRun(SyncResult.Failure(if (config.isConfigured) NO_PASSWORD else NOT_CONFIGURED))
            return
        }
        _state.update { current -> current.copy(syncing = true) }
        run = scope.launch {
            val outcome = withContext(dispatcher) { runSync(config, password) }
            onSynced()
            // Clear the slot before recording, so a coalesced follow-up can start a
            // fresh run instead of seeing this (still-active) coroutine and keeping
            // the trigger pending forever.
            run = null
            recordRun(outcome)
        }
    }

    /**
     * Records a finished run (issue #121): the outcome is kept device-locally so it
     * survives a restart, the failure count drives the next backoff, and a trigger
     * that arrived mid-run is honoured once rather than dropped. A run that finishes
     * after sync was turned off is not recorded, so disabling or disconnecting
     * cannot have a late run resurrect an outcome the user just cleared.
     */
    private fun recordRun(outcome: SyncResult) {
        failures = if (outcome is SyncResult.Failure) failures + 1 else 0
        if (_state.value.config.enabled) {
            services.outcomes.save(outcome.toOutcome())
            _state.update { current -> current.copy(syncing = false, lastSync = outcome) }
        } else {
            _state.update { current -> current.copy(syncing = false) }
        }
        if (pending) {
            pending = false
            if (_state.value.config.enabled) requestSync()
        }
        // Rebase the periodic wait on this run's outcome, so a push or resume that
        // just changed the failure count is not left waiting on a stale delay.
        if (started && _state.value.config.enabled) scheduleTicker()
    }

    /**
     * Runs the periodic trigger for as long as sync is on (issue #121; ADR-0028):
     * it waits the backoff interval for the last outcome's failure count, so a
     * failing target is polled less rather than hammered. [recordRun] restarts it
     * after every run, so the wait always reflects the latest outcome, and a
     * trigger raised while a run is in flight coalesces in [requestSync].
     */
    private fun scheduleTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(schedule.intervalAfter(failures))
                requestSync()
            }
        }
    }

    /**
     * Pushes a run on each local change (issue #121; ADR-0028): a discrete content
     * change after [SyncSchedule.contentChangeDelay], a reading-progress change
     * after [SyncSchedule.progressChangeDelay], so a burst of edits or of page
     * turns settles into one run rather than one run per edit or turn.
     */
    @OptIn(FlowPreview::class) // `debounce` is the settle this needs; it is a preview Flow operator.
    private fun startCollectingChanges() {
        if (changeJobs.isNotEmpty()) return
        changeJobs = listOf(
            scope.launch {
                changes.content.debounce(schedule.contentChangeDelay).collect { requestSync() }
            },
            scope.launch {
                changes.progress.debounce(schedule.progressChangeDelay).collect { requestSync() }
            },
        )
    }

    /** Stops the change subscribers, so turning sync off leaves nothing pending. */
    private fun stopCollectingChanges() {
        changeJobs.forEach { job -> job.cancel() }
        changeJobs = emptyList()
    }

    /** Stops every automatic trigger, so turning sync off leaves nothing pending. */
    private fun stopTriggers() {
        ticker?.cancel()
        ticker = null
        stopCollectingChanges()
        pending = false
    }

    /** Lists the target as the lightest proof that the URL and credentials reach it. */
    private suspend fun probe(config: SyncSettings, password: String): SyncResult = reach(
        key = TextKey.MESSAGE_SYNC_PROBE_FAILED,
        unknown = TextKey.MESSAGE_SYNC_PROBE_FAILED_UNKNOWN,
    ) {
        services.target(config, password).list()
        CONNECTED
    }

    /** Runs one sync, mapping a recoverable failure to a plain-language line. */
    private suspend fun runSync(config: SyncSettings, password: String): SyncResult = reach(
        key = TextKey.MESSAGE_SYNC_RUN_FAILED,
        unknown = TextKey.MESSAGE_SYNC_RUN_FAILED_UNKNOWN,
    ) {
        services.engine(services.target(config, password)).sync().summary()
    }

    /**
     * Runs [action], reporting any failure as a plain-language line rather than a
     * crash: a transport failure must leave the app usable, and the next sync
     * retries ([app.lekto.core.sync.SyncTargetException], ADR-0009).
     */
    @Suppress("TooGenericExceptionCaught") // Any driver failure becomes a message; the app stays usable.
    private suspend fun reach(key: TextKey, unknown: TextKey, action: suspend () -> AppMessage): SyncResult = try {
        SyncResult.Success(action())
    } catch (failure: Exception) {
        val reason = failure.message
        SyncResult.Failure(
            if (reason.isNullOrBlank()) AppMessage.Text(unknown) else AppMessage.of(key, reason),
        )
    }

    /** Stores [value] as the application password, returning an honest message when the store is unavailable. */
    private fun storePassword(value: String): AppMessage? = when (val outcome = secrets.put(PASSWORD_KEY, value)) {
        is SecretResult.Unavailable -> AppMessage.core(outcome.message)
        else -> null
    }

    private fun storedPassword(): String? = (secrets.get(PASSWORD_KEY) as? SecretResult.Found)?.value

    companion object {
        /** The key slot the single application password lives under in the **Secret store**. */
        const val PASSWORD_KEY: String = "sync-password"

        val SAVED: AppMessage = AppMessage.Text(TextKey.MESSAGE_SAVED)
        val CONNECTED: AppMessage = AppMessage.Text(TextKey.MESSAGE_SYNC_CONNECTED)
        val DISCONNECTED: AppMessage = AppMessage.Text(TextKey.MESSAGE_SYNC_DISCONNECTED)
        val NOT_CONFIGURED: AppMessage = AppMessage.Text(TextKey.MESSAGE_SYNC_NOT_CONFIGURED)
        val NO_PASSWORD: AppMessage = AppMessage.Text(TextKey.MESSAGE_SYNC_NO_PASSWORD)
    }
}

/**
 * The last sync's outcome in plain language (issue #28): what moved, or that
 * nothing needed to, with a note when a write could not be settled and will
 * retry.
 */
internal fun SyncReport.summary(): AppMessage = AppMessage.SyncReportText(
    uploaded = uploaded,
    downloaded = downloaded,
    deletions = deletions,
    attachments = attachments,
    unresolved = unresolved,
    resynced = resynced,
)

/** The outcome as it is kept device-locally, so it can be read back after a restart (issue #121). */
private fun SyncResult.toOutcome(): SyncOutcome = SyncOutcome(
    succeeded = this is SyncResult.Success,
    message = AppMessage.encode(message),
)

/** The stored outcome back as the section's value, so a restart shows the same line. */
private fun SyncOutcome.toResult(): SyncResult =
    if (succeeded) SyncResult.Success(AppMessage.decode(message)) else SyncResult.Failure(AppMessage.decode(message))
