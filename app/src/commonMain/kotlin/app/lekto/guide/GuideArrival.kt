package app.lekto.guide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lekto.theme.LektoSpacing

/**
 * The guide's arrival (issue #277): the shelf it hands over to, with the background
 * download's line under it. The shelf keeps its own frame and spacing; the line sits
 * inside the same edges, with no gap of its own above it — the shelf's own bottom
 * panel is that gap — and one panel unit under it, so the two read as one screen
 * rather than as stacked blocks.
 *
 * [shelf] is the caller's **Library** (it needs the vault, the covers and the
 * shelf's actions); this frame only decides where the download line lives, so the
 * composition root and the layout suite compose the arrival the same way.
 */
@Composable
fun GuideArrival(
    download: GuideDownloadUiState,
    downloadActions: GuideDownloadActions = GuideDownloadActions(),
    modifier: Modifier = Modifier,
    shelf: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) { shelf() }
        if (download.phase != GuideDownloadPhase.IDLE) {
            GuideDownloadLine(
                state = download,
                actions = downloadActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = LektoSpacing.ScreenEdge,
                        end = LektoSpacing.ScreenEdge,
                        bottom = LektoSpacing.InsidePanel,
                    ),
            )
        }
    }
}
