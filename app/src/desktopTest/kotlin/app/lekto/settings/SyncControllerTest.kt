package app.lekto.settings

import app.lekto.LocalChanges
import app.lekto.core.MasteryLevel
import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.core.sync.Revision
import app.lekto.core.sync.SyncEngine
import app.lekto.core.sync.SyncListing
import app.lekto.core.sync.SyncOutcome
import app.lekto.core.sync.SyncSchedule
import app.lekto.core.sync.SyncSettings
import app.lekto.core.sync.SyncTarget
import app.lekto.core.sync.SyncTargetException
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.VersionedRecord
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.core.vocabulary.VocabularyRecord
import app.lekto.testkit.InMemoryRemoteIndexStore
import app.lekto.testkit.InMemorySecretStore
import app.lekto.testkit.InMemorySyncOutcomeStore
import app.lekto.testkit.InMemorySyncSettingsStore
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TestClock
import app.lekto.testkit.deterministicSeams
import app.lekto.testkit.testVaultRecord
import app.lekto.text.AppMessage
import app.lekto.text.appTextsFor
import app.lekto.vocabulary.VocabularyController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val FIXED_RUN_MILLIS = 1_700_000_000_000L

/** How many rapid page turns the coalescing test emits; only the count matters, not the value. */
private const val PAGE_TURNS = 10

