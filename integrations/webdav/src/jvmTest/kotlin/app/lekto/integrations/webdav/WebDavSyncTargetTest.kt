package app.lekto.integrations.webdav

import app.lekto.core.sync.Revision
import app.lekto.core.sync.SyncCapabilities
import app.lekto.core.sync.SyncChanges
import app.lekto.core.sync.SyncCursor
import app.lekto.core.sync.SyncListing
import app.lekto.core.sync.SyncTarget
import app.lekto.core.sync.SyncTargetException
import app.lekto.core.sync.WriteOutcome
import app.lekto.core.vault.VersionedRecord
import app.lekto.testkit.FakeWebDavServer
import app.lekto.testkit.InMemorySyncTarget
import app.lekto.testkit.SyncTargetContract
import app.lekto.testkit.testVaultRecord
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.net.URI
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The WebDAV driver's fast lane (ticket #27): the shared [SyncTargetContract]
 * run against an in-process WebDAV server, so the driver's mapping — the remote
 * layout, the conditional writes, the attachment channel — is proven without
 * Docker. The same contract runs against a real `mod_dav` container in
 * [WebDavSyncTargetContainerTest], the lane CI runs on a pull request.
 */
class WebDavSyncTargetTest {

    private lateinit var server: FakeWebDavServer

    @BeforeEach
    fun start() {
        server = FakeWebDavServer()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun newTarget(): SyncTarget =
        WebDavSyncTarget("${server.baseUrl}/lekto-${UUID.randomUUID()}", "lekto", "lekto")

    @TestFactory
    fun `passes the shared SyncTarget contract`(): List<DynamicTest> =
        SyncTargetContract(::newTarget).cases().map { case ->
            DynamicTest.dynamicTest(case.name) { runBlocking { case.body() } }
        }

    @Test
    fun `reports conditional writes and no change cursor`() {
        assertEquals(
            SyncCapabilities(conditionalWrites = true, changeCursor = false),
            newTarget().capabilities(),
        )
    }

    @Test
    fun `a driver that misreports its capabilities fails the contract`() {
        // A target that claims a compare-and-swap but writes unconditionally is
        // exactly the dishonesty ADR-0009 warns about; the capability-gated
        // contract must catch it rather than let it corrupt a vault.
        val liar = object : SyncTarget by InMemorySyncTarget(conditionalWrites = false) {
            override fun capabilities(): SyncCapabilities =
                SyncCapabilities(conditionalWrites = true, changeCursor = false)
        }
        val stale = SyncTargetContract { liar }.cases()
            .single { case -> case.name == "a write with a stale revision is rejected" }

        assertFailsWith<AssertionError> { runBlocking { stale.body() } }
    }

    @Test
    fun `a driver that misreports a change cursor fails the contract`() {
        // A target that claims an incremental cursor but answers every change set
        // with the whole collection cannot pass the cursor cases the claim adds.
        val liar = object : SyncTarget by InMemorySyncTarget(changeCursor = false) {
            override fun capabilities(): SyncCapabilities =
                SyncCapabilities(conditionalWrites = false, changeCursor = true)

            override suspend fun changes(cursor: SyncCursor?): SyncChanges =
                SyncChanges(list().items, SyncCursor("everything"))
        }
        val cursorCase = SyncTargetContract { liar }.cases()
            .single { case -> case.name == "a cursor returns only the items changed after it" }

        assertFailsWith<AssertionError> { runBlocking { cursorCase.body() } }
    }

    @Test
    fun `a driver that ignores the known revisions fails the contract`() {
        // A driver that re-reads every item would make the whole optimisation a
        // no-op; the contract's listing case must catch it (ADR-0027).
        val delegate = InMemorySyncTarget(changeCursor = false)
        val liar = object : SyncTarget by delegate {
            override suspend fun list(known: Map<String, Revision>): SyncListing =
                delegate.list(emptyMap()).let { SyncListing(it.items, emptySet()) }
        }
        val reReadCase = SyncTargetContract { liar }.cases()
            .single { case -> case.name == "a listing told the known revisions does not re-read them" }

        assertFailsWith<AssertionError> { runBlocking { reReadCase.body() } }
    }

    @Test
    fun `a listing told the known revisions does not GET an unchanged record`() {
        // The point of issue #119: a steady-state listing is one PROPFIND plus the
        // changed records, not a GET per record.
        val target = newTarget()
        val record = testVaultRecord("a", updatedAtMillis = 1)
        runBlocking { target.put(VersionedRecord.of(record), null) }
        val known = runBlocking { target.list() }.items.associate { it.id to it.revision }
        val getsBefore = server.getCount

        val again = runBlocking { target.list(known) }

        assertEquals(getsBefore, server.getCount, "an unchanged record must not be fetched")
        assertTrue("a" in again.unchanged, "an unchanged record must be named unchanged")
        assertTrue(again.items.isEmpty(), "an unchanged record must not be read again")
    }

    @Test
    fun `a record whose ETag moved is fetched again`() {
        val target = newTarget()
        runBlocking { target.put(VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 1)), null) }
        val known = runBlocking { target.list() }.items.associate { it.id to it.revision }
        runBlocking { target.put(VersionedRecord.of(testVaultRecord("a", updatedAtMillis = 2)), known.getValue("a")) }

