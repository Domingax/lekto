package app.lekto.core.dictionary

import app.lekto.testkit.DictionaryPackContract
import app.lekto.testkit.TestResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * The dictionary reader on a **simulated Android runtime** (issue #18): the same
 * [DictionaryPackContract] `jvmTest` runs, but through the Android framework's
 * `SQLiteDatabase`, so the two platforms cannot answer a query differently.
 *
 * The pack fixture is the pipeline's real output, committed under
 * `commonTest/resources`; only the SQLite implementation under the seam differs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AndroidDictionaryPackHostTest {

    @Test
    fun `the SQLite pack reader satisfies the pack contract on Android`() {
        DictionaryPackContract {
            SqlDictionaryPack(AndroidPackDatabaseFactory.open(fixture()))
        }.cases().forEach { case -> case.body() }
    }

    @Test
    fun `a path with no pack reports it missing`() {
        val missing = File(Files.createTempDirectory("lekto-dictionary").toFile(), "absent.sqlite")

        assertThrows(DictionaryPackMissing::class.java) { AndroidPackDatabaseFactory.open(missing.path) }
        assertFalse(missing.exists())
    }

    @Test
    fun `the pack carries the attribution on Android too`() {
        val pack = SqlDictionaryPack(AndroidPackDatabaseFactory.open(fixture()))

        assertEquals("CC BY-SA 4.0", pack.metadata.license)
        assertTrue("the source must be named", pack.metadata.source?.contains("wiktionary") == true)
        pack.close()
    }

    private fun fixture(): String {
        val file = File.createTempFile("lekto-dictionary", ".sqlite")
        file.deleteOnExit()
        file.writeBytes(TestResources.bytes("/dictionary/en-fr-sample.sqlite"))
        return file.path
    }
}
