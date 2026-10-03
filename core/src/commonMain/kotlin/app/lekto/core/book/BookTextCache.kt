package app.lekto.core.book

import app.lekto.core.text.StructuredText
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.VaultCodec

/**
 * The device-local cache of a book's parsed text (ADR-0005): the parsed
 * [StructuredText] the reader renders, keyed by book id.
 *
 * It is a derived asset, so it never enters the vault or an export; a wiped or
 * corrupt cache is not data loss, because [VaultBookLibrary] re-parses the
 * stored original and re-caches. Putting the path and the JSON here keeps that
 * knowledge out of the library, which only asks for "the text for this book".
 */
internal class BookTextCache(private val derived: DerivedAssetStore) {

    /** The cached text for [bookId], or `null` when there is none or it is unreadable. */
    fun textOf(bookId: String): StructuredText? = derived.get(path(bookId))?.let { bytes ->
        runCatching { VaultCodec.json.decodeFromJsonElement(StructuredText.serializer(), jsonOf(bytes)) }.getOrNull()
    }

    /** Stores [text] as [bookId]'s parsed text. */
    fun put(bookId: String, text: StructuredText) {
        derived.put(path(bookId), VaultCodec.json.encodeToString(StructuredText.serializer(), text).encodeToByteArray())
    }

    /** Removes [bookId]'s cached text. */
    fun remove(bookId: String) {
        derived.remove(path(bookId))
    }

    private fun path(bookId: String): String = "book-text/$bookId.json"

    private fun jsonOf(bytes: ByteArray) = VaultCodec.json.parseToJsonElement(bytes.decodeToString())
}
