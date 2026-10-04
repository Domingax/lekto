package app.lekto.core.dictionary

import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The platform downloader: it streams the published gzip archive, decompresses
 * it into place, replaces a previous file and refuses a failed or invalid
 * response, so `install` starts from a clean state when the network is down
 * (issue #18).
 */
class JvmPackDownloaderTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        fun target(name: String = "pack.sqlite"): File {
            val dir = Files.createTempDirectory("lekto-download").toFile().also(roots::add)
            dir.mkdirs()
            return File(dir, name)
        }

        fun serve(status: Int, body: ByteArray): HttpServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/pack.sqlite.gz") { exchange ->
                exchange.sendResponseHeaders(status, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            return server
        }

        fun gzip(payload: ByteArray): ByteArray =
            ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(payload) } }.toByteArray()

        fun url(server: HttpServer): String = "http://127.0.0.1:${server.address.port}/pack.sqlite.gz"

        test("it streams and decompresses the pack to the destination") {
            val payload = "a pack payload".encodeToByteArray()
            val server = serve(200, gzip(payload))
            val destination = target()
            try {
                JvmPackDownloader().download(url(server), destination.path)

                destination.readBytes() shouldBe payload
            } finally {
                server.stop(0)
            }
        }

        test("it creates the destination's parent directories") {
            val server = serve(200, gzip("payload".encodeToByteArray()))
            val nested = File(target(), "nested/deep/pack.sqlite")
            try {
                JvmPackDownloader().download(url(server), nested.path)

                assertTrue(nested.isFile, "the nested destination must be created")
            } finally {
                server.stop(0)
            }
        }

        test("it replaces a previous pack atomically") {
            val server = serve(200, gzip("new".encodeToByteArray()))
            val destination = target()
            destination.writeText("old")
            try {
                JvmPackDownloader().download(url(server), destination.path)

                destination.readText() shouldBe "new"
            } finally {
                server.stop(0)
            }
        }

        test("a failed response throws and leaves no file behind") {
            val server = serve(404, "missing".encodeToByteArray())
            val destination = target()
            try {
                assertFailsWith<IOException> { JvmPackDownloader().download(url(server), destination.path) }

                destination.exists() shouldBe false
            } finally {
                server.stop(0)
            }
        }

        test("an invalid gzip throws and leaves no file behind") {
            val server = serve(200, "not gzip".encodeToByteArray())
            val destination = target()
            try {
                assertFailsWith<IOException> { JvmPackDownloader().download(url(server), destination.path) }

                destination.exists() shouldBe false
            } finally {
                server.stop(0)
            }
        }
    })
