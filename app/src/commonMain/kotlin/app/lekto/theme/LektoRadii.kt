package app.lekto.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The three radii of `docs/design.md` §4, as roles a screen can name.
 *
 * Controls take 8 dp, cards and panels 12 dp, sheets and dialogues 16 dp. A panel
 * anchored to a screen edge is square, so it is not one of these roles.
 */
object LektoRadii {
    /** Buttons, chips, fields and language vignettes. */
    val Control = RoundedCornerShape(8.dp)

    /** Cards, panels and sections. */
    val Surface = RoundedCornerShape(12.dp)

    /** Sheets and dialogues. */
    val Sheet = RoundedCornerShape(16.dp)
}
