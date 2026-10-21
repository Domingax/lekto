package app.lekto.theme

import androidx.compose.material3.ColorScheme
import app.lekto.core.theme.AppTheme

/**
 * The [ColorScheme] an **App theme** paints with, resolving **System** from the
 * platform's own report ([systemInDark]) (ADR-0036).
 *
 * This is the one place the choice becomes a scheme, so the app's painting and the
 * window's own background cannot disagree. A chosen paper ignores the platform;
 * [Light], [Dark] and [Sepia] are the identity's three (docs/design.md §2.1).
 */
fun appThemeScheme(theme: AppTheme, systemInDark: Boolean): ColorScheme = when (theme.resolve(systemInDark)) {
    AppTheme.LIGHT -> Light

    AppTheme.DARK -> Dark

    AppTheme.SEPIA -> Sepia

    // resolve() never hands System back: it is the platform's to resolve.
    AppTheme.SYSTEM -> Light
}
