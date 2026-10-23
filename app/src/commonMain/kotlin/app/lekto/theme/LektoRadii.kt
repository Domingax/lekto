package app.lekto.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The sheet radius of `docs/design.md` §4, shared by a sheet's free corners. */
private val SheetRadius = 16.dp

/**
 * The radii of `docs/design.md` §4, as roles a screen can name.
 *
 * Controls take 8 dp, cards and panels 12 dp, sheets and dialogues 16 dp.
 */
object LektoRadii {
    /** Buttons, chips, fields and language vignettes. */
    val Control = RoundedCornerShape(8.dp)

    /** Cards, panels and sections. */
    val Surface = RoundedCornerShape(12.dp)

    /** Sheets and dialogues. */
    val Sheet = RoundedCornerShape(SheetRadius)

    /**
     * A sheet anchored to the bottom edge of the screen: the same 16 dp as [Sheet]
     * on the two corners that are free, and square where it meets the edge.
     */
    val SheetTop = RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius)
}
