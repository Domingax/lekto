@file:Suppress("MagicNumber") // The preview lays out the sheet's fixed bands and bars as a planche does.

package app.lekto.translation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.reader.selectionHighlight
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.ui.LookupSheet
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.icons.X
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The phrase lookup sheet in the screenshot lane (issue #211; docs/design.md
 * §7.3): the connected state with a translation and its disclosure, and the
 * unconnected state with the prompt, the settings link and the zero-configuration
 * **Translation shortcut**.
 *
 * It is deliberately text-free, like the other goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. The preview raises
 * the real [LookupSheet] shell — square at the bottom edge, the surface tone and
 * the inset hairline, the drag handle — and draws the phrase body's geometry in
 * bars: the phrase in the serif band with the close icon, then either a
 * translation with its disclosure or the prompt with its settings link, above
 * the pinned shortcut. The semantics suites prove the text, the scroll and the
 * interactions.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class PhraseLookupGoldenTest {

    @Test
    fun connectedSheet() = runComposeUiTest {
        setContent { LektoTheme(theme = Light) { PhraseLookupPreview(connected = true) } }
        onRoot().captureRoboImage("phrase-lookup-connected-light.png")
    }

    @Test
    fun unconnectedSheet() = runComposeUiTest {
        setContent { LektoTheme(theme = Light) { PhraseLookupPreview(connected = false) } }
        onRoot().captureRoboImage("phrase-lookup-unconnected-light.png")
    }
}

/** The preview's window, the size the planche draws a phone at. */
private val SHEET_WIDTH = 360.dp
private val SHEET_HEIGHT = 640.dp

/** A bar where a line of text would be: its height and the share of the width it takes. */
private data class LineBar(val height: Dp, val share: Float)

private val PHRASE_BAR = LineBar(height = 24.dp, share = 0.44f)
private val BODY_BAR = LineBar(height = 10.dp, share = 0.82f)
private val SHORT_BODY_BAR = LineBar(height = 10.dp, share = 0.6f)
private val LABEL_BAR = LineBar(height = 8.dp, share = 0.14f)
private val LINK_BAR = LineBar(height = 10.dp, share = 0.24f)
private val SHORTCUT_BAR = LineBar(height = 10.dp, share = 0.28f)

/**
 * The page's lines: twenty, so the washed phrase lands just above the sheet, and
 * the first one is clipped at the top — the reader has lifted the page under the
 * sheet, not repaginated it.
 */
private val PAGE_LINE_SHARES = listOf(
    0.94f, 0.9f, 0.52f, 0.96f, 0.88f, 0.94f, 0.62f, 0.9f, 0.93f, 0.48f,
    0.95f, 0.9f, 0.94f, 0.88f, 0.6f, 0.96f, 0.9f, 0.93f, 0.86f, 0.72f,
)

/** How far the page is lifted in the preview, so its first line is clipped. */
private val PAGE_LIFT = 24.dp

/** The page, the washed phrase and the phrase sheet raised over it on the shared shell. */
@Composable
private fun PhraseLookupPreview(connected: Boolean) {
    Box(Modifier.size(width = SHEET_WIDTH, height = SHEET_HEIGHT).background(MaterialTheme.colorScheme.background)) {
        PageBehind()
        LookupSheet(modifier = Modifier.matchParentSize()) {
            Column(Modifier.fillMaxWidth()) {
                PhraseHeader()
                PreviewDivider()
                if (connected) {
                    TranslationBody()
                } else {
                    PromptBody()
                }
                PreviewDivider()
                ShortcutBody()
            }
        }
    }
}

/** The page above the sheet: the reading margin, the line rhythm and one washed phrase. */
@Composable
private fun PageBehind() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .padding(
                horizontal = LektoSpacing.ScreenEdge,
                vertical = LektoSpacing.InsidePanel,
            )
            .offset(y = -PAGE_LIFT),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        PAGE_LINE_SHARES.forEachIndexed { index, share ->
            if (index == PAGE_LINE_SHARES.lastIndex) {
                WashedPhrase(share)
            } else {
                Line(theme.onBackground, LineBar(height = 10.dp, share = share))
            }
        }
    }
}

/** The selected phrase: wider than a word, inside the ink wash that keeps it found on the page. */
@Composable
private fun WashedPhrase(share: Float) {
    val theme = MaterialTheme.colorScheme
    Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Line(theme.onBackground, LineBar(height = 10.dp, share = share))
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.width(122.dp).height(18.dp).background(selectionHighlight(theme.onBackground), LektoRadii.Control),
        ) {
            Box(Modifier.padding(3.dp).fillMaxWidth().height(10.dp).background(theme.onBackground, LektoRadii.Control))
        }
    }
}

/** The phrase in the serif band, with the close icon in its 48 dp control. */
@Composable
private fun PhraseHeader() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = LektoSpacing.InsidePanel,
            end = LektoSpacing.InsideLabelPair,
            top = LektoSpacing.InsideLabelPair,
        ),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.weight(1f).padding(bottom = LektoSpacing.BetweenRelatedLines)) {
            Line(theme.onBackground, PHRASE_BAR)
        }
        BandIcon(imageVector = LektoIcons.X, tint = theme.onBackground)
    }
}

/** An icon in its 48 dp control, centred as the sheet's icon buttons centre theirs. */
@Composable
private fun BandIcon(imageVector: ImageVector, tint: Color) {
    Box(Modifier.size(LektoSpacing.TouchTarget), contentAlignment = Alignment.Center) {
        Icon(imageVector = imageVector, contentDescription = null, tint = tint)
    }
}

/** A translation in the body band, then the disclosure control under it. */
@Composable
private fun TranslationBody() {
    val theme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(
            horizontal = LektoSpacing.InsidePanel,
            vertical = LektoSpacing.BetweenRows,
        ),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Line(theme.onBackground, BODY_BAR)
        Line(theme.onBackground, SHORT_BODY_BAR)
        Line(theme.onSurfaceVariant, LABEL_BAR)
    }
}

/** The prompt that a phrase needs a provider, its settings link, then the shortcut band. */
@Composable
private fun PromptBody() {
    val theme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(
            horizontal = LektoSpacing.InsidePanel,
            vertical = LektoSpacing.BetweenRows,
        ),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Line(theme.onBackground, BODY_BAR)
        Line(theme.onSurfaceVariant, LABEL_BAR)
        Line(theme.onSurfaceVariant, LINK_BAR)
    }
}

/** The zero-configuration shortcut: its label, then the service's name. */
@Composable
private fun ShortcutBody() {
    val theme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(
            horizontal = LektoSpacing.InsidePanel,
            vertical = LektoSpacing.BetweenRows,
        ),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
    ) {
        Line(theme.onSurfaceVariant, LABEL_BAR)
        Line(theme.onBackground, SHORTCUT_BAR)
    }
}

/** A hairline between two blocks, inset to the content as the panel draws it. */
@Composable
private fun PreviewDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = LektoSpacing.InsidePanel),
        color = MaterialTheme.colorScheme.outline,
    )
}

/** A bar where a line of text would be. */
@Composable
private fun Line(colour: Color, bar: LineBar, modifier: Modifier = Modifier) {
    Box((modifier.then(Modifier.fillMaxWidth(bar.share))).height(bar.height).background(colour, LektoRadii.Control))
}
