package app.lekto.ui

/**
 * Desktop has a pointer, not a thumb: a row's delete appears on hover instead, so
 * no drag is ever mistaken for a command (issue #208, #212).
 */
internal actual val swipeToDelete: Boolean = false
