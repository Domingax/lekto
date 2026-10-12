package app.lekto.core.difficulty

import app.lekto.core.dictionary.AndroidPackDatabaseFactory
import app.lekto.testkit.TestResources
import app.lekto.testkit.WordDifficultyContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The difficulty reader on a **simulated Android runtime** (ADR-0029): the same
 * [WordDifficultyContract] `jvmTest` runs, but through the Android framework's
 * `SQLiteDatabase`, so the two platforms cannot answer a query differently.
 *
 * The fixture is the pipeline's real output, committed under
 * `commonTest/resources`; only the SQLite implementation under the seam differs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36]) // `android-compileSdk`; Robolectric 4.16 supports API 36.
class AndroidWordDifficultyHostTest {

    @Test
    fun `the SQLite difficulty reader satisfies the contract on Android`() {
        WordDifficultyContract {
            SqlWordDifficulty(AndroidPackDatabaseFactory.open(fixture()))
        }.cases().forEach { case -> case.body() }
    }

    @Test
    fun `the table carries the wordfreq attribution on Android too`() {
        val table = SqlWordDifficulty(AndroidPackDatabaseFactory.open(fixture()))

        assertEquals("CC BY-SA 4.0", table.metadata.license)
        assertTrue("the attribution must name Robyn Speer", table.metadata.attribution?.contains("Robyn Speer") == true)
        table.close()
    }

    private fun fixture(): String {
        val file = File.createTempFile("lekto-difficulty", ".sqlite")
        file.deleteOnExit()
        file.writeBytes(TestResources.bytes("/difficulty/en-sample.sqlite"))
        return file.path
    }
}
