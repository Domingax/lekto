package app.lekto.core.book

import app.lekto.core.text.ChapterStart
import app.lekto.core.text.StructuredText
import app.lekto.core.text.chapterStarts
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.VaultCodec
import kotlinx.serialization.Serializable

/**
 * The device-local cache of a book's parsed text (ADR-0005): the parsed
 * [StructuredText] the reader renders, keyed by book id, the rendered length
 * the Library reads for **Reading progress** (issue #196), and the chapter
 * starts that map a **Reading position** to the **Chapter** holding it
 * (issue #260).
 *
 * All three are derived assets, so they never enter the vault or an export; a
 * wiped or corrupt cache is not data loss, because [VaultBookLibrary] re-parses
 * the stored original and re-caches. Putting the paths and the JSON here keeps
 * that knowledge out of the library, which only asks for "the text for this
 * book".
 *
 * The length entry and the chapter starts are written and removed with the
 * text, so an import, a delete and a re-parse cannot drift apart. The Library
 * reads them without decoding the text, which is what keeps listing a shelf
 * cheap; a surface that names the current chapter needs no second read of the
 * book.
 *
 * The text is stored in an envelope carrying [FORMAT_VERSION] (ADR-0037); the
 * chapter starts are indexed beside it under the same version (ADR-0043). A
 * missing or different version reads as a cache miss, so a shape this build
 * does not understand is re-parsed from the original rather than decoded as
 * valid. There is no migration — a derived asset is cheap to rebuild.
 */
@Suppress("TooManyFunctions") // The text, its length and its chapter index are one asset.
internal class BookTextCache(private val derived: DerivedAssetStore) {

    /** The cached text for [bookId], or `null` when there is none or it is unreadable. */
    fun textOf(bookId: String): StructuredText? = derived.get(textPath(bookId))?.let { bytes ->
        runCatching {
            val envelope = VaultCodec.json.decodeFromJsonElement(CachedText.serializer(), jsonOf(bytes))
            envelope.text.takeIf { envelope.formatVersion == FORMAT_VERSION }
        }.getOrNull()
    }

    /** The cached rendered length for [bookId], or `null` when the text has not been measured. */
    fun lengthOf(bookId: String): Int? = derived.get(lengthPath(bookId))?.decodeToString()?.toIntOrNull()

    /**
     * The cached chapter starts for [bookId] in reading order, a part before its
     * children, or `null` when there is no index yet: a book cached before the
     * index existed, or an entry written by a shape this build does not
     * understand. An indexed book with no chapters reads as an empty list.
     */
    fun chaptersOf(bookId: String): List<ChapterStart>? = derived.get(chaptersPath(bookId))?.let { bytes ->
        runCatching {
            val envelope = VaultCodec.json.decodeFromJsonElement(CachedChapters.serializer(), jsonOf(bytes))
            envelope.chapters.takeIf { envelope.formatVersion == FORMAT_VERSION }
        }.getOrNull()
    }

    /** Stores [text] as [bookId]'s parsed text, with its rendered length and chapter starts beside it. */
    fun put(bookId: String, text: StructuredText) {
        val envelope = CachedText(FORMAT_VERSION, text)
        derived.put(
            textPath(bookId),
            VaultCodec.json.encodeToString(CachedText.serializer(), envelope).encodeToByteArray(),
        )
        putLength(bookId, text.renderedLength())
        putChapters(bookId, text)
    }

    /** Stores [length] as [bookId]'s rendered length, repairing an entry a cache lost. */
    fun putLength(bookId: String, length: Int) {
        derived.put(lengthPath(bookId), length.toString().encodeToByteArray())
    }

    /** Stores [text]'s chapter starts for [bookId], so a surface can name the current chapter. */
    fun putChapters(bookId: String, text: StructuredText) {
        val envelope = CachedChapters(FORMAT_VERSION, text.chapterStarts())
        derived.put(
            chaptersPath(bookId),
            VaultCodec.json.encodeToString(CachedChapters.serializer(), envelope).encodeToByteArray(),
        )
    }

    /**
     * Writes [text]'s chapter starts unless [bookId] already has them, which is
     * how a book cached before the index existed gains one when it is opened.
     * The index always names the book's whole chapter list, empty included, so a
     * book with no structure is not re-indexed on every open.
     */
    fun ensureChapters(bookId: String, text: StructuredText) {
        if (chaptersOf(bookId) == null) putChapters(bookId, text)
    }

    /** Removes [bookId]'s cached text, its length and its chapter starts. */
    fun remove(bookId: String) {
        derived.remove(textPath(bookId))
        derived.remove(lengthPath(bookId))
        derived.remove(chaptersPath(bookId))
    }

    private fun textPath(bookId: String): String = "book-text/$bookId.json"

    private fun lengthPath(bookId: String): String = "book-text/$bookId.length"

    private fun chaptersPath(bookId: String): String = "book-text/$bookId.chapters"

    private fun jsonOf(bytes: ByteArray) = VaultCodec.json.parseToJsonElement(bytes.decodeToString())

    /** The versioned envelope the parsed text is stored in (ADR-0037). */
    @Serializable
    private data class CachedText(val formatVersion: Int, val text: StructuredText)

    /** The versioned envelope the chapter starts are stored in, in step with [CachedText]. */
    @Serializable
    private data class CachedChapters(val formatVersion: Int, val chapters: List<ChapterStart>)

    companion object {
        /**
         * The shape of [CachedText] this build writes and accepts; anything else
         * is a miss. Version 2 is ADR-0041's (a TXT carries the chapter list
         * inferred from its text); version 3 kept hard line breaks and list
         * markers; version 4 added the image blocks. A text cached by an older
         * version is re-parsed.
         *
         * [CachedChapters] carries the same version: a chapter start is an
         * offset into that text, so a text shape that shifts an offset must
         * invalidate the index with it.
         */
        const val FORMAT_VERSION = 4
    }
}
