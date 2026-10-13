package app.lekto.reader

import app.lekto.LocalChanges
import app.lekto.core.CefrLevel
import app.lekto.core.MasteryLevel
import app.lekto.core.difficulty.WordDifficulty
import app.lekto.core.readinglevel.ReaderLevels
import app.lekto.core.readinglevel.VaultReaderLevels
import app.lekto.core.text.WordKey
import app.lekto.core.vault.DeviceId
import app.lekto.core.vocabulary.VaultVocabulary
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.testkit.InMemoryReaderLevels
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.InMemoryWordDifficulty
import app.lekto.testkit.deterministicSeams
import app.lekto.vocabulary.VocabularyController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The reader's single mastery composition (ADR-0030): a saved word wins, an
 * unsaved word derives from its difficulty band and the reader's level, and a
 * level change recolours the open book at once and survives a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // One behaviour, one test; splitting the class hides the controller's contract.
class MasteryControllerTest {

    private val blorple = WordKey("en", "blorple")

    private fun controller(
        vault: InMemoryVaultStore = InMemoryVaultStore(),
        levels: ReaderLevels = InMemoryReaderLevels(),
        difficulty: WordDifficulty? = InMemoryWordDifficulty(),
        changes: LocalChanges = LocalChanges(),
    ): MasteryController {
        val vocabulary = VocabularyController(
            VaultVocabulary(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a")),
            changes,
        )
        return MasteryController(vocabulary, levels, { difficulty }, changes)
    }

    @Test
    fun anUnsavedWordWithNoSignalIsUnknown() {
        assertEquals(MasteryLevel.UNKNOWN, controller().mastery.levelOf(blorple))
    }

    @Test
    fun aWordAtOrBelowTheReaderLevelReadsAsKnownWithNoSavedEntry() {
        // blorple is B1; a reader at B1 (or above) sees it as normal text.
        val controller = controller(levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.B1)))

