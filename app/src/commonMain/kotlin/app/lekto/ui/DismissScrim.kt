package app.lekto.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * A transparent scrim that fills its parent and dismisses a panel when the reader
 * taps outside it. Placed *behind* a panel, it gives a panel the platform's
 * "tap outside to dismiss" affordance without the tap falling through to whatever
 * is underneath — a word in the reader, a page turn — which is what makes the
 * gesture safe on a phone.
 *
 * The panel itself must [swallowTaps], so a tap between its controls does not
 * dismiss it; a tap on one of its controls reaches the control first, because a
 * child takes the pointer before the scrim behind the panel.
 */
@Composable
fun DismissScrim(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
}

/**
 * Consumes every pointer event on the receiver, so a tap on a panel does not fall
 * through to a [DismissScrim] behind it. Children keep working: a pointer reaches
 * the innermost target first, so a control still acts before the panel swallows.
 */
fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent().changes.forEach { change -> change.consume() }
        }
    }
}