        val again = runBlocking { target.list(known) }

        assertTrue(again.items.any { it.id == "a" }, "a moved ETag must be fetched again")
        assertTrue("a" !in again.unchanged, "a moved ETag is not unchanged")
    }

    @Test
    fun `a rejected credential is a recoverable sync error`() {
        val target = WebDavSyncTarget("${server.baseUrl}/private", "lekto", "not-the-password")

        val error = assertFailsWith<SyncTargetException> { runBlocking { target.list() } }

        assertTrue(error.message.orEmpty().contains("401"), error.message.orEmpty())
    }

    @Test
    fun `a server error is a recoverable sync error, not an empty result`() {
        val target = newTarget()
        server.failNextRequestWith = 500

        assertFailsWith<SyncTargetException> { runBlocking { target.get("a") } }
    }

    @Test
    fun `a server that rejects MKCOL on an existing collection still lists`() {
        // Infomaniak kDrive answers 404 to MKCOL on a collection that already
        // exists — even the drive root — so the driver reads existence with a
        // zero-depth PROPFIND instead of demanding a MKCOL outcome (issue #117).
        server.mkcolExistingStatus = 404
        val base = "${server.baseUrl}/lekto-${UUID.randomUUID()}"
        val record = testVaultRecord("a", updatedAtMillis = 1)
        runBlocking { WebDavSyncTarget(base, "lekto", "lekto").put(VersionedRecord.of(record), null) }

        val listing = runBlocking { WebDavSyncTarget(base, "lekto", "lekto").list() }

        assertTrue(
            listing.items.any { item -> item.id == "a" },
            "a collection that already exists must still be listed",
        )
    }

    @Test
    fun `a base the server does not recognise fails with a clear message`() {
        val target = WebDavSyncTarget("${server.baseUrl}/no-such-parent/child", "lekto", "lekto")

        val error = assertFailsWith<SyncTargetException> { runBlocking { target.list() } }

        assertTrue(error.message.orEmpty().contains("did not recognise"), error.message.orEmpty())
    }

    @Test
    fun `a write whose PUT omits the ETag reads it back with a HEAD`() {
        server.omitEtagOnPut = true
        val target = newTarget()
        val record = testVaultRecord("a", updatedAtMillis = 1)

        val outcome = runBlocking { target.put(VersionedRecord.of(record), null) }
        val readBack = runBlocking { target.get("a") }

        assertEquals((outcome as WriteOutcome.Written).revision, readBack?.revision)
        assertEquals(VersionedRecord.of(record), readBack?.version)
    }

    @Test
    fun `a legacy bin attachment is migrated to data and reads back`() {
        // A pre-issue-#116 client wrote `.bin`, which Koofr refuses to serve; the
        // driver renames it to `.data` in place, so the original is recovered
        // without re-uploading it.
        val base = "${server.baseUrl}/lekto-${UUID.randomUUID()}"
        val id = "a/b c-é"
        val bytes = byteArrayOf(0, 1, 2, -1)
        server.seed("${URI(base).path}/attachments/${WebDavNames.encode(id)}.bin", bytes)
        val target = WebDavSyncTarget(base, "lekto", "lekto")

        val ids = runBlocking { target.attachmentIds() }
        val readBack = runBlocking { target.attachment(id) }

        assertTrue(id in ids, "the legacy attachment must be listed")
        assertTrue(readBack?.contentEquals(bytes) == true, "the migrated attachment must read back")
    }
}
