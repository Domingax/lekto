package app.lekto.navigation

import androidx.compose.runtime.Composable

/**
 * The platform's own way back, wired to the app's [BackStack] (issue #185;
 * docs/design.md §6).
 *
 * It is a seam rather than a direct `BackHandler` call because only Android has a
 * system back gesture: there, the enablement decides whether the app walks its
 * stack or the system leaves the app, which is the defect this fixes — back used
 * to leave from anywhere. Desktop has no system back, so its actual does nothing
 * and a screen's own Back action is the way out.
 *
 * [enabled] is false at the stack's root, so the platform handles Back itself
 * (on Android that is the app finishing) instead of the app swallowing it.
 */
@Composable
internal expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
