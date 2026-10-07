package app.lekto.integrations.webdav

import app.lekto.core.sync.SyncCapabilities
import app.lekto.core.sync.SyncTarget
import app.lekto.testkit.SyncTargetContract
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
 * `disabledWithoutDocker = true` keeps the lane green where Docker is absent —
 * a developer's machine, or the `fast` CI job, which excludes this task — and
 * proves it where Docker is present, which is the `webdav` lane on a pull
 * request. The image is pinned by digest because it publishes no version tags.
 */
@Testcontainers(disabledWithoutDocker = true)
class WebDavSyncTargetContainerTest {

    @Container
    val server: GenericContainer<*> = GenericContainer(
        DockerImageName.parse(
            "morrisjobke/webdav@sha256:047ee10e9c203359be976b293b36e86c309c6d3f1bde3fb5889ac068e3057760",
        ),
    )
        .withEnv("USERNAME", "lekto")
        .withEnv("PASSWORD", "lekto")
        .withExposedPorts(80)

    private fun newTarget(): SyncTarget = WebDavSyncTarget("${baseUrl()}/lekto-${UUID.randomUUID()}", "lekto", "lekto")

    private fun baseUrl(): String = "http://${server.host}:${server.getMappedPort(80)}/webdav"

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
}
