package app.lekto.ui

import androidx.compose.ui.unit.dp

/**
 * The 1 dp rule that parts two surfaces or two rows (`docs/design.md` §4: no
 * shadows, so a surface separates by its tone and a hairline). It is shared by
 * the search field and the Library's inline failure so the one thickness is
 * named once.
 */
internal val HAIRLINE_THICKNESS = 1.dp
