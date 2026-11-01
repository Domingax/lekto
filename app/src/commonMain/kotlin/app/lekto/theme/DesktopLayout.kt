package app.lekto.theme

import androidx.compose.ui.unit.dp

/**
 * The width at which a window has room to spare (`docs/design.md` §6): the reader
 * reserves its right-hand **context column** from here (issue #199), and the
 * Vocabulary list gives its open word the same column (issue #178). Below it, both
 * fall back to the phone's shapes.
 *
 * One figure, so the two surfaces cannot disagree about where the window became
 * wide.
 */
val WIDE_WINDOW_BREAKPOINT = 1100.dp

/**
 * The width of a reserved detail column beside the content (`docs/design.md` §6):
 * the reader's chapters and lookup, the Vocabulary list's open word. One width, so
 * moving between the two screens does not move the column's edge.
 */
val WIDE_COLUMN_WIDTH = 320.dp
