package app.lekto.reader

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reader's own state around a window change (ADR-0040, issue #241): a jump or
 * a step across a chapter boundary starts a new window, and the pager measures it
 * from the chapter's start. Until that reaches the anchor the reader must keep
 * drawing the page it had, rather than a blank page with no bar.
 */
@Suppress("MagicNumber") // The offsets and lengths are the test's parameters.
class ReaderStateTest {

    private val firstWindow = listOf(ReaderPage(0, 100), ReaderPage(100, 200))

    @Test
    fun thePageStaysOnScreenWhileTheNextWindowIsMeasured() {
        val state = ReaderState(onPositionChange = {}, initialOffset = 0, initiallyFinished = false)
        state.bind(firstWindow, textLength = 1000, initialOffset = 0)
        assertEquals(ReaderPage(0, 100), state.page)

        // A step forward moves the anchor past this window; the next one is empty
        // until the pager has measured its first page.
        state.next()
        state.bind(emptyList(), textLength = 1000, initialOffset = 0)

        assertEquals(ReaderPage(0, 100), state.page, "the measured page stays until its replacement exists")
    }

    @Test
    fun theProgressIsTheShareOfTheTextBeforeTheAnchor() {
        val state = ReaderState(onPositionChange = {}, initialOffset = 0, initiallyFinished = false)
        state.bind(firstWindow, textLength = 1000, initialOffset = 250)

        assertEquals(25, state.progress)
    }
}
