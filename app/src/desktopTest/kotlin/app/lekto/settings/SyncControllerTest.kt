package app.lekto.settings

import app.lekto.core.secret.SecretResult
import app.lekto.core.secret.SecretStore
import app.lekto.core.sync.SyncEngine
import app.lekto.core.sync.SyncItem
import app.lekto.core.sync.SyncSettings
import app.lekto.core.sync.SyncTarget
import app.lekto.core.sync.SyncTargetException
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemorySecretStore
import app.lekto.testkit.InMemorySyncSettingsStore
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.TestClock
import app.lekto.testkit.testVaultRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The sync section's state holder (issue #28): loading the stored endpoint,
 * saving it app-privately while the password goes to the **Secret store**,
 * testing the connection off the UI thread, turning sync on and off, running a
 * manual sync and reporting its outcome, and disconnecting without touching the
 * local vault. The dispatcher is the test dispatcher, so the asynchronous work
 * is driven by virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One state holder, one test per behaviour; splitting the class hides it.
class SyncControllerTest {

    @Test
    fun `it loads the stored endpoint and reports it available`() = runTest {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader"),
        )

        val controller = controller(settings = settings)

        assertTrue(controller.state.value.available)
        assertEquals("https://cloud.example.test/dav", controller.state.value.config.serverUrl)
        assertEquals(SyncController.SYNC_OFF, controller.state.value.status)
    }

    @Test
    fun `an unconfigured store starts unconfigured and off`() = runTest {
        val controller = controller()

        assertFalse(controller.state.value.config.isConfigured)
        assertFalse(controller.state.value.config.enabled)
        assertEquals(SyncController.NOT_SET_UP, controller.state.value.status)
    }

    @Test
    fun `an enabled store reports sync on`() = runTest {
        val settings = InMemorySyncSettingsStore(
            SyncSettings(serverUrl = "https://x.test/dav", username = "r", enabled = true),
        )

        assertEquals(SyncController.SYNC_ON, controller(settings = settings).state.value.status)
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

        assertEquals(SyncResult.Failure(SecretStore.UNAVAILABLE), controller.state.value.result)
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
        assertTrue(result.message.contains("unreachable"), "the reason must be shown: ${result.message}")
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
        assertEquals(SyncController.SYNC_ON, controller.state.value.status)
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
        assertEquals(SyncController.SYNC_OFF, controller.state.value.status)
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
        val controller = controller(vault = vault, secrets = secrets)
        controller.setServerUrl("https://cloud.example.test/dav")
        controller.setUsername("reader")
        controller.save("")
        controller.enable()

        controller.syncNow()
        advanceUntilIdle()

        val lastSync = controller.state.value.lastSync
        assertIs<SyncResult.Success>(lastSync)
        assertTrue(lastSync.message.contains("uploaded 1"), "the outcome names the upload: ${lastSync.message}")
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
        assertTrue(lastSync.message.contains("unreachable"), "the reason is shown: ${lastSync.message}")
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

        assertEquals(SyncResult.Failure(SecretStore.UNAVAILABLE), controller.state.value.result)
    }

    @Test
    fun `the controller state never carries the password`() = runTest {
        val controller = controller()

        controller.save("top-secret-password")

        assertFalse(controller.state.value.toString().contains("top-secret-password"))
    }

    private fun TestScope.controller(
        settings: InMemorySyncSettingsStore = InMemorySyncSettingsStore(),
        secrets: InMemorySecretStore = InMemorySecretStore(),
        target: SyncTarget = InMemorySyncTarget(),
        vault: InMemoryVaultStore = InMemoryVaultStore(),
    ): SyncController = SyncController(
        services = SyncServices(
            settings = settings,
            target = { _, _ -> target },
            engine = { chosen ->
                SyncEngine(vault, InMemoryTombstoneStore(), chosen, TestClock(), DeviceId("device-a"))
            },
        ),
        secrets = secrets,
        dispatcher = StandardTestDispatcher(testScheduler),
        scope = this,
    )
}

/** A target that cannot be reached, so the controller's failure path is exercised. */
private class FailingSyncTarget : SyncTarget by InMemorySyncTarget(changeCursor = false) {
    override suspend fun list(): List<SyncItem> = throw SyncTargetException("the WebDAV server is unreachable")
}
