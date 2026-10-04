package app.lekto

import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.WordLookup
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop dictionary wiring (issue #18): it builds an installer over the
 * app-private derived root beside the vault and starts from "not installed",
 * with no network touched.
 */
class DesktopDictionaryTest {

    @Test
    fun `wires an installer over the derived root, starting not installed`() {
        val root = Files.createTempDirectory("lekto-desktop-dictionary").toFile()
        try {
            val services = desktopDictionary(root)

            assertEquals(DictionaryPackState.NotInstalled, services.installer.status())
            assertTrue(services.lookup.lookup("blorple", "en") is WordLookup.Unavailable)
        } finally {
            root.deleteRecursively()
        }
    }
}
