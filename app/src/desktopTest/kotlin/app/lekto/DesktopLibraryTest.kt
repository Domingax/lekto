package app.lekto

import app.lekto.core.MasteryLevel
import app.lekto.core.book.BookFormat
import app.lekto.core.text.WordKey
import app.lekto.core.vault.VaultCodec
import app.lekto.core.vocabulary.VocabularyEntry
import app.lekto.core.vocabulary.VocabularyRecord
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop wiring of the reading loop: its vault library imports a text file
 * to a real directory, its vocabulary saves a word there that survives a
 * restart, and its device id is stable and stored beside the vault (ADR-0010).
 * The Swing picker and `main()` are the untestable entry points and live in
 * their own files.
 */
class DesktopLibraryTest {

    @Test
    fun theLibraryImportsAndOpensABookOverTheDesktopVault() {
        val root = Files.createTempDirectory("lekto-desktop-library").toFile().resolve("vault")
        try {
            val library = desktopBookLibrary(root)

            val book = library.import("article.txt", "Bonjour le monde.".encodeToByteArray())

            assertEquals(listOf(book), library.books())
            assertEquals(BookFormat.TXT, book.format)
            assertEquals("Bonjour le monde.", library.open(book.id)?.text?.plainText())
        } finally {
            root.parentFile.deleteRecursively()
        }
    }

    @Test
    fun theVocabularySavesAWordOverTheDesktopVaultAndReloadsIt() {
        val root = Files.createTempDirectory("lekto-desktop-vocabulary").toFile().resolve("vault")
        try {
            val entry = VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.FAMILIAR)

            desktopVocabulary(root).save(entry)

            assertEquals(entry, desktopVocabulary(root).entryFor(entry.key))
        } finally {
            root.parentFile.deleteRecursively()
        }
    }

    @Test
    fun theVaultExportCarriesSavedVocabulary() {
        val root = Files.createTempDirectory("lekto-desktop-vocab-export").toFile().resolve("vault")
        try {
            val entry = VocabularyEntry(WordKey("en", "lantern"), "lantern", mastery = MasteryLevel.FAMILIAR)
            desktopVocabulary(root).save(entry)

            val bundle = VaultCodec.decodeBundle(desktopVaultStore(root).exportBundle().decodeToString())
            val record = bundle.records.single { it.kind == VocabularyRecord.KIND }

            assertTrue(
                record.kind == VocabularyRecord.KIND,
                "the whole-vault export must carry the saved word, so it is portable",
            )
            assertEquals(entry, VocabularyRecord.entryOf(record))
        } finally {
            root.parentFile.deleteRecursively()
        }
    }

    @Test
    fun theDeviceIdIsStableAndStoredBesideTheVault() {
        val root = Files.createTempDirectory("lekto-desktop-device").toFile().resolve("vault")
        try {
            desktopBookLibrary(root)
            val deviceIdFile = File(root.parentFile, "device-id")

            val first = deviceIdFile.readText()

            desktopBookLibrary(root)
            assertEquals(first, deviceIdFile.readText())
            assertTrue(first.isNotBlank())
        } finally {
            root.parentFile.deleteRecursively()
        }
    }
}
