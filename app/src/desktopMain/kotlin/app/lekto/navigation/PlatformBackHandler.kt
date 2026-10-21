package app.lekto.navigation

import androidx.compose.runtime.Composable

/**
 * Desktop has no system back gesture, so the shell's sections and each screen's
 * own Back action are the way out (issue #185). This actual deliberately does
 * nothing: binding Escape here would take the key from the platform's own
 * dialogs, and the desktop client has no system back to walk.
 */
@Composable
@Suppress("UNUSED_PARAMETER") // The seam's parameters; the desktop has nothing to bind them to.
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
