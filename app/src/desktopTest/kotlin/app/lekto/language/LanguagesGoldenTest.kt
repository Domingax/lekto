@file:Suppress("MagicNumber") // The preview lays out the screen's frame in design tokens and fixed heights.

package app.lekto.language

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.theme.Dark
import app.lekto.theme.LektoSpacing
import app.lekto.theme.LektoTheme
import app.lekto.theme.Light
import app.lekto.theme.Sepia
import app.lekto.ui.HAIRLINE_THICKNESS
import app.lekto.ui.icons.ArrowLeft
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * The **Languages** surface's visual anatomy in the screenshot lane, one sheet
 * per **App theme** (issue #215; docs/design.md §7.5, §7.6).
 *
 * It is deliberately text-free, like the other goldens: glyphs are rendered with
 * the host's fonts and would not verify on another machine. Instead it records
 * the frame the identity gives the screen — the back arrow and serif title with
 * **Reload list** at the edge, the short description, the **Translation
 * language** row in the printed-form grammar, then one row per language: its
 * name and its **Reader level** picker on the first line, and each artifact's
 * state in the muted ink with its download at the trailing edge. The three rows
 * the semantics suites drive are here: one ready, one missing both artifacts and
 * one whose dictionary is corrupt. A hairline parts two languages, the
 * attribution sits at the foot in the muted ink, and no download is a filled
 * full-width button. A spacing step, a divider or a chevron that moves is caught
 * here; the words are the semantics suites' business.
 *
 * Record with `./gradlew :app:recordRoborazziDesktop`; verify with
 * `./gradlew :app:verifyRoborazziDesktop`.
 */
@OptIn(ExperimentalTestApi::class)
class LanguagesGoldenTest {

    @Test
    fun lightLanguages() = languagesGolden("light", Light)

    @Test
    fun darkLanguages() = languagesGolden("dark", Dark)

    @Test
    fun sepiaLanguages() = languagesGolden("sepia", Sepia)

    private fun languagesGolden(name: String, theme: ColorScheme) = runComposeUiTest {
        setContent { LektoTheme(theme = theme) { LanguagesPreview() } }
        onRoot().captureRoboImage("languages-$name.png")
    }
}

/** The phone frame the planche draws every screen at. */
private val SCREEN_WIDTH = 360.dp
private val SCREEN_HEIGHT = 640.dp

/** A bar where a line of text would be: its height and the width it takes. */
private data class Bar(val height: Dp, val width: Dp)

/** The four wrapped lines the screen's short explanation takes at this width. */
private val DESCRIPTION_LINES = listOf(300.dp, 288.dp, 268.dp, 176.dp)

/** One artifact line the preview draws: its pair label's bar, its state and its action. */
private data class PreviewArtifact(val label: Dp?, val status: Dp, val action: Dp?)

/** One language row the preview draws: its name's bar and its artifact lines. */
private data class PreviewLanguage(val name: Dp, val artifacts: List<PreviewArtifact>)

private val PREVIEW_LANGUAGES = listOf(
    PreviewLanguage(
        name = 58.dp,
        artifacts = listOf(
            PreviewArtifact(label = null, status = 172.dp, action = 66.dp),
            PreviewArtifact(label = 84.dp, status = 150.dp, action = 66.dp),
        ),
    ),
    PreviewLanguage(
        name = 62.dp,
        artifacts = listOf(
            PreviewArtifact(label = null, status = 160.dp, action = 48.dp),
            PreviewArtifact(label = 90.dp, status = 160.dp, action = 48.dp),
        ),
    ),
    PreviewLanguage(
        name = 58.dp,
        artifacts = listOf(
            PreviewArtifact(label = null, status = 172.dp, action = 66.dp),
            PreviewArtifact(label = 84.dp, status = 166.dp, action = 66.dp),
        ),
    ),
)

/** The screen's frame: its header, description, translation row, language rows and attribution foot. */
@Composable
private fun LanguagesPreview() {
    Column(
        modifier = Modifier
            .width(SCREEN_WIDTH)
            .height(SCREEN_HEIGHT)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
    ) {
        HeaderPreview()
        Column(Modifier.padding(top = LektoSpacing.ScreenEdge)) {
            TranslationRowPreview()
            LanguagesSectionPreview()
            PREVIEW_LANGUAGES.forEachIndexed { index, language ->
                LanguageRowPreview(language, last = index == PREVIEW_LANGUAGES.lastIndex)
            }
        }
        Line(
            colour = MaterialTheme.colorScheme.onSurfaceVariant,
            bar = Bar(height = 12.dp, width = 66.dp),
            modifier = Modifier.padding(top = LektoSpacing.BetweenRows),
        )
    }
}

/** The header: the back arrow, the serif title, and **Reload list** at the trailing edge. */
@Composable
private fun HeaderPreview() {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Icon(
            imageVector = LektoIcons.ArrowLeft,
            contentDescription = null,
            tint = theme.onBackground,
            modifier = Modifier.size(24.dp),
        )
        Line(theme.onBackground, Bar(height = 22.dp, width = 110.dp), Modifier.weight(1f))
        Line(theme.onBackground, Bar(height = 12.dp, width = 62.dp))
    }
}

