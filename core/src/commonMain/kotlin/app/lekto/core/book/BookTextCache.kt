package app.lekto.core.book

import app.lekto.core.text.StructuredText
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.VaultCodec

/**
 * The device-local cache of a book's parsed text (ADR-0005): the parsed
 * [StructuredText] the reader renders, keyed by book id, and the rendered
 * length the Library reads for **Reading progress** (issue #196).
 *
 * Both are derived assets, so they never enter the vault or an export; a wiped
 * or corrupt cache is not data loss, because [VaultBookLibrary] re-parses the
 * stored original and re-caches. Putting the paths and the JSON here keeps that
 * knowledge out of the library, which only asks for "the text for this book".
 *
 * The length entry is written and removed with the text, so an import, a
 * delete and a re-parse cannot drift apart. The Library reads it without
 * decoding the text, which is what keeps listing a shelf cheap.
 */
internal class BookTextCache(private val derived: DerivedAssetStore) {

    /** The cached text for [bookId], or `null` when there is none or it is unreadable. */
    fun textOf(bookId: String): StructuredText? = derived.get(textPath(bookId))?.let { bytes ->
        runCatching { VaultCodec.json.decodeFromJsonElement(StructuredText.serializer(), jsonOf(bytes)) }.getOrNull()
    }

    /** The cached rendered length for [bookId], or `null` when the text has not been measured. */
    fun lengthOf(bookId: String): Int? = derived.get(lengthPath(bookId))?.decodeToString()?.toIntOrNull()

    /** Stores [text] as [bookId]'s parsed text, with its rendered length beside it. */
    fun put(bookId: String, text: StructuredText) {
        derived.put(
            textPath(bookId),
            VaultCodec.json.encodeToString(StructuredText.serializer(), text).encodeToByteArray(),
        )
        putLength(bookId, text.renderedLength())
    }

    /** Stores [length] as [bookId]'s rendered length, repairing an entry a cache lost. */
    fun putLength(bookId: String, length: Int) {
        derived.put(lengthPath(bookId), length.toString().encodeToByteArray())
    }

    /** Removes [bookId]'s cached text and its length. */
    fun remove(bookId: String) {
        derived.remove(textPath(bookId))
        derived.remove(lengthPath(bookId))
    }

    private fun textPath(bookId: String): String = "book-text/$bookId.json"

    private fun lengthPath(bookId: String): String = "book-text/$bookId.length"

    private fun jsonOf(bytes: ByteArray) = VaultCodec.json.parseToJsonElement(bytes.decodeToString())
}
