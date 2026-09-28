package app.lekto.integrations.webdav

import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import kotlin.test.assertTrue

/**
 * The WebDAV integration lane's readiness check (ticket #7).
 *
 * It starts the Apache `mod_dav` server the driver contract will run against and
 * proves the lane is wired: the container is reachable and speaks WebDAV over
 * authenticated `OPTIONS`. Where Docker is absent — a developer's machine, or a CI
 * runner without it — `disabledWithoutDocker` skips the class instead of failing,
 * so the lane stays green without the dependency. Ticket #27 replaces this with the
 * `SyncTarget` contract run against the same server.
 *
 * See docs/testing.md and docs/research/testing-harness.md §4.
 */
@Testcontainers(disabledWithoutDocker = true)
class WebDavIntegrationLaneTest {

    @Container
    val server: GenericContainer<*> = GenericContainer(
        // Pinned by digest: the image publishes no version tags, and a floating
        // `:latest` would let the server change under a green build. The digest is
        // the amd64 `linux/amd64` image the CI runner pulls.
        DockerImageName.parse(
            "morrisjobke/webdav@sha256:047ee10e9c203359be976b293b36e86c309c6d3f1bde3fb5889ac068e3057760",
        ),
    )
        .withEnv("USERNAME", "lekto")
        .withEnv("PASSWORD", "lekto")
        .withExposedPorts(80)

    @Test
    fun `serves the WebDAV protocol over authenticated OPTIONS`() {
        val url = "http://${server.host}:${server.getMappedPort(80)}/"
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "OPTIONS"
        val credentials = Base64.getEncoder().encodeToString("lekto:lekto".toByteArray())
        connection.setRequestProperty("Authorization", "Basic $credentials")

        try {
            connection.connect()
            val dav = connection.getHeaderField("DAV")
            assertTrue(
                dav != null && "1" in dav,
                "OPTIONS should advertise DAV compliance classes, but DAV header was '$dav'",
            )
        } finally {
            connection.disconnect()
        }
    }
}
