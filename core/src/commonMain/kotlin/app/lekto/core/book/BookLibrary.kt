package app.lekto.core.book

import app.lekto.core.text.TextBlock

/**
 * The library: the vault's books, imported and opened (CONTEXT.md, "Book";
 * issues #15 and #16).
 *
 * Import parses a file and stores it; [books] lists what is stored; [open]
 * starts a reading session carrying the saved [ReadingPosition]; [position] and
 * [savePosition] read and write that position. The seam is synchronous — parsing
 * and file writes are blocking — so a caller that must not block (the UI) runs
 * it off the main thread and forwards [ImportProgress] to its own state.
 */
@Suppress("TooManyFunctions") // One operation per member: the façade's size is its contract.
interface BookLibrary {

    /** Every book in the vault, ordered by title. */
    fun books(): List<Book>

    /**
     * The books in the vault split the way the Library screen shows them: the
     * three most recently read unfinished books first, most recent first, then
     * every other book by title (CONTEXT.md, "Library"; issues #188 and #200).
     * Each book carries its [ReadingState], so the screen can filter without a
     * second read, and the **Chapter** its **Reading position** sits in, when
     * that is cached (issue #260). The parsed text is never decoded to list a
     * shelf.
     */
    fun listing(): LibraryListing

    /**
     * Imports [bytes] as the file named [fileName], storing the book in the
     * vault and reporting [onProgress] as it runs.
     *
     * @throws ImportException when the format is unsupported, the file cannot be
     *   parsed, or it cannot be stored. Nothing is left in the vault on failure.
     */
    fun import(fileName: String, bytes: ByteArray, onProgress: (ImportProgress) -> Unit = {}): Book

    /**
     * Removes the book with [id] from the vault — its record, its original
     * attachment, its cached parsed text, its **Resume snippet**, its reading
     * position and its finished declaration — and leaves a **Tombstone** for the
     * book, for its position and for its declaration (ADR-0015, ADR-0038), so none
     * resurrects on the next sync. An unknown [id] is a no-op (issue #131).
     *
     * The steps are ordered so a failure never leaves a half-deleted book
     * visible: the derived cache goes first, the reading position next, and the
     * book record and attachment last, so a failure before the last step leaves
     * the book fully listed and re-deletable. An all-or-nothing transaction is
     * not achievable over [VaultStore][app.lekto.core.vault.VaultStore]; a
     * replayed delete completes one that failed part-way.
     */
    fun delete(id: String)

    /**
     * The reading session for the book with [id], or `null` when it is unknown.
     * The session carries the saved [ReadingPosition], or `null` when the book
     * was never opened.
     */
    fun open(id: String): ReadingSession?

    /**
     * The cover image bytes stored for [bookId], or `null` when the book has none
     * or none it could read (issue #259). A **Derived asset** regenerated from
     * the **Book original**: a surface decodes it with its own platform, and
     * nothing here ever entered the **Vault**.
     */
    fun cover(bookId: String): ByteArray?

    /** Where the user left off in the book with [bookId], or `null` when unread. */
    fun position(bookId: String): ReadingPosition?

    /**
     * The **Resume snippet** stored for the book with [bookId] (issue #258), or
     * `null` when the last save had no paragraph to name. It is a **Derived
     * asset**, never a **Vault** record, so a surface that reads it on one device
     * and not another loses nothing.
     */
    fun snippet(bookId: String): String?

    /**
     * The reader's declaration that the book with [bookId] is **Finished**, or
     * `null` when they have not declared it (ADR-0038). The absence of a record
     * is what "not declared" means, so no migration is needed.
     */
    fun finished(bookId: String): FinishedBook?

    /**
     * Declares the book with [bookId] **Finished**, stamped with the clock's
     * now. It writes the separate finished record and never touches the
     * [ReadingPosition] or **Last read**: a declaration is not a page turn
     * (ADR-0038). An unknown book is a no-op.
     */
    fun markFinished(bookId: String)

    /**
     * Undoes the declaration for the book with [bookId]: the record is removed
     * and a **Tombstone** marks the deletion, so a device that has not seen the
     * undo cannot copy the declaration back (ADR-0015, ADR-0038). The
     * [ReadingPosition] and vocabulary are untouched.
     */
    fun markUnfinished(bookId: String)

    /**
     * Measures the rendered length of the book with [bookId] and caches it beside
     * its parsed text, then returns the book's **Reading progress** (issue #196).
     *
     * It is the background repair for a length a cache does not hold: a caller
     * that must not block (the Library) runs it off the main thread. It returns
     * `null` — and changes nothing — when the book has no Reading position or
     * its text cannot be parsed, so the row stays at `…` rather than claiming
     * `Not started`. The write is a **Derived asset**, so it never raises the
     * sync signal.
     */
    fun measure(bookId: String): ReadingProgress?

    /**
     * Stores [position] as the book's reading position, and the first paragraph
     * at or after its offset as the book's **Resume snippet** (issue #258).
     *
     * [blocks] is the text the reader lays out, passed from the open session so
     * the snippet costs no parse and no book opening. A legacy caller that has
     * no text passes none; nothing is derived, and the stored snippet is removed
     * so no stale line survives the move. When the offset falls past the last
     * paragraph — the end of the book, an empty text — the snippet is removed
     * too.
     */
    fun savePosition(position: ReadingPosition, blocks: List<TextBlock> = emptyList())
}
