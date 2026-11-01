package app.lekto.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import app.lekto.core.text.WordToken

/**
 * What a tap needs from the page it lands on: where its words are, the chapter
 * offset the page starts at, and who to tell (issue #251).
 */
@Suppress("LongParameterList") // The page's own geometry, the chapter offset, and who to tell.
internal class PageTap(
    val marginPx: Int,
    val tokens: ReaderTokens?,
    val layout: TextLayoutResult?,
    val pageStart: Int,
    val chapterText: String,
    val onWordTap: (WordTap) -> Unit,
)

/**
 * Taps on the page: the **initial** pass sees them before the text's selection
 * container consumes them, so a tap that lands on a word opens its lookup
 * wherever it lands. The page itself has no tap zones — a tap that misses every
 * word does nothing, so reaching for the text never turns a page (issue #243).
 */
internal fun Modifier.readerTapInput(onTap: State<(Offset) -> Unit>): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = awaitTapUp(down, viewConfiguration.touchSlop) ?: return@awaitEachGesture
        if (up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
            onTap.value(down.position)
        }
    }
}

/**
 * Waits for the tap's release in the initial pass, cancelling on movement beyond
 * [touchSlop] so a drag or a selection is not mistaken for a tap. The three exits
 * are the three ways a gesture resolves, so the return count is deliberately over
 * detekt's default.
 */
@Suppress("ReturnCount")
private suspend fun AwaitPointerEventScope.awaitTapUp(down: PointerInputChange, touchSlop: Float): PointerInputChange? {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) return change
        if ((change.position - down.position).getDistance() > touchSlop) return null
    }
}

/** The word token under [position], or `null` when the tap is not on a word. */
internal fun wordAt(position: Offset, marginPx: Int, words: List<WordToken>, layout: TextLayoutResult?): WordToken? =
    wordIndexAt(position, marginPx, words, layout)?.let { index -> words[index] }
