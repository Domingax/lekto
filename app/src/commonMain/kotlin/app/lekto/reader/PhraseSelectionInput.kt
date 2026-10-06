package app.lekto.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import app.lekto.core.text.WordToken

/**
 * Long-press and drag to select a phrase: the word under the touch anchors the
 * selection, dragging extends it word by word, and releasing reports the phrase
 * through [onSelected]. [onSelecting] carries the page-local range while the
 * gesture is live (`null` once it ends), so the reader can wash the chosen words
 * as they are picked. A short tap is left to the page's word links, so word
 * lookup is unchanged.
 *
 * It sees the touch on the **initial** pass, before the word links consume it: a
 * link claims the down on the main pass, so a long press could never start on a
 * word otherwise. [chapter] and [pageStart] lift the page-local words back to
 * the chapter coordinates the panel works in. [words] and [layout] are read at
 * event time, so a recomposition while the drag is live (the wash rebuilding the
 * page's tokens) does not restart the gesture.
 */
@Suppress("LongParameterList") // The gesture's inputs are the page's numbers and the callbacks.
internal fun Modifier.phraseSelectionInput(
    words: () -> List<WordToken>,
    layout: () -> TextLayoutResult?,
    marginPx: Int,
    chapter: String,
    pageStart: Int,
    onSelecting: (IntRange?) -> Unit,
    onSelected: (PhraseSelection) -> Unit,
): Modifier = pointerInput(marginPx, chapter, pageStart) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val anchor = wordIndexAt(down.position, marginPx, words(), layout(), snapToNearest = true)
            ?: return@awaitEachGesture
        awaitLongPress(down) ?: return@awaitEachGesture
        var current = anchor
        onSelecting(phraseRangeAt(words(), anchor, current))
        var dragging = true
        while (dragging) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                dragging = false
            } else {
                change.consume()
                wordIndexAt(change.position, marginPx, words(), layout(), snapToNearest = true)?.let { index ->
                    current = index
                    onSelecting(phraseRangeAt(words(), anchor, current))
                }
            }
        }
        onSelecting(null)
        onSelected(phraseInChapter(chapter, pageStart, phraseRangeAt(words(), anchor, current)))
    }
}

/**
 * Waits for a long press on [down] on the **initial** pass, so the touch is seen
 * before the word links — a link consumes the down on the main pass and would
 * otherwise leave nothing to press. Returns the long-pressed change, or null if
 * the finger lifts or moves away before the timeout.
 */
@Suppress("ReturnCount") // The two exits are the two ways a long press resolves.
private suspend fun AwaitPointerEventScope.awaitLongPress(down: PointerInputChange): PointerInputChange? {
    var latest = down
    val slop = viewConfiguration.touchSlop
    return try {
        withTimeout(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) return@withTimeout null
                if ((change.position - down.position).getDistance() > slop) return@withTimeout null
                latest = change
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        latest.consume()
        latest
    }
}
