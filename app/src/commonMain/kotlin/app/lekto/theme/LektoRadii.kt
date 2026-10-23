package app.lekto.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The three radii of `docs/design.md` §4, as roles a screen can name.
 *
 * Controls take 8 dp, cards and panels 12 dp, sheets and dialogues 16 dp.
 */
object LektoRadii {
    /** Buttons, chips, fields and language vignettes. */
    val Control = RoundedCornerShape(8.dp)

    /** Cards, panels and sections. */
    val Surface = RoundedCornerShape(12.dp)

    /** Sheets and dialogues. */
    val Sheet = RoundedCornerShape(16.dp)

    /**
     * A sheet anchored to the bottom edge of the screen: the same 16 dp as [Sheet]
     * on the two corners that are free, and square where it meets the edge.
     */
    val SheetTop = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
}
