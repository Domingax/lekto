package app.lekto.core.book

import app.lekto.core.Seams
import app.lekto.core.sync.TombstoneStore
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.StructuredText
import app.lekto.core.vault.DerivedAssetStore
import app.lekto.core.vault.DeviceId
import app.lekto.core.vault.Tombstone
import app.lekto.core.vault.VaultRecord
import app.lekto.core.vault.VaultStore
import kotlin.time.Instant

/**
 * The [BookLibrary] over a [VaultStore] and a [DerivedAssetStore] (issue #15).
 *
 * A book is a vault record of kind [BookRecord.KIND] plus its original bytes as
 * that record's attachment (ADR-0016). The parser turns the original into
 * [StructuredText], which is cached in the derived store — device-local and
 * never exported (ADR-0005). Opening a book reads the cache, or re-parses and
 * re-caches when the cache is missing, so a wiped cache is not data loss.
 *
 * The composition root supplies the [parsers] and the installation's stable
 * [deviceId] (ADR-0003); the domain reads neither a platform nor a global.
 *
 * A [delete] removes the book with its original and cached text **and** leaves a
 * [Tombstone] in the engine's own store (ADR-0015), so the deletion survives a
 * sync instead of being copied back from a device that has not seen it — the same
 * pattern [VaultVocabulary][app.lekto.core.vocabulary.VaultVocabulary] uses. The
 * store is the same one the sync engine reads, so it is the composition root's
 * job to point both at it.
 *
 * @param parsers the parser for each supported [BookFormat]; a missing format is
 *   refused rather than guessed.
 */
