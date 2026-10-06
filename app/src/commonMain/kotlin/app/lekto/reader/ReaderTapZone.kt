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

/** A tap zone the reader's surface is divided into. */
internal enum class ReaderTapZone { PREVIOUS, NEXT, CHROME }

/** The width fraction each of the left and right zones takes, leaving the middle for the chrome. */
private const val FIRST_THIRD = 1f / 3f
private const val LAST_THIRD = 2f / 3f

/** The zone [x] (in pixels, from the viewport's left) falls in, for a viewport [width] pixels wide. */
internal fun readerTapZone(x: Float, width: Int): ReaderTapZone = when {
    x < width * FIRST_THIRD -> ReaderTapZone.PREVIOUS
    x > width * LAST_THIRD -> ReaderTapZone.NEXT
    else -> ReaderTapZone.CHROME
}

/**
 * Taps on the page: the **initial** pass sees them before the word layer and the
 * selection container consume them, so the reader can route the tap itself,
 * while the word layer still handles a tap that lands on a word.
 */
internal fun Modifier.readerTapInput(onTap: State<(Offset, Float) -> Unit>): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val up = awaitTapUp(down, viewConfiguration.touchSlop) ?: return@awaitEachGesture
        if (up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
            onTap.value(down.position, size.width.toFloat())
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
