package app.lekto

import app.lekto.core.sync.SyncSettings
import app.lekto.integrations.webdav.WebDavSyncTarget
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.testVaultRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The Android composition root's sync wiring (issue #116; ADR-0026) on a
 * **simulated Android runtime**: `androidSync` is what makes **Settings → Sync**
 * usable on the first-class client, so this proves the three pieces it roots
 * under `Context.filesDir` — the settings document, the tombstone store and the
 * device id — and that the driver it builds is the real WebDAV one.
 *
 * The settings document is app-private (never the vault, ADR-0005), a delete
 * leaves its tombstone under `sync/`, the engine reconciles the Android vault the
 * library writes, and the driver is the WebDAV [WebDavSyncTarget] the Android
 * transport reaches (ADR-0026). The section's controls are proved on this lane by
 * `SettingsScreenSemanticsTest` and `AppSemanticsTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AndroidSyncHostTest {

    @Test
    fun `the endpoint is stored app-privately under filesDir`() {
        val config = SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader")

        androidSync(context()).settings.save(config)

        assertEquals(config, androidSync(context()).settings.load())
        assertTrue(File(context().filesDir, "sync/sync-settings.json").isFile)
    }

    @Test
    fun `the engine reconciles the Android vault and persists a device id under filesDir`() {
        val record = testVaultRecord("vocabulary-1")
        androidVaultStore(context()).put(record)
        val target = InMemorySyncTarget()

        val report = runBlocking { androidSync(context()).engine(target).sync() }
        val uploaded = runBlocking { target.list() }

        assertEquals(1, report.uploaded)
        assertTrue(uploaded.any { item -> item.id == record.id })
        assertTrue(File(context().filesDir, "device-id").isFile)
    }

    @Test
    fun `a delete leaves its tombstone app-privately under filesDir`() {
        val record = testVaultRecord("vocabulary-1")
        androidVaultStore(context()).put(record)

        androidSync(context()).engine(InMemorySyncTarget()).delete(record.id)

        assertTrue(File(context().filesDir, "sync/tombstones.json").isFile)
    }

    @Test
    fun `the target is the WebDAV driver`() {
        val config = SyncSettings(serverUrl = "https://cloud.example.test/dav", username = "reader")

        assertTrue(androidSync(context()).target(config, "app-password") is WebDavSyncTarget)
    }

    private fun context() = RuntimeEnvironment.getApplication()
}
