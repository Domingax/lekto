package app.lekto.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.max

/** The turn a horizontal swipe asks for. */
internal enum class PageSwipe { PREVIOUS, NEXT }

/** The share of the page's width a drag must cover to count as a swipe. */
private const val SWIPE_WIDTH_FRACTION = 0.15f

/**
 * The turn a drag of [dx] and [dy] pixels across a [width]-pixel page asks for,
 * or `null` when the drag is too short or more vertical than horizontal. A
 * leftward drag asks for the next page, a rightward one for the previous.
 */
internal fun pageSwipe(dx: Float, dy: Float, width: Float): PageSwipe? = when {
    abs(dx) < max(width * SWIPE_WIDTH_FRACTION, 1f) -> null
    abs(dx) <= abs(dy) -> null
    dx < 0f -> PageSwipe.NEXT
    else -> PageSwipe.PREVIOUS
}

/**
 * Turns the page on a horizontal swipe. It sees the touch on the **initial**
 * pass, before the word links and the text's selection container consume it, so
 * a valid swipe is not lost to them; it consumes the drag once it is recognised
 * as one, so the same drag does not also extend a text selection.
 *
 * Only a touch pointer swipes: a mouse drag belongs to the text's own selection.
 * A gesture that stays still for the long-press timeout, or that moves mostly
 * vertically, is left alone — the first is [phraseSelectionInput]'s, and the
 * second is nobody's.
 */
internal fun Modifier.readerSwipeInput(enabled: Boolean, onSwipe: State<(PageSwipe) -> Unit>): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.type != PointerType.Touch) return@awaitEachGesture
            val up = awaitSwipe(down) ?: return@awaitEachGesture
            val swipe = pageSwipe(
                dx = up.position.x - down.position.x,
                dy = up.position.y - down.position.y,
                width = size.width.toFloat(),
            ) ?: return@awaitEachGesture
            onSwipe.value(swipe)
        }
    }

/**
 * Follows [down] until the finger lifts, returning the release when the drag is
 * a horizontal swipe and `null` when it is not: the finger moved mostly
 * vertically, the drag began after the long-press timeout, or another gesture
 * claimed the touch.
 */
@Suppress("ReturnCount") // Each exit is a distinct way the gesture stops being a swipe.
private suspend fun AwaitPointerEventScope.awaitSwipe(down: PointerInputChange): PointerInputChange? {
    var dragging = false
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            ?: return null
        if (!change.pressed) return change.takeIf { dragging }
        when (swipeStep(change, down, dragging)) {
            SwipeStep.ABANDON -> return null
            SwipeStep.CLAIM -> dragging = true
            SwipeStep.HOLD -> Unit
        }
        if (dragging) change.consume()
    }
}

/** What the next pointer event makes of the gesture. */
private enum class SwipeStep { HOLD, CLAIM, ABANDON }

/**
 * The step [change] takes the gesture: it abandons once another gesture owns the
 * touch or the finger has been held still for the long-press timeout, claims the
 * drag when it travels far enough horizontally, and holds while it is still
 * undecided.
 */
private fun AwaitPointerEventScope.swipeStep(
    change: PointerInputChange,
    down: PointerInputChange,
    dragging: Boolean,
): SwipeStep {
    val dx = change.position.x - down.position.x
    val dy = change.position.y - down.position.y
    // After the long-press timeout the gesture is a phrase selection, not a
    // swipe, even if the finger then travels far enough.
    val held = change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis
    return when {
        change.isConsumed -> SwipeStep.ABANDON

        // Once the drag is a swipe it stays one, however the finger wanders after.
        dragging -> SwipeStep.CLAIM

        held -> SwipeStep.ABANDON

        abs(dy) > viewConfiguration.touchSlop -> SwipeStep.ABANDON

        abs(dx) > viewConfiguration.touchSlop && abs(dx) > abs(dy) -> SwipeStep.CLAIM

        else -> SwipeStep.HOLD
    }
}
