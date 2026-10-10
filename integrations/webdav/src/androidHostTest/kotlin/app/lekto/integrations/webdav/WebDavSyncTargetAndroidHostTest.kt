package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncCapabilities
import app.lekto.core.sync.SyncTarget
import app.lekto.core.sync.SyncTargetException
import app.lekto.testkit.FakeWebDavServer
import app.lekto.testkit.SyncTargetContract
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI
import java.util.UUID

/**
 * The WebDAV driver on a **simulated Android runtime** (issue #116; ADR-0026):
 * the same [SyncTargetContract] the JVM driver passes, run here through the
 * Android transport — OkHttp — so the first-class client cannot quietly drift
 * from the desktop one. The in-process [FakeWebDavServer] speaks the same subset
 * over the loopback interface, so the transport is proved **without a network**
 * and without Docker, exactly as the JVM fast lane is (docs/testing.md).
 *
 * Robolectric runs the test on the host JVM, so the fake's
 * `com.sun.net.httpserver` server is available, and OkHttp speaks plain HTTP to
 * it. The failure mapping the JVM lane pins — a rejected credential and a server
 * error both become a recoverable [SyncTargetException] — is pinned here too,
 * because the transport changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class WebDavSyncTargetAndroidHostTest {

    private lateinit var server: FakeWebDavServer

    @Before
    fun start() {
        server = FakeWebDavServer()
    }

    @After
    fun stop() {
        server.close()
    }

    private fun newTarget(): SyncTarget =
        WebDavSyncTarget("${server.baseUrl}/lekto-${UUID.randomUUID()}", "lekto", "lekto")

    @Test
    fun `the Android transport passes the shared SyncTarget contract`() {
        SyncTargetContract(::newTarget).cases().forEach { case ->
            runBlocking { case.body() }
        }
    }

    @Test
    fun `reports conditional writes and no change cursor`() {
        assertEquals(
            SyncCapabilities(conditionalWrites = true, changeCursor = false),
            newTarget().capabilities(),
        )
    }

    @Test
    fun `a rejected credential is a recoverable sync error`() {
        val target = WebDavSyncTarget("${server.baseUrl}/private", "lekto", "not-the-password")

        val error = assertThrows(SyncTargetException::class.java) { runBlocking { target.list() } }

        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("401"))
    }

    @Test
    fun `a server error is a recoverable sync error, not an empty result`() {
        val target = newTarget()
        server.failNextRequestWith = SERVER_ERROR_STATUS

        assertThrows(SyncTargetException::class.java) { runBlocking { target.get("a") } }
    }

    @Test
    fun `the Android transport migrates a legacy bin attachment to data`() {
        // OkHttp must speak the MOVE the migration uses, not only the verbs the
        // contract exercises (issue #116).
        val base = "${server.baseUrl}/lekto-${UUID.randomUUID()}"
        val id = "a/b c-é"
        val bytes = byteArrayOf(0, 1, 2, -1)
        server.seed("${URI(base).path}/attachments/${WebDavNames.encode(id)}.bin", bytes)
        val target = WebDavSyncTarget(base, "lekto", "lekto")

        val ids = runBlocking { target.attachmentIds() }
        val readBack = runBlocking { target.attachment(id) }

        assertTrue("the legacy attachment must be listed", id in ids)
        assertTrue("the migrated attachment must read back", readBack?.contentEquals(bytes) == true)
    }
}

/** An internal server error, so the driver's non-2xx mapping is pinned. */
private const val SERVER_ERROR_STATUS = 500