        assertEquals(MasteryLevel.KNOWN, controller.mastery.levelOf(blorple))
        assertEquals(null, controller.entryFor(blorple))
    }

    @Test
    fun aWordAboveTheReaderLevelDropsOneLevelPerBand() {
        val bands = mapOf(
            ("en" to "blorple") to CefrLevel.C2,
            ("en" to "one") to CefrLevel.C1,
            ("en" to "two") to CefrLevel.B2,
            ("en" to "three") to CefrLevel.B1,
        )
        val controller = controller(
            levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.A1)),
            difficulty = InMemoryWordDifficulty(bands = bands),
        )

        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(blorple)) // C2 − A1 = 5 bands above
        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(WordKey("en", "one"))) // C1 − A1 = 4
        assertEquals(MasteryLevel.RECOGNIZED, controller.mastery.levelOf(WordKey("en", "two"))) // B2 − A1 = 3
        assertEquals(MasteryLevel.FAMILIAR, controller.mastery.levelOf(WordKey("en", "three"))) // B1 − A1 = 2
    }

    @Test
    fun aSavedWordsExplicitMasteryAlwaysWins() {
        val controller = controller(levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.C2)))

        // The derived default would be KNOWN (B1 ≤ C2), but the save wins.
        controller.save(VocabularyEntry(blorple, "blorple", mastery = MasteryLevel.RECOGNIZED))

        assertEquals(MasteryLevel.RECOGNIZED, controller.mastery.levelOf(blorple))
    }

    @Test
    fun aWordWithNoDifficultyBandIsUnknown() {
        val controller = controller(
            levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.C2)),
            difficulty = InMemoryWordDifficulty(bands = emptyMap()),
        )

        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(blorple))
    }

    @Test
    fun aLanguageWithNoLevelIsUnknown() {
        // The reader has declared French only, so English derives nothing.
        val controller = controller(levels = InMemoryReaderLevels(mapOf("fr" to CefrLevel.C2)))

        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(blorple))
    }

    @Test
    fun aMissingDifficultyTableIsUnknown() {
        val controller = controller(
            levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.C2)),
            difficulty = null,
        )

        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(blorple))
    }

    @Test
    fun settingALevelRecoloursAtOnce() {
        val controller = controller()
        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(blorple))
        val before = controller.revision

        controller.setLevel("en", CefrLevel.B2)

        assertEquals(MasteryLevel.KNOWN, controller.mastery.levelOf(blorple))
        assertEquals(before + 1, controller.revision)
    }

    @Test
    fun clearingALevelRecoloursAtOnce() {
        val controller = controller(levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.B2)))
        assertEquals(MasteryLevel.KNOWN, controller.mastery.levelOf(blorple))
        val before = controller.revision

        controller.clearLevel("en")

        assertEquals(MasteryLevel.UNKNOWN, controller.mastery.levelOf(blorple))
        assertEquals(null, controller.levelFor("en"))
        assertEquals(before + 1, controller.revision)
    }

    @Test
    fun aClearedLevelIsGoneFromTheVault() {
        val vault = InMemoryVaultStore()
        val levels = VaultReaderLevels(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
        val controller = controller(vault = vault, levels = levels)
        controller.setLevel("en", CefrLevel.B1)

        controller.clearLevel("en")

        val restarted = controller(vault = vault, levels = levels)
        assertEquals(null, restarted.levelFor("en"))
        assertEquals(MasteryLevel.UNKNOWN, restarted.mastery.levelOf(blorple))
    }

    @Test
    fun aLevelSurvivesARestartOverTheVault() {
        val vault = InMemoryVaultStore()
        val levels = VaultReaderLevels(vault, InMemoryTombstoneStore(), deterministicSeams(), DeviceId("device-a"))
        controller(vault = vault, levels = levels).setLevel("en", CefrLevel.B1)

        // A fresh controller over the same vault sees the level.
        val restarted = controller(vault = vault, levels = levels)

        assertEquals(CefrLevel.B1, restarted.levelFor("en"))
        assertEquals(MasteryLevel.KNOWN, restarted.mastery.levelOf(blorple))
    }

    @Test
    fun settingALevelNotifiesTheLocalChangeSignal() = runTest {
        val changes = LocalChanges()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.content.collect { received += it } }

        controller(changes = changes).setLevel("en", CefrLevel.B1)

        assertTrue(received.isNotEmpty(), "a level is vault data, so sync must push it")
    }

    @Test
    fun clearingALevelNotifiesTheLocalChangeSignal() = runTest {
        val changes = LocalChanges()
        val received = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { changes.content.collect { received += it } }

        controller(levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.B1)), changes = changes).clearLevel("en")

        assertTrue(received.isNotEmpty(), "clearing a level is a vault change, so sync must push it")
    }

    @Test
    fun theFreshSaveDefaultFollowsTheShownLevelWhenTheWordHasSignal() {
        val controller = controller(levels = InMemoryReaderLevels(mapOf("en" to CefrLevel.B1)))

        assertEquals(MasteryLevel.KNOWN, controller.freshSaveLevel(blorple))
    }

    @Test
    fun theFreshSaveDefaultIsFamiliarWhenTheWordHasNoSignal() {
        // No reader level set, so blorple has no signal; the default floors at familiar.
        assertEquals(MasteryLevel.FAMILIAR, controller().freshSaveLevel(blorple))
    }

    @Test
    fun everySaveAndDeleteBumpsTheRevision() {
        val controller = controller()
        val before = controller.revision

        controller.save(VocabularyEntry(blorple, "blorple"))
        controller.delete(blorple)

        assertEquals(before + 2, controller.revision)
    }

    @Test
    fun invalidatingBumpsTheRevisionSoAnInstalledTableRecolours() {
        val controller = controller()
        val before = controller.revision

        controller.invalidate()

        assertEquals(before + 1, controller.revision)
    }

    @Test
    fun refreshingAdoptsALevelTheSyncMoved() {
        val levels = InMemoryReaderLevels()
        val controller = controller(levels = levels)
        assertEquals(null, controller.levelFor("en"))

        // A sync writes the level behind the controller's back.
        levels.setLevel("en", CefrLevel.B2)
        controller.refresh()

        assertEquals(CefrLevel.B2, controller.levelFor("en"))
        assertEquals(MasteryLevel.KNOWN, controller.mastery.levelOf(blorple))
    }
}
