package app.lekto.ui

/**
 * Desktop has a pointer, not a thumb: a row's delete appears on hover, and the
 * filter chips wrap instead of scrolling (issue #208, #212).
 */
internal actual val touchClient: Boolean = false
