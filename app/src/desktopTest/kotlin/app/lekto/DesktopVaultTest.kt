package app.lekto

import app.lekto.core.book.BookRecord
import app.lekto.core.vault.VaultCodec
import app.lekto.testkit.testVaultRecord
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The desktop wiring of the vault: its root is an app-private folder, and the
 * store it builds round-trips a record to a real directory. The same store
 * implementation is what Android constructs; only the root differs (ADR-0010).
 * The whole-file transfer (issue #20) is rooted the same way, so it carries what
 * the library wrote.
 */
class DesktopVaultTest {

    @Test
    fun theVaultRootIsAnAppPrivateFolderNamedForTheApp() {
        val root = desktopVaultRoot()

        assertTrue(root.isAbsolute, "the vault root must be absolute")
        assertEquals("vault", root.name)
        assertEquals("lekto", root.parentFile?.name)
    }

    @Test
    fun theStoreRoundTripsARecordToDisk() {
        val root = Files.createTempDirectory("lekto-desktop-vault").toFile()
        try {
            val store = desktopVaultStore(root)
            val record = testVaultRecord("a")

            store.put(record)

            assertEquals(record, store.get("a"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun theVaultTransferCarriesWhatTheLibraryWrote() {
        val root = Files.createTempDirectory("lekto-desktop-transfer").toFile().resolve("vault")
        try {
            val library = desktopBookLibrary(root)
            library.import("article.txt", "Bonjour le monde.".encodeToByteArray())
            var exported: ByteArray? = null
            val transfer = desktopVaultTransfer(root, save = { _, bytes ->
                exported = bytes
                true
            }, open = { null })

            runBlocking { transfer.save("lekto-vault.json", transfer.vault.exportBundle()) }

            val bundle = VaultCodec.decodeBundle(assertNotNull(exported).decodeToString())
            assertTrue(
                bundle.records.any { it.kind == BookRecord.KIND },
                "the export must carry the library's book",
            )
        } finally {
            root.parentFile.deleteRecursively()
        }
    }
}
