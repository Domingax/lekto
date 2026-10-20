package app.lekto.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/**
 * The app's one theme: the three papers of `docs/design.md` §2.1, the two voices
 * of §3 and the radii of §4, applied together so a screen cannot inherit
 * Material 3's purple baseline by accident.
 *
 * [theme] is a [ColorScheme] rather than a preference: choosing between Light,
 * Dark and Sepia — and resolving **System** — is the **App theme**'s job
 * (ADR-0036; issue #182), and this composable only paints the chosen one. It
 * defaults to [Light] so the app has a paper before that choice exists.
 *
 * A [Surface] fills the content with the paper and sets the content colour, so
 * interface text is ink on paper in every theme. Shadows are not part of the
 * theme: a card or panel separates by its surface tone and a hairline instead.
 */
@Composable
fun LektoTheme(theme: ColorScheme = Light, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = theme,
        typography = LektoTypography,
        shapes = LektoShapes,
    ) {
        Surface(color = theme.background, contentColor = theme.onBackground) {
            content()
        }
    }
}
