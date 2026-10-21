package app.lekto.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * Android's system back gesture, walked through the app's own back stack (issue
 * #185). It is the platform's `BackHandler` over the activity's
 * `OnBackPressedDispatcher`, so a disabled handler in the composition (the stack
 * at its root) lets the activity finish as the platform expects.
 */
@Composable
internal actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
