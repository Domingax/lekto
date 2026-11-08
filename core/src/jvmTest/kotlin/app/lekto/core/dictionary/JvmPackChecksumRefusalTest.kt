package app.lekto.core.dictionary

import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.JvmVaultFileSystem
import app.lekto.testkit.FakePackDatabase
import app.lekto.testkit.TEST_SHA256
import com.sun.net.httpserver.HttpServer
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

/**
 * The install check for a published asset (issue #305, ADR-0047): a real HTTP
 * server serves a gzipped pack whose SHA-256 is not the one the manifest
 * published, and the installer refuses it rather than opening it — the same
 * honesty the format handshake applies, now applied to the bytes themselves.
 */
class JvmPackChecksumRefusalTest :
    FunSpec({

        val roots = mutableListOf<File>()

        afterSpec { roots.forEach(File::deleteRecursively) }

        fun serve(body: ByteArray): HttpServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/pack.sqlite.gz") { exchange ->
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            server.start()
            return server
        }

        fun gzip(payload: ByteArray): ByteArray =
            ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(payload) } }.toByteArray()

        test("a downloaded pack whose digest is not the manifest's is refused, not installed") {
            val root = Files.createTempDirectory("lekto-checksum").toFile().also(roots::add)
            val derived = DerivedAssetStore(JvmVaultFileSystem(root))
            val installer = DictionaryPackInstaller(
                derived = derived,
                downloader = JvmPackDownloader(),
                databases = { path ->
                    if (File(path).isFile) FakePackDatabase() else throw DictionaryPackMissing()
                },
                readingLanguage = "en",
                translationLanguage = "fr",
            )
            val server = serve(gzip("a corrupt pack".encodeToByteArray()))
            try {
                val state = installer.install(
                    url = "http://127.0.0.1:${server.address.port}/pack.sqlite.gz",
                    sha256 = TEST_SHA256,
                )

                state.shouldBeInstanceOf<DictionaryPackState.Corrupt>()
                installer.open() shouldBe null
                derived.paths() shouldBe emptyList()
            } finally {
                server.stop(0)
            }
        }
    })
