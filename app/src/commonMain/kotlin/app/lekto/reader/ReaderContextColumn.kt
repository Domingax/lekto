package app.lekto.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lekto.theme.WIDE_COLUMN_WIDTH

/** The test tag on the reserved column, so a UI test can tell it is there in both states. */
internal const val READER_CONTEXT_COLUMN_TAG = "reader-context-column"

/**
 * What the reader shows beside the page when the context column is reserved
 * (issue #199; ADR-0037; docs/design.md §6, §7.2/§7.3): the chapter list when
 * nothing is touched, and the open lookup panel when [isLookupOpen] says a word or
 * phrase is selected.
 *
 * The panel is the caller's, because a word or a phrase is the composition root's
 * business; only its **placement** is the reader's. [panel] receives the modifier
 * to apply and whether it is drawn `inColumn`, so the same panel can fill the
 * column on desktop and be raised from the bottom edge on a phone.
 */
class ReaderContext(
    /** Whether a word or phrase lookup is open, so the column shows [panel] instead of the chapters. */
    val isLookupOpen: Boolean = false,
    /**
     * The lookup to show: in the column at 1100 dp and above, over the page below
     * it. A plain class, not a data class: a composable lambda has no value
     * equality, so a `copy` would compare two different lambdas as different.
     */
    val panel: @Composable (modifier: Modifier, inColumn: Boolean) -> Unit = { _, _ -> },
)

/**
 * The reserved right-hand column (docs/design.md §6): a surface beside the page,
 * parted from it by a hairline rule. It is always there at the breakpoint, in both
 * states, which is what keeps the page still when a lookup opens or closes.
 *
 * Idle, it is the chapter list — the same [ChapterList] the phone's sheet shows —
 * so a chapter's title, its place in the hierarchy and a jump behave identically
 * whichever shape the reader is using. A book with one chapter (or a text file)
 * has no list, and the column says so rather than standing empty.
 */
@Composable
internal fun ReaderContextColumn(
    entries: List<ReaderChapterEntry>,
    current: ReaderChapterEntry?,
    onChoose: (ReaderChapterEntry) -> Unit,
    context: ReaderContext,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .width(WIDE_COLUMN_WIDTH)
            .background(MaterialTheme.colorScheme.surface)
            .testTag(READER_CONTEXT_COLUMN_TAG),
    ) {
        VerticalDivider(color = MaterialTheme.colorScheme.outline)
        // The content takes the column minus its 1 dp rule, so the chapter list and
        // the lookup panel agree on where the column's edge is.
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (context.isLookupOpen) {
                context.panel(Modifier.fillMaxSize(), true)
            } else {
                ChapterList(entries = entries, current = current, onChoose = onChoose)
            }
        }
    }
}
