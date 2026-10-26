package app.lekto.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android is the touch client: the chip row is swiped, so it draws no scrollbar (issue #212). */
@Composable
internal actual fun ChipRowScrollbar(state: ScrollState, modifier: Modifier) = Unit
