package app.lekto.ui

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop has a pointer, so its chip row carries the platform's scrollbar (issue #212). */
@Composable
internal actual fun ChipRowScrollbar(state: ScrollState, modifier: Modifier) {
    HorizontalScrollbar(adapter = rememberScrollbarAdapter(state), modifier = modifier)
}
