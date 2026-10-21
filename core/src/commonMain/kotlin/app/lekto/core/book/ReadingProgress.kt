package app.lekto.core.book

/**
 * How far through a **Book** the reader is (CONTEXT.md, "Reading progress"):
 * the share of the book's rendered text that lies before its **Reading
 * position** (issue #196).
 *
 * [offset] is the position and [length] the rendered text's length, so the same
 * pair names the same place whatever the screen or font. [percent] is the whole
 * percentage the Library row shows: rounded **down**, so a book on its last page
 * reads a little under 100, and never capped below 100 — a position past the end
 * is 100, not 99.
 *
 * The length itself is a **Derived asset** kept beside the parsed text, never in
 * the vault (ADR-0005).
 */
data class ReadingProgress(val offset: Int, val length: Int) {

    /**
     * The whole percentage before [offset], floored and clamped to `0..100`.
     * A position at 0 is `0`; an offset past [length] is `100`. A length of
     * zero reads `0`, because there is no text to be a share of.
     */
    val percent: Int
        get() = if (length <= 0) 0 else ((offset.toLong() * PERCENT) / length).coerceIn(0L, PERCENT).toInt()

    private companion object {
        /** A percentage is a share out of this, not a magic number in the arithmetic. */
        const val PERCENT: Long = 100
    }
}

/**
 * What one Library row says about a **Book**'s **Reading progress** (issue
 * #196; docs/design.md §7.1). The three states are deliberately distinct:
 * `Not started` describes the data — no **Reading position** — and never the
 * reader's history, because opening a book without turning a page writes none.
 */
sealed interface BookProgress {

    /** The book has no Reading position: `FORMAT · Not started`, and no rule. */
    data object NotStarted : BookProgress

    /** The book has a position but its rendered length is not measured yet: `FORMAT · …`, and no rule. */
    data object Unmeasured : BookProgress

    /** The whole percentage before the position, shared by the line and the rule. */
    data class Measured(val percent: Int) : BookProgress

    companion object {
        /**
         * The row state for a book at [position] whose rendered length is
         * [length], or `null` until it has been measured.
         */
        fun forPosition(position: ReadingPosition, length: Int?): BookProgress =
            if (length == null) Unmeasured else Measured(ReadingProgress(position.offset, length).percent)
    }
}
