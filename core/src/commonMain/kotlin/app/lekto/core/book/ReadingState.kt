package app.lekto.core.book

/**
 * Where a **Book** stands in the reader's **Library** (CONTEXT.md, "Reading
 * state"; ADR-0038).
 *
 * **Finished** is declared by the reader and wins over everything else: a
 * finished book that is reopened or reread stays finished until an explicit
 * undo. Otherwise the **Reading position** decides: a position means
 * [IN_PROGRESS], and no position means [UNREAD].
 */
enum class ReadingState {

    /** Not finished, and no **Reading position**: the reader has not turned a page. */
    UNREAD,

    /** Not finished, with a **Reading position**: the reader has started. */
    IN_PROGRESS,

    /** Declared finished by the reader, whatever the position says. */
    FINISHED,
    ;

    companion object {

        /**
         * The state [bookId]'s two facts give it: [finished] wins, then
         * [hasPosition]. The app never derives finished from the position
         * (ADR-0038).
         */
        fun of(hasPosition: Boolean, finished: Boolean): ReadingState = when {
            finished -> FINISHED
            hasPosition -> IN_PROGRESS
            else -> UNREAD
        }
    }
}
