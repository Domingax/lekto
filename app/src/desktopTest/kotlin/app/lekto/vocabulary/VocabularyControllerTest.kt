package app.lekto.vocabulary

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reader's live vocabulary state (issue #22): it loads the vault once, an
 * unsaved word is unknown, a save drives both the mastery the reader paints and
 * the revision that forces it to repaint, and a save from a previous session is
 * present on construction.
 */
class VocabularyControllerTest {

    private val key = WordKey("en", "lantern")

    private fun controller(vault: InMemoryVaultStore = InMemoryVaultStore()): VocabularyController =
        VocabularyController(VaultVocabulary(vault, deterministicSeams(), DeviceId("device-a")))

    @Test
    fun anUnsavedWordIsUnknown() {
        assertEquals(MasteryLevel.UNKNOWN, controller().mastery.levelOf(key))
    }

    @Test
    fun aSavedWordDrivesTheMastery() {
        val controller = controller()

        controller.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.MASTERED))

        assertEquals(MasteryLevel.MASTERED, controller.mastery.levelOf(key))
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
    fun aWordSavedBeforeStartupIsLoaded() {
        val vault = InMemoryVaultStore()
        VaultVocabulary(vault, deterministicSeams(), DeviceId("device-a"))
            .save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.FAMILIAR))

        val controller = controller(vault)

        assertEquals(MasteryLevel.FAMILIAR, controller.mastery.levelOf(key))
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
        controller.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.MASTERED))
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
        controller.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.MASTERED))

        controller.delete(key)

        assertEquals(null, controller(vault).entryFor(key))
    }
}
