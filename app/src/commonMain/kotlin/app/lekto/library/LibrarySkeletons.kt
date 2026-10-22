package app.lekto.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoRadii
import app.lekto.theme.LektoSpacing

/** The test tag on the rows a loading shelf shows instead of the empty message (issue #208). */
internal const val LIBRARY_SKELETONS_TAG: String = "library-skeletons"

/** How many skeleton rows a loading shelf shows: a shelf's worth, not its inventory (issue #208). */
internal const val SKELETON_ROW_COUNT: Int = 4

/** The skeleton's two bars: a title's share of the width and a second line's, and their heights. */
private const val SKELETON_TITLE_SHARE = 0.7f
private const val SKELETON_LINE_SHARE = 0.4f
private val TITLE_BAR_HEIGHT = 16.dp
private val LINE_BAR_HEIGHT = 12.dp

/**
 * The shelf while it is being read (docs/design.md §7.1, §8; issue #208):
 * skeleton rows in place of both the list and the empty message. A screen shown
 * for 40 ms is a flicker, so the wait has a shape rather than a flash of "No
 * books yet".
 *
 * The bars carry no text, so the column names itself once for a screen reader
 * instead of reading four silent rows.
 */
@Composable
internal fun LibrarySkeletons() {
    val texts = LocalAppTexts.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LektoSpacing.BetweenRelatedLines)
            .testTag(LIBRARY_SKELETONS_TAG)
            .semantics { contentDescription = texts[TextKey.LIBRARY_LOADING] },
        verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRows),
    ) {
        repeat(SKELETON_ROW_COUNT) { SkeletonRow() }
    }
}

/** One skeleton row: the vignette's frame and the two lines a real row would carry. */
@Composable
private fun SkeletonRow() {
    val skeleton = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = LektoSpacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(LANGUAGE_VIGNETTE_WIDTH)
                .height(LANGUAGE_VIGNETTE_HEIGHT)
                .background(skeleton, LektoRadii.Control),
        )
        Spacer(Modifier.width(LektoSpacing.BetweenRows))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LektoSpacing.BetweenRelatedLines)) {
            SkeletonBar(SKELETON_TITLE_SHARE, TITLE_BAR_HEIGHT)
            SkeletonBar(SKELETON_LINE_SHARE, LINE_BAR_HEIGHT)
        }
    }
}

/** One skeleton bar: [share] of the column wide, [height] tall, in the theme's separator tone. */
@Composable
private fun SkeletonBar(share: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(share)
            .height(height)
            .background(MaterialTheme.colorScheme.outlineVariant, LektoRadii.Control),
    )
}
