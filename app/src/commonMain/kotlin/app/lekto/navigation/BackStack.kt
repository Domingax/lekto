package app.lekto.navigation

/**
 * The app's back stack: where it is now, at the end, and the way back before it
 * (issue #185; docs/design.md §6).
 *
 * It is a value, not a holder, so every move is a pure function a test can drive
 * without a composition: [push] opens a place, [select] taps a bar item, [back]
 * walks one step home. The composition keeps the current value in state and
 * replaces it, which is what makes Android's back button a walk rather than the
 * app leaving from wherever it stands.
 *
 * [select] unwinds instead of pushing when the section is already behind the
 * current place: the bar cannot build a loop, so Back always retraces a route the
 * reader has seen, and the Library — the root — always exits the app.
 */
data class BackStack private constructor(private val entries: List<Destination>) {

    init {
        require(entries.isNotEmpty()) { "the back stack always holds the place the app is in" }
    }

    /** Where the app is now. */
    val current: Destination get() = entries.last()

    /** The steps behind [current], oldest first — the places Back walks through. */
    val behind: List<Destination> get() = entries.dropLast(1)

    /** Whether there is anywhere to go back to. At the root, Back is the platform's (it exits). */
    val canGoBack: Boolean get() = entries.size > 1

    /** How deep the stack is: `1` at the root, one more for each place behind it. */
    val depth: Int get() = entries.size

    /**
     * Opens [destination] above the current place. The reader is pushed, so Back
     * returns to the shelf it was opened from, and a failure's settings link is
     * pushed, so Back returns to the book.
     *
     * Pushing the place already shown is a no-op, so a repeated tap cannot stack
     * the same screen twice.
     */
    fun push(destination: Destination): BackStack =
        if (destination == current) this else BackStack(entries + destination)

    /**
     * Taps [section] in the bar: it becomes the current place, and a section
     * already behind it unwinds to the copy the reader has been to, rather than
     * stacking a second one.
     */
    fun select(section: ShellSection): BackStack {
        val known = entries.indexOfFirst { destination -> destination.section == section }
        return when {
            known >= 0 -> BackStack(entries.take(known + 1))
            else -> push(section.root)
        }
    }

    /**
     * Walks one step home, dropping the current place. At the root there is
     * nothing to drop, so the stack is unchanged and the caller lets the platform
     * leave the app.
     */
    fun back(): BackStack = if (canGoBack) BackStack(entries.dropLast(1)) else this

    /**
     * Leaves the current place for [destination]: the reader's settings link takes
     * the reader out of the book, so the book is not kept behind it. Returning to
     * it would reopen a session captured before the reader turned a page, which
     * would move them. At the root — nothing behind — this is a [push].
     */
    fun replace(destination: Destination): BackStack =
        if (canGoBack) BackStack(entries.dropLast(1) + destination) else push(destination)

    companion object {
        /** The stack the app opens on: the Library, with nothing behind it. */
        val Root: BackStack = BackStack(listOf(Destination.Library))
    }
}
