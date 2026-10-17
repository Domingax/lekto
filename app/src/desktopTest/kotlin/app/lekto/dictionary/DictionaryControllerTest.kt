package app.lekto.dictionary

import app.lekto.core.dictionary.DictionaryPackInstaller
import app.lekto.core.dictionary.DictionaryPackState
import app.lekto.core.dictionary.WordLookup
import app.lekto.core.language.OfferedDictionary
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.testkit.FakeDictionaryPackFiles
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.testOfferedLanguage
import app.lekto.text.AppMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The dictionary's state holder, one state per (Reading language × **Translation
 * language**) pair (issues #18, #149): each published pair's state is read from
 * its own installer, the download runs off the UI thread and reports ready or a
 * failure, and a lookup degrades honestly with no pack. The dispatcher is the
 * test dispatcher, so the asynchronous work is driven by virtual time rather than
 * a wall clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One behaviour, one test; splitting the class hides the controller's contract.
class DictionaryControllerTest {

    private val derived = DerivedAssetStore(InMemoryVaultFileSystem())
    private val files = mutableMapOf<Pair<String, String>, FakeDictionaryPackFiles>()
    private val services = DictionaryServices { reading, translation ->
        val fake = FakeDictionaryPackFiles(derived, reading, translation)
        files[reading to translation] = fake
        DictionaryPackInstaller(derived, fake, fake, reading, translation)
    }

    /** The fake behind a pair's installer, building it first so its flags can be set. */
    private fun file(reading: String, translation: String): FakeDictionaryPackFiles {
        services.installer(reading, translation)
        return files.getValue(reading to translation)
    }

    private fun TestScope.controller(): DictionaryController =
        DictionaryController(services, StandardTestDispatcher(testScheduler), this)

    @Test
    fun `refresh reads each published pair's installed state`() = runTest {
        val controller = controller()
        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        controller.refresh(
            listOf(
                testOfferedLanguage("en", dictionaries = listOf(OfferedDictionary("fr", artifact("en", "fr")))),
                testOfferedLanguage("fr", dictionaries = listOf(OfferedDictionary("en", artifact("fr", "en")))),
            ),
        )

        assertTrue(controller.states.value.getValue(artifact("en", "fr")).status is DictionaryPackState.Ready)
        assertEquals(DictionaryPackState.NotInstalled, controller.states.value.getValue(artifact("fr", "en")).status)
    }

    @Test
    fun `an install downloads that pair's pack and reports it ready`() = runTest {
        val controller = controller()

        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        assertTrue(controller.states.value.getValue(artifact("en", "fr")).status is DictionaryPackState.Ready)
        assertEquals(1, files.getValue("en" to "fr").downloads)
        assertNull(controller.states.value.getValue(artifact("en", "fr")).error)
    }

    @Test
    fun `installing one pair leaves another untouched`() = runTest {
        val controller = controller()
        controller.refresh(
            listOf(
                testOfferedLanguage("en", dictionaries = listOf(OfferedDictionary("fr", artifact("en", "fr")))),
                testOfferedLanguage("fr", dictionaries = listOf(OfferedDictionary("en", artifact("fr", "en")))),
            ),
        )

        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        assertEquals(DictionaryPackState.NotInstalled, controller.states.value.getValue(artifact("fr", "en")).status)
        assertEquals(0, file("fr", "en").downloads, "no download for a pair that was not installed")
    }

    @Test
    fun `a failed download becomes a message and clears the installing flag`() = runTest {
        file("en", "fr").failure = IOException("offline")
        val controller = controller()

        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        assertEquals(AppMessage.core("offline"), controller.states.value.getValue(artifact("en", "fr")).error)
        assertTrue(!controller.states.value.getValue(artifact("en", "fr")).installing)
    }

    @Test
    fun `a dismissed error is gone`() = runTest {
        file("en", "fr").failure = IOException("offline")
        val controller = controller()
        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        controller.dismissError(artifact("en", "fr"))

        assertNull(controller.states.value.getValue(artifact("en", "fr")).error)
    }

    @Test
    fun `an install already in progress is not started again`() = runTest {
        val controller = controller()

        controller.install("en", "fr", artifact("en", "fr"))
        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        assertEquals(1, files.getValue("en" to "fr").downloads)
    }

    @Test
    fun `a lookup with no pack degrades to unavailable`() = runTest {
        val controller = controller()

        assertTrue(controller.lookUp("blorple", "en", "fr") is WordLookup.Unavailable)
    }

    @Test
    fun `a lookup with no chosen translation language has no pack and stays unavailable`() = runTest {
        val controller = controller()
        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        assertTrue(
            controller.lookUp("blorple", "en", null) is WordLookup.Unavailable,
            "the pair is (reading × translation); with no translation there is no pack",
        )
    }

    @Test
    fun `a lookup resolves the pack for the chosen translation language`() = runTest {
        val controller = controller()
        controller.install("en", "es", artifact("en", "es"))
        advanceUntilIdle()

        assertTrue(
            controller.lookUp("blorple", "en", "es") is WordLookup.NotInDictionary,
            "an installed en-es pack answers the es lookup even though en-fr is not installed",
        )
        assertTrue(
            controller.lookUp("blorple", "en", "fr") is WordLookup.Unavailable,
            "the uninstalled en-fr pair has no pack",
        )
    }

    @Test
    fun `a lookup in an installed pack that does not know the word reports it`() = runTest {
        val controller = controller()
        controller.install("en", "fr", artifact("en", "fr"))
        advanceUntilIdle()

        assertTrue(controller.lookUp("blorple", "en", "fr") is WordLookup.NotInDictionary)
    }

    /** The asset name the manifest publishes a pair's pack under. */
    private fun artifact(reading: String, translation: String): String =
        DictionaryRelease.artifact(reading, translation)
}
