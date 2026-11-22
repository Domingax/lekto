package app.lekto.guide

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.language.artifactSizeText
import app.lekto.text.AppMessage
import app.lekto.text.AppTexts
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.TextAction

/** The test tag on the guide's download line, so a UI test can read it apart from the page. */
internal const val GUIDE_DOWNLOAD_TAG: String = "guide-download"

/** The test tag on the line's `Stop`, so a UI test can stop exactly the download. */
internal const val GUIDE_DOWNLOAD_STOP_TAG: String = "guide-download-stop"

/** The test tag on the line's `Retry`, so a UI test can retry a failed download. */
internal const val GUIDE_DOWNLOAD_RETRY_TAG: String = "guide-download-retry"

/** The test tag on the line's `Resume`, so a UI test can resume a stopped download. */
internal const val GUIDE_DOWNLOAD_RESUME_TAG: String = "guide-download-resume"

/**
 * What the line's phase can offer the reader: `Stop` while it runs, `Retry` when
 * the manifest or an artefact failed, `Resume` when the reader stopped it. A
 * `null` is a surface that does not act on that phase; the line still tells the
 * truth about where the download stands.
 */
data class GuideDownloadActions(
    val onStop: (() -> Unit)? = null,
    val onRetry: (() -> Unit)? = null,
    val onResume: (() -> Unit)? = null,
)

/**
 * The background download in one block (issue #277): what it fetches, its
 * published size while it runs, and the action its phase offers — `Stop` beside
 * the running line, `Retry` or `Resume` on its own row under the message. It
 * appears on the language question and on the shelf after the arrival, so the
 * reader can always tell whether the table and dictionary are on their way,
 * installed, stopped or failed, and can act on the answer.
 *
 * The failure and stopped actions are separate rows, never a word glued to the
 * end of the sentence: the eye must not read `…votreRéessayer`.
 */
@Composable
internal fun GuideDownloadLine(
    state: GuideDownloadUiState,
    actions: GuideDownloadActions = GuideDownloadActions(),
    modifier: Modifier = Modifier,
) {
    if (state.phase == GuideDownloadPhase.IDLE) return
    val texts = LocalAppTexts.current
    Column(modifier.fillMaxWidth().testTag(GUIDE_DOWNLOAD_TAG)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            if (state.isRunning && actions.onStop != null) {
                DownloadAction(
                    text = texts[TextKey.GUIDE_DOWNLOAD_STOP],
                    tag = GUIDE_DOWNLOAD_STOP_TAG,
                    onClick = actions.onStop,
                )
            }
        }
        followUpAction(state, actions, texts)?.let { action ->
            DownloadAction(
                text = action.text,
                tag = action.tag,
                onClick = action.onClick,
                modifier = Modifier.padding(top = LektoSpacing.BetweenRelatedLines),
            )
        }
    }
}

/** One action on its own row: the retry a failure offers, or the resume a stop offers. */
private class FollowUpAction(val text: String, val tag: String, val onClick: () -> Unit)

/** The action that stands under the message, when the phase and the surface offer one. */
private fun followUpAction(
    state: GuideDownloadUiState,
    actions: GuideDownloadActions,
    texts: AppTexts,
): FollowUpAction? = when {
    state.isFailed() && actions.onRetry != null -> FollowUpAction(
        text = texts[TextKey.GUIDE_DOWNLOAD_RETRY],
        tag = GUIDE_DOWNLOAD_RETRY_TAG,
        onClick = actions.onRetry,
    )

    state.phase == GuideDownloadPhase.STOPPED && actions.onResume != null -> FollowUpAction(
        text = texts[TextKey.GUIDE_DOWNLOAD_RESUME],
        tag = GUIDE_DOWNLOAD_RESUME_TAG,
        onClick = actions.onResume,
    )

    else -> null
}

/** Whether the line's phase is one the reader can retry: an unreachable manifest or a failed artefact. */
private fun GuideDownloadUiState.isFailed(): Boolean =
    phase == GuideDownloadPhase.UNAVAILABLE || phase == GuideDownloadPhase.FAILED

/** One action at the line's trailing edge, or under its message. */
@Composable
private fun DownloadAction(text: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextAction(onClick = onClick, modifier = modifier.testTag(tag)) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
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
        GuideDownloadPhase.DOWNLOADING -> downloadingStatus(state, texts)
        GuideDownloadPhase.INSTALLED -> texts[TextKey.GUIDE_DOWNLOAD_INSTALLED]
        GuideDownloadPhase.STOPPED -> texts[TextKey.GUIDE_DOWNLOAD_STOPPED]
        GuideDownloadPhase.FAILED -> texts[TextKey.GUIDE_DOWNLOAD_FAILED]
        GuideDownloadPhase.UNAVAILABLE -> texts[TextKey.GUIDE_DOWNLOAD_UNAVAILABLE]
        GuideDownloadPhase.REFUSED -> texts[TextKey.GUIDE_DOWNLOAD_REFUSED]
        GuideDownloadPhase.LANGUAGE_NOT_OFFERED -> texts[TextKey.GUIDE_DOWNLOAD_LANGUAGE_NOT_OFFERED]
        GuideDownloadPhase.IDLE -> ""
    }
}

/** The running line's size, or the preparation line when the manifest names no size. */
@Composable
private fun downloadingStatus(state: GuideDownloadUiState, texts: AppTexts): String = state.sizeBytes?.let { bytes ->
    texts.message(AppMessage.of(TextKey.GUIDE_DOWNLOAD_BACKGROUND, artifactSizeText(texts, bytes)))
} ?: texts[TextKey.GUIDE_DOWNLOAD_PREPARING]
