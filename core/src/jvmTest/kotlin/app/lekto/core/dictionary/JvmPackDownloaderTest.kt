package app.lekto.core.dictionary

import app.lekto.testkit.MISMATCHED_SHA256
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The platform downloader: it streams the published gzip archive, decompresses
 * it into place only when its SHA-256 is the one the manifest published, replaces
 * a previous file and refuses a failed response or a mismatched digest, so
 * `install` starts from a clean state and never installs bytes the manifest did
 * not vouch for (issue #18, issue #305).
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

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

        test("it streams and decompresses a pack whose bytes match the manifest's digest") {
            val payload = "a pack payload".encodeToByteArray()
            val body = gzip(payload)
            val server = serve(200, body)
            val destination = target()
            try {
                JvmPackDownloader().download(url(server), destination.path, sha256(body))

                destination.readBytes() shouldBe payload
            } finally {
                server.stop(0)
            }
        }

        test("it creates the destination's parent directories") {
            val body = gzip("payload".encodeToByteArray())
            val server = serve(200, body)
            val nested = File(target(), "nested/deep/pack.sqlite")
            try {
                JvmPackDownloader().download(url(server), nested.path, sha256(body))

                assertTrue(nested.isFile, "the nested destination must be created")
            } finally {
                server.stop(0)
            }
        }

        test("it replaces a previous pack atomically") {
            val body = gzip("new".encodeToByteArray())
            val server = serve(200, body)
            val destination = target()
            destination.writeText("old")
            try {
                JvmPackDownloader().download(url(server), destination.path, sha256(body))

                destination.readText() shouldBe "new"
            } finally {
                server.stop(0)
            }
        }

        test("a pack whose bytes are not the manifest's is refused and leaves no file behind") {
            val server = serve(200, gzip("corrupt".encodeToByteArray()))
            val destination = target()
            try {
                assertFailsWith<AssetChecksumMismatch> {
                    JvmPackDownloader().download(url(server), destination.path, MISMATCHED_SHA256)
                }

                destination.exists() shouldBe false
            } finally {
                server.stop(0)
            }
        }

        test("a refused download leaves a previous pack in place") {
            val server = serve(200, gzip("new".encodeToByteArray()))
            val destination = target()
            destination.writeText("old")
            try {
                assertFailsWith<AssetChecksumMismatch> {
                    JvmPackDownloader().download(url(server), destination.path, MISMATCHED_SHA256)
                }

                destination.readText() shouldBe "old"
            } finally {
                server.stop(0)
            }
        }

        test("a failed response throws and leaves no file behind") {
            val server = serve(404, "missing".encodeToByteArray())
            val destination = target()
            try {
                assertFailsWith<IOException> {
                    JvmPackDownloader().download(url(server), destination.path, MISMATCHED_SHA256)
                }

                destination.exists() shouldBe false
            } finally {
                server.stop(0)
            }
        }

        test("an invalid gzip throws and leaves no file behind") {
            val body = "not gzip".encodeToByteArray()
            val server = serve(200, body)
            val destination = target()
            try {
                assertFailsWith<IOException> {
                    JvmPackDownloader().download(url(server), destination.path, sha256(body))
                }

                destination.exists() shouldBe false
            } finally {
                server.stop(0)
            }
        }
    })
