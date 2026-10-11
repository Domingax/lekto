package app.lekto.core.book

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
interface BookLibrary {

    /** Every book in the vault, ordered by title. */
    fun books(): List<Book>

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
     * attachment, its cached parsed text and its reading position — and leaves a
     * **Tombstone** for the book and for its position (ADR-0015), so neither
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

    /** Where the user left off in the book with [bookId], or `null` when unread. */
    fun position(bookId: String): ReadingPosition?

    /** Stores [position] as the book's reading position. */
    fun savePosition(position: ReadingPosition)
}
