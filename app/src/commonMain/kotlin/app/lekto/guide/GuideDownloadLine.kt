package app.lekto.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.language.artifactSizeText
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.ui.TextAction

/** The test tag on the guide's download line, so a UI test can read it apart from the page. */
internal const val GUIDE_DOWNLOAD_TAG: String = "guide-download"

/** The test tag on the line's `Stop`, so a UI test can stop exactly the download. */
internal const val GUIDE_DOWNLOAD_STOP_TAG: String = "guide-download-stop"

/**
 * The background download in one line (issue #277): what it fetches, its published
 * size while it runs, and the `Stop` that cancels it. It appears on the guide's
 * page screens and on the shelf after the arrival, so the reader can always tell
 * whether the table and dictionary are on their way, installed or stopped.
 *
 * [onStop] is `null` where stopping is not the surface's act — the shelf still
 * offers it, because the line is the same fact wherever it is read.
 */
@Composable
internal fun GuideDownloadLine(state: GuideDownloadUiState, onStop: (() -> Unit)?, modifier: Modifier = Modifier) {
    if (state.phase == GuideDownloadPhase.IDLE) return
    val texts = LocalAppTexts.current
    Row(
        modifier.fillMaxWidth().testTag(GUIDE_DOWNLOAD_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title(state, texts.languageName(state.language.orEmpty())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = status(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.isRunning && onStop != null) {
            TextAction(onClick = onStop, modifier = Modifier.testTag(GUIDE_DOWNLOAD_STOP_TAG)) {
                Text(texts[TextKey.GUIDE_DOWNLOAD_STOP], style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The line's first half: the assets, naming the language when one is chosen. */
@Composable
private fun title(state: GuideDownloadUiState, languageName: String): String {
    val texts = LocalAppTexts.current
    return if (state.language != null) {
        texts.message(AppMessage.of(TextKey.GUIDE_DOWNLOAD_ASSETS, languageName))
    } else {
        texts[TextKey.GUIDE_DOWNLOAD_TITLE]
    }
}

/** The line's second half: where the download stands, in the reader's language. */
@Composable
private fun status(state: GuideDownloadUiState): String {
    val texts = LocalAppTexts.current
    return when (state.phase) {
        GuideDownloadPhase.PREPARING -> texts[TextKey.GUIDE_DOWNLOAD_PREPARING]

        GuideDownloadPhase.DOWNLOADING -> state.sizeBytes?.let { bytes ->
            texts.message(AppMessage.of(TextKey.GUIDE_DOWNLOAD_BACKGROUND, artifactSizeText(texts, bytes)))
        } ?: texts[TextKey.GUIDE_DOWNLOAD_PREPARING]

        GuideDownloadPhase.INSTALLED -> texts[TextKey.GUIDE_DOWNLOAD_INSTALLED]

        GuideDownloadPhase.STOPPED -> texts[TextKey.GUIDE_DOWNLOAD_STOPPED]

        GuideDownloadPhase.FAILED -> texts[TextKey.GUIDE_DOWNLOAD_FAILED]

        GuideDownloadPhase.UNAVAILABLE -> texts[TextKey.GUIDE_DOWNLOAD_UNAVAILABLE]

        GuideDownloadPhase.IDLE -> ""
    }
}
