package app.lekto.ui

/**
 * Whether this client is a touch client — a thumb, not a pointer
 * (`docs/design.md` §7.1, §7.4, §8; issues #208, #212).
 *
 * Two idioms turn on it, and they turn on the same fact:
 *
 * - a list row's delete is a swipe from the trailing edge on a touch screen, and
 *   a trash that appears in the row's hover wash under a pointer;
 * - a row of filter chips is one line that scrolls on a touch screen, and a
 *   wrapping group under a pointer, because a mouse cannot drag a row of buttons
 *   and a scrollbar under six chips is a control nobody wants.
 *
 * It is a seam rather than a direct check because only the platform entry point
 * knows which client it is: a dragged row or a hidden chip on desktop is an
 * accident, not a command.
 */
internal expect val touchClient: Boolean
