package app.lekto.theme

import androidx.compose.ui.unit.dp

/**
 * The 4 dp spacing scale of `docs/design.md` §4, named by the interval it
 * settles rather than by a size, so a screen asks for "between rows" instead of
 * choosing 12 or 13 by eye.
 *
 * The design gives bands — 4 inside a label pair, 6–8 between related lines,
 * 12–14 between rows, 16–20 inside a panel, 20–24 at a screen's edges and 24–48
 * between blocks — and these are their 4 dp representatives.
 */
object LektoSpacing {
    /** Inside a label pair: a label and the value under it. */
    val InsideLabelPair = 4.dp

    /** Between related lines. */
    val BetweenRelatedLines = 8.dp

    /** Between list rows. */
    val BetweenRows = 12.dp

    /** Inside a panel or card. */
    val InsidePanel = 16.dp

    /** At a screen's edges. */
    val ScreenEdge = 24.dp

    /**
     * At the window's edge for a bar's icon control, and for a screen header's: the
     * 4 dp Material gives an app bar, so a 48 dp control lands its glyph near the
     * border instead of floating inward. Text and rows keep [ScreenEdge]; only a
     * header's controls take this one (docs/design.md §6, §7.5).
     */
    val BarInset = 4.dp

    /** Between a screen's blocks. */
    val BetweenBlocks = 32.dp

    /** Between a screen's sections, at the top of the band. */
    val BetweenSections = 48.dp

    /** The minimum touch target on Android, which no row may shrink below. */
    val TouchTarget = 48.dp
}
