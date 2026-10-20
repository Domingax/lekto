package app.lekto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.lekto.theme.Dark
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * One golden per **App theme**, recorded from the screenshot lane (ticket #7;
 * issue #181).
 *
 * Each sheet is text-free, so it does not depend on the fonts installed on the
 * machine that recorded it, and verifies the same on a developer's laptop and in
 * CI. The padding shows the theme's paper; the row shows the ink, the surface,
 * the muted ink, the hairline and the red. It fails when a theme colour moves,
 * which is what the lane exists to catch.
 *
 * Record or update a golden with `./gradlew :app:recordRoborazziDesktop`; verify
 * with `./gradlew :app:verifyRoborazziDesktop` — the task the golden CI job runs.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeGoldenTest {

    @Test
    fun lightThemeColours() = themeColours("light", Light)

    @Test
    fun darkThemeColours() = themeColours("dark", Dark)

    @Test
    fun sepiaThemeColours() = themeColours("sepia", Sepia)

    private fun themeColours(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent {
            LektoTheme(theme = theme) {
                Row(modifier = Modifier.size(width = 240.dp, height = 40.dp).padding(4.dp)) {
                    val scheme = MaterialTheme.colorScheme
                    val swatches = listOf(
                        scheme.primary,
                        scheme.surface,
                        scheme.onSurfaceVariant,
                        scheme.outline,
                        scheme.error,
                    )
                    swatches.forEach { colour ->
                        Box(modifier = Modifier.fillMaxHeight().weight(1f).background(colour))
                    }
                }
            }
        }

        onRoot().captureRoboImage("theme-colours-$name.png")
    }
}
