package app.lekto.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android is the touch client: the chip row is swiped, so it draws no scrollbar
 * (issue #212). The bar's state and place are deliberately unused — a second way
 * to do what the thumb already does would only be a control nobody needs.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal actual fun ChipRowScrollbar(state: ScrollState, modifier: Modifier) = Unit
