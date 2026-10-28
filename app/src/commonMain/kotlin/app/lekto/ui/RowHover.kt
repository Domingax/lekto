package app.lekto.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lekto.theme.hoverWash

/** The radius of a row's wash: the control radius, so a hovered row reads as one target. */
private val HOVER_CORNER = 8.dp

/** How far the wash reaches past the row's content, so the text is never flush with its edge. */
internal val HOVER_INSET = 10.dp

/**
 * The wash a row carries while the pointer rests on it (`docs/design.md` §8): the
 * theme's ink at 5 %, drawn as a rounded band wider than the row's content.
 *
 * The band is drawn behind the row rather than as its background, so the inset
 * costs no alignment: a title still lines up with the screen's other rows and
 * with the hairline under it, while the wash gives the pointer a calm target
 * instead of a full-width rectangle cut flush against the text.
 *
 * [ink] is the theme's ink, the only thing the wash is made of; nothing new
 * enters the screen.
 */
internal fun Modifier.rowHover(hovered: Boolean, ink: Color): Modifier = drawBehind {
    if (!hovered) return@drawBehind
    val inset = HOVER_INSET.toPx()
    drawRoundRect(
        color = hoverWash(ink),
        topLeft = Offset(-inset, 0f),
        size = Size(size.width + inset * 2, size.height),
        cornerRadius = CornerRadius(HOVER_CORNER.toPx()),
    )
}