/**
 * The sync section's state holder (issue #28): loading the stored endpoint,
 * saving it app-privately while the password goes to the **Secret store**,
 * testing the connection off the UI thread, turning sync on and off, running a
 * manual sync and reporting its outcome, and disconnecting without touching the
 * local vault. The dispatcher is the test dispatcher, so the asynchronous work
 * is driven by virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions", "LargeClass") // One state holder, one test per behaviour; splitting the class hides it.
class SyncControllerTest {

    @Test
    fun `it loads the stored endpoint and reports it available`() = runTest {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
        )

        val controller = controller(settings = settings)

        assertTrue(controller.state.value.available)
        assertEquals("https://cloud.example.test/dav", controller.state.value.config.serverUrl)
        assertEquals(SyncUiState.SYNC_OFF, controller.state.value.status)
    }

    @Test
    fun `an unconfigured store starts unconfigured and off`() = runTest {
        val controller = controller()

        assertFalse(controller.state.value.config.isConfigured)
        assertFalse(controller.state.value.config.enabled)
        assertEquals(SyncUiState.NOT_SET_UP, controller.state.value.status)
    }

    @Test
    fun `an enabled store with a stored password reports sync on`() = runTest {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://x.test/dav", username = "r", enabled = true),
        )
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }

        assertEquals(SyncUiState.SYNC_ON, controller(settings = settings, secrets = secrets).state.value.status)
    }

    @Test
    fun `an enabled store without a stored password does not claim sync is on`() = runTest {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://x.test/dav", username = "r", enabled = true),
        )

        assertEquals(SyncUiState.SYNC_OFF, controller(settings = settings).state.value.status)
    }

    @Test
    fun `saving persists the endpoint and stores the password in the secret store`() = runTest {
        val settings = InMemorySyncSettingsStore()
        val secrets = InMemorySecretStore()
        val controller = controller(settings = settings, secrets = secrets)

        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("app-password")

        assertEquals(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            settings.load(),
        )
        assertEquals(SecretResult.Found("app-password"), secrets.get(SyncController.PASSWORD_KEY))
        assertTrue(controller.state.value.hasStoredPassword)
        assertEquals(SyncResult.Success(SyncController.SAVED), controller.state.value.result)
    }

    @Test
    fun `connecting tests the endpoint, stores the password and turns sync on`() = runTest {
        val settings = InMemorySyncSettingsStore()
        val secrets = InMemorySecretStore()
        val target = InMemorySyncTarget()
        val controller = controller(settings = settings, secrets = secrets, target = target)

        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.connect("app-password")
        advanceUntilIdle()

        assertEquals(1, target.listCalls)
        assertEquals(SecretResult.Found("app-password"), secrets.get(SyncController.PASSWORD_KEY))
        assertTrue(settings.load().enabled, "a successful connection turns sync on")
        assertEquals(SyncResult.Success(SyncController.CONNECTED), controller.state.value.result)
        assertNotNull(controller.state.value.lastRunAt, "the first sync runs and records its time")
    }

    @Test
    fun `a failed connection stores nothing and leaves sync off`() = runTest {
        val settings = InMemorySyncSettingsStore()
        val secrets = InMemorySecretStore()
        val target = FailingSyncTarget()
        val controller = controller(settings = settings, secrets = secrets, target = target)

        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.connect("app-password")
        advanceUntilIdle()

        assertIs<SyncResult.Failure>(controller.state.value.result)
        assertEquals(SecretResult.Absent, secrets.get(SyncController.PASSWORD_KEY))
        assertFalse(settings.load().enabled)
    }

    @Test
    fun `connecting without a password says so`() = runTest {
        val controller = controller()

        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.connect("")
        advanceUntilIdle()

        assertEquals(SyncResult.Failure(SyncController.NO_PASSWORD), controller.state.value.result)
    }

    @Test
    fun `connecting without an endpoint says so`() = runTest {
        val controller = controller()

        controller.connect("app-password")
        advanceUntilIdle()

        assertEquals(SyncResult.Failure(SyncController.NOT_CONFIGURED), controller.state.value.result)
    }

    @Test
    fun `the endpoint and username write at once`() = runTest {
        val settings = InMemorySyncSettingsStore()
        val controller = controller(settings = settings)

        // Reversible fields apply on change; only the password needs an explicit Save (issue #213).
        controller.setServerUrl("https://cloud.example.test/dav")
        assertEquals(SyncSettings(serverUrl = "https://cloud.example.test/dav"), settings.load())

        controller.setUsername("reader")
        assertEquals(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
            settings.load(),
        )
    }

    @Test
    fun `saving with no password typed leaves the stored password in place`() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(SyncController.PASSWORD_KEY, "existing")
        val controller = controller(secrets = secrets)

        controller.save("")

        assertEquals(SecretResult.Found("existing"), secrets.get(SyncController.PASSWORD_KEY))
        assertTrue(controller.state.value.hasStoredPassword)
    }

    @Test
    fun `saving reports an unavailable secret store inline`() = runTest {
        val controller = controller(secrets = InMemorySecretStore(available = false))

        controller.save("app-password")

        assertEquals(SyncResult.Failure(AppMessage.core(SecretStore.UNAVAILABLE)), controller.state.value.result)
        assertFalse(controller.state.value.hasStoredPassword)
    }

    @Test
    fun `a test lists the configured target and reports it reachable`() = runTest {
        val target = InMemorySyncTarget()
        val controller = controller(target = target)

        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.test("app-password")
        advanceUntilIdle()

        assertEquals(1, target.listCalls)
        assertEquals(SyncResult.Success(SyncController.CONNECTED), controller.state.value.result)
        assertFalse(controller.state.value.testing)
    }

    @Test
    fun `a test uses the stored password when the field is empty`() = runTest {
        val secrets = InMemorySecretStore()
        secrets.put(SyncController.PASSWORD_KEY, "stored")
        val target = InMemorySyncTarget()
        val controller = controller(secrets = secrets, target = target)

        controller.test("")
        advanceUntilIdle()

        assertEquals(1, target.listCalls)
    }

    @Test
    fun `a test with no password says so rather than reaching the server`() = runTest {
        val target = InMemorySyncTarget()
        val controller = controller(target = target)

        controller.test("")
        advanceUntilIdle()

        assertEquals(0, target.listCalls)
        assertEquals(SyncResult.Failure(SyncController.NO_PASSWORD), controller.state.value.result)
    }

    @Test
    fun `a failed test is reported inline and clears the testing flag`() = runTest {
        val controller = controller(target = FailingSyncTarget())

        controller.test("app-password")
        advanceUntilIdle()

        val result = controller.state.value.result
        assertIs<SyncResult.Failure>(result)
        assertTrue(
            appTextsFor(null).message(result.message).contains("unreachable"),
            "the reason must be shown: ${result.message}",
        )
        assertFalse(controller.state.value.testing)
    }

    @Test
    fun `enabling needs a configured endpoint`() = runTest {
        val controller = controller()

        controller.enable()

        assertEquals(SyncResult.Failure(SyncController.NOT_CONFIGURED), controller.state.value.result)
        assertFalse(controller.state.value.config.enabled)
    }

    @Test
    fun `enabling needs a stored password`() = runTest {
        val controller = controller()

        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.enable()

        assertEquals(SyncResult.Failure(SyncController.NO_PASSWORD), controller.state.value.result)
        assertFalse(controller.state.value.config.enabled)
    }

    @Test
    fun `enabling persists the choice`() = runTest {
        val settings = InMemorySyncSettingsStore()
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(settings = settings, secrets = secrets)
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")

        controller.enable()

        assertTrue(settings.load().enabled)
        assertTrue(controller.state.value.config.enabled)
        assertEquals(SyncUiState.SYNC_ON, controller.state.value.status)
    }

    @Test
    fun `enabling runs the first sync, so the choice has a visible outcome`() = runTest {
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val target = InMemorySyncTarget()
        val controller = controller(secrets = secrets, target = target)
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")

        controller.enable()
        advanceUntilIdle()

        assertEquals(1, target.changesCalls)
        assertIs<SyncResult.Success>(controller.state.value.lastSync)
    }

    @Test
    fun `disabling persists the choice and leaves the endpoint`() = runTest {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
        )
        val controller = controller(settings = settings)

        controller.disable()

        assertFalse(settings.load().enabled)
        assertEquals("https://cloud.example.test/dav", settings.load().serverUrl)
        assertEquals(SyncUiState.SYNC_OFF, controller.state.value.status)
    }

    @Test
    fun `sync now is a no-op while sync is off`() = runTest {
        val target = InMemorySyncTarget()
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(secrets = secrets, target = target)
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")

        controller.syncNow()
        advanceUntilIdle()

        assertEquals(0, target.listCalls)
        assertEquals(null, controller.state.value.lastSync)
    }

    @Test
    fun `sync now runs the engine and reports what moved in plain language`() = runTest {
        val vault = InMemoryVaultStore().apply { put(testVaultRecord(id = "vocabulary-1")) }
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
        )
        val controller = controller(settings = settings, vault = vault, secrets = secrets)

        controller.syncNow()
        advanceUntilIdle()

        val lastSync = controller.state.value.lastSync
        assertIs<SyncResult.Success>(lastSync)
        assertTrue(
            appTextsFor(null).message(lastSync.message).contains("uploaded 1"),
            "the outcome names the upload: ${lastSync.message}",
        )
        assertFalse(controller.state.value.syncing)
    }

    @Test
    fun `a failed sync is reported in plain language and leaves the vault usable`() = runTest {
        val vault = InMemoryVaultStore().apply { put(testVaultRecord(id = "vocabulary-1")) }
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(vault = vault, target = FailingSyncTarget(), secrets = secrets)
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")
        controller.enable()

        controller.syncNow()
        advanceUntilIdle()

        val lastSync = controller.state.value.lastSync
        assertIs<SyncResult.Failure>(lastSync)
        assertTrue(
            appTextsFor(null).message(lastSync.message).contains("unreachable"),
            "the reason is shown: ${lastSync.message}",
        )
        assertTrue(vault.all().isNotEmpty(), "the local vault is untouched by a failed sync")
    }

    @Test
    fun `disconnecting clears the endpoint and the stored password`() = runTest {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
        )
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(settings = settings, secrets = secrets)

        controller.disconnect()

        assertEquals(SyncSettings.DEFAULT, settings.load())
        assertEquals(SecretResult.Absent, secrets.get(SyncController.PASSWORD_KEY))
        assertFalse(controller.state.value.config.enabled)
        assertEquals(null, controller.state.value.lastSync)
        assertTrue(controller.state.value.result is SyncResult.Success)
    }

    @Test
    fun `disconnecting reports an unavailable secret store rather than claiming the password is gone`() = runTest {
        val secrets = InMemorySecretStore(available = false)
        val controller = controller(secrets = secrets)

        controller.disconnect()

        assertEquals(SyncResult.Failure(AppMessage.core(SecretStore.UNAVAILABLE)), controller.state.value.result)
    }

    @Test
    fun `the controller state never carries the password`() = runTest {
        val controller = controller()

        controller.save("top-secret-password")

        assertFalse(controller.state.value.toString().contains("top-secret-password"))
    }

    @Test
    fun `a test runs the blocking probe on the injected dispatcher`() = runTest {
        // Android forbids a network call on the main thread; the probe must move
        // to the injected dispatcher, not run on whatever scope the caller owns
        // (issue #116).
        val dispatches = AtomicInteger()
        val controller = controller(dispatcher = countingDispatcher(dispatches))
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")

        controller.test("app-password")
        advanceUntilIdle()

        assertTrue(dispatches.get() > 0, "the probe must run on the injected dispatcher")
    }

    @Test
    fun `sync now runs the blocking engine on the injected dispatcher`() = runTest {
        val dispatches = AtomicInteger()
        val vault = InMemoryVaultStore().apply { put(testVaultRecord(id = "vocabulary-1")) }
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(vault = vault, secrets = secrets, dispatcher = countingDispatcher(dispatches))
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")

        controller.enable()
        advanceUntilIdle()

        assertTrue(dispatches.get() > 0, "the sync must run on the injected dispatcher")
    }

    @Test
    fun `a completed sync refreshes the vault readers`() = runTest {
        // The library reads the vault once; a sync writes to it directly, so the
        // controller must tell the library to reload or it shows a stale list
        // (issue #116).
        val refreshes = AtomicInteger()
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(secrets = secrets, onSynced = { refreshes.incrementAndGet() })
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")

        controller.enable()
        advanceUntilIdle()

        assertTrue(refreshes.get() > 0, "a sync must refresh the library")
    }

    @Test
    fun `the last outcome is shown at startup from the device-local store`() = runTest {
        // The outcome must survive a restart, so a fresh controller shows what the
        // previous session ended with (issue #121).
        val stored = AppMessage.SyncReportText(
            uploaded = 3,
            downloaded = 0,
            deletions = 0,
            attachments = 0,
            unresolved = 0,
            resynced = false,
        )
        val outcomes = InMemorySyncOutcomeStore(
            SyncOutcome(succeeded = true, message = AppMessage.encode(stored), atMillis = FIXED_RUN_MILLIS),
        )

        val controller = controller(outcomes = outcomes)

        assertEquals(SyncResult.Success(stored), controller.state.value.lastSync)
        assertEquals(FIXED_RUN_MILLIS, controller.state.value.lastRunAt)
    }

    @Test
    fun `a completed sync keeps its outcome for the next launch`() = runTest {
        val outcomes = InMemorySyncOutcomeStore()
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(secrets = secrets, outcomes = outcomes)
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")

        controller.enable()
        advanceUntilIdle()

        val saved = outcomes.load()
        assertIs<SyncOutcome>(saved)
        assertTrue(saved.succeeded, "a successful run is remembered as a success")
    }

    @Test
    fun `a failed sync keeps its plain-language outcome for the next launch`() = runTest {
        val outcomes = InMemorySyncOutcomeStore()
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(target = FailingSyncTarget(), secrets = secrets, outcomes = outcomes)
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")

        controller.enable()
        advanceUntilIdle()

        val saved = outcomes.load()
        assertIs<SyncOutcome>(saved)
        assertFalse(saved.succeeded)
        assertTrue(saved.message.contains("unreachable"), "the reason is kept: ${saved.message}")
    }

    @Test
    fun `starting with sync on runs a sync without a manual press`() = runTest {
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target)

        controller.start()
        runCurrent()

        assertEquals(1, target.changesCalls, "an app-start run")
        controller.disable()
    }

    @Test
    fun `starting with sync off raises no trigger`() = runTest {
        val target = InMemorySyncTarget()
        val controller = controller(target = target)

        controller.start()
        runCurrent()

        assertEquals(0, target.changesCalls)
    }

    @Test
    fun `the periodic timer runs a sync after the interval`() = runTest {
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target, schedule = SyncSchedule(1.minutes, 4.minutes))

        controller.start()
        runCurrent()
        assertEquals(1, target.changesCalls, "the app-start run")

        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(2, target.changesCalls, "the periodic run")
        controller.disable()
    }

    @Test
    fun `a trigger while a run is in flight coalesces into one follow-up`() = runTest {
        // Runs never overlap: a burst of triggers raised during one run must
        // collapse to a single follow-up, not a storm (issue #121).
        val target = InMemorySyncTarget().apply { latency = 1.seconds }
        val controller = enabledController(target = target)

        controller.syncNow()
        controller.syncNow()
        controller.syncNow()
        advanceUntilIdle()

        assertEquals(2, target.changesCalls, "one run plus a single coalesced follow-up")
    }

    @Test
    fun `resuming the app runs a sync when sync is on`() = runTest {
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target)

        controller.onResumed()
        runCurrent()

        assertEquals(1, target.changesCalls)
        controller.disable()
    }

    @Test
    fun `resuming the app raises no trigger while sync is off`() = runTest {
        val target = InMemorySyncTarget()
        val controller = controller(target = target)

        controller.onResumed()
        runCurrent()

        assertEquals(0, target.changesCalls)
    }

    @Test
    fun `turning sync off stops the periodic trigger`() = runTest {
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target, schedule = SyncSchedule(1.minutes, 4.minutes))

        controller.start()
        runCurrent()
        controller.disable()
        val afterTurningOff = target.changesCalls

        advanceTimeBy(10.minutes)
        runCurrent()

        assertEquals(afterTurningOff, target.changesCalls, "no trigger may fire after sync is off")
    }

    @Test
    fun `a content change pushes a sync after a short settle`() = runTest {
        // A book import or a saved word is discrete, so it pushes a run (issue #121).
        val changes = LocalChanges()
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target, changes = changes)

        controller.start()
        runCurrent()
        val baseline = target.changesCalls

        changes.contentChanged()
        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(baseline + 1, target.changesCalls, "the content change pushes a run")
        controller.disable()
    }

    @Test
    fun `changing a word's mastery pushes the updated record to the target`() = runTest {
        // Does a mastery change sync? Yes: the level lives on the vocabulary
        // record, so moving it is a content change that pushes a run (issue #121).
        val vault = InMemoryVaultStore()
        val changes = LocalChanges()
        val words = VocabularyController(
            VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a")),
            changes,
        )
        val target = InMemorySyncTarget(changeCursor = false)
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
        )
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val sync = controller(settings = settings, secrets = secrets, target = target, vault = vault, changes = changes)
        sync.start()
        runCurrent()

        val entry = VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.RECOGNIZED)
        words.save(entry)
        advanceTimeBy(5.seconds)
        runCurrent()
        val id = VocabularyRecord.idOf(entry.key)
        assertEquals(MasteryLevel.RECOGNIZED, storedMastery(target, id), "the first save pushes the word")

        words.save(entry.copy(mastery = MasteryLevel.LEARNED))
        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(MasteryLevel.LEARNED, storedMastery(target, id), "the mastery change pushes the update")
        sync.disable()
    }

    /** The mastery the target now holds for [id], decoded from the pushed record. */
    private fun storedMastery(target: InMemorySyncTarget, id: String): MasteryLevel? {
        val live = target.stored(id)?.version as? VersionedRecord.Live
        return live?.let { version -> VocabularyRecord.entryOf(version.record).mastery }
    }

    @Test
    fun `a burst of content changes coalesces into one pushed run`() = runTest {
        val changes = LocalChanges()
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target, changes = changes)

        controller.start()
        runCurrent()
        val baseline = target.changesCalls

        changes.contentChanged()
        advanceTimeBy(2.seconds)
        changes.contentChanged()
        advanceTimeBy(2.seconds)
        changes.contentChanged()
        advanceTimeBy(5.seconds)
        runCurrent()

        assertEquals(baseline + 1, target.changesCalls, "a burst settles into one run")
        controller.disable()
    }

    @Test
    fun `a reading change pushes a run only after a pause`() = runTest {
        // A page turn is frequent, so it waits for the reader to settle (issue #121).
        val changes = LocalChanges()
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target, changes = changes)

        controller.start()
        runCurrent()
        val baseline = target.changesCalls

        changes.progressChanged()
        advanceTimeBy(20.seconds)
        runCurrent()

        assertEquals(baseline, target.changesCalls, "no run before the reading pause")

        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(baseline + 1, target.changesCalls, "the run after the reading pause")
        controller.disable()
    }

    @Test
    fun `a burst of page turns coalesces into one pushed run`() = runTest {
        val changes = LocalChanges()
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target, changes = changes)

        controller.start()
        runCurrent()
        val baseline = target.changesCalls

        repeat(PAGE_TURNS) {
            changes.progressChanged()
            advanceTimeBy(1.seconds)
        }
        advanceTimeBy(30.seconds)
        runCurrent()

        assertEquals(baseline + 1, target.changesCalls, "a burst of turns settles into one run")
        controller.disable()
    }

    @Test
    fun `a local change raises no trigger while sync is off`() = runTest {
        val changes = LocalChanges()
        val target = InMemorySyncTarget()
        val controller = controller(target = target, changes = changes)

        controller.start()
        runCurrent()
        changes.contentChanged()
        changes.progressChanged()
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(0, target.changesCalls)
    }

    @Test
    fun `turning sync off stops the change push`() = runTest {
        val changes = LocalChanges()
        val target = InMemorySyncTarget()
        val controller = enabledController(target = target, changes = changes)

        controller.start()
        runCurrent()
        controller.disable()
        val afterTurningOff = target.changesCalls

        changes.contentChanged()
        changes.progressChanged()
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(afterTurningOff, target.changesCalls, "no change may push a run after sync is off")
    }

    @Test
    fun `a failing target is backed off, so its runs grow apart`() = runTest {
        val target = CountingFailingTarget()
        val controller = enabledController(target = target, schedule = SyncSchedule(1.minutes, 4.minutes))

        controller.start()
        runCurrent()
        assertEquals(1, target.attempts.get(), "the app-start run")

        // After the failure the wait doubles to two minutes: nothing at one.
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(1, target.attempts.get(), "no run before the backed-off interval")

        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(2, target.attempts.get(), "the run after the backed-off interval")

        // The second failure doubles again to four minutes.
        val afterTwoFailures = target.attempts.get()
        advanceTimeBy(3.minutes)
        runCurrent()
        assertEquals(afterTwoFailures, target.attempts.get(), "no run before the next backed-off interval")

        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(afterTwoFailures + 1, target.attempts.get(), "the run after the next backed-off interval")
        controller.disable()
    }

    @Test
    fun `a success rebases the periodic wait, so a recovered target is not left waiting long`() = runTest {
        val target = FailOnceTarget()
        val changes = LocalChanges()
        val schedule = SyncSchedule(1.minutes, 4.minutes)
        val controller = enabledController(target = target, changes = changes, schedule = schedule)

        controller.start()
        runCurrent()
        assertEquals(1, target.attempts.get(), "the app-start run fails and backs the timer off to two minutes")

        // A push that now succeeds resets the failure count and the wait to the base.
        changes.contentChanged()
        advanceTimeBy(5.seconds)
        runCurrent()
        val afterPush = target.attempts.get()
        assertEquals(2, afterPush, "the pushed run succeeds")

        // The timer fires at the base one minute later, not the stale two-minute wait.
        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(afterPush + 1, target.attempts.get(), "the timer uses the rebased wait")
        controller.disable()
    }

    @Test
    fun `disconnecting forgets the stored outcome`() = runTest {
        val outcomes = InMemorySyncOutcomeStore(SyncOutcome(succeeded = true, message = "Synced."))
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
        )
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(settings = settings, secrets = secrets, outcomes = outcomes)

        controller.disconnect()

        assertNull(outcomes.load())
        assertNull(controller.state.value.lastSync)
    }

    @Test
    fun `a run that finishes after disconnecting does not resurrect the outcome`() = runTest {
        // The in-flight run completes after the user disconnected; it must not
        // re-save an outcome the disconnect just cleared (issue #121).
        val outcomes = InMemorySyncOutcomeStore()
        val target = InMemorySyncTarget().apply { latency = 1.seconds }
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
        )
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        val controller = controller(settings = settings, secrets = secrets, target = target, outcomes = outcomes)

        controller.syncNow()
        controller.disconnect()
        advanceUntilIdle()

        assertNull(outcomes.load())
        assertNull(controller.state.value.lastSync)
        assertFalse(controller.state.value.syncing)
    }

    /** A controller with sync already on and a stored password, ready to be started. */
    private fun TestScope.enabledController(
        target: SyncTarget,
        changes: LocalChanges = LocalChanges(),
        schedule: SyncSchedule = SyncSchedule(),
    ): SyncController {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader", enabled = true),
        )
        val secrets = InMemorySecretStore().apply { put(SyncController.PASSWORD_KEY, "stored") }
        return controller(
            settings = settings,
            secrets = secrets,
            target = target,
            changes = changes,
            schedule = schedule,
        )
    }

    @Suppress("LongParameterList") // The test's own inputs, bundled so every case reads the same.
    private fun TestScope.controller(
        settings: InMemorySyncSettingsStore = InMemorySyncSettingsStore(),
        secrets: InMemorySecretStore = InMemorySecretStore(),
        target: SyncTarget = InMemorySyncTarget(),
        vault: InMemoryVaultStore = InMemoryVaultStore(),
        outcomes: InMemorySyncOutcomeStore = InMemorySyncOutcomeStore(),
        changes: LocalChanges = LocalChanges(),
        schedule: SyncSchedule = SyncSchedule(),
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        clock: TestClock = TestClock(),
        onSynced: () -> Unit = {},
    ): SyncController = SyncController(
        services = SyncServices(
            settings = settings,
            target = { _, _ -> target },
            engine = { chosen ->
                SyncEngine(
                    vault,
                    InMemoryTombstoneStore(),
                    InMemoryRemoteIndexStore(),
                    chosen,
                    TestClock(),
                )
            },
            outcomes = outcomes,
        ),
        secrets = secrets,
        dispatcher = dispatcher,
        scope = this,
        onSynced = onSynced,
        changes = changes,
        schedule = schedule,
        clock = clock,
    )
}

