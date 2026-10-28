package app.lekto.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lekto.text.LocalAppTexts
import app.lekto.text.TextKey
import app.lekto.theme.LektoSpacing
import app.lekto.ui.LookupCloseButton
import app.lekto.ui.handCursor
import app.lekto.ui.swallowTaps

/** How much of the reader the chapter sheet may cover (ADR-0037, docs/design.md §7.2). */
internal const val CHAPTER_SHEET_MAX_HEIGHT_FRACTION: Float = 0.6f

/** The indent one level of the chapter hierarchy adds to its rows. */
private val CHAPTER_INDENT = 16.dp

/** How strongly the hovered chapter row tints with the accent ink. */
private const val HOVER_WASH_ALPHA = 0.08f

/** The sheet's drag handle: its bar's width and height. */
private val HANDLE_WIDTH = 32.dp
private val HANDLE_HEIGHT = 4.dp

/** The test tag on one chapter row, by its index in the flattened list. */
internal fun readerChapterRowTag(index: Int): String = "reader-chapter-row-$index"

/**
 * The chapter list (ADR-0037; docs/design.md §7.2): a bottom sheet over the
 * page, capped at [CHAPTER_SHEET_MAX_HEIGHT_FRACTION] of it, keeping the book's
 * hierarchy. It highlights the current chapter and scrolls it into view when it
 * opens, and closes on a choice, a tap outside, **Close** or Back.
 *
 * A **Part** that only groups is a heading, not a choice: it has no text of its
 * own to jump to. A leaf, or a part with text before its first child, is a row
 * that moves the **Reading position**.
 */
@Suppress("LongParameterList") // The list, its place and its two actions are the sheet's whole surface.
@Composable
internal fun ChapterSheet(
    entries: List<ReaderChapterEntry>,
    current: ReaderChapterEntry?,
    onChoose: (ReaderChapterEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val maxSheetHeight = maxHeight * CHAPTER_SHEET_MAX_HEIGHT_FRACTION
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .testTag(READER_CHAPTER_SHEET_TAG),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Box(Modifier.fillMaxWidth()) {
                // The sheet's own surface swallows taps, so only a tap truly outside
                // it — the scrim behind — dismisses it.
                Box(Modifier.matchParentSize().swallowTaps())
                ChapterSheetBody(entries, current, onChoose, onDismiss)
            }
        }
    }
}

@Composable
private fun ChapterSheetBody(
    entries: List<ReaderChapterEntry>,
    current: ReaderChapterEntry?,
    onChoose: (ReaderChapterEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val texts = LocalAppTexts.current
    val listState = rememberLazyListState()
    val currentIndex = entries.indexOfFirst { entry -> entry === current }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }
    Column(Modifier.fillMaxWidth()) {
        DragHandle()
        Row(
            modifier = Modifier.fillMaxWidth().padding(
                start = LektoSpacing.InsidePanel,
                end = LektoSpacing.InsideLabelPair,
                top = LektoSpacing.InsideLabelPair,
                bottom = LektoSpacing.BetweenRelatedLines,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = texts[TextKey.CHAPTERS],
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            ChapterSheetClose(onDismiss)
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(entries) { index, entry ->
                ChapterRow(
                    entry = entry,
                    current = entry === current,
                    onClick = { onChoose(entry) },
                    tag = readerChapterRowTag(index),
                )
            }
        }
    }
}

/** The sheet's close action, named so a screen reader meets a control. */
@Composable
private fun ChapterSheetClose(onDismiss: () -> Unit) {
    LookupCloseButton(onDismiss = onDismiss, tag = READER_CHAPTER_SHEET_CLOSE_TAG)
}

/**
 * One row: indented by its depth, a heading when it only groups, and a control
 * otherwise. The current chapter carries the accent colour and the selected
 * state, so it reads as "where you are" as well as looking like it.
 */
@Composable
private fun ChapterRow(entry: ReaderChapterEntry, current: Boolean, onClick: () -> Unit, tag: String) {
    val theme = MaterialTheme.colorScheme
    val choice = entry.chapter.isReadingUnit
    val indent = CHAPTER_INDENT * entry.depth
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash = if (hovered) theme.primary.copy(alpha = HOVER_WASH_ALPHA) else Color.Transparent
    val base = Modifier
        .fillMaxWidth()
        .background(wash)
        .padding(start = LektoSpacing.InsidePanel + indent, end = LektoSpacing.InsidePanel)
        .heightIn(min = LektoSpacing.TouchTarget)
    val modifier = when {
        !choice -> base.padding(vertical = LektoSpacing.BetweenRelatedLines).testTag(tag)

        else ->
            base
                .testTag(tag)
                .handCursor()
                // A selector row shows no ripple, and its hover is a tint of the
                // accent ink rather than Material's grey box (docs/design.md §8).
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .semantics { selected = current }
                .padding(vertical = LektoSpacing.BetweenRelatedLines)
    }
    Text(
        text = entry.chapter.title,
        style = if (choice) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,
        color = if (current) {
            theme.primary
        } else if (choice) {
            theme.onSurface
        } else {
            theme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

/** The grabber that says the sheet was raised from the bottom edge. It is a hint, not a control. */
@Composable
private fun DragHandle() {
    Box(
        Modifier.fillMaxWidth().padding(top = LektoSpacing.BetweenRelatedLines),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(HANDLE_WIDTH)
                .height(HANDLE_HEIGHT)
                .background(MaterialTheme.colorScheme.outline, CircleShape),
        )
    }
}
