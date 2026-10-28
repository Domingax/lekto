package app.lekto.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import app.lekto.APP_VERSION
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor

/** The test tag on the way back to Settings, so a UI test can click exactly it. */
internal const val ABOUT_BACK_TAG: String = "about-back"

/** The test tag on the screen's title, so a test can tell the screen from the shell's bar. */
internal const val ABOUT_TITLE_TAG: String = "about-title"

/** The test tag on the version line, so a UI test can read exactly it. */
internal const val ABOUT_VERSION_TAG: String = "about-version"

/** The test tag on the way to the source, so a UI test can click exactly it. */
internal const val ABOUT_SOURCE_TAG: String = "about-source"

/** The project's source, the one link the About screen offers (issue #214). */
const val LEKTO_SOURCE_URL: String = "https://github.com/Domingax/lekto"

/** The actions the **About** screen raises: open the source, or go back to Settings. */
data class AboutActions(val onOpenSource: () -> Unit = {}, val onBack: () -> Unit = {})

/**
 * The **About** screen (issue #214): the version an installed copy reports, the
 * licence it is released under (ADR-0011) and the link to its source.
 *
 * It exists so the Settings index has somewhere to point for **About**, the same
 * way the two secret forms and the Appearance screen do (docs/design.md §7.5).
 * The version is passed in, defaulting to [APP_VERSION], so a test can pin the
 * row and the screen without depending on the build's number.
 */
@Composable
fun AboutScreen(actions: AboutActions = AboutActions(), modifier: Modifier = Modifier, version: String = APP_VERSION) {
    val texts = LocalAppTexts.current
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = LektoSpacing.ScreenEdge, vertical = LektoSpacing.InsidePanel),
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        SettingsFormHeader(
            title = texts[TextKey.ABOUT],
            backTag = ABOUT_BACK_TAG,
            titleTag = ABOUT_TITLE_TAG,
            onBack = actions.onBack,
        )
        Text(
            texts.message(AppMessage.of(TextKey.ABOUT_VERSION, version)),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag(ABOUT_VERSION_TAG),
        )
        Text(
            texts[TextKey.ABOUT_LICENCE],
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(
            onClick = actions.onOpenSource,
            modifier = Modifier.handCursor().testTag(ABOUT_SOURCE_TAG),
        ) {
            Text(texts[TextKey.ABOUT_SOURCE])
        }
    }
}