/** A target that cannot be reached, so the controller's failure path is exercised. */
private class FailingSyncTarget : SyncTarget by InMemorySyncTarget(changeCursor = false) {
    override suspend fun list(known: Map<String, Revision>): SyncListing =
        throw SyncTargetException("the WebDAV server is unreachable")
}

/** A target that cannot be reached and counts its attempts, so the backoff is observable. */
private class CountingFailingTarget : SyncTarget by InMemorySyncTarget(changeCursor = false) {
    val attempts = AtomicInteger()

    override suspend fun list(known: Map<String, Revision>): SyncListing {
        attempts.incrementAndGet()
        throw SyncTargetException("the WebDAV server is unreachable")
    }
}

/** A target that fails its first attempt and then answers, so a rebase can be observed. */
private class FailOnceTarget(private val delegate: InMemorySyncTarget = InMemorySyncTarget(changeCursor = false)) :
    SyncTarget by delegate {
    val attempts = AtomicInteger()

    override suspend fun list(known: Map<String, Revision>): SyncListing {
        if (attempts.incrementAndGet() == 1) throw SyncTargetException("the WebDAV server is unreachable")
        return delegate.list(known)
    }
}

/**
 * A dispatcher that counts the work it is handed and runs it inline, so a test
 * can prove a blocking call was moved onto the injected dispatcher rather than
 * left on the caller's scope (issue #116: Android forbids network on the main
 * thread, and the composition scope is the main thread there).
 */
private fun countingDispatcher(dispatches: AtomicInteger): CoroutineDispatcher = object : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatches.incrementAndGet()
        block.run()
    }
}
