package app.lekto.library

/**
 * Whether a row is swiped to reach its delete action (docs/design.md §7.1; issue
 * #208).
 *
 * The swipe is the touch idiom: a thumb on a row that has no visible control.
 * It is a seam rather than a direct check because the gesture is wrong where a
 * pointer clicks — a dragged row on desktop is an accident, not a command — and
 * only the platform entry point knows which it is. On desktop the trash appears
 * when the row is hovered instead.
 */
internal expect val swipeToDelete: Boolean