/** The second section: its small muted label and the explanation of the two derived assets. */
@Composable
private fun LanguagesSectionPreview() {
    val theme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.padding(top = LektoSpacing.BetweenBlocks),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Line(theme.onSurfaceVariant, Bar(height = 10.dp, width = 54.dp))
        DESCRIPTION_LINES.forEach { width -> Line(theme.onSurfaceVariant, Bar(height = 11.dp, width = width)) }
    }
}

/** The **Translation language** row: its small label, its value, the chevron and the hairline. */
@Composable
private fun TranslationRowPreview() {
    val theme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Line(theme.onSurfaceVariant, Bar(height = 10.dp, width = 74.dp))
        Spacer(Modifier.height(LektoSpacing.InsideLabelPair))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Line(theme.onBackground, Bar(height = 14.dp, width = 72.dp))
            Spacer(Modifier.weight(1f))
            Chevron(rotated = false)
        }
        Spacer(Modifier.height(2.dp))
        Hairline()
    }
}

/** One language: name and level picker, its derived asset lines, then the hairline that parts it from the next. */
@Composable
private fun LanguageRowPreview(language: PreviewLanguage, last: Boolean) {
    val theme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = LektoSpacing.BetweenRows),
            verticalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
            ) {
                Line(theme.onBackground, Bar(height = 15.dp, width = language.name), Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
                ) {
                    Line(theme.onSurfaceVariant, Bar(height = 9.dp, width = 22.dp))
                    Line(theme.onBackground, Bar(height = 12.dp, width = 22.dp))
                    Chevron(rotated = true)
                }
            }
            language.artifacts.forEach { artifact -> ArtifactPreview(artifact) }
        }
        if (!last) Hairline()
    }
}

/** One artifact: its state in the muted ink and its download at the trailing edge. */
@Composable
private fun ArtifactPreview(artifact: PreviewArtifact) {
    val theme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LektoSpacing.InsideLabelPair),
        ) {
            artifact.label?.let { width ->
                Line(theme.onSurfaceVariant, Bar(height = 10.dp, width = width))
                Line(theme.onSurfaceVariant, Bar(height = 10.dp, width = 3.dp))
            }
            Line(theme.onSurfaceVariant, Bar(height = 10.dp, width = artifact.status))
        }
        artifact.action?.let { width -> Line(theme.onBackground, Bar(height = 10.dp, width = width)) }
    }
}

/** The chevron a picker opens with: the row's `chevron-right`, or its quarter turn for a dropdown. */
@Composable
private fun Chevron(rotated: Boolean) {
    Icon(
        imageVector = LektoIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(CHEVRON_SIZE).then(if (rotated) Modifier.rotate(90f) else Modifier),
    )
}

/** The hairline that parts two languages, never after the last: the attribution follows it. */
@Composable
private fun Hairline() {
    Spacer(
        Modifier.fillMaxWidth().height(HAIRLINE_THICKNESS).background(MaterialTheme.colorScheme.outline),
    )
}

/** A bar where a line of text would be. */
@Composable
private fun Line(colour: Color, bar: Bar, modifier: Modifier = Modifier) {
    Column(modifier) {
        Spacer(Modifier.width(bar.width).height(bar.height).background(colour))
    }
}

/** The chevron's frame, the size the screen draws it at. */
private val CHEVRON_SIZE = 16.dp
