package app.lekto.settings

import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.core.sync.SyncEngine
import app.lekto.core.sync.SyncReport
import app.lekto.core.sync.SyncSettings
import app.lekto.core.sync.SyncSettingsStore
import app.lekto.core.sync.SyncTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
)

/**
 * The inline outcome of a sync action — a save, a connection test, enabling or
 * disconnecting (issue #28): a [Success] or a [Failure], so the section renders
 * the failure in the error colour and nothing has to read a boolean flag.
 */
sealed interface SyncResult {
    val message: String

    /** The action completed; [message] is a short confirmation. */
    data class Success(override val message: String) : SyncResult

    /** The action did not complete; [message] is an honest reason, never carrying a password. */
    data class Failure(override val message: String) : SyncResult
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
    val status: String
        get() = when {
            !config.isConfigured -> NOT_SET_UP
            config.enabled && hasStoredPassword -> SYNC_ON
            else -> SYNC_OFF
        }

    companion object {
        const val SYNC_ON: String = "Sync is on."
        const val SYNC_OFF: String = "Sync is off."
        const val NOT_SET_UP: String = "Sync isn't set up yet."
    }
}

/**
 * The sync section's state holder (issue #28; ADR-0009, ADR-0023): it saves the
 * WebDAV endpoint app-privately and the application password in the **Secret
 * store** (ADR-0021), tests the connection off the UI thread, turns sync on or
 * off as a deliberate act, runs a manual sync, and disconnects without leaving
 * the local vault unusable.
 *
 * The [dispatcher] is injected because testing a connection and syncing block,
 * and a hard-coded one cannot be driven by virtual time; the [scope] carries that
 * work (docs/testing.md, "Deterministic seams").
 */
@Suppress("TooManyFunctions") // One state holder with one small method per action; splitting scatters the state.
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
) {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

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
    }

    /** Turns sync off, leaving the endpoint configured and the vault local and usable. */
    fun disable() {
        services.settings.save(_state.value.config.copy(enabled = false))
        _state.update { current ->
            current.copy(
                config = current.config.copy(enabled = false),
                result = SyncResult.Success(SyncUiState.SYNC_OFF),
            )
        }
    }

    /**
     * Runs one manual sync (issue #28): the engine reconciles the local vault with
     * the target off the UI thread, and its outcome — moved records, or a
     * recoverable failure — is reported in plain language. A sync that fails
     * changes nothing locally, so the app stays fully usable.
     */
    fun syncNow() {
        val config = _state.value.config
        if (_state.value.syncing || !config.enabled) return
        // A run needs both halves: the endpoint and the stored password. When
        // either is missing the honest reason names the one that is.
        val password = if (config.isConfigured) storedPassword() else null
        if (password == null) {
            val reason = if (config.isConfigured) NO_PASSWORD else NOT_CONFIGURED
            _state.update { current -> current.copy(lastSync = SyncResult.Failure(reason)) }
            return
        }
        _state.update { current -> current.copy(syncing = true) }
        scope.launch {
            val outcome = withContext(dispatcher) { runSync(config, password) }
            onSynced()
            _state.update { current -> current.copy(syncing = false, lastSync = outcome) }
        }
    }

    /**
     * Disconnects (issue #28): the endpoint is cleared, sync is turned off and the
     * stored password is removed. The local vault is untouched, so the app remains
     * fully usable offline; when the **Secret store** cannot be reached the
     * password stays behind and that is reported honestly (ADR-0021).
     */
    fun disconnect() {
        services.settings.save(SyncSettings.DEFAULT)
        val deletion = secrets.delete(PASSWORD_KEY)
        _state.update { current ->
            current.copy(
                config = SyncSettings.DEFAULT,
                hasStoredPassword = deletion is SecretResult.Unavailable && current.hasStoredPassword,
                lastSync = null,
                result = (deletion as? SecretResult.Unavailable)?.let { outcome -> SyncResult.Failure(outcome.message) }
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
        return SyncUiState(available = true, config = config, hasStoredPassword = storedPassword() != null)
    }

    /** Lists the target as the lightest proof that the URL and credentials reach it. */
    private suspend fun probe(config: SyncSettings, password: String): SyncResult =
        reach("Couldn't reach the server:") {
            services.target(config, password).list()
            CONNECTED
        }

    /** Runs one sync, mapping a recoverable failure to a plain-language line. */
    private suspend fun runSync(config: SyncSettings, password: String): SyncResult =
        reach("The last sync didn't finish:") {
            services.engine(services.target(config, password)).sync().summary()
        }

    /**
     * Runs [action], reporting any failure as a plain-language line rather than a
     * crash: a transport failure must leave the app usable, and the next sync
     * retries ([app.lekto.core.sync.SyncTargetException], ADR-0009).
     */
    @Suppress("TooGenericExceptionCaught") // Any driver failure becomes a message; the app stays usable.
    private suspend fun reach(context: String, action: suspend () -> String): SyncResult = try {
        SyncResult.Success(action())
    } catch (failure: Exception) {
        SyncResult.Failure("$context ${failure.message ?: UNKNOWN_REASON}")
    }

    /** Stores [value] as the application password, returning an honest message when the store is unavailable. */
    private fun storePassword(value: String): String? = when (val outcome = secrets.put(PASSWORD_KEY, value)) {
        is SecretResult.Unavailable -> outcome.message
        else -> null
    }

    private fun storedPassword(): String? = (secrets.get(PASSWORD_KEY) as? SecretResult.Found)?.value

    companion object {
        /** The key slot the single application password lives under in the **Secret store**. */
        const val PASSWORD_KEY: String = "sync-password"

        const val SAVED: String = "Saved."
        const val CONNECTED: String = "The server answered. Sync is ready."
        const val DISCONNECTED: String = "Disconnected. Your vault stays on this device."
        const val NOT_CONFIGURED: String = "Add a server address and a username first."
        const val NO_PASSWORD: String = "Add the application password first."

        private const val UNKNOWN_REASON: String = "an unknown error"
    }
}

/**
 * The last sync's outcome in plain language (issue #28): what moved, or that
 * nothing needed to, with a note when a write could not be settled and will
 * retry.
 */
internal fun SyncReport.summary(): String {
    val parts = buildList {
        if (downloaded > 0) add("downloaded $downloaded")
        if (uploaded > 0) add("uploaded $uploaded")
        if (deletions > 0) add("removed $deletions")
        if (attachments > 0) add("carried $attachments book ${plural("original", attachments)}")
    }
    val base = if (parts.isEmpty()) "Everything was already up to date." else "Synced: ${parts.joinToString(", ")}."
    return if (unresolved > 0) "$base Some changes couldn't be settled; they'll retry next time." else base
}

private fun plural(noun: String, count: Int): String = if (count == 1) noun else "${noun}s"
