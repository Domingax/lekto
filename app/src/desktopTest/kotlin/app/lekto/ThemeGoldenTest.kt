package app.lekto

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The first golden in the screenshot lane (ticket #7).
 *
 * It records the Material colour scheme as a row of swatches — deliberately
 * text-free, so the image does not depend on the fonts installed on the machine
 * that recorded it and verify the same on a developer's laptop and CI. It fails
 * when a theme colour changes, which is what the lane exists to catch. The reader
 * adds text-bearing goldens of its own once its typography is pinned.
 *
 * Record or update a golden with `./gradlew :app:recordRoborazziDesktop`; verify
 * with `./gradlew :app:verifyRoborazziDesktop` — the task the golden CI job runs.
 */
@OptIn(ExperimentalTestApi::class)
class ThemeGoldenTest {

    @Test
    fun themeColours() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Row(modifier = Modifier.size(width = 240.dp, height = 40.dp)) {
                    val swatches = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.error,
                        MaterialTheme.colorScheme.surfaceVariant,
                    )
                    swatches.forEach { colour ->
                        Box(modifier = Modifier.fillMaxHeight().weight(1f).background(colour))
                    }
                }
            }
        }

        onRoot().captureRoboImage("theme-colours.png")
    }
}
