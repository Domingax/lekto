package app.lekto

import app.lekto.core.MasteryLevel
import app.lekto.core.text.WordKey
import app.lekto.core.vault.VersionedRecord
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.core.vocabulary.VocabularyRecord
import app.lekto.testkit.InMemorySyncTarget
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The desktop vocabulary and the desktop sync wiring over the same vault: a
 * deleted word must leave a tombstone the engine propagates, so a later sync does
 * not copy the live record back from the server (ADR-0015). Reproduces the
 * "deleted word reappears after a restart" report.
 */
class DesktopVocabularySyncTest {

    @Test
    fun aDeletedWordLeavesATombstoneSoASyncDoesNotBringItBack() {
        val root = Files.createTempDirectory("lekto-desktop-vocab-sync").toFile().resolve("vault")
        try {
            val vocabulary = desktopVocabulary(root)
            val sync = desktopSync(root)
            val target = InMemorySyncTarget(changeCursor = false)
            val key = WordKey("en", "lantern")
            vocabulary.save(VocabularyEntry(key, "lantern", mastery = MasteryLevel.RECOGNIZED))
            runBlocking { sync.engine(target).sync() }

            vocabulary.delete(key)
            runBlocking { sync.engine(target).sync() }

            assertNull(vocabulary.entryFor(key), "the deleted word must not be copied back from the server")
            val stored = target.stored(VocabularyRecord.idOf(key))?.version
            assertIs<VersionedRecord.Deleted>(stored, "the server must hold a tombstone, not the live word")
        } finally {
            root.parentFile.deleteRecursively()
        }
    }
}
