package app.lekto.core.language

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.net.InetSocketAddress
import kotlin.test.assertFailsWith

/**
 * The platform **Language manifest** downloader (issue #146): it returns the
 * published JSON bytes, follows the release alias's redirect, and throws on a
 * failed response, so a reload starts from a clean state when the network is
 * down.
 */
class JvmManifestDownloaderTest :
    FunSpec({

        fun serve(status: Int, body: ByteArray): HttpServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/language-manifest.json") { exchange ->
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            return server
        }

        fun url(server: HttpServer): String = "http://127.0.0.1:${server.address.port}/language-manifest.json"

        test("it returns the manifest's bytes") {
            val body = """{"formatVersion":1,"languages":[]}""".encodeToByteArray()
            val server = serve(200, body)
            try {
                JvmManifestDownloader().download(url(server)) shouldBe body
            } finally {
                server.stop(0)
            }
        }

        test("a failed response throws and yields no bytes") {
            val server = serve(404, "missing".encodeToByteArray())
            try {
                assertFailsWith<IOException> { JvmManifestDownloader().download(url(server)) }
            } finally {
                server.stop(0)
            }
        }

        test("it follows the release alias's redirect") {
            val body = """{"formatVersion":1,"languages":[{"language":"en"}]}""".encodeToByteArray()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/language-manifest.json") { exchange ->
                exchange.responseHeaders.add("Location", "/asset.json")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/asset.json") { exchange ->
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            try {
                JvmManifestDownloader().download(url(server)) shouldBe body
            } finally {
                server.stop(0)
            }
        }
    })
