package app.lekto.core.book

/**
 * The library: the vault's books, imported and opened (CONTEXT.md, "Book";
 * issue #15).
 *
 * Import parses a file and stores it; [books] lists what is stored; [open]
 * starts a reading session. The seam is synchronous — parsing and file writes
 * are blocking — so a caller that must not block (the UI) runs it off the main
 * thread and forwards [ImportProgress] to its own state.
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

    /** The reading session for the book with [id], or `null` when it is unknown. */
    fun open(id: String): ReadingSession?
}
