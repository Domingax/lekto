package app.lekto.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * A click with no Material indication. The identity's `docs/design.md` §8 is
 * explicit: every clickable surface shows the pointing hand on desktop, and a
 * click flashes no ripple — a whole row blinking is an animation, and the
 * identity has none. The row's hover wash is the row's own (§8: the theme's ink
 * at 5 %), drawn by `DeletingRow`, not a side effect of this click.
 */
internal fun Modifier.plainClickable(enabled: Boolean = true, role: Role? = null, onClick: () -> Unit): Modifier =
    clickable(interactionSource = null, indication = null, enabled = enabled, role = role, onClick = onClick)