@Suppress("LongParameterList") // Its collaborators are seams; bundling them adds a type, not clarity.
class VaultBookLibrary(
    private val vault: VaultStore,
    derived: DerivedAssetStore,
    private val parsers: Map<BookFormat, BookTextParser>,
    private val tombstones: TombstoneStore,
    private val seams: Seams,
    private val deviceId: DeviceId,
) : BookLibrary {

    private val cache = BookTextCache(derived)

    override fun books(): List<Book> = booksIn(vault.all())

    override fun listing(): LibraryListing {
        val records = vault.all()
        return LibraryListing.of(books = booksIn(records), lastReadByBook = lastReadIn(records))
    }

    override fun import(fileName: String, bytes: ByteArray, onProgress: (ImportProgress) -> Unit): Book {
        val name = displayName(fileName)
        val format = BookFormat.ofFileName(name)
            ?: throw ImportException("Lekto cannot import '$name'. Choose an EPUB or a text file.")
        if (bytes.isEmpty()) throw ImportException("'$name' is empty.")

        onProgress(ImportProgress(ImportStage.PARSING, 0f))
        val text = parse(format, name, bytes)
        onProgress(ImportProgress(ImportStage.SAVING, PARSE_SHARE))

        val book = Book(
            id = seams.ids.newId(),
            title = text.title?.takeIf { title -> title.isNotBlank() } ?: name.substringBeforeLast('.'),
            language = text.language,
            format = format,
            fileName = name,
        )
        store(book, bytes, text)
        onProgress(ImportProgress(ImportStage.SAVING, 1f))
        return book
    }

    override fun open(id: String): ReadingSession? {
        val book = vault.get(id)?.let { record -> runCatching { BookRecord.bookOf(record) }.getOrNull() }
        val text = book?.let { cache.textOf(it.id) ?: reparse(it) }
        return if (book != null && text != null) ReadingSession(book, text, position(book.id)) else null
    }

    override fun position(bookId: String): ReadingPosition? = vault.get(ReadingPositionRecord.idOf(bookId))
        ?.let { record -> runCatching { ReadingPositionRecord.positionOf(record) }.getOrNull() }

    override fun savePosition(position: ReadingPosition) {
        vault.put(ReadingPositionRecord.of(position, seams.clock.now(), deviceId))
    }

    /**
     * Removes the book's original, cached text and reading position, then the
     * book record itself, leaving a tombstone for the book and for its position
     * (issue #131; ADR-0015).
     *
     * Each tombstone is written **before** the record it marks is removed, so a
     * failure leaves the book fully listed and a replayed delete completes it:
     * were the record removed first, a failed tombstone write would leave nothing
     * for the replay to key on. The book record goes last, so a failure never
     * leaves a half-deleted book **visible**; removing it takes its attachment
     * with it (ADR-0016). An unknown [id] is a no-op, matching the vocabulary's
     * own "deleting an unsaved word is not an error".
     */
    override fun delete(id: String) {
        if (vault.get(id) == null) return
        val now = seams.clock.now()
        cache.remove(id)
        val positionId = ReadingPositionRecord.idOf(id)
        if (vault.get(positionId) != null) {
            tombstones.put(Tombstone(id = positionId, updatedAt = now, deviceId = deviceId))
            vault.remove(positionId)
        }
        tombstones.put(Tombstone(id = id, updatedAt = now, deviceId = deviceId))
        vault.remove(id)
    }

    /** Writes the record, its original attachment and the parsed-text cache. */
    @Suppress("TooGenericExceptionCaught") // Any storage failure must become an ImportException.
    private fun store(book: Book, bytes: ByteArray, text: StructuredText) {
        try {
            vault.put(BookRecord.of(book, seams.clock.now(), deviceId))
            vault.putAttachment(book.id, bytes)
            cache.put(book.id, text)
        } catch (failure: RuntimeException) {
            rollback(book.id)
            throw ImportException("Could not save '${book.title}'.", failure)
        }
    }

    /** Undoes a partial [store] so a failed import leaves the vault unchanged. */
    private fun rollback(id: String) {
        runCatching { vault.removeAttachment(id) }
        runCatching { vault.remove(id) }
        runCatching { cache.remove(id) }
    }

    /** Re-parses [book]'s stored original and re-caches it, or `null` when it cannot be read. */
    private fun reparse(book: Book): StructuredText? = vault.getAttachment(book.id)
        ?.let { bytes -> runCatching { parse(book.format, book.fileName, bytes) }.getOrNull() }
        ?.also { text -> runCatching { cache.put(book.id, text) } }

    /** Parses [bytes], wrapping any parser failure in a user-facing [ImportException]. */
    @Suppress("TooGenericExceptionCaught") // A parser may fail any way; report it, do not crash.
    private fun parse(format: BookFormat, fileName: String, bytes: ByteArray): StructuredText {
        val parser = parsers[format] ?: throw ImportException("Lekto has no ${format.name} support in this build.")
        return try {
            parser.parse(bytes)
        } catch (failure: RuntimeException) {
            throw ImportException(
                "Could not read '$fileName': ${failure.message ?: "the file is not valid ${format.name}"}",
                failure,
            )
        }
    }

    private companion object {
        /** Parsing is most of the work; the remaining tenth is the vault write. */
        const val PARSE_SHARE = 0.9f
    }
}

private fun displayName(fileName: String): String = fileName.substringAfterLast('/')

/** The vault's books, in title order, skipping a record this build cannot read. */
private fun booksIn(records: List<VaultRecord>): List<Book> = records
    .filter { record -> record.kind == BookRecord.KIND }
    .mapNotNull { record -> runCatching { BookRecord.bookOf(record) }.getOrNull() }
    .inTitleOrder()

/**
 * When each book's position last moved: the position record's `updatedAt`
 * (CONTEXT.md, "Last read"). A record this build cannot read names no book, so
 * it is skipped rather than misdated.
 */
private fun lastReadIn(records: List<VaultRecord>): Map<String, Instant> = records
    .filter { record -> record.kind == ReadingPositionRecord.KIND }
    .mapNotNull { record ->
        runCatching { ReadingPositionRecord.positionOf(record) }.getOrNull()
            ?.let { position -> position.bookId to record.updatedAt }
    }
    .toMap()
