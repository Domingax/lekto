package app.lekto.theme

import androidx.compose.ui.unit.dp

/**
 * The width at which a window has room to spare (`docs/design.md` §6): the reader
 * reserves its right-hand **context column** from here (issue #199), and a sheet
 * raised over a list keeps a column's width instead of stretching to the edges
 * (issues #178). Below it, both fall back to the phone's shapes.
 *
 * One figure, so the two surfaces cannot disagree about where the window became
 * wide.
 */
val WIDE_WINDOW_BREAKPOINT = 1100.dp
