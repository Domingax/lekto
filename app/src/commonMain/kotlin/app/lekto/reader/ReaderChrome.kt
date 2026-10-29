package app.lekto.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lekto.text.AppMessage
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.handCursor
import app.lekto.ui.icons.ALargeSmall
import app.lekto.ui.icons.ArrowLeft
import app.lekto.ui.icons.ChevronLeft
import app.lekto.ui.icons.ChevronRight
import app.lekto.ui.icons.LektoIcons
import app.lekto.ui.plainClickable

/** The top band's height: the icon, the title and the **Aa** control (docs/design.md §6, §7.2). */
internal val CHROME_HEIGHT = 48.dp

/** The bottom band's height: the two chevron controls and the position (docs/design.md §6, §7.2). */
internal val PAGE_BAR_HEIGHT = 64.dp

/** The test tag on the reader's way back to the shelf, an icon in its left band (docs/design.md §7.2). */
internal const val READER_BACK_TAG = "reader-back"

/** The test tag on the book's title in the top band. */
internal const val READER_TITLE_TAG = "reader-title"

/** The test tag on the position line in the bottom band. */
internal const val READER_POSITION_TAG = "reader-position"

/** The test tag on the left chevron, which marks the previous third and turns the page. */
internal const val READER_PREVIOUS_PAGE_TAG = "reader-previous-page"

/** The test tag on the right chevron, which marks the next third and turns the page. */
internal const val READER_NEXT_PAGE_TAG = "reader-next-page"

/** The opacity of a disabled control, matching Material's own content alphas. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The reader's top band (docs/design.md §6, §7.2): the way back, the book's title
 * in the theme's muted ink, and the **Aa** control. Nothing else.
 */
@Composable
internal fun ReaderTitle(title: String, onBack: (() -> Unit)?, onPreferences: () -> Unit) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().height(CHROME_HEIGHT).padding(horizontal = LektoSpacing.ScreenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // `arrow-left`, not a word: the reader's band is an icon, a title and the
        // **Aa** control (docs/design.md §7.2), and the icon mirrors in a
        // right-to-left layout. Its description names it, since no label does.
        onBack?.let { back ->
            IconButton(
                onClick = back,
                modifier = Modifier.handCursor().testTag(READER_BACK_TAG),
            ) {
                Icon(imageVector = LektoIcons.ArrowLeft, contentDescription = texts[TextKey.BACK])
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = LektoSpacing.BetweenRelatedLines)
                .testTag(READER_TITLE_TAG),
        )
        // The `Aa` icon, not its two letters: the identity's set carries it, and
        // its description names the control for a screen reader.
        IconButton(
            onClick = onPreferences,
            modifier = Modifier.handCursor().testTag(READING_PREFERENCES_TAG),
        ) {
            Icon(imageVector = LektoIcons.ALargeSmall, contentDescription = texts[TextKey.READING_PREFERENCES])
        }
    }
}

/**
 * The bottom band (docs/design.md §6, §7.2): two chevrons that mark where the
 * page's tap zones are **and** turn the page when tapped, and the position in the
 * theme's muted ink. A chevron disabled at a bound stays visible but faded, so the
 * band keeps its shape as the reader moves.
 *
 * [hasChapters] is false in the context column (issue #199): the list is already
 * there, so the position stays a label rather than a control that would open a
 * sheet that shape does not have.
 */
@Suppress("LongParameterList") // The bar's controls and its two counters are independent.
@Composable
internal fun ReaderPageBar(
    page: Int,
    total: Int,
    chapter: String?,
    hasChapters: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenChapters: () -> Unit,
) {
    val texts = LocalAppTexts.current
    Row(
        modifier = Modifier.fillMaxWidth().height(PAGE_BAR_HEIGHT).padding(horizontal = LektoSpacing.ScreenEdge),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TapZoneButton(
            imageVector = LektoIcons.ChevronLeft,
            contentDescription = texts[TextKey.PREVIOUS_PAGE],
            enabled = page > 0,
            onClick = onPrevious,
            tag = READER_PREVIOUS_PAGE_TAG,
        )
        ReaderPosition(
            page = page,
            total = total,
            chapter = chapter,
            onOpenChapters = onOpenChapters.takeIf {
                hasChapters
            },
        )
        TapZoneButton(
            imageVector = LektoIcons.ChevronRight,
            contentDescription = texts[TextKey.NEXT_PAGE],
            enabled = page < total - 1,
            onClick = onNext,
            tag = READER_NEXT_PAGE_TAG,
        )
    }
}

/**
 * The position line: the chapter's own title then the page, or the page alone
 * when the book has one chapter or the page is before the first. With chapters
 * it is also the control that opens the chapter list — a quiet label, so it
 * takes the pointing hand and no ripple or Material hover box (docs/design.md
 * §8, ADR-0039).
 */
@Composable
private fun RowScope.ReaderPosition(page: Int, total: Int, chapter: String?, onOpenChapters: (() -> Unit)?) {
    val texts = LocalAppTexts.current
    val base = Modifier
        .weight(1f)
        .testTag(READER_POSITION_TAG)
        .heightIn(min = LektoSpacing.TouchTarget)
    val interactive = if (onOpenChapters != null) {
        base.handCursor().plainClickable(role = Role.Button, onClick = onOpenChapters)
    } else {
        base
    }
    Box(
        // One accessibility node, so a screen reader reads the two lines as one
        // position rather than two stray labels.
        interactive.semantics(mergeDescendants = true) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            chapter?.let { title ->
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = texts.message(AppMessage.of(TextKey.PAGE_OF, page + 1, total)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One chevron marking a tap zone and turning the page: muted ink, and faded while
 * it is disabled at a bound. Its description names the turn, so a screen reader
 * meets a control rather than a decorative glyph.
 */
@Suppress("LongParameterList") // The icon, its spoken name, its state and its tap are independent.
@Composable
private fun TapZoneButton(
    imageVector: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.handCursor(enabled).testTag(tag),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA),
        )
    }
}
