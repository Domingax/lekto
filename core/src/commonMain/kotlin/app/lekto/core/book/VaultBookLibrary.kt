package app.lekto.core.book

import app.lekto.core.Seams
import app.lekto.core.sync.TombstoneStore
import app.lekto.core.text.BookAsset
import app.lekto.core.text.BookTextParser
import app.lekto.core.text.ParsedBook
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
 * never exported (ADR-0005). The same store holds the book's images and its
 * cover, both re-derived with the text when a cache is wiped. Opening a book
 * reads the cache, or re-parses and re-caches when the cache is missing, so a
 * wiped cache is not data loss.
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
@Suppress("LongParameterList", "TooManyFunctions") // Seams, and one function per BookLibrary operation, not a grab bag.
class VaultBookLibrary(
    private val vault: VaultStore,
    private val derived: DerivedAssetStore,
    private val parsers: Map<BookFormat, BookTextParser>,
    private val tombstones: TombstoneStore,
    private val seams: Seams,
    private val deviceId: DeviceId,
) : BookLibrary {

    private val cache = BookTextCache(derived)
    private val covers = BookCoverCache(derived)

    override fun books(): List<Book> = booksIn(vault.all())

    override fun listing(): LibraryListing {
        val records = vault.all()
        // Only the position, finished and tiny length entries are read here: the
        // parsed text is never decoded to list a shelf (issues #196, #200).
        val positions = positionsIn(records)
        val finished = finishedIn(records).keys
        val progress = positions.mapValues { (bookId, position) ->
            BookProgress.forPosition(position, cache.lengthOf(bookId))
        }
        val books = booksIn(records)
        val states = books.associate { book ->
            book.id to ReadingState.of(hasPosition = book.id in positions, finished = book.id in finished)
        }
        return LibraryListing.of(
            books = books,
            lastReadByBook = lastReadIn(records),
            progressByBook = progress,
            readingStateByBook = states,
        )
    }

    override fun import(fileName: String, bytes: ByteArray, onProgress: (ImportProgress) -> Unit): Book {
        val name = displayName(fileName)
        val format = BookFormat.ofFileName(name)
            ?: throw ImportException("Lekto cannot import '$name'. Choose an EPUB or a text file.")
        if (bytes.isEmpty()) throw ImportException("'$name' is empty.")

        onProgress(ImportProgress(ImportStage.PARSING, 0f))
        val parsed = parse(format, name, bytes)
        val text = parsed.text
        onProgress(ImportProgress(ImportStage.SAVING, PARSE_SHARE))

        val book = Book(
            id = seams.ids.newId(),
            title = text.title?.takeIf { title -> title.isNotBlank() } ?: name.substringBeforeLast('.'),
            language = text.language,
            format = format,
            fileName = name,
        )
        store(book, bytes, parsed)
        onProgress(ImportProgress(ImportStage.SAVING, 1f))
        return book
    }

    override fun open(id: String): ReadingSession? {
        val book = vault.get(id)?.let { record -> runCatching { BookRecord.bookOf(record) }.getOrNull() }
        val text = book?.let { cache.textOf(it.id) ?: reparse(it) }
        return if (book != null && text != null) {
            ReadingSession(book, text, position(book.id), finished(book.id))
        } else {
            null
        }
    }

    override fun position(bookId: String): ReadingPosition? = vault.get(ReadingPositionRecord.idOf(bookId))
        ?.let { record -> runCatching { ReadingPositionRecord.positionOf(record) }.getOrNull() }

    override fun finished(bookId: String): FinishedBook? = vault.get(FinishedBookRecord.idOf(bookId))
        ?.let { record -> runCatching { FinishedBookRecord.finishedOf(record) }.getOrNull() }

    override fun savePosition(position: ReadingPosition) {
        vault.put(ReadingPositionRecord.of(position, seams.clock.now(), deviceId))
    }

    /**
     * Writes the finished record with the clock's now. The position record is
     * deliberately untouched, so **Last read** does not move (ADR-0038).
     */
    override fun markFinished(bookId: String) {
        if (vault.get(bookId) == null) return
        val finished = FinishedBook(bookId, seams.clock.now())
        vault.put(FinishedBookRecord.of(finished, finished.finishedAt, deviceId))
    }

    /**
     * Removes the finished record, writing its tombstone first so a device that
     * has not seen the undo cannot copy the declaration back (ADR-0015,
     * ADR-0038). A book that was not declared finished is a no-op: no record
     * exists to delete, so there is nothing for a tombstone to out-vote.
     */
    override fun markUnfinished(bookId: String) {
        removeWithTombstone(FinishedBookRecord.idOf(bookId), seams.clock.now())
    }

    /**
     * A missing rendered length is repaired from the cached text when that
     * survives, and from the original otherwise — the re-parse re-caches both
     * ([BookTextCache.put]). A read or write that fails leaves the length
     * absent, so the row keeps `…`; it never becomes `Not started`.
     */
    override fun measure(bookId: String): ReadingProgress? {
        /** The cached text's length, or the original's when the cache is gone. */
        fun renderedLength(book: Book): Int? =
            cache.textOf(book.id)?.renderedLength() ?: reparse(book)?.renderedLength()

        /** The entry when it survives, a repaired one otherwise; `null` when it cannot be stored. */
        fun cachedLength(book: Book): Int? = runCatching {
            cache.lengthOf(book.id) ?: renderedLength(book)?.also { length -> cache.putLength(book.id, length) }
        }.getOrNull()

        val book = vault.get(bookId)?.let { record -> runCatching { BookRecord.bookOf(record) }.getOrNull() }
        return position(bookId)?.let { position ->
            book?.let(::cachedLength)?.let { length -> ReadingProgress(position.offset, length) }
        }
    }

    /**
     * Removes the book's original, cached text, images, cover, reading position
     * and finished declaration, then the book record itself, leaving a tombstone
     * for the book and for each fact (issue #131; ADR-0015, ADR-0038).
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
        covers.remove(id)
        derived.removeUnder(imagesOf(id))
        removeWithTombstone(ReadingPositionRecord.idOf(id), now)
        removeWithTombstone(FinishedBookRecord.idOf(id), now)
        tombstones.put(Tombstone(id = id, updatedAt = now, deviceId = deviceId))
        vault.remove(id)
    }

    /** Removes [recordId] if it exists, writing its tombstone first so the deletion can sync. */
    private fun removeWithTombstone(recordId: String, now: Instant) {
        if (vault.get(recordId) == null) return
        tombstones.put(Tombstone(id = recordId, updatedAt = now, deviceId = deviceId))
        vault.remove(recordId)
    }

    /** Writes the record, its original attachment, its images, its cover and the parsed-text cache. */
    @Suppress("TooGenericExceptionCaught") // Any storage failure must become an ImportException.
    private fun store(book: Book, bytes: ByteArray, parsed: ParsedBook) {
        // A failure part-way must leave the vault unchanged.
        fun rollback() {
            runCatching { vault.removeAttachment(book.id) }
            runCatching { vault.remove(book.id) }
            runCatching { derived.removeUnder(imagesOf(book.id)) }
            runCatching { covers.remove(book.id) }
            runCatching { cache.remove(book.id) }
        }
        try {
            vault.put(BookRecord.of(book, seams.clock.now(), deviceId))
            vault.putAttachment(book.id, bytes)
            writeImages(book.id, parsed.assets)
            storeCover(book.id, parsed.cover)
            cache.put(book.id, parsed.text)
        } catch (failure: RuntimeException) {
            rollback()
            throw ImportException("Could not save '${book.title}'.", failure)
        }
    }

    /**
     * Writes or removes the cover, best-effort: a cover is decoration beside the
     * text, so a storage failure loses the art and never fails the import
     * (issue #259). The write is atomic, so a failure leaves no partial file.
     */
    private fun storeCover(bookId: String, cover: ByteArray?) {
        runCatching { cover?.let { bytes -> covers.put(bookId, bytes) } ?: covers.remove(bookId) }
    }

    /** Writes the images a parse extracted, beside the text they belong to. */
    private fun writeImages(bookId: String, assets: List<BookAsset>) {
        assets.forEach { asset -> derived.put(imagesOf(bookId) + asset.name, asset.bytes) }
    }

    /** Where a book's images live in the derived store: one directory per book, never in the **Vault**. */
    private fun imagesOf(bookId: String): String = "book-images/$bookId/"

    /**
     * Re-parses [book]'s stored original and re-caches it, or `null` when it
     * cannot be read. Its images and its cover are re-derived with its text, so
     * a wiped cache restores all three.
     */
    private fun reparse(book: Book): StructuredText? = vault.getAttachment(book.id)
        ?.let { bytes -> runCatching { parse(book.format, book.fileName, bytes) }.getOrNull() }
        ?.also { parsed ->
            runCatching {
                writeImages(book.id, parsed.assets)
                storeCover(book.id, parsed.cover)
                cache.put(book.id, parsed.text)
            }
        }
        ?.text

    /** Parses [bytes], wrapping any parser failure in a user-facing [ImportException]. */
    @Suppress("TooGenericExceptionCaught") // A parser may fail any way; report it, do not crash.
    private fun parse(format: BookFormat, fileName: String, bytes: ByteArray): ParsedBook {
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
 * Where each book was left off: the position record's body, keyed by its book id
 * (issue #196). A record this build cannot read names no book, so it is skipped,
 * exactly as [lastReadIn] skips it.
 */
private fun positionsIn(records: List<VaultRecord>): Map<String, ReadingPosition> = records
    .filter { record -> record.kind == ReadingPositionRecord.KIND }
    .mapNotNull { record -> runCatching { ReadingPositionRecord.positionOf(record) }.getOrNull() }
    .associate { position -> position.bookId to position }

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

/**
 * The books declared **Finished**, keyed by book id (ADR-0038). A record this
 * build cannot read names no book, so it is skipped exactly as [lastReadIn]
 * skips one — the absence of a readable declaration already means "not
 * declared", which is why there is no migration. A position record also lives
 * under [FinishedBookRecord.KIND] but has no `finishedAt`, so decoding it as a
 * declaration fails and it is skipped.
 */
private fun finishedIn(records: List<VaultRecord>): Map<String, FinishedBook> = records
    .filter { record -> record.kind == FinishedBookRecord.KIND }
    .mapNotNull { record -> runCatching { FinishedBookRecord.finishedOf(record) }.getOrNull() }
    .associate { finished -> finished.bookId to finished }
