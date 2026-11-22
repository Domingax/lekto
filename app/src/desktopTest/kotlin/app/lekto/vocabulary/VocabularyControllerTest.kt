package app.lekto.vocabulary

import app.lekto.LocalChanges
import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.JsonVaultStore
import app.lekto.core.vault.VaultStore
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.testkit.FlakyVaultFileSystem
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The reader's live vocabulary state (issue #22): it loads the vault once, an
 * unsaved word is unknown, a save drives both the mastery the reader paints and
 * the revision that forces it to repaint, and a save from a previous session is
 * present on construction. A write the vault refuses is reported rather than
 * thrown, so the lookup footer can say it failed (issue #279).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One behaviour, one test; splitting the class hides the controller's contract.
class VocabularyControllerTest {

    private val key = WordKey("en", "lantern")

    private fun controller(
        vault: VaultStore = InMemoryVaultStore(),
        changes: LocalChanges = LocalChanges(),
    ): VocabularyController = VocabularyController(
        VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a")),
        changes,
    )

    /** A controller over a vault that refuses every vocabulary write, as a full disk would. */
    private fun refusingController(): VocabularyController {
        val files = FlakyVaultFileSystem().apply { failOn = { path -> path.startsWith("vocabulary/") } }
        return controller(JsonVaultStore(files))
    }

    @Test
    fun anUnsavedWordIsUnknown() {
        assertEquals(MasteryLevel.UNKNOWN, controller().mastery.levelOf(key))
    }

    @Test
    fun aSavedWordDrivesTheMastery() {
        val controller = controller()

        controller.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.LEARNED))

        assertEquals(MasteryLevel.LEARNED, controller.mastery.levelOf(key))
        assertEquals("lantern", controller.entryFor(key)?.surface)
    }

    @Test
    fun everySaveBumpsTheRevisionSoTheReaderRecolours() {
        val controller = controller()
        val before = controller.revision

        controller.save(VocabularyEntry(key, "lantern"))

        assertEquals(before + 1, controller.revision)
    }

    @Test
    fun aLandedSaveSaysSo() {
        assertTrue(
            controller().save(VocabularyEntry(key, "lantern")),
            "a save the vault accepted must report that it landed",
        )
    }

    @Test
    fun aRefusedWriteIsReportedAndLeavesTheWordUnsaved() {
        // #279: a failed write must not read `Saved`. The controller reports the
        // outcome so the footer can state it, and changes nothing on the way out.
        val controller = refusingController()
        val before = controller.revision

        val landed = controller.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.LEARNED))

        assertFalse(landed, "the vault refused the write, so the save must report the failure")
        assertEquals(null, controller.entryFor(key), "a refused write leaves the word unsaved")
        assertEquals(before, controller.revision, "nothing was saved, so nothing recolours")
    }

    @Test
    fun aWordSavedBeforeStartupIsLoaded() {
        val vault = InMemoryVaultStore()
        VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
            .save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.RECOGNIZED))

        val controller = controller(vault)

        assertEquals(MasteryLevel.RECOGNIZED, controller.mastery.levelOf(key))
        assertEquals("lantern", controller.entryFor(key)?.surface)
    }

    @Test
    fun allListsTheSavedEntries() {
        val controller = controller()
        controller.save(VocabularyEntry(key, "lantern"))
        controller.save(VocabularyEntry(WordKey("fr", "manger"), "mangeais"))

        assertEquals(
            setOf("lantern", "mangeais"),
            controller.all().map { entry -> entry.surface }.toSet(),
        )
    }

    @Test
    fun deletingAWordClearsItsMasteryAndBumpsTheRevision() {
        val controller = controller()
        controller.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.LEARNED))
        val before = controller.revision

        controller.delete(key)

        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(key))
        assertEquals(null, controller.entryFor(key))
        assertEquals(before + 1, controller.revision)
    }

    @Test
    fun aDeletedWordStaysDeletedOverTheVault() {
        val vault = InMemoryVaultStore()
        val controller = controller(vault)
        controller.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.LEARNED))

        controller.delete(key)

        assertEquals(null, controller(vault).entryFor(key))
    }

    @Test
    fun savingAWordNotifiesTheLocalChangeSignal() = runTest {
        // A saved word is a local change, so sync pushes it (issue #121).
        val changes = LocalChanges()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.content.collect { received += it } }

        controller(changes = changes).save(VocabularyEntry(key, "lantern"))

        assertTrue(received.isNotEmpty(), "a save must notify the local-change signal")
    }

    @Test
    fun deletingAWordNotifiesTheLocalChangeSignal() = runTest {
        val changes = LocalChanges()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.content.collect { received += it } }
        val controller = controller(changes = changes)
        controller.save(VocabularyEntry(key, "lantern"))
        received.clear()

        controller.delete(key)

        assertTrue(received.isNotEmpty(), "a delete must notify the local-change signal")
    }

    @Test
    fun refreshingDropsTheStaleCacheAndAdoptsWhatTheSyncMoved() {
        // A sync writes the vault behind the controller's back; refresh folds it
        // in so a word from another device shows without a restart (issue #132).
        val vault = InMemoryVaultStore()
        val controller = controller(vault)
        VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
            .save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.LEARNED))

        assertEquals(null, controller.entryFor(key), "the controller is stale until it refreshes")

        controller.refresh()

        assertEquals(MasteryLevel.LEARNED, controller.mastery.levelOf(key))
        assertEquals("lantern", controller.entryFor(key)?.surface)
    }

    @Test
    fun refreshingDropsAWordTheSyncRemoved() {
        val vault = InMemoryVaultStore()
        val writer = VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
        writer.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.RECOGNIZED))
        val controller = controller(vault)

        writer.delete(key)
        controller.refresh()

        assertEquals(null, controller.entryFor(key))
        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(key))
    }

    @Test
    fun refreshingBumpsTheRevisionSoTheReaderRecolours() {
        val controller = controller()
        val before = controller.revision

        controller.refresh()

        assertEquals(before + 1, controller.revision)
    }
}
