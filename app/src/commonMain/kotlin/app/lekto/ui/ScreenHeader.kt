package app.lekto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.icons.ArrowLeft
import app.lekto.ui.icons.LektoIcons

/**
 * The header a screen one tap below a section shares (docs/design.md §7.5, §7.6):
 * the way back as the `arrow-left` icon, then the screen's title in the reading
 * serif. [trailing] carries a screen's own quiet action at the edge — the
 * **Languages** surface's **Reload list** — so the header stays one row.
 *
 * The header takes [LektoSpacing.BarInset], not the screen's [LektoSpacing.ScreenEdge]:
 * a bar's icon belongs near the window's edge, the way an app bar's does, while
 * the screen's text and rows keep the content margin. A screen therefore pads its
 * content, never its header (issue #245).
 *
 * The shell keeps the section current while the screen is open, and the icon and
 * the platform's Back both walk the one back stack.
 */
@Composable
@Suppress("LongParameterList") // The header's words, its two test tags and its way back are independent.
internal fun ScreenHeader(
    title: String,
    backTag: String,
    titleTag: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LektoSpacing.BarInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.handCursor().testTag(backTag),
        ) {
            Icon(
                imageVector = LektoIcons.ArrowLeft,
                contentDescription = LocalAppTexts.current[TextKey.BACK],
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f).testTag(titleTag),
        )
        trailing?.invoke()
    }
}
