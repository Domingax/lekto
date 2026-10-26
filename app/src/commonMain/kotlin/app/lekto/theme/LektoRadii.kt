package app.lekto.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The three radii of `docs/design.md` §4, as roles a screen can name.
 *
 * Controls take 8 dp, cards and panels 12 dp, sheets and dialogues 16 dp. A panel
 * anchored to a screen edge is square where it meets the edge, so only a sheet's
 * top corners take [SheetTop].
 */
object LektoRadii {
    /** The radius a sheet and a dialogue share. */
    private val SheetRadius = 16.dp

    /** Buttons, chips, fields and language vignettes. */
    val Control = RoundedCornerShape(8.dp)

    /** Cards, panels and sections. */
    val Surface = RoundedCornerShape(12.dp)

    /** Sheets and dialogues. */
    val Sheet = RoundedCornerShape(SheetRadius)

    /**
     * A sheet that joins the bottom edge: the [Sheet] radius on the two top
     * corners only, so it reads as raised over the page rather than floating above
     * it (the lookup panel, `docs/design.md` §7.3).
     */
    val SheetTop = RoundedCornerShape(topStart = SheetRadius, topEnd = SheetRadius)
}
