package app.lekto

import android.content.res.Configuration
import androidx.annotation.StyleRes
import app.lekto.core.theme.AppTheme

/**
 * The Android window theme an **App theme** asks for (ADR-0036): the one whose
 * `windowBackground` is the active paper, so the window Android paints before the
 * first Compose frame is the theme's background rather than a white flash.
 *
 * [configuration] is the device's own configuration, passed rather than read here
 * so the resolution is a pure function a host test can drive with both answers.
 * **System** follows the platform's night bit; a chosen paper ignores it.
 */
@StyleRes
fun androidWindowTheme(theme: AppTheme, configuration: Configuration): Int {
    val systemInDark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    return when (theme.resolve(systemInDark)) {
        AppTheme.LIGHT -> R.style.Theme_Lekto_Light

        AppTheme.DARK -> R.style.Theme_Lekto_Dark

        AppTheme.SEPIA -> R.style.Theme_Lekto_Sepia

        // resolve() never hands System back: it is the platform's to resolve.
        AppTheme.SYSTEM -> R.style.Theme_Lekto
    }
}
