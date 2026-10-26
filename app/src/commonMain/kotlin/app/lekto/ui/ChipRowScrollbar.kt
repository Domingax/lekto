package app.lekto.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The scrollbar a pointer needs under a row that scrolls sideways
 * (`docs/design.md` §7.4; issue #212).
 *
 * A touch screen swipes the row, and a partly cut chip is what says it moves. A
 * mouse has neither gesture: dragging a row of buttons starts a click, and a
 * vertical wheel over a horizontal row goes nowhere — so on desktop the platform's
 * own scrollbar stands under the row, the one control a pointer can take hold of.
 * A touch platform draws nothing, because its own gesture is the control.
 *
 * [state] is the row's own scroll state, so the bar and the row cannot disagree.
 */
@Composable
internal expect fun ChipRowScrollbar(state: ScrollState, modifier: Modifier = Modifier)
