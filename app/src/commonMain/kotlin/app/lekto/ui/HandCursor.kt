package app.lekto.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/**
 * The pointing hand over a surface that acts on click. This is the only shared
 * modifier in `app` that asks for it: every control applies it here, and the
 * reader's page is the one documented exception, where `pointerHoverIcon` is
 * applied directly with `overrideDescendants` so the text inside stays a hand
 * (issue #217; docs/design.md §8).
 *
 * [enabled] is the control's own `enabled`: a disabled control promises nothing,
 * so it keeps the platform's arrow. The modifier is a no-op on a touch-only
 * platform, and a mouse on Android picks it up for free, so no platform-specific
 * code is needed.
 */
fun Modifier.handCursor(enabled: Boolean = true): Modifier = if (enabled) pointerHoverIcon(PointerIcon.Hand) else this
