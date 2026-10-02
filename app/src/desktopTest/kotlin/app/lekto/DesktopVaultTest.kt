package app.lekto

import app.lekto.testkit.testVaultRecord
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop wiring of the vault: its root is an app-private folder, and the
 * store it builds round-trips a record to a real directory. The same store
 * implementation is what Android constructs; only the root differs (ADR-0010).
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
}
