package app.lekto.library

/**
 * Desktop has a pointer, not a thumb: the row's delete appears on hover instead,
 * so no drag is ever mistaken for a command (issue #208).
 */
internal actual val swipeToDelete: Boolean = false
