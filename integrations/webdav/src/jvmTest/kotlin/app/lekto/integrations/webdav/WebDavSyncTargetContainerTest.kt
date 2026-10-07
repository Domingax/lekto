package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncCapabilities
import app.lekto.core.sync.SyncTarget
import app.lekto.core.vault.VersionedRecord
import app.lekto.testkit.SyncTargetContract
import app.lekto.testkit.testVaultRecord
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.UUID
import kotlin.test.assertEquals

/**
 * The WebDAV integration lane (ticket #27; ADR-0009): the shared
 * [SyncTargetContract] run against a real Apache `mod_dav` server through
 * Testcontainers. The server implements ETags and conditional writes
 * (`If-Match`/`If-None-Match`), so the contract's compare-and-swap cases run for
 * real, not against a fake.
 *
 * The image runs a current Apache (≥ 2.4.16). Apache 2.4.10–2.4.14 shipped a
 * broken `ap_condition_if_match` that rejects a matching `If-Match`, so an older
 * server would fail the compare-and-swap cases through no fault of the driver.
 * The image is pinned by digest so a green build does not move under it.
 *
 * `disabledWithoutDocker = true` keeps the lane green where Docker is absent —
 * a developer's machine, or the `fast` CI job, which excludes this task — and
 * proves it where Docker is present, which is the `webdav` lane on a pull
 * request.
 */
@Testcontainers(disabledWithoutDocker = true)
class WebDavSyncTargetContainerTest {

    @Container
    val server: GenericContainer<*> = GenericContainer(
        DockerImageName.parse(
            "bytemark/webdav@sha256:bcabbc024c511b9c63ed3345f88573e31d84c952ee493c9acb3fe345f4f80f57",
        ),
    )
        .withEnv("AUTH_TYPE", "Basic")
        .withEnv("USERNAME", "lekto")
        .withEnv("PASSWORD", "lekto")
        .withExposedPorts(80)

    private fun newTarget(): SyncTarget = WebDavSyncTarget("${baseUrl()}/lekto-${UUID.randomUUID()}", "lekto", "lekto")

    private fun baseUrl(): String = "http://${server.host}:${server.getMappedPort(80)}"

    @TestFactory
    fun `passes the shared SyncTarget contract against a real server`(): List<DynamicTest> =
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
    fun `diagnostic of a conditional update against the container`() {
        val target = WebDavSyncTarget(baseUrl(), "lekto", "lekto")
        val message = runBlocking {
            val created = target.put(VersionedRecord.of(testVaultRecord("diag", updatedAtMillis = 1)), null)
            val first = target.get("diag")
            val updated = target.put(
                VersionedRecord.of(testVaultRecord("diag", updatedAtMillis = 2)),
                first?.revision,
            )
            val second = target.get("diag")
            "created=$created | firstRev=${first?.revision} | updated=$updated | secondRev=${second?.revision}"
        }
        throw AssertionError(message)
    }
}
