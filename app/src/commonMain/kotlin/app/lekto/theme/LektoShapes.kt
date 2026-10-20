package app.lekto.theme

import androidx.compose.material3.Shapes

/**
 * Material 3's five shape slots, mapped onto the identity's three radii
 * (`docs/design.md` §4) so a component cannot silently take a radius the design
 * does not name. The roles themselves are [LektoRadii].
 */
val LektoShapes: Shapes = Shapes(
    extraSmall = LektoRadii.Control,
    small = LektoRadii.Control,
    medium = LektoRadii.Surface,
    large = LektoRadii.Sheet,
    extraLarge = LektoRadii.Sheet,
)
