package app.lekto

import app.lekto.core.book.Book
import app.lekto.core.book.BookFormat
import app.lekto.core.book.ReadingPosition
import app.lekto.core.book.VaultBookLibrary
import app.lekto.core.text.BLOCK_SEPARATOR
import app.lekto.core.text.TextBlock
import app.lekto.core.text.TxtParser
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.testkit.InMemoryTombstoneStore
import app.lekto.testkit.InMemoryVaultFileSystem
import app.lekto.testkit.InMemoryVaultStore
import app.lekto.testkit.deterministicSeams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The page-turn path that stores the **Resume snippet** (issue #258): the
 * [ReadingProgressWriter] hands the open session's blocks to the library with
 * the position, so the paragraph the reader landed in is written beside the book
 * without a parse. A legacy save carries no text and writes none.
 */
@Suppress("InjectDispatcher") // The test is the caller: Unconfined makes the save land before the assertion.
class ResumeSnippetWriterTest {

    private fun library(): Pair<VaultBookLibrary, Book> {
        val library = VaultBookLibrary(
            vault = InMemoryVaultStore(),
            derived = DerivedAssetStore(InMemoryVaultFileSystem()),
            parsers = mapOf(BookFormat.TXT to TxtParser()),
            tombstones = InMemoryTombstoneStore(),
            seams = deterministicSeams(),
            deviceId = DeviceId("device-a"),
        )
        val book = library.import("Lantern.txt", "First paragraph.\n\nSecond paragraph.".encodeToByteArray())
        return library to book
    }

    private fun writerOver(library: VaultBookLibrary): ReadingProgressWriter {
        val save: suspend (ReadingPosition, List<TextBlock>) -> Unit = { position, blocks ->
            library.savePosition(position, blocks)
        }
        return ReadingProgressWriter(
            CoroutineScope(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
            LocalChanges(),
            save,
        )
    }

    @Test
    fun aPositionSaveWritesTheParagraphTheReaderLandedIn() {
        val (library, book) = library()
        val blocks = requireNotNull(library.open(book.id)).text.blocks
        val second = blocks[0].text.length + BLOCK_SEPARATOR.length

        writerOver(library).record(ReadingPosition(book.id, second), blocks)

        assertEquals("Second paragraph.", library.snippet(book.id))
    }

    @Test
    fun aLegacySaveWithoutTextWritesNoSnippet() {
        val (library, book) = library()

        writerOver(library).record(ReadingPosition(book.id, 0))

        assertNull(library.snippet(book.id))
    }
}
