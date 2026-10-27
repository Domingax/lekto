@file:Suppress("MagicNumber") // The preview lays out a form's frame in design tokens and fixed heights.

package app.lekto.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.ui.HAIRLINE_THICKNESS
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The two settings forms' visual anatomy in the screenshot lane (issue #213;
 * docs/design.md §7.5, §7.6): **LLM provider** and **Vault and sync** as screens
 * of their own, their fields read as printed forms.
 *
 * It is deliberately text-free, like the other goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. Instead it records
 * the frame the screens give the reader — the back arrow and the serif title as
 * bars, each field's label, value and hairline, the one filled action per screen,
 * the outlined secondary actions, and the two irreversible acts in the reserved
 * red. The style comes from the screens' own tokens and the shared form
 * composables' values; only the words are bars, because glyphs are the one thing
 * a golden cannot pin. A spacing step, a hairline or a button shape that moves is
 * caught here — the semantics suites cover the text.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsFormsGoldenTest {

    @Test
    fun theTwoForms() = runComposeUiTest {
        setContent { LektoTheme(theme = Light) { FormsPreview() } }
        onRoot().captureRoboImage("settings-forms-light.png")
    }
}

/** The two screens side by side, the way the design's planche draws them. */
@Composable
private fun FormsPreview() {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(LektoSpacing.BetweenRows),
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
    ) {
        ScreenFrame { ProviderPreview() }
        ScreenFrame { VaultPreview() }
    }
}

/** One phone-sized screen: its paper, its header and the form below it. */
@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(SCREEN_WIDTH)
            .padding(LektoSpacing.ScreenEdge),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenBlocks),
    ) {
        HeaderBar()
        content()
    }
}

/** The back arrow, then the screen title in the reading serif, as bars. */
@Composable
private fun HeaderBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(LektoSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Box(Modifier.size(18.dp).background(MaterialTheme.colorScheme.onBackground, LektoRadii.Control))
        Bar(width = 0.45f, height = 22.dp)
    }
}

/** A printed-form field: its label, its value and the hairline under it. */
@Composable
private fun FieldPreview() {
    Column(Modifier.fillMaxWidth()) {
        Bar(width = 0.22f, height = LABEL_HEIGHT, muted = true)
        Box(Modifier.height(LektoSpacing.InsideLabelPair))
        Row(
            modifier = Modifier.fillMaxWidth().height(LektoSpacing.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Bar(width = 0.55f, height = VALUE_HEIGHT)
        }
        Box(Modifier.height(LektoSpacing.InsideLabelPair))
        Hairline()
    }
}

/** One row of the vault's action pair: both outlined, the way the screen draws them. */
@Composable
private fun OutlinedPairPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows)) {
        OutlinedBar(Modifier.weight(1f))
        OutlinedBar(Modifier.weight(1f))
    }
}

/** The one filled action: an ink rectangle at the control radius. */
@Composable
private fun FilledBar() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(BUTTON_HEIGHT)
            .background(MaterialTheme.colorScheme.primary, LektoRadii.Control),
    )
}

/** A secondary action: the paper behind it and a hairline frame. */
@Composable
private fun OutlinedBar(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(BUTTON_HEIGHT)
            .border(HAIRLINE_THICKNESS, MaterialTheme.colorScheme.outline, LektoRadii.Control),
    )
}

/** The irreversible act's red, as a short bar. */
@Composable
private fun RedBar() {
    Box(Modifier.height(LABEL_HEIGHT).width(72.dp).background(MaterialTheme.colorScheme.error))
}

/** The provider screen's frame: its preset, key and model rows, then its one filled action. */
@Composable
private fun ProviderPreview() {
    FieldPreview()
    FieldPreview()
    FieldPreview()
    Bar(width = 0.3f, height = LABEL_HEIGHT, muted = true)
    FilledBar()
    OutlinedBar(Modifier.fillMaxWidth())
    RedBar()
}

/** The vault screen's frame: export/import, then the sync form and its actions. */
@Composable
private fun VaultPreview() {
    OutlinedPairPreview()
    Bar(width = 0.16f, height = LABEL_HEIGHT, muted = true)
    FieldPreview()
    FieldPreview()
    FieldPreview()
    FilledBar()
    OutlinedBar(Modifier.fillMaxWidth())
    RedBar()
}

/** A bar where a line of text would be: its share of the width, its height and whether it is muted. */
@Composable
private fun Bar(width: Float, height: Dp, muted: Boolean = false) {
    val colour = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground
    Box(Modifier.fillMaxWidth(width).height(height).background(colour))
}

/** The hairline that closes one field and parts it from the next. */
@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(HAIRLINE_THICKNESS)
            .background(MaterialTheme.colorScheme.outline),
    )
}

/** The preview's phone width: the frame the design's planche gives each screen. */
private val SCREEN_WIDTH = 360.dp

/** The four heights that stand in for type and controls. */
private val LABEL_HEIGHT = 11.dp
private val VALUE_HEIGHT = 15.dp
private val BUTTON_HEIGHT = 44.dp
